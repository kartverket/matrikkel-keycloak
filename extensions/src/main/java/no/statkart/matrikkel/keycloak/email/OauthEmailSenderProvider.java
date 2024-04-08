package no.statkart.matrikkel.keycloak.email;

import com.microsoft.graph.models.*;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import org.jboss.logging.Logger;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailSenderProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OauthEmailSenderProvider implements EmailSenderProvider {

    private static final Logger logger = Logger.getLogger(OauthEmailSenderProvider.class);

    private final String userId;
    private final GraphServiceClient graphServiceClient;

    public OauthEmailSenderProvider(GraphServiceClient graphServiceClient, String userId) {
        this.graphServiceClient = graphServiceClient;
        this.userId = userId;
        logger.debug("Init OauthEmailSenderProvider");
    }

    @Override
    public void send(Map<String, String> config, String address, String subject, String textBody, String htmlBody) throws EmailException {
        logger.debugv("Sending email with OauthEmailSenderProvider");

        SendMailPostRequestBody sendMailPostRequestBody = new SendMailPostRequestBody();
        sendMailPostRequestBody.setMessage(createMessage(subject, address, textBody, htmlBody));
        sendMailPostRequestBody.setSaveToSentItems(false);

        graphServiceClient.users().byUserId(userId).sendMail().post(sendMailPostRequestBody);
        logger.debug("Email sent using OauthEmailSenderProvider");
    }

    private static Message createMessage(String subject, String recipient, String textBody, String htmlBody) {
        Message message = new Message();
        message.setToRecipients(List.of(createRecipient(recipient)));
        message.setSubject(subject);
        message.setBody(createBody(textBody, htmlBody).orElse(null));
        return message;
    }

    private static Optional<ItemBody> createBody(String textBody, String htmlBody) {
        if (htmlBody != null) {
            ItemBody itemBody = new ItemBody();
            itemBody.setContent(htmlBody);
            itemBody.setContentType(BodyType.Html);
            return Optional.of(itemBody);
        }

        if (textBody != null) {
            ItemBody itemBody = new ItemBody();
            itemBody.setContent(textBody);
            itemBody.setContentType(BodyType.Text);
            return Optional.of(itemBody);
        }

        return Optional.empty();
    }

    private static Recipient createRecipient(String emailAddress) {
        Recipient r = new Recipient();
        EmailAddress address = new EmailAddress();
        address.setAddress(emailAddress);
        r.setEmailAddress(address);
        return r;
    }

    @Override
    public void close() {

    }
}
