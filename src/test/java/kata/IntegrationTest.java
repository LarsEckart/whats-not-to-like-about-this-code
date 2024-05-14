package kata;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

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
                  "id": 2,
                  "timeOfDelivery": "%s",
                  "latitude": 58.377066,
                  "longitude": 26.727897
                }""".formatted(LocalDateTime.now());
        HttpEntity<String> deliveryRequest = new HttpEntity<>(deliveryJson, headers);
        restTemplate.exchange("/delivery/update", HttpMethod.POST, deliveryRequest, String.class);

    }
}
