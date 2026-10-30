package no.statkart.matrikkel.keycloak.email;

import com.microsoft.graph.models.*;
import com.microsoft.graph.models.odataerrors.ODataError;
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

        try {
            graphServiceClient.users().byUserId(userId).sendMail().post(sendMailPostRequestBody);
        } catch (ODataError e) {
            String code = e.getError() != null ? e.getError().getCode() : null;
            String message = e.getError() != null ? e.getError().getMessage() : e.getMessage();
            logger.errorf(e, "MS Graph sendMail failed: status=%d, code=%s, message=%s",
                    e.getResponseStatusCode(), code, message);
            throw new EmailException(String.format("MS Graph sendMail failed: status=%d, code=%s, message=%s",
                    e.getResponseStatusCode(), code, message), e);
        } catch (RuntimeException e) {
            logger.errorf(e, "MS Graph sendMail failed: %s", e.getMessage());
            throw new EmailException("MS Graph sendMail failed: " + e.getMessage(), e);
        }
        logger.debug("Email sent using OauthEmailSenderProvider");
    }

    @Override
    public void validate(Map<String, String> config) throws EmailException {
        // MSGraph deals with validation for us
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
