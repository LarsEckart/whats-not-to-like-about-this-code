package kata;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

@RestController
@RequestMapping("/delivery")
public class DeliveryController {

    private static final Logger log = getLogger(DeliveryController.class);

    @Autowired
    DeliveryRepository repository;
    @Autowired
    SendgridEmailGatewayImpl emailGateway;
    @Autowired
    MapService mapService;

    @PostMapping(value = "/new", consumes = MediaType.APPLICATION_JSON_VALUE)

    public ResponseEntity<String> newDelivery(@RequestBody NewDelivery newDelivery) {
        log.info("create new delivery");
        repository.create(newDelivery.email(), newDelivery.longitude(), newDelivery.latitude());
        return ResponseEntity.ok("all good");
    }

    @PostMapping(value = "/update", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public void onDelivery(@RequestBody DeliveryEvent deliveryEvent) {
        log.info("update delivery");
        try {
            List<Delivery> deliverySchedule = repository.findTodaysDeliveries();
            Delivery nextDelivery = null;
            for (int i = 0; i < deliverySchedule.size(); i++) {
                Delivery delivery = deliverySchedule.get(i);
                // find current delivery
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
                    // check for existence of delivery after current one
                    if (deliverySchedule.size() > i + 1) {
                        nextDelivery = deliverySchedule.get(i + 1);
                    }

                    if (!delivery.isOnTime() && deliverySchedule.size() > 1 && i > 0) {
                        var previousDelivery = deliverySchedule.get(i - 1);
                        Duration elapsedTime =
                                Duration.between(previousDelivery.getTimeOfDelivery(), delivery.getTimeOfDelivery());
                        mapService.updateAverageSpeed(elapsedTime, previousDelivery.getLongitude(),
                                previousDelivery.getLatitude(), delivery.getLongitude(), delivery.getLatitude());
                    }
                    repository.save(delivery);
                }
            }

            if (nextDelivery != null) {
                var nextEta = mapService.calculateETA(
                        deliveryEvent.longitude(), deliveryEvent.latitude(),
                        nextDelivery.getLongitude(), nextDelivery.getLatitude());
                String subject = "Your delivery will arrive soon";
                var message =
                        "Your delivery to [%s,%s] is next, estimated time of arrival is in %s minutes. Be ready!"
                                .formatted(
                                        nextDelivery.getLongitude(),
                                        nextDelivery.getLatitude(),
                                        nextEta.getSeconds() / 60);
                emailGateway.send(nextDelivery.getContactEmail(), subject, message);
            }

        } catch (Exception e) {

        }
    }

    private record NewDelivery(String email, Float latitude, Float longitude) {
    }

    private record DeliveryEvent(long id, LocalDateTime timeOfDelivery, float latitude, float longitude) {
    }

}
