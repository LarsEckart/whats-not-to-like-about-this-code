package kata;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

class NoOpEmailGateway implements EmailGateway {

    private static final Logger log = getLogger(NoOpEmailGateway.class);

    private final List<Email> sentEmails = new ArrayList<>();

    @Override
    public void send(String to, String s, String m) {
        log.info("Sending email with subject \"{}\" to {} with message \"{}\"", s, to, m);
        sentEmails.add(new Email(to, s, m));
    }

    public List<Email> getSentEmails() {
        return Collections.unmodifiableList(sentEmails);
    }

    public Email getLastEmail() {
        return sentEmails.isEmpty() ? null : sentEmails.get(sentEmails.size() - 1);
    }

    public record Email(String recipient, String subject, String message) {}
}
