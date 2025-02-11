package monero.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.jcajce.provider.digest.Keccak;

import common.utils.GenUtils;
import monero.daemon.model.MoneroNetworkType;
import monero.daemon.model.MoneroTx;
import monero.wallet.model.MoneroTxWallet;

/**
 * Collection of Monero utilities.
 */
public class MoneroUtils {
  
  // core wallet2 syncs on a fixed intervals
  public static final long WALLET2_REFRESH_INTERVAL = 10000;

  private static final int NUM_MNEMONIC_WORDS = 25;
  private static final int VIEW_KEY_LENGTH = 64;
  private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
  private static final List<Character> CHARS = new ArrayList<Character>();
  static {
    for (char c : ALPHABET.toCharArray()) {
      CHARS.add(c);
    }
  }
  protected final static BigDecimal ALPHABET_SIZE = new BigDecimal(ALPHABET.length());
  protected final static Map<Integer, Integer> ENCODED_BLOCK_SIZE = new HashMap<Integer, Integer>();
  static {
      ENCODED_BLOCK_SIZE.put(0, 0);
      ENCODED_BLOCK_SIZE.put(2, 1);
      ENCODED_BLOCK_SIZE.put(3, 2);
      ENCODED_BLOCK_SIZE.put(5, 3);
      ENCODED_BLOCK_SIZE.put(6, 4);
      ENCODED_BLOCK_SIZE.put(7, 5);
      ENCODED_BLOCK_SIZE.put(9, 6);
      ENCODED_BLOCK_SIZE.put(10, 7);
      ENCODED_BLOCK_SIZE.put(11, 8);
  };
  protected final static int FULL_BLOCK_SIZE = 8;
  protected final static int FULL_ENCODED_BLOCK_SIZE = 11;
  protected final static BigDecimal UINT64_MAX = new BigDecimal(Math.pow(2, 64));
  protected final static int[] EMPTY_INT_ARRAY = new int[0];
  
  protected final static Pattern MONERO_REGULAR_ADDRESS_PATTERN =
      Pattern.compile("^[" + ALPHABET + "]{95}$");

  protected final static Pattern MONERO_INTEGRATED_ADDRESS_PATTERN =
        Pattern.compile("^[" + ALPHABET + "]{106}$");

  /**
   * Validates a wallet seed.
   * 
   * TODO: beef this up
   * 
   * @param seed is the seed to validate
   */
  public static void validateSeed(String seed) {
    GenUtils.assertNotNull(seed);
    GenUtils.assertEquals(64, seed.length());
  }
  
  /**
   * Validates the given mnemonic phrase.
   * 
   * @param mnemonic is the mnemonic to validate
   * @throws MoneroException if the given mnemonic is invalid
   */
  public static void validateMnemonic(String mnemonic) {
    GenUtils.assertNotNull("Mnemonic phrase is not initialized", mnemonic);
    GenUtils.assertFalse("Mnemonic phrase is empty", mnemonic.isEmpty());
    String[] words = mnemonic.split(" ");
    if (words.length != MoneroUtils.NUM_MNEMONIC_WORDS) throw new Error("Mnemonic phrase is " + words.length + " words but must be " + MoneroUtils.NUM_MNEMONIC_WORDS);
  }
  
  // TODO: improve validation
  public static void validatePrivateViewKey(String privateViewKey) {
    GenUtils.assertNotNull(privateViewKey);
    GenUtils.assertEquals(64, privateViewKey.length());
  }
  
  // TODO: improve validation
  public static void validatePrivateSpendKey(String privateSpendKey) {
    GenUtils.assertNotNull(privateSpendKey);
    GenUtils.assertEquals(64, privateSpendKey.length());
  }
  
  // TODO: improve validation
  public static void validatePublicViewKey(String publicViewKey) {
    GenUtils.assertNotNull(publicViewKey);
    GenUtils.assertEquals(64, publicViewKey.length());
  }
  
  // TODO: improve validation
  public static void validatePublicSpendKey(String publicSpendKey) {
    GenUtils.assertNotNull(publicSpendKey);
    GenUtils.assertEquals(64, publicSpendKey.length());
  }
  
  /**
   * Validate an address but ignore the type of network.
   */
  public static void validateAddress(String address) {
    validateAddress(address, null);
  }
  
  /**
   * @param moneroNetworkType if <code>null</code> the validation will
   * ignore the type of network.
   */
  public static void validateAddress(String address, MoneroNetworkType moneroNetworkType) {
    assertNotNull(address);
    assertFalse(address.isEmpty()); 
    
    boolean isIntegratedAddress = false;
    if (!MONERO_REGULAR_ADDRESS_PATTERN.matcher(address).matches()) {
        if (MONERO_INTEGRATED_ADDRESS_PATTERN.matcher(address).matches()) {
            isIntegratedAddress = true;
        } else {
            fail("Invalid regEx pattern for the address");
        }
    }

    String decodedAddrStr = decodeToHexString(address);
    assertTrue(isValidAddressNetwork(decodedAddrStr, isIntegratedAddress, moneroNetworkType));
    assertTrue(isValidAddressHash(decodedAddrStr));
  }
  
  protected static boolean isValidAddressNetwork(String decodedAddrStr,
                                                 boolean isIntegratedAddress,
                                                 MoneroNetworkType moneroNetworkType) {

    int networkType = Integer.parseInt(decodedAddrStr.substring(0, 2), 16);
    
    boolean match = false;
    if (moneroNetworkType == null || moneroNetworkType == MoneroNetworkType.MAINNET) {
      match = isIntegratedAddress ? MoneroNetworkType.MAINNET.getCodeForIntegratedAddress() == networkType : 
                                    MoneroNetworkType.MAINNET.getCodeForRegularAddress() == networkType;
    }
    
    if (match == false && (moneroNetworkType == null || moneroNetworkType == MoneroNetworkType.TESTNET)) {
      match = isIntegratedAddress ? MoneroNetworkType.TESTNET.getCodeForIntegratedAddress() == networkType : 
                                    MoneroNetworkType.TESTNET.getCodeForRegularAddress() == networkType;
    }
    
    if (match == false && (moneroNetworkType == null || moneroNetworkType == MoneroNetworkType.STAGENET)) {
      match = isIntegratedAddress ? MoneroNetworkType.STAGENET.getCodeForIntegratedAddress() == networkType : 
                                    MoneroNetworkType.STAGENET.getCodeForRegularAddress() == networkType;
    }
    
    return match;
  }
    
  protected static boolean isValidAddressHash(String decodedAddrStr) {
    String checksumCheck = decodedAddrStr.substring(decodedAddrStr.length() - 8);
    String withoutChecksumStr = decodedAddrStr.substring(0, decodedAddrStr.length() - 8);
    byte[] withoutChecksumBytes = hexToBin(withoutChecksumStr);
    
    Keccak.Digest256 digest256 = new Keccak.Digest256();
    byte[] hashbytes = digest256.digest(withoutChecksumBytes);
    String encodedStr = Hex.encodeHexString(hashbytes);
    
    String hashChecksum = encodedStr.substring(0, 8);
    return hashChecksum != null && hashChecksum.equals(checksumCheck);
  }
  
  // TODO: improve validation
  public static void validatePaymentId(String paymentId) {
    GenUtils.assertTrue(paymentId.length() == 16 || paymentId.length() == 64);
  }
  
  /**
   * Validates the given view key.
   * 
   * @param viewKey is the view key to validate
   * @throws MoneroException if the given view key is invalid
   */
  public static void validateViewKey(String viewKey) {
    if (viewKey == null) throw new MoneroException("View key is null");
    if (viewKey.length() != VIEW_KEY_LENGTH) throw new MoneroException("View key is " + viewKey.length() + " characters but must be " + VIEW_KEY_LENGTH);
  }
  
  /**
   * Converts the string to a URI.  Throws MoneroException if exception.
   * 
   * @param endpoint is the string to convert to a URI
   * @return URI is the initialized object from the string endpoint
   */
  public static URI parseUri(String endpoint) {
    try {
      return new URI(endpoint);
    } catch (Exception e) {
      throw new MoneroException(e);
    }
  }

  public static void validateHex(String str) {
    if (!str.matches("^([0-9A-Fa-f]{2})+$")) throw new MoneroException("Invalid hex: " + str);
  }

  public static void validateBase58(String standardAddress) {
    for (char c : standardAddress.toCharArray()) {
      if (!CHARS.contains((Character) c)) throw new MoneroException("Invalid Base58 " + standardAddress);
    }
  }
  
  /**
   * Determines if two payment ids are functionally equal.
   * 
   * For example, 03284e41c342f032 and 03284e41c342f032000000000000000000000000000000000000000000000000 are considered equal.
   * 
   * @param paymentId1 is a payment id to compare
   * @param paymentId2 is a payment id to compare
   * @return true if the payment ids are equal, false otherwise
   */
  public static boolean paymentIdsEqual(String paymentId1, String paymentId2) {
    int maxLength = Math.max(paymentId1.length(), paymentId2.length());
    for (int i = 0; i < maxLength; i++) {
      if (i < paymentId1.length() && i < paymentId2.length() && paymentId1.charAt(i) != paymentId2.charAt(i)) return false;
      if (i >= paymentId1.length() && paymentId2.charAt(i) != '0') return false;
      if (i >= paymentId2.length() && paymentId1.charAt(i) != '0') return false;
    }
    return true;
  }
  
  /**
   * Convenience method to reconcile two values with default configuration by
   * calling reconcile(val1, val2, null, null, null).
   * 
   * @param val1 is a value to reconcile
   * @param val2 is a value to reconcile
   * @return the reconciled value if reconcilable
   * @throws Exception if the values cannot be reconciled
   */
  public static <T> T reconcile(T val1, T val2) {
    return reconcile(val1, val2, null, null, null);
  }
  
  /**
   * Reconciles two values.
   * 
   * @param val1 is a value to reconcile
   * @param val2 is a value to reconcile
   * @param resolveDefined uses defined value if true or null, null if false
   * @param resolveTrue uses true over false if true, false over true if false, must be equal if null
   * @param resolveMax uses max over min if true, min over max if false, must be equal if null
   * @returns the reconciled value if reconcilable
   * @throws Exception if the values cannot be reconciled
   */
  @SuppressWarnings("unchecked")
  public static <T> T reconcile(T val1, T val2, Boolean resolveDefined, Boolean resolveTrue, Boolean resolveMax) {
    
    // check for same reference
    if (val1 == val2) return val1;
    
    // check for BigInteger equality
    Integer comparison = null; // save comparison for later if applicable
    if (val1 instanceof BigInteger && val2 instanceof BigInteger) {
      comparison = ((BigInteger) val1).compareTo((BigInteger) val2);  
      if (comparison == 0) return val1;
    }
    
    // resolve one value null
    if (val1 == null || val2 == null) {
      if (Boolean.FALSE.equals(resolveDefined)) return null;  // use null
      else return val1 == null ? val2 : val1;  // use defined value
    }
    
    // resolve different booleans
    if (resolveTrue != null && Boolean.class.isInstance(val1) && Boolean.class.isInstance(val2)) {
      return (T) resolveTrue;
    }
    
    // resolve different numbers
    if (resolveMax != null) {
      
      // resolve BigIntegers
      if (val1 instanceof BigInteger && val2 instanceof BigInteger) {
        return resolveMax ? (comparison < 0 ? val2 : val1) : (comparison < 0 ? val1 : val2);
      }
      
      // resolve integers
      if (val1 instanceof Integer && val2 instanceof Integer) {
        return (T) (Integer) (resolveMax ? Math.max((Integer) val1, (Integer) val2) : Math.min((Integer) val1, (Integer) val2));
      }
      
      // resolve longs
      if (val1 instanceof Long && val2 instanceof Long) {
        return (T) (Long) (resolveMax ? Math.max((Long) val1, (Long) val2) : Math.min((Long) val1, (Long) val2));
      }

      throw new RuntimeException("Need to resolve primitives and object versions");
//      // resolve js numbers
//      if (typeof val1 === "number" && typeof val2 === "number") {
//        return config.resolveMax ? Math.max(val1, val2) : Math.min(val1, val2);
//      }
    }
    
    // assert deep equality
    GenUtils.assertEquals("Cannot reconcile values " + val1 + " and " + val2 + " with config: [" + resolveDefined + ", " + resolveTrue + ", " + resolveMax + "]", val1, val2);
    return val1;
  }
  
  /**
   * Reconciles two int arrays.  The arrays must be identical or an
   * exception is thrown.
   * 
   * @param val1
   * @param val2
   * @return
   */
  public static int[] reconcileIntArrays(int[] arr1, int[] arr2) {
    
    // check for same reference or null
    if (arr1 == arr2) return arr1;
    
    // resolve one value defined
    if (arr1 == null || arr2 == null) {
      return arr1 == null ? arr2 : arr1;
    }
    
    // assert deep equality
    GenUtils.assertTrue("Cannot reconcile arrays", Arrays.equals(arr1, arr2));
    return arr1;
  }

  
  /**
   * Returns a human-friendly key value line.
   * 
   * @param key is the key
   * @param value is the value
   * @param indent indents the line
   * @return the human-friendly key value line
   */
  public static String kvLine(Object key, Object value, int indent) {
    return kvLine(key, value, indent, true, true);
  }
  
  /**
   * Returns a human-friendly key value line.
   * 
   * @param key is the key
   * @param value is the value
   * @param indent indents the line
   * @param newline specifies if the string should be terminated with a newline or not
   * @param ignoreUndefined specifies if undefined values should return an empty string
   * @return the human-friendly key value line
   */
  public static String kvLine(Object key, Object value, int indent, boolean newline, boolean ignoreUndefined) {
    if (value == null && ignoreUndefined) return "";
    return GenUtils.getIndent(indent) + key + ": " + value + (newline ? '\n' : "");
  }
  
  /**
   * Merges a transaction into a list of existing transactions.
   * 
   * TODO: collapse into MoneroUtils.mergeTx(List<MoneroTxWallet> txs, ...)?
   * 
   * @param txs are existing transactions to merge into
   * @param tx is the transaction to merge into the list
   */
  public static void mergeTx(List<MoneroTx> txs, MoneroTx tx) {
    for (MoneroTx aTx : txs) {
      if (aTx.getId().equals(tx.getId())) {
        aTx.merge(tx);
        return;
      }
    }
    txs.add(tx);
  }
  
  /**
   * Merges a transaction into a list of existing transactions.
   * 
   * @param txs are existing transactions to merge into
   * @param tx is the transaction to merge into the list
   */
  public static void mergeTx(List<MoneroTxWallet> txs, MoneroTxWallet tx) {
    for (MoneroTx aTx : txs) {
      if (aTx.getId().equals(tx.getId())) {
        aTx.merge(tx);
        return;
      }
    }
    txs.add(tx);
  }
  
  private static String decodeToHexString(String address) {
    int[] bin = new int[address.length()];
    for (int i = 0; i < address.length(); i++) {
      bin[i] = address.codePointAt(i);
    }

    int fullBlockCount = (int)Math.floor(bin.length / FULL_ENCODED_BLOCK_SIZE);
    int lastBlockSize = (int)bin.length % FULL_ENCODED_BLOCK_SIZE;
    int lastBlockDecodedSize = ENCODED_BLOCK_SIZE.get(lastBlockSize);
    if (lastBlockDecodedSize < 0) {
      throw new IllegalArgumentException("Invalid encoded length");
    }

    int dataSize = fullBlockCount * FULL_BLOCK_SIZE + lastBlockDecodedSize;
    int[] data = new int[dataSize];
    for (int i = 0; i < fullBlockCount; i++) {
      data = decodeBlock(GenUtils.subarray(bin,
                    i * FULL_ENCODED_BLOCK_SIZE,
                    i * FULL_ENCODED_BLOCK_SIZE + FULL_ENCODED_BLOCK_SIZE),
                 data,
                 i * FULL_BLOCK_SIZE);
    }
    if (lastBlockSize > 0) {
      int[] subarray = GenUtils.subarray(bin,
                    fullBlockCount * FULL_ENCODED_BLOCK_SIZE,
                    fullBlockCount * FULL_ENCODED_BLOCK_SIZE + FULL_BLOCK_SIZE);

      data = decodeBlock(subarray,
                 data,
                 fullBlockCount * FULL_BLOCK_SIZE);
    }

    return toHexString(data);
  }

  private static int[] decodeBlock(int[] data, int[] buf, int index) {

    if (data.length < 1 || data.length > FULL_ENCODED_BLOCK_SIZE) {
      throw new RuntimeException("Invalid block length: " + data.length);
    }

    int resSize = ENCODED_BLOCK_SIZE.get(data.length);
    if (resSize <= 0) {
      throw new RuntimeException("Invalid block size");
    }
    BigDecimal resNum = BigDecimal.ZERO;
    BigDecimal order = BigDecimal.ONE;
    for (int i = data.length - 1; i >= 0; i--) {
      int digit = ALPHABET.indexOf(data[i]);
      if (digit < 0) {
        throw new RuntimeException("Invalid symbol");
      }
      BigDecimal product = order.multiply(new BigDecimal(digit)).add(resNum);
      // if product > UINT64_MAX
      if (product.compareTo(UINT64_MAX) > 0) {
        throw new RuntimeException("Overflow");
      }
      resNum = product;
      order = order.multiply(ALPHABET_SIZE);
    }
    if (resSize < FULL_BLOCK_SIZE && (new BigDecimal(2).pow(8 * resSize).compareTo(resNum) <= 0)) {
      throw new RuntimeException("Overflow 2");
    }

    int[] tmpBuf = uint64To8be(resNum, resSize);
    for (int j = 0; j < tmpBuf.length; j++) {
      buf[j + index] = tmpBuf[j];
    }

    return buf;
  }

  private static int[] uint64To8be(BigDecimal num, int size) {
    int[] res = new int[size];
    if (size < 1 || size > 8) {
      throw new RuntimeException("Invalid input length");
    }
    BigDecimal twopow8 = new BigDecimal(2).pow(8);
    for (int i = size - 1; i >= 0; i--) {
      res[i] = num.remainder(twopow8).intValue();
      num = num.divide(twopow8);
    }
    return res;
  }

  private static byte[] hexToBin(String hexStr) {
    if (hexStr == null || hexStr.length() % 2 != 0) {
      return null;
    }
    byte[] res = new byte[hexStr.length() / 2];
    for (int i = 0; i < hexStr.length() / 2; ++i) {
      res[i] = (byte)Integer.parseInt(hexStr.substring(i * 2, i * 2 + 2), 16);
    }
    return res;
  }

  private static String toHexString(int[] data) {
    StringBuilder builder = new StringBuilder();
    for (int i : data) {
      builder.append(String.format("%02x", i));
    }
    return builder.toString();
  }
  
}
