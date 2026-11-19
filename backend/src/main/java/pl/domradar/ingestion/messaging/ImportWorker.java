package pl.domradar.ingestion.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import pl.domradar.ingestion.ImportFinalizer;
import pl.domradar.ingestion.ImportJobService;
import pl.domradar.ingestion.IngestionProperties;
import pl.domradar.ingestion.adapter.AdapterRegistry;
import pl.domradar.ingestion.adapter.RawListing;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Worker importu: pobiera ogłoszenia przez adapter źródła i publikuje je w paczkach (batchach)
 * do kolejki normalizacji i deduplikacji. Wyjątek adaptera → ponowienie (nowy run_token), po 3 próbach → DLQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportWorker {

    private final ImportJobService jobService;
    private final ImportFinalizer finalizer;
    private final AdapterRegistry adapterRegistry;
    private final RabbitTemplate rabbitTemplate;
    private final IngestionProperties properties;

    @RabbitListener(queues = RabbitConfig.IMPORT_QUEUE, containerFactory = "importListenerFactory")
    public void onImportRequested(ImportRequestedMessage message) {
        ImportJobService.RunInfo run = jobService.startRun(message.jobId()).orElse(null);
        if (run == null) {
            return;
        }
        log.info("Import job {} started: source={}, adapter={}, run={}", message.jobId(),
                run.context().sourceCode(), run.adapterCode(), run.context().runNumber());
        int batchSize = properties.batchSize();
        int fetched = 0;
        int batchNo = 0;
        List<RawListing> buffer = new ArrayList<>(batchSize);
        try (Stream<RawListing> stream = adapterRegistry.get(run.adapterCode()).fetch(run.context())) {
            Iterator<RawListing> it = stream.iterator();
            while (it.hasNext()) {
                buffer.add(it.next());
                fetched++;
                if (buffer.size() == batchSize) {
                    publish(run, ++batchNo, buffer);
                    buffer = new ArrayList<>(batchSize);
                }
            }
        }
        if (!buffer.isEmpty()) {
            publish(run, ++batchNo, buffer);
        }
        if (jobService.fetchCompleted(message.jobId(), run.runToken(), fetched, batchNo)) {
            finalizer.finalizeIfComplete(message.jobId());
        }
        log.info("Import job {}: fetched {} listings in {} batches", message.jobId(), fetched, batchNo);
    }

    private void publish(ImportJobService.RunInfo run, int batchNo, List<RawListing> listings) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.BATCH_QUEUE,
                new ListingBatchMessage(run.context().jobId(), run.sourceId(), run.runToken(), batchNo, listings));
    }
}
