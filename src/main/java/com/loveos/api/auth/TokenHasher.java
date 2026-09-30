package com.loveos.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Generation and hashing of the opaque tokens: refresh, verification, reset.
 *
 * <p>WHY SHA-256 HERE AND BCRYPT FOR PASSWORDS. The two look similar and are
 * solving opposite problems. A password is short, human-chosen and drawn from a
 * tiny effective keyspace, so it must be hashed *slowly* to make guessing
 * expensive. These tokens are 256 bits of output from a CSPRNG: there is nothing
 * to guess, brute force is not on the table, and a slow hash would only add
 * latency to every single authenticated request that needs a refresh. A fast
 * cryptographic digest is the right tool, and using bcrypt here would be
 * cargo-culting.
 *
 * <p>The digest also has to be *deterministic*, because the token is looked up by
 * its hash in a unique index. bcrypt salts every call, producing a different
 * output each time, and so cannot be searched for.
 */
@Component
public class TokenHasher {

  /**
   * One shared instance. {@code SecureRandom} is thread-safe, and creating one
   * per call can block on entropy — a real and well-documented source of stalls
   * under load on Linux.
   */
  private static final SecureRandom RANDOM = new SecureRandom();

  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

  /**
   * A new opaque token, URL-safe so it can travel in an emailed link unescaped.
   *
   * @param byteLength entropy in bytes; 32 gives 256 bits
   */
  public String generate(int byteLength) {
    byte[] bytes = new byte[byteLength];
    RANDOM.nextBytes(bytes);
    return URL_ENCODER.encodeToString(bytes);
  }

  /** Hex-encoded SHA-256. Stable across processes, so it can be a database key. */
  public String hash(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      // SHA-256 is mandated by the JLS for every conformant JRE.
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }
}
