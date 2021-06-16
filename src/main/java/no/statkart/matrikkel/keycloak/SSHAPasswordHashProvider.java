package no.statkart.matrikkel.keycloak;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import org.keycloak.credential.hash.PasswordHashProvider;
import org.keycloak.models.PasswordPolicy;
import org.keycloak.models.credential.PasswordCredentialModel;

public class SSHAPasswordHashProvider implements PasswordHashProvider {
    private static final String ALGORITHM = "SHA-1";
    private static final int RAW_SALT_SIZE = 3;
    private static final int CHAR_SALT_SIZE = 4;

    private final String providerId;

	public SSHAPasswordHashProvider(String providerId) {
		this.providerId = providerId;
	}

	@Override
	public void close() {
	}

	@Override
	public boolean policyCheck(PasswordPolicy policy, PasswordCredentialModel credential) {
		return providerId.equals(credential.getPasswordCredentialData().getAlgorithm());
	}

	@Override
	public PasswordCredentialModel encodedCredential(String rawPassword, int iterations) {
        return PasswordCredentialModel.createFromValues(providerId, new byte[0], iterations, encodePassword(rawPassword, generateSalt()));
	}

	@Override
	public String encode(String rawPassword, int iterations) {
        return encodePassword(rawPassword, generateSalt());
	}

    @Override
    public boolean verify(String rawPassword, PasswordCredentialModel credential) {
        String oldValue = credential.getPasswordSecretData().getValue();
        String newValue = encodePassword(rawPassword, oldValue.substring(0, CHAR_SALT_SIZE));
        return newValue.equals(oldValue);
    }

    private String generateSalt() {
        byte[] buffer = new byte[RAW_SALT_SIZE];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(buffer);
        return Base64.getEncoder().encodeToString(buffer);
    }

    private String encodePassword(String rawPassword, String salt) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
	        throw new RuntimeException(ALGORITHM + " algorithm not found", e);
        }
        md.update(salt.getBytes(StandardCharsets.UTF_8));
        md.update(rawPassword.getBytes(StandardCharsets.UTF_8));
        return salt + Base64.getEncoder().encodeToString(md.digest());
    }
}
