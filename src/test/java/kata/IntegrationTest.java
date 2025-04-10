package kata;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class IntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pgvector = new PostgreSQLContainer<>("postgres:13.3")
            .withDatabaseName("delivery")
            .withUsername("admin")
            .withPassword("admin");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private NoOpEmailGateway emailGateway;

    @Test
    void it_works() {
        String newDeliveryJson = """
                {
                  "email": "test@example.com",
                  "latitude": 58.377065,
                  "longitude": 26.727897
                }""";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> newDeliveryRequest = new HttpEntity<>(newDeliveryJson, headers);
        restTemplate.exchange("/delivery/new", HttpMethod.POST, newDeliveryRequest, String.class);

        String deliveryJson = """
                {
                  "id": 1,
                  "timeOfDelivery": "%s",
                  "latitude": 58.377066,
                  "longitude": 26.727897
                }""".formatted(LocalDateTime.now());
        HttpEntity<String> deliveryRequest = new HttpEntity<>(deliveryJson, headers);
        restTemplate.exchange("/delivery/update", HttpMethod.POST, deliveryRequest, String.class);

        // Verify the email sent
        assertThat(emailGateway.getLastRecipient()).isEqualTo("test@example.com");
        assertThat(emailGateway.getLastSubject()).isNotNull();
        assertThat(emailGateway.getLastMessage()).contains("Regarding your delivery today at ", "How likely would you be to recommend this delivery service to a friend?");

    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public EmailGateway emailGateway() {
            return new NoOpEmailGateway();
        }
    }
}
