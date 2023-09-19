package no.statkart.matrikkel.keycloak.email;

import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.models.*;
import com.microsoft.graph.requests.GraphServiceClient;
import okhttp3.Request;
import org.jboss.logging.Logger;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailSenderProvider;
import org.keycloak.models.KeycloakSession;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OauthEmailSenderProvider implements EmailSenderProvider {

    private static final Logger logger = Logger.getLogger(OauthEmailSenderProvider.class);

    private final String userId;
    private final GraphServiceClient<Request> graphServiceClient;

    public OauthEmailSenderProvider(GraphServiceClient<Request> graphServiceClient, String userId) {
        this.graphServiceClient = graphServiceClient;
        this.userId = userId;
        logger.debug("Init OauthEmailSenderProvider");
    }

    @Override
    public void send(Map<String, String> config, String address, String subject, String textBody, String htmlBody) throws EmailException {
        logger.debugv("Sending email with OauthEmailSenderProvider. address: {0}, subject: {1}, textBody: {2}, htmlBody: {3}",
                address, subject, textBody, htmlBody);

        try {
            graphServiceClient.users(userId)
                    .sendMail(UserSendMailParameterSet.newBuilder()
                            .withMessage(createMessage(subject, address, textBody, htmlBody))
                            .withSaveToSentItems(false)
                            .build())
                    .buildRequest()
                    .post();
            logger.debug("Email sent using OauthEmailSenderProvider");
        } catch (ClientException e) {
            throw new EmailException("Error sending email using OauthEmailSenderProvider", e);
        }
    }

    private static Message createMessage(String subject, String recipient, String textBody, String htmlBody) {
        Message message = new Message();
        message.toRecipients = List.of(createRecipient(recipient));
        message.subject = subject;
        message.body = createBody(textBody, htmlBody).orElse(null);
        return message;
    }

    private static Optional<ItemBody> createBody(String textBody, String htmlBody) {
        if (htmlBody != null) {
            ItemBody itemBody = new ItemBody();
            itemBody.content = htmlBody;
            itemBody.contentType = BodyType.HTML;
            return Optional.of(itemBody);
        }

        if (textBody != null) {
            ItemBody itemBody = new ItemBody();
            itemBody.content = textBody;
            itemBody.contentType = BodyType.TEXT;
            return Optional.of(itemBody);
        }

        return Optional.empty();
    }

    private static Recipient createRecipient(String emailAddress) {
        Recipient r = new Recipient();
        r.emailAddress = new EmailAddress();
        r.emailAddress.address = emailAddress;
        return r;
    }

    @Override
    public void close() {

    }
}
