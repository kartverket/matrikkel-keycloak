package no.statkart.matrikkel.keycloak;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import no.statkart.matrikkel.keycloak.scheduler.ScheduledTask;
import no.statkart.matrikkel.keycloak.util.UserModels;
import org.jboss.logging.Logger;
import org.keycloak.authentication.actiontoken.execactions.ExecuteActionsActionToken;
import org.keycloak.credential.CredentialModel;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.PasswordPolicy;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.services.resources.LoginActionsService;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AktiveringReminderTask extends ScheduledTask.Simple {
    public static final String SENT_INITIAL_REMINDER_CREATED_AT = "matrikkel.sent_initial_reminder_created_at";
    public static final String SENT_LAST_REMINDER_CREATED_AT = "matrikkel.sent_last_reminder_created_at";
    public static final String SEND_AKTIVERING_REMINDER = "matrikkel.send_aktivering_reminder";
    public static final String ONLY_PROGRAMVAREBRUKER = "matrikkel.only_programvarebruker";
    private static final Logger log = Logger.getLogger(AktiveringReminderTask.class);
    private static final ZoneId TZ = ZoneId.of("Europe/Oslo");
    private static final CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);

    @Override
    protected Optional<Cron> getCron(RealmModel realm) {
        String attribute = realm.getAttribute(SEND_AKTIVERING_REMINDER);
        if (attribute!= null) {
            try {
                return Optional.of(new CronParser(cronDefinition).parse(attribute));
            } catch (IllegalArgumentException e) {
                log.errorf(e, "Unable to parse cron expression for %s in realm %s", SEND_AKTIVERING_REMINDER, realm.getName());
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    protected ZoneId getTimeZone() {
        return TZ;
    }

    @Override
    public String getId() {
        return "matrikkel-aktivering-reminder";
    }

    @Override
    protected void run(KeycloakSession keycloakSession) {
        RealmModel realm = keycloakSession.getContext().getRealm();
        int realmDaysToExpirePassword;
        if (realm.getPasswordPolicy().getPolicies().contains(PasswordPolicy.FORCE_EXPIRED_ID)) {
            realmDaysToExpirePassword = realm.getPasswordPolicy().getDaysToExpirePassword();
        } else {
            realmDaysToExpirePassword = 0;
        }
        try (Stream<UserModel> allUsers = keycloakSession.users().searchForUserStream(realm, Collections.emptyMap())) {
            LocalDate today = LocalDate.now(TZ);
            allUsers.forEach(user -> {
                if (!user.isEnabled()) {
                    log.debugf("Aktiverings-email skipped for %s in %s realm: User not enabled", user.getUsername(), realm.getName());
                    return;
                }

                if (realm.getAttribute(ONLY_PROGRAMVAREBRUKER, false) && !UserModels.isProgramvarebruker(user)) {
                    log.debugf("Aktiverings-email skipped for %s in %s realm: User is not a 'programvarebruker'", user.getUsername(), realm.getName());
                    return;
                }

                boolean midlertidigUserExpired = user
                        .getAttributeStream(UserModels.USER_EXPIRES_AT)
                        .flatMap(s -> {
                            try {
                                return Stream.of(Instant.ofEpochMilli(Long.parseLong(s)));
                            } catch (NumberFormatException | DateTimeException e) {
                                return Stream.empty();
                            }
                        })
                        .findAny()
                        .isPresent();
                if (midlertidigUserExpired) {
                    log.debugf("Aktiverings-email skipped for %s in %s realm: Temporary user", user.getUsername(), realm.getName());
                    return;
                }

                List<CredentialModel> passwords = user.credentialManager().getStoredCredentialsByTypeStream(PasswordCredentialModel.TYPE).collect(Collectors.toList());
                if (passwords.size() != 1) {
                    log.debugf("Aktiverings-email skipped for %s in %s realm: User has no (or multiple?) password(s)", user.getUsername(), realm.getName(), realm.getName());
                    return;
                }
                CredentialModel credential = passwords.get(0);

                Long passwordCreatedEpochMilli = credential.getCreatedDate();
                if (passwordCreatedEpochMilli == null) {
                    log.warnf("Aktiverings-email skipped for %s in %s realm: Password has no age", user.getUsername(), realm.getName());
                    return;
                }

                int daysToExpirePassword = daysToExpirePassword(user).orElse(realmDaysToExpirePassword);
                if (daysToExpirePassword <= 0) {
                    log.debugf("Aktiverings-email skipped for %s in %s realm: User's password does not expire", user.getUsername(), realm.getName());
                    return;
                }

                if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
                    log.warnf("Aktiverings-email skipped for %s in %s realm: User has no email address", user.getUsername(), realm.getName());
                    return;
                }

                LocalDate passwordCreatedDay = Instant.ofEpochMilli(passwordCreatedEpochMilli).atZone(TZ).toLocalDate();
                LocalDate passwordExpiresDay = passwordCreatedDay.plusDays(daysToExpirePassword);
                LocalDate passwordReminderSendDay = passwordExpiresDay.minusDays(AktiveringAuthenticator.PASSWORD_UPDATE_LEEWAY_DAYS);
                LocalDate passwordLeewayExpiresDay = passwordExpiresDay.plusDays(AktiveringAuthenticator.PASSWORD_UPDATE_LEEWAY_DAYS);
                if (today.isBefore(passwordReminderSendDay)) {
                    log.debugf(
                            "Aktiverings-email skipped for %s in %s realm: " +
                                    "Password(created: %s) does not expire yet(%s). Reminder will be sent %s",
                            user.getUsername(), realm.getName(), passwordCreatedDay, passwordExpiresDay, passwordReminderSendDay);

                    return;
                }
                if (today.compareTo(passwordLeewayExpiresDay) >= 0) {
                    log.debugf(
                            "Aktiverings-email skipped for %s in %s realm: " +
                                    "Password(created: %s) has passed leeway for password update(%s)",
                            user.getUsername(), realm.getName(), passwordCreatedDay, passwordLeewayExpiresDay);

                    return;
                }

                LocalDate emailExpiresDay;
                String userAttribute;
                if (today.isBefore(passwordExpiresDay)) {
                    userAttribute = SENT_INITIAL_REMINDER_CREATED_AT;
                    if (getLongUserAttribute(user, userAttribute)
                            .map(createdAt -> createdAt.equals(passwordCreatedEpochMilli))
                            .orElse(false))
                    {
                        log.debugf(
                                "Aktiverings-email skipped for %s in %s realm: " +
                                        "Initial reminder already sent, next reminder will be sent %s",
                                user.getUsername(), realm.getName(), passwordExpiresDay);
                        return;
                    }
                    emailExpiresDay = passwordExpiresDay;
                } else {
                    userAttribute = SENT_LAST_REMINDER_CREATED_AT;
                    if (getLongUserAttribute(user, userAttribute)
                            .map(createdAt -> createdAt.equals(passwordCreatedEpochMilli))
                            .orElse(false))
                    {
                        log.debugf(
                                "Aktiverings-email skipped for %s in %s realm: " +
                                        "Last reminder already sent, leeway expires %s",
                                user.getUsername(), realm.getName(), passwordLeewayExpiresDay);
                        return;
                    }
                    user.addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);
                    user.addRequiredAction(UserModel.RequiredAction.VERIFY_EMAIL);
                    emailExpiresDay = passwordLeewayExpiresDay;
                }

                ExecuteActionsActionToken token = new ExecuteActionsActionToken(
                        user.getId(), Math.toIntExact(Instant.EPOCH.until(emailExpiresDay.atStartOfDay(TZ).toInstant(), ChronoUnit.SECONDS)),
                        Arrays.asList("VERIFY_EMAIL", "UPDATE_PASSWORD"),
                        null,
                        Constants.ACCOUNT_MANAGEMENT_CLIENT_ID);
                String link = LoginActionsService
                        .actionTokenProcessor(keycloakSession.getContext().getUri())
                        .queryParam("key", token.serialize(keycloakSession, realm, keycloakSession.getContext().getUri()))
                        .build(realm.getName())
                        .toString();
                EmailTemplateProvider emailTemplateProvider = keycloakSession.getProvider(EmailTemplateProvider.class)
                        .setAttribute(Constants.TEMPLATE_ATTR_REQUIRED_ACTIONS, token.getRequiredActions())
                        .setRealm(realm)
                        .setUser(user);

                // Expiration truncated til hele dager eller hele timer, for å få bedre
                // formatering på e-postens utløpstidspunkt.
                long emailExpirationMinutes = ZonedDateTime.now(TZ).until(emailExpiresDay.atStartOfDay(TZ), ChronoUnit.MINUTES);
                if (emailExpirationMinutes > 1440L) {
                    emailExpirationMinutes = (emailExpirationMinutes / 1440L) * 1440L;
                } else if (emailExpirationMinutes > 60L) {
                    emailExpirationMinutes = (emailExpirationMinutes / 60L) * 60L;
                }

                try {
                    emailTemplateProvider.sendExecuteActions(link, emailExpirationMinutes);
                    user.setSingleAttribute(userAttribute, passwordCreatedEpochMilli.toString());
                    log.infof(
                            "Aktiverings-email sent to %s in %s realm. Password expire %s, leeway expires %s",
                            user.getUsername(), realm.getName(), passwordExpiresDay, passwordLeewayExpiresDay
                    );
                } catch (EmailException e) {
                    log.errorf(e, "Unable to send aktiverings reminder to %s in %s realm", user.getUsername(), realm.getName());
                }

            });
        }
    }

    private static Optional<Long> getLongUserAttribute(UserModel user, String attribute) {
        String value = user.getFirstAttribute(attribute);
        try {
            return Optional.ofNullable(value).map(Long::valueOf);
        } catch (NumberFormatException e) {
            log.warnf(e, "%s attribute for user %s is not a number: %s",
                    SENT_INITIAL_REMINDER_CREATED_AT, user.getUsername(), value);
            return Optional.empty();
        }
    }

    private static OptionalInt daysToExpirePassword(UserModel user) {
        return user
                .getAttributeStream(AktiveringAuthenticator.EXPIRE_PASSWORD_DAYS)
                .mapToInt(s -> {
                    try {
                        return Integer.parseInt(s);
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .filter(n -> n > 0)
                .findAny();
    }
}
