package no.statkart.matrikkel.keycloak;

import no.statkart.matrikkel.keycloak.util.UserModels;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.PasswordPolicy;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.stream.Stream;

public class AktiveringAuthenticator implements Authenticator {
    private static final Logger logger = Logger.getLogger(AktiveringAuthenticator.class);
    @SuppressWarnings("squid:S2068") // dette er ikke et passord
    public static final String EXPIRE_PASSWORD_DAYS = "matrikkel.expire_password_days";
    public static final long PASSWORD_UPDATE_LEEWAY_DAYS = 14L;

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        RealmModel realm = context.getRealm();
        UserModel user = context.getUser();
        String flowPath = context.getFlowPath();
        Instant now = Instant.now();
        LocalDate today = now.atZone(ZoneId.systemDefault()).toLocalDate();

        if (now.compareTo(UserModels.getExpirationDate(user).orElse(Instant.MAX)) >= 0) {
            logger.infov(
                    "Innlogging ({0}) feilet for {1}: Midlertidig bruker gått ut på dato",
                    flowPath, user.getUsername());
            // Midlertidig bruker har gått ut på dato
            context.failure(AuthenticationFlowError.USER_DISABLED);
            return;
        }

        Optional<LocalDate> passwordCreatedOption = context
                .getSession()
                .userCredentialManager()
                .getStoredCredentialsByTypeStream(realm, user, PasswordCredentialModel.TYPE)
                .map(CredentialModel::getCreatedDate)
                .map(n -> n != null ? Instant.ofEpochMilli(n) : Instant.ofEpochMilli(Long.MIN_VALUE))
                .min(Instant::compareTo)
                .map(t -> t.atZone(ZoneId.systemDefault()).toLocalDate());
        if (!passwordCreatedOption.isPresent()) {
            // Det er ingen passord credentials på brukeren, alt OK
            context.success();
            return;
        }

        LocalDate userPasswordExpires = getUserPasswordExpiresDays(user)
                .flatMap(passwordExpiresDays -> passwordCreatedOption
                        .flatMap(passwordCreated -> {
                            try {
                                return Optional.of(passwordCreated.plusDays(passwordExpiresDays));
                            } catch (Exception e) {
                                return Optional.empty();
                            }
                        }))
                .orElse(LocalDate.MAX);
        LocalDate realmPasswordExpires = getRealmPasswordExpiresDays(realm)
                .flatMap(passwordExpiresDays -> passwordCreatedOption
                        .flatMap(passwordCreated -> {
                            try {
                                return Optional.of(passwordCreated.plusDays(passwordExpiresDays));
                            } catch (Exception e) {
                                return Optional.empty();
                            }
                        }))
                .orElse(LocalDate.MAX);
        LocalDate passwordExpires = userPasswordExpires.compareTo(realmPasswordExpires) <= 0
                ? userPasswordExpires
                : realmPasswordExpires;

        if (today.compareTo(passwordExpires) >= 0) {
            boolean passwordUpdateNotAllowed = today.compareTo(passwordExpires.plusDays(PASSWORD_UPDATE_LEEWAY_DAYS)) >= 0;
            if (passwordUpdateNotAllowed) {
                logger.infov(
                        "Innlogging ({0}) for {1}: Passord ikke fornyet i løpet av 14 dager. Konto må reaktiveres av brukeradmin",
                        flowPath, user.getUsername());
                context.failure(AuthenticationFlowError.CREDENTIAL_SETUP_REQUIRED);
                return;
            }
        }

        context.success();
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // trenger ikke å gjøre noe
    }

    @Override
    public void close() {
        // trenger ikke å gjøre noe
    }

    private static Optional<Long> getUserPasswordExpiresDays(UserModel user) {
        return user
                .getAttributeStream(AktiveringAuthenticator.EXPIRE_PASSWORD_DAYS)
                .flatMap(s -> {
                    try {
                        return Stream.of(Long.valueOf(s));
                    } catch (NumberFormatException e) {
                        return Stream.empty();
                    }
                })
                .min(Long::compareTo);
    }

    private static Optional<Long> getRealmPasswordExpiresDays(RealmModel realm) {
        long realmDaysToExpirePassword = 0L;
        if (realm.getPasswordPolicy().getPolicies().contains(PasswordPolicy.FORCE_EXPIRED_ID)) {
            realmDaysToExpirePassword = realm.getPasswordPolicy().getDaysToExpirePassword();
        }
        if (realmDaysToExpirePassword > 0) {
            return Optional.of(realmDaysToExpirePassword);
        } else {
            return Optional.empty();
        }

    }

}
