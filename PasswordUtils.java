package flightapp;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Properties;
import java.util.Random;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;


/**
 * A collection of utility methods to help with managing passwords
 */
public class PasswordUtils {
  /**
   * Generates a cryptographically-secure salted password.
   */
  public static byte[] saltAndHashPassword(String password) {
    byte[] salt = generateSalt();
    byte[] saltedHash = hashWithSalt(password, salt);
    int length2 = saltedHash.length;
    byte[] result = new byte[SALT_LENGTH_BYTES + length2];
    // TODO: combine the salt and the salted hash into a single byte array that
    // can be written to the database
    System.arraycopy(salt, 0, result, 0, SALT_LENGTH_BYTES);
    System.arraycopy(saltedHash, 0, result, SALT_LENGTH_BYTES, length2);
    return result;
  }

  /**
   * Verifies whether the plaintext password can be hashed to provided salted hashed password.
   */
  public static boolean plaintextMatchesSaltedHash(String plaintext, byte[] saltedHashed) {
    // TODO: extract the salt from the byte array (ie, undo the logic you implemented in 
    // saltAndHashPassword), then use it to check whether the user-provided plaintext
    // password matches the password hash.
    byte[] salt = Arrays.copyOfRange(saltedHashed, 0, SALT_LENGTH_BYTES);
    byte[] saltedHash = Arrays.copyOfRange(saltedHashed, SALT_LENGTH_BYTES, saltedHashed.length);
    byte[] hashed2 = hashWithSalt(plaintext, salt);
    return Arrays.equals(saltedHash, hashed2);
  }
  
  // Password hashing parameter constants.
  private static final int HASH_STRENGTH = 65536;
  private static final int KEY_LENGTH_BYTES = 128;
  private static final int SALT_LENGTH_BYTES = 16;

  /**
   * Generate a small bit of randomness to serve as a password "salt"
   */
  static byte[] generateSalt() {
    // TODO: implement this.
    Random r = new Random();
    byte[] salt = new byte[SALT_LENGTH_BYTES];
    r.nextBytes(salt);
    return salt;
  }

  /**
   * Uses the provided salt to generate a cryptographically-secure hash of the provided password.
   * The resultant byte array will be KEY_LENGTH_BYTES bytes long.
   */
  static byte[] hashWithSalt(String password, byte[] salt)
    throws IllegalStateException {
    // Specify the hash parameters, including the salt
    KeySpec spec = new PBEKeySpec(password.toCharArray(), salt,
                                  HASH_STRENGTH, KEY_LENGTH_BYTES * 8 /* length in bits */);

    // Hash the whole thing
    SecretKeyFactory factory = null;
    byte[] hash = null; 
    try {
      factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      hash = factory.generateSecret(spec).getEncoded();
      return hash;
    } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
      throw new IllegalStateException();
    }
  }

}
