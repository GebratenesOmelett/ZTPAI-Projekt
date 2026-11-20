package pl.domradar.ingestion.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import pl.domradar.ingestion.ImportFinalizer;
import pl.domradar.ingestion.ImportJobService;

/**
 * Topologia RabbitMQ:
 * <pre>
 * domradar.ingestion (direct) ──► domradar.import.requests ──(po 3 próbach)──► domradar.ingestion.dlx ──► *.dlq
 *                             └─► domradar.listing.batches ──(po 3 próbach)──┘
 * </pre>
 * Retry: 3 próby z wykładniczym backoffem, następnie recoverer odnotowuje błąd w ImportJob
 * i odrzuca wiadomość bez ponownego kolejkowania → trafia do DLQ.
 */
@Slf4j
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "domradar.ingestion";
    public static final String DLX = "domradar.ingestion.dlx";
    public static final String IMPORT_QUEUE = "domradar.import.requests";
    public static final String BATCH_QUEUE = "domradar.listing.batches";
    public static final String IMPORT_DLQ = IMPORT_QUEUE + ".dlq";
    public static final String BATCH_DLQ = BATCH_QUEUE + ".dlq";

    private static final int MAX_ATTEMPTS = 3;

    @Bean
    DirectExchange ingestionExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    Queue importQueue() {
        return QueueBuilder.durable(IMPORT_QUEUE).deadLetterExchange(DLX).deadLetterRoutingKey(IMPORT_DLQ).build();
    }

    @Bean
    Queue batchQueue() {
        return QueueBuilder.durable(BATCH_QUEUE).deadLetterExchange(DLX).deadLetterRoutingKey(BATCH_DLQ).build();
    }

    @Bean
    Queue importDeadLetterQueue() {
        return QueueBuilder.durable(IMPORT_DLQ).build();
    }

    @Bean
    Queue batchDeadLetterQueue() {
        return QueueBuilder.durable(BATCH_DLQ).build();
    }

    @Bean
    Declarables bindings() {
        return new Declarables(
                BindingBuilder.bind(importQueue()).to(ingestionExchange()).with(IMPORT_QUEUE),
                BindingBuilder.bind(batchQueue()).to(ingestionExchange()).with(BATCH_QUEUE),
                BindingBuilder.bind(importDeadLetterQueue()).to(deadLetterExchange()).with(IMPORT_DLQ),
                BindingBuilder.bind(batchDeadLetterQueue()).to(deadLetterExchange()).with(BATCH_DLQ));
    }

    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("pl.domradar.ingestion.messaging", "pl.domradar.ingestion.adapter");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    /** Liczba równoległych importów (różnych źródeł). */
    static final int IMPORT_WORKERS = 4;

    @Bean
    SimpleRabbitListenerContainerFactory importListenerFactory(SimpleRabbitListenerContainerFactoryConfigurer configurer,
                                                               ConnectionFactory connectionFactory,
                                                               MessageConverter converter,
                                                               @Lazy ImportJobService jobService) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(converter);
        // każdy worker pobiera inny portal (jedno aktywne zadanie na źródło), więc limit żądań na portal jest zachowany
        factory.setConcurrentConsumers(IMPORT_WORKERS);
        factory.setMaxConcurrentConsumers(IMPORT_WORKERS);
        factory.setPrefetchCount(1);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(MAX_ATTEMPTS)
                .backOffOptions(1000, 2.0, 8000)
                .recoverer(importRecoverer(converter, jobService))
                .build());
        return factory;
    }

    /** Konsument batchy działa sekwencyjnie (1 wątek), co eliminuje wyścigi przy deduplikacji między portalami. */
    @Bean
    SimpleRabbitListenerContainerFactory batchListenerFactory(SimpleRabbitListenerContainerFactoryConfigurer configurer,
                                                              ConnectionFactory connectionFactory,
                                                              MessageConverter converter,
                                                              @Lazy ImportJobService jobService,
                                                              @Lazy ImportFinalizer finalizer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(converter);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setPrefetchCount(5);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(MAX_ATTEMPTS)
                .backOffOptions(500, 2.0, 5000)
                .recoverer(batchRecoverer(converter, jobService, finalizer))
                .build());
        return factory;
    }

    private static MessageRecoverer importRecoverer(MessageConverter converter, ImportJobService jobService) {
        return (message, cause) -> {
            Object payload = safeConvert(converter, message);
            if (payload instanceof ImportRequestedMessage m) {
                jobService.markFailed(m.jobId(), "Import nie powiódł się po " + MAX_ATTEMPTS + " próbach: " + rootMessage(cause));
            }
            log.error("Import request moved to DLQ: {}", rootMessage(cause));
            throw new AmqpRejectAndDontRequeueException("Retries exhausted", cause);
        };
    }

    private static MessageRecoverer batchRecoverer(MessageConverter converter, ImportJobService jobService, ImportFinalizer finalizer) {
        return (message, cause) -> {
            Object payload = safeConvert(converter, message);
            if (payload instanceof ListingBatchMessage m) {
                if (jobService.registerFailedBatch(m.jobId(), m.runToken(), m.listings().size())) {
                    finalizer.finalizeIfComplete(m.jobId());
                }
            }
            log.error("Listing batch moved to DLQ: {}", rootMessage(cause));
            throw new AmqpRejectAndDontRequeueException("Retries exhausted", cause);
        };
    }

    private static Object safeConvert(MessageConverter converter, Message message) {
        try {
            return converter.fromMessage(message);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
