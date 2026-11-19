package pl.domradar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Uwierzytelnianie realizujemy własnym AuthService + JWT, dlatego domyślny UserDetailsService jest wyłączony. */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class DomRadarApplication {

    public static void main(String[] args) {
        SpringApplication.run(DomRadarApplication.class, args);
    }
}
