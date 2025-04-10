package kata;

import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

class NoOpEmailGateway implements EmailGateway {

    private static final Logger log = getLogger(NoOpEmailGateway.class);

    private String lastRecipient;
    private String lastSubject;
    private String lastMessage;

    @Override
    public void send(String to, String s, String m) {
        log.info("Sending email with subject \"{}\" to {} with message \"{}\"", s, to, m);
        this.lastRecipient = to;
        this.lastSubject = s;
        this.lastMessage = m;
    }

    public String getLastRecipient() {
        return lastRecipient;
    }

    public String getLastSubject() {
        return lastSubject;
    }

    public String getLastMessage() {
        return lastMessage;
    }
}
