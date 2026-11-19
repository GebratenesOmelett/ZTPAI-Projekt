package pl.domradar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DomRadarApplication {

    public static void main(String[] args) {
        SpringApplication.run(DomRadarApplication.class, args);
    }
}
