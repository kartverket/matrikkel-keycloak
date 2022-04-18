package no.statkart.matrikkel.keycloak.util;

import org.keycloak.models.UserModel;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.Optional;

public class UserModels {
    public static final String PROGRAMVAREBRUKER_KEY = "matrikkel.pv_user";
    public static final String USER_EXPIRES_AT = "matrikkel.user_expires_at";

    public static boolean isProgramvarebruker(UserModel userModel) {
        return userModel
                .getAttributeStream(PROGRAMVAREBRUKER_KEY)
                .map(UserModels::parseBooleanAttribute)
                .reduce((a, b) -> a && b)
                .orElse(false);

    }

    public static Optional<Instant> getExpirationDate(UserModel user) {
        return user
                .getAttributeStream(USER_EXPIRES_AT).map(s -> {
                    try {
                        return Instant.ofEpochMilli(Long.parseLong(s));
                    } catch (NumberFormatException | DateTimeException e) {
                        return Instant.MIN;
                    }
                })
                .min(Instant::compareTo);
    }

    private static boolean parseBooleanAttribute(String s) {
        s = s.trim();
        try {
            int i = Integer.parseInt(s);
            return i != 0;
        } catch (NumberFormatException ignored) {
            return Boolean.parseBoolean(s);
        }
    }
}
