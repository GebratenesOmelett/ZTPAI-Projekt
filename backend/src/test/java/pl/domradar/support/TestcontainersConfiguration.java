package pl.domradar.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Kontenery infrastruktury dla testów integracyjnych. Spring cache'uje kontekst,
 * więc kontenery są współdzielone przez wszystkie klasy testowe z tą konfiguracją.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:16-3.5")
                .asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("domradar")
                .withUsername("domradar")
                .withPassword("domradar");
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.1-management"));
    }

    /** Wspólny mock serwera SMTP dla wszystkich testów (jeden kontekst Springa = jeden zestaw kontenerów). */
    @Bean
    org.springframework.mail.javamail.JavaMailSender javaMailSender() {
        return org.mockito.Mockito.mock(org.springframework.mail.javamail.JavaMailSender.class);
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    }
}
