package searchapp;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Random;

public class PkceUtil {
    public static String generateCodeVerifier() {
        Random random = new Random();
        byte[] codeVerifierBytes = new byte[32];
        random.nextBytes(codeVerifierBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifierBytes);
    }

    public static String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = digest.digest(codeVerifier.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
