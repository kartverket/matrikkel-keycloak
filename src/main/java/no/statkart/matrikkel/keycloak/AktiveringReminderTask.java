package no.statkart.matrikkel.keycloak;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import no.statkart.matrikkel.keycloak.scheduler.ScheduledTask;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AktiveringReminderTask extends ScheduledTask.Simple {
    public static final String LAST_SENT_AKTIVERING_REMINDER_ID = "matrikkel.last_sent_aktivering_reminder_id";
    public static final String SEND_AKTIVERING_REMINDER = "matrikkel.send_aktivering_reminder";
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
                log.warnf(e, "Unable to parse cron expression for %s in realm %s", SEND_AKTIVERING_REMINDER, realm.getName());
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
        try (Stream<UserModel> allUsers = keycloakSession.users().getUsersStream(realm)) {
            LocalDate today = LocalDate.now(TZ);
            allUsers.forEach(user -> {
                if (!user.isEnabled()) {
                    log.debugf("Aktiverings-email skipped for %s in % realm: User not enabled", user.getUsername(), realm.getName());
                    return;
                }

                boolean midlertidigUserExpired = user
                        .getAttributeStream(AktiveringAuthenticator.USER_EXPIRES_AT)
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
                    log.debugf("Aktiverings-email skipped for %s in % realm: Temporary user", user.getUsername(), realm.getName());
                    return;
                }

                List<CredentialModel> passwords = keycloakSession.userCredentialManager().getStoredCredentialsByTypeStream(realm, user, PasswordCredentialModel.TYPE).collect(Collectors.toList());
                if (passwords.size() != 1) {
                    log.infof("Aktiverings-email skipped for %s in %s realm: User has no (or multiple?) password(s)", user.getUsername(), realm.getName(), realm.getName());
                    return;
                }
                CredentialModel credential = passwords.get(0);

                List<String> sentForPasswordIds = user.getAttributeStream(LAST_SENT_AKTIVERING_REMINDER_ID).collect(Collectors.toList());
                boolean reminderAlreadySent = sentForPasswordIds
                        .stream()
                        .anyMatch(id -> !id.trim().isEmpty() && id.equals(credential.getId()));
                if (reminderAlreadySent) {
                    log.debugf("Aktiverings-email skipped for %s in %s realm: Already sent", user.getUsername(), realm.getName());
                    return;
                }

                boolean requiredActionsSet = user
                        .getRequiredActionsStream()
                        .flatMap(s -> {
                            try {
                                return Stream.of(UserModel.RequiredAction.valueOf(s));
                            } catch (IllegalArgumentException e) {
                                return Stream.empty();
                            }
                        })
                        .collect(Collectors.toSet())
                        .containsAll(EnumSet.of(UserModel.RequiredAction.UPDATE_PASSWORD, UserModel.RequiredAction.VERIFY_EMAIL));
                if (requiredActionsSet) {
                    log.infof("Aktiverings-email skipped for %s in % realm: All required actions already set. " +
                            "Assuming that an email is already sent", user.getUsername(), realm.getName());
                }

                if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
                    log.infof("Aktiverings-email skipped for %s in %s realm: User has no email address", user.getUsername(), realm.getName());
                    return;
                }
                int daysToExpirePassword = daysToExpirePassword(user).orElse(realmDaysToExpirePassword);
                if (daysToExpirePassword <= 0) {
                    log.debugf("Aktiverings-email skipped for %s in % realm: User's password does not expire", user.getUsername(), realm.getName());
                    return;
                }

                Long passwordCreatedEpochMilli = credential.getCreatedDate();
                if (passwordCreatedEpochMilli == null) {
                    log.infof("Aktiverings-email skipped for %s in % realm: Password has no age", user.getUsername(), realm.getName());
                    return;
                }
                LocalDate passwordCreatedDay = Instant.ofEpochMilli(passwordCreatedEpochMilli).atZone(TZ).toLocalDate();
                LocalDate passwordExpiresDay = passwordCreatedDay.plusDays(daysToExpirePassword);
                LocalDate leewayExpiresDay = passwordExpiresDay.plusDays(AktiveringAuthenticator.PASSWORD_UPDATE_LEEWAY_DAYS);
                if (today.isBefore(passwordExpiresDay) || today.compareTo(leewayExpiresDay) >= 0) {
                    log.debugf(
                            "Aktiverings-email skipped for %s in %s realm: " +
                                    "Password(created: %s) does not expire yet " +
                                    "(%s), or passed leeway for password update(%s)",
                            user.getUsername(), realm.getName(), passwordCreatedDay, passwordExpiresDay, leewayExpiresDay);
                    return;
                }
                ExecuteActionsActionToken token = new ExecuteActionsActionToken(
                        user.getId(), Math.toIntExact(Instant.EPOCH.until(leewayExpiresDay.atStartOfDay(TZ).toInstant(), ChronoUnit.SECONDS)),
                        Arrays.asList("verify-email", "update-password"),
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
                try {
                    user.addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);
                    user.addRequiredAction(UserModel.RequiredAction.VERIFY_EMAIL);
                    emailTemplateProvider.sendExecuteActions(link, Instant.now().until(leewayExpiresDay.atStartOfDay(TZ).toInstant(), ChronoUnit.MINUTES));
                    ArrayList<String> newSentForPasswordIds = new ArrayList<>(sentForPasswordIds.size());
                    newSentForPasswordIds.addAll(sentForPasswordIds);
                    newSentForPasswordIds.add(credential.getId());
                    user.setAttribute(LAST_SENT_AKTIVERING_REMINDER_ID, newSentForPasswordIds);
                    log.infof(
                            "Aktiverings-email sent to %s in %s realm. Password expired %s, leeway expires %s",
                            user.getUsername(), realm.getName(), passwordExpiresDay, leewayExpiresDay
                    );
                } catch (EmailException e) {
                    log.warnf(e, "Unable to send aktiverings reminder to %s in % realm", user.getUsername(), realm.getName());
                }

            });
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
