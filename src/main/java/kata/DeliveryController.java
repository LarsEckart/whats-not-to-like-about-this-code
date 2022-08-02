package kata;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import jakarta.inject.Inject;
import java.time.ZoneOffset;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

@Controller("/delivery")
public class DeliveryController {

    private static final Logger log = getLogger(DeliveryController.class);

    @Inject
    DeliveryRepository repository;
    @Inject
    SendgridEmailGatewayImpl emailGateway;
    @Inject
    MapService mapService;

    @Consumes({MediaType.APPLICATION_JSON})
    @Post("/new")
    public HttpResponse<String> newDelivery(NewDelivery newDelivery) {
        log.info("create new delivery");
        repository.create(newDelivery.email(), newDelivery.longitude(), newDelivery.latitude());
        return HttpResponse.ok("all good");
    }

    private record NewDelivery(String email, float latitude, float longitude) {
    }

    @Consumes({MediaType.APPLICATION_JSON})
    @Post
    public HttpResponse<Void> onDelivery(DeliveryEvent deliveryEvent) {
        log.info("update delivery");
        try {
            List<Delivery> deliverySchedule = repository.findTodaysDeliveries();
            Delivery nextDelivery = null;
            for (int i = 0; i < deliverySchedule.size(); i++) {
                Delivery delivery = deliverySchedule.get(i);
                if (deliveryEvent.id() == delivery.getId()) {
                    delivery.setArrived(true);
                    long millis = delivery.getTimeOfDelivery().toInstant(ZoneOffset.UTC).toEpochMilli()
                        - deliveryEvent.timeOfDelivery().toInstant(ZoneOffset.UTC).toEpochMilli();

                    long earlyOrLateInMinutes = Math.abs((millis / 1000) / 60);

                    if (earlyOrLateInMinutes < 10 == true)
                        delivery.setOnTime(true);

                    delivery.setTimeOfDelivery(deliveryEvent.timeOfDelivery());
                    String message =
                            """
                                    Regarding your delivery today at %s.
                                    How likely would you be to recommend this delivery service to a friend? 
                                                            
                                    Click <a href='http://example.com/feedback'>here</a>""".formatted(
                                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(delivery.getTimeOfDelivery()));
                    emailGateway.send(delivery.getContactEmail(), "Your feedback is important to us", message);
                    if (deliverySchedule.size() > i + 1) {
                        nextDelivery = deliverySchedule.get(i + 1);
                    }

                    if (!delivery.isOnTime() && deliverySchedule.size() > 1 && i > 0) {
                        var previousDelivery = deliverySchedule.get(i - 1);
                        Duration elapsedTime =
                                Duration.between(previousDelivery.getTimeOfDelivery(), delivery.getTimeOfDelivery());
                        mapService.updateAverageSpeed(
                                elapsedTime, previousDelivery.getLatitude(),
                                previousDelivery.getLongitude(), delivery.getLatitude(),
                                delivery.getLongitude());
                    }
                    repository.save(delivery);
                }
            }

            if (nextDelivery != null) {
                var nextEta = mapService.calculateETA(
                        deliveryEvent.latitude(), deliveryEvent.longitude(),
                        nextDelivery.getLatitude(), nextDelivery.getLongitude());
                String subject = "Your delivery will arrive soon";
                var message =
                        "Your delivery to [%s,%s] is next, estimated time of arrival is in %s minutes. Be ready!"
                                .formatted(
                                        nextDelivery.getLatitude(),
                                        nextDelivery.getLongitude(),
                                        nextEta.getSeconds() / 60);
                emailGateway.send(nextDelivery.getContactEmail(), subject, message);
            }

            return HttpResponse.ok(); // 200
        } catch (Exception e) {
            // if status is not in 2xx range our http client in tests throws exception
            return HttpResponse.noContent(); // 204
        }
    }

    private record DeliveryEvent(long id, LocalDateTime timeOfDelivery, float latitude, float longitude) {
    }

}
