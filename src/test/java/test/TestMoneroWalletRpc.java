package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import monero.utils.MoneroException;
import monero.utils.MoneroUtils;
import monero.wallet.MoneroWallet;
import monero.wallet.MoneroWalletRpc;
import monero.wallet.model.MoneroAccount;
import monero.wallet.model.MoneroAccountTag;
import monero.wallet.model.MoneroAddressBookEntry;
import monero.wallet.model.MoneroIntegratedAddress;
import monero.wallet.model.MoneroTxWallet;
import utils.TestUtils;

/**
 * Tests monero-wallet-rpc non-relaying calls.
 */
public class TestMoneroWalletRpc extends TestMoneroWalletCommon {
  
  protected MoneroWalletRpc wallet;
  
  public TestMoneroWalletRpc() {
    this.wallet = (MoneroWalletRpc) getTestWallet();
  }

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    
  }

  @Override
  protected MoneroWallet getTestWallet() {
    return TestUtils.getWalletRpc();
  }
  
  @Override
  protected MoneroWallet createRandomWallet() {
    wallet.createWalletRandom(UUID.randomUUID().toString(), TestUtils.WALLET_PASSWORD);
    return wallet;
  }

  @Override
  protected MoneroWallet openWallet(String path) {
    wallet.openWallet(path, TestUtils.WALLET_PASSWORD);
    return wallet;
  }
  
  // ---------------------------- BEGIN TESTS ---------------------------------
  
  // Can create a wallet with a randomly generated seed
  @Test
  public void testCreateWalletRandom() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    try {
      
      // create random wallet with defaults
      String path = UUID.randomUUID().toString();
      wallet.createWalletRandom(path, TestUtils.WALLET_PASSWORD);
      String mnemonic = wallet.getMnemonic();
      MoneroUtils.validateMnemonic(mnemonic);
      assertNotEquals(TestUtils.MNEMONIC, mnemonic);
      MoneroUtils.validateAddress(wallet.getPrimaryAddress());
      assertEquals(1, wallet.getHeight()); // TODO monero core: why does height of new unsynced wallet start at 1?
      wallet.sync();  // very quick because restore height is chain height
      wallet.close();

      // create random wallet with non defaults
      path = UUID.randomUUID().toString();
      wallet.createWalletRandom(path, TestUtils.WALLET_PASSWORD, "Spanish");
      MoneroUtils.validateMnemonic(wallet.getMnemonic());
      assertNotEquals(mnemonic, wallet.getMnemonic());
      MoneroUtils.validateAddress(wallet.getPrimaryAddress());
      assertEquals(1, wallet.getHeight()); // TODO monero core: why is height of unsynced wallet 1?
      wallet.close();
      
      // attempt to create wallet which already exists
      try {
        wallet.createWalletRandom(path, TestUtils.WALLET_PASSWORD, "Spanish");
      } catch (MoneroException e) {
        assertEquals(-21, (int) e.getCode());
      }
    } finally {
      
      // open main test wallet for other tests
      wallet.openWallet(TestUtils.WALLET_RPC_NAME_1, TestUtils.WALLET_PASSWORD);
    }
  }
  
  // Can create a wallet from a mnemonic phrase
  @Test
  public void testCreateWalletFromMnemonic() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    try {
      
      // create wallet with mnemonic and defaults
      String path = UUID.randomUUID().toString();
      wallet.createWalletFromMnemonic(path, TestUtils.WALLET_PASSWORD, TestUtils.MNEMONIC, TestUtils.FIRST_RECEIVE_HEIGHT);
      assertEquals(TestUtils.MNEMONIC, wallet.getMnemonic());
      assertEquals(TestUtils.ADDRESS, wallet.getPrimaryAddress());
      if (wallet.getHeight() != 1) System.out.println("WARNING: createWalletFromMnemonic() already has height as if synced");
      if (wallet.getTxs().size() != 0) System.out.println("WARNING: createWalletFromMnemonic() already has txs as if synced");
      //assertEquals(1, wallet.getHeight());      // TODO monero core: sometimes height is as if synced
      //assertEquals(0, wallet.getTxs().size());  // TODO monero core: sometimes wallet has txs as if synced
      wallet.sync();
      assertEquals(daemon.getHeight(), wallet.getHeight());
      List<MoneroTxWallet> txs = wallet.getTxs();
      assertFalse(txs.isEmpty()); // wallet is used
      assertEquals(TestUtils.FIRST_RECEIVE_HEIGHT, (long) txs.get(0).getHeight());
      wallet.close();
      
      // create wallet with non-defaults
      path = UUID.randomUUID().toString();
      wallet.createWalletFromMnemonic(path, TestUtils.WALLET_PASSWORD, TestUtils.MNEMONIC, TestUtils.FIRST_RECEIVE_HEIGHT, "German", "my offset!", false);
      MoneroUtils.validateMnemonic(wallet.getMnemonic());
      assertNotEquals(TestUtils.MNEMONIC, wallet.getMnemonic());  // mnemonic is different because of offset
      assertNotEquals(TestUtils.ADDRESS, wallet.getPrimaryAddress());
      assertEquals(1, wallet.getHeight());
      wallet.close();
      
    } finally {
      
      // open main test wallet for other tests
      wallet.openWallet(TestUtils.WALLET_RPC_NAME_1, TestUtils.WALLET_PASSWORD);
    }
  }
  
  // Can open wallets
  @Test
  public void testOpenWallet() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    try {
      
      // create names of test wallets
      int numTestWallets = 3;
      List<String> names = new ArrayList<String>();
      for (int i = 0; i < numTestWallets; i++) names.add(UUID.randomUUID().toString());
      
      // create test wallets
      List<String> mnemonics = new ArrayList<String>();
      for (String name : names) {
        wallet.createWalletRandom(name, TestUtils.WALLET_PASSWORD);
        mnemonics.add(wallet.getMnemonic());
        wallet.close();
      }
      
      // open test wallets
      for (int i = 0; i < numTestWallets; i++) {
        wallet.openWallet(names.get(i), TestUtils.WALLET_PASSWORD);
        assertEquals(mnemonics.get(i), wallet.getMnemonic());
        wallet.close();
      }
      
      // attempt to re-open already opened wallet
      try {
        wallet.openWallet(names.get(numTestWallets - 1), TestUtils.WALLET_PASSWORD);
      } catch (MoneroException e) {
        assertEquals(-1, (int) e.getCode());
      }
      
      // attempt to open non-existent
      try {
        wallet.openWallet("btc_integrity", TestUtils.WALLET_PASSWORD);
      } catch (MoneroException e) {
        assertEquals(-1, (int) e.getCode());  // -1 indicates wallet does not exist (or is open by another app)
      }
    } finally {
      
      // open main test wallet for other tests
      try {
        wallet.openWallet(TestUtils.WALLET_RPC_NAME_1, TestUtils.WALLET_PASSWORD);
      } catch (MoneroException e) {
        assertEquals(-1, (int) e.getCode()); // ok if wallet is already open
      }
    }
  }
  
  // Can indicate if multisig import is needed for correct balance information
  @Test
  public void testIsMultisigNeeded() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    assertEquals(false, wallet.isMultisigImportNeeded()); // TODO: test with multisig wallet
  }
  
  // Can tag accounts and query accounts by tag
  @Test
  public void testAccountTags() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    
    // get accounts
    List<MoneroAccount> accounts = wallet.getAccounts();
    assertTrue("Not enough accounts to test; run create account test", accounts.size() >= 3);
    
    // tag some of the accounts
    MoneroAccountTag tag = new MoneroAccountTag("my_tag_" + UUID.randomUUID(), "my tag label", Arrays.asList(0, 1));
    wallet.tagAccounts(tag.getTag(), tag.getAccountIndices());
    
    // query accounts by tag
    List<MoneroAccount> taggedAccounts = wallet.getAccounts(false, tag.getTag());
    assertEquals(2, taggedAccounts.size());
    assertEquals(0, (int) taggedAccounts.get(0).getIndex());
    assertEquals(tag.getTag(), taggedAccounts.get(0).getTag());
    assertEquals(1, (int) taggedAccounts.get(1).getIndex());
    assertEquals(tag.getTag(), taggedAccounts.get(1).getTag());

    // set tag label
    wallet.setAccountTagLabel(tag.getTag(), tag.getLabel());
    
    // fetch tags and ensure new tag is contained
    List<MoneroAccountTag> tags = wallet.getAccountTags();
    assertTrue(tags.contains(tag));
    
    // re-tag an account
    MoneroAccountTag tag2 = new MoneroAccountTag("my_tag_" + UUID.randomUUID(), "my tag label 2", Arrays.asList(1));
    wallet.tagAccounts(tag2.getTag(), tag2.getAccountIndices());
    List<MoneroAccount> taggedAccounts2 = wallet.getAccounts(false, tag2.getTag());
    assertEquals(1, taggedAccounts2.size(), 1);
    assertEquals(1, taggedAccounts2.get(0).getIndex(), 1);
    assertEquals(tag2.getTag(), taggedAccounts2.get(0).getTag());
    
    // re-query original tag which only applies to one account now
    taggedAccounts = wallet.getAccounts(false, tag.getTag());
    assertEquals(1, taggedAccounts.size());
    assertEquals(0, (int) taggedAccounts.get(0).getIndex());
    assertEquals(tag.getTag(), taggedAccounts.get(0).getTag());
    
    // untag and query accounts
    wallet.untagAccounts(Arrays.asList(0, 1));
    assertEquals(0, wallet.getAccountTags().size());
    try {
      wallet.getAccounts(false, tag.getTag());
      fail("Should have thrown exception with unregistered tag");
    } catch (MoneroException e) {
      assertEquals(-1, (int) e.getCode());
    }
    
    // test that non-existing tag returns no accounts
    try {
      wallet.getAccounts(false, "non_existing_tag");
      fail("Should have thrown exception with unregistered tag");
    } catch (MoneroException e) {
      assertEquals(-1, (int) e.getCode());
    }
  }
  
  // Can get addresses out of range of used accounts and subaddresses
  @Test
  public void testGetSubaddressAddressOutOfRange() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    List<MoneroAccount> accounts = wallet.getAccounts(true);
    int accountIdx = accounts.size() - 1;
    int subaddressIdx = accounts.get(accountIdx).getSubaddresses().size();
    String address = wallet.getAddress(accountIdx, subaddressIdx);
    assertNull(address);
  }
  
  // Has an address book
  @Test
  public void testAddressBook() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    
    // initial state
    List<MoneroAddressBookEntry> entries = wallet.getAddressBookEntries();
    int numEntriesStart = entries.size();
    for (MoneroAddressBookEntry entry : entries) testAddressBookEntry(entry);
    
    // test adding standard addresses
    int NUM_ENTRIES = 5;
    String address = wallet.getSubaddress(0, 0).getAddress();
    List<Integer> indices = new ArrayList<Integer>();
    for (int i = 0; i < NUM_ENTRIES; i++) {
      indices.add(wallet.addAddressBookEntry(address, "hi there!"));
    }
    entries = wallet.getAddressBookEntries();
    assertEquals(numEntriesStart + NUM_ENTRIES, entries.size());
    for (int idx : indices) {
      boolean found = false;
      for (MoneroAddressBookEntry entry : entries) {
        if (idx == entry.getIndex()) {
          testAddressBookEntry(entry);
          assertEquals(entry.getAddress(), address);
          assertEquals(entry.getDescription(), "hi there!");
          found = true;
          break;
        }
      }
      assertTrue("Index " + idx + " not found in address book indices", found);
    }
    
    // delete entries at starting index
    int deleteIdx = indices.get(0);
    for (int i = 0; i < indices.size(); i++) {
      wallet.deleteAddressBookEntry(deleteIdx);
    }
    entries = wallet.getAddressBookEntries();
    assertEquals(entries.size(), numEntriesStart);
    
    // test adding integrated addresses
    indices = new ArrayList<Integer>();
    String paymentId = "03284e41c342f03"; // payment id less one character
    Map<Integer, MoneroIntegratedAddress> integratedAddresses = new HashMap<Integer, MoneroIntegratedAddress>();
    Map<Integer, String> integratedDescriptions = new HashMap<Integer, String>();
    for (int i = 0; i < NUM_ENTRIES; i++) {
      MoneroIntegratedAddress integratedAddress = wallet.getIntegratedAddress(paymentId + i); // create unique integrated address
      String uuid = UUID.randomUUID().toString();
      int idx = wallet.addAddressBookEntry(integratedAddress.toString(), uuid);
      indices.add(idx);
      integratedAddresses.put(idx, integratedAddress);
      integratedDescriptions.put(idx, uuid);
    }
    entries = wallet.getAddressBookEntries();
    assertEquals(entries.size(), numEntriesStart + NUM_ENTRIES);
    for (int idx : indices) {
      boolean found = false;
      for (MoneroAddressBookEntry entry : entries) {
        if (idx == entry.getIndex()) {
          testAddressBookEntry(entry);
          assertEquals(entry.getDescription(), integratedDescriptions.get(idx));
          assertEquals(entry.getAddress(), integratedAddresses.get(idx).getStandardAddress());
          assertTrue(MoneroUtils.paymentIdsEqual(integratedAddresses.get(idx).getPaymentId(), entry.getPaymentId()));
          found = true;
          break;
        }
      }
      assertTrue("Index " + idx + " not found in address book indices", found);
    }
    
    // delete entries at starting index
    deleteIdx = indices.get(0);
    for (int i = 0; i < indices.size(); i++) {
      wallet.deleteAddressBookEntry(deleteIdx);
    }
    entries = wallet.getAddressBookEntries();
    assertEquals(numEntriesStart, entries.size());
  }
  
  // Can rescan spent
  @Test
  public void testRescanSpent() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    wallet.rescanSpent();
  }
  
  // Can save the wallet
  @Test
  public void testSave() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    wallet.save();
  }
  
  // Can close a wallet
  @Test
  public void testClose() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    
    // create a test wallet
    String path = UUID.randomUUID().toString();
    wallet.createWalletRandom(path, TestUtils.WALLET_RPC_PASSWORD);
    wallet.sync();
    assertTrue(wallet.getHeight() > 1);
    
    // close the wallet
    wallet.close();
    
    // attempt to interact with the wallet
    try {
      wallet.getHeight();
    } catch (MoneroException e) {
      assertEquals(-13, (int) e.getCode());
      assertEquals("No wallet file", e.getMessage());
    }
    try {
      wallet.getMnemonic();
    } catch (MoneroException e) {
      assertEquals(-13, (int) e.getCode());
      assertEquals("No wallet file", e.getMessage());
    }
    try {
      wallet.sync();
    } catch (MoneroException e) {
      assertEquals(-13, (int) e.getCode());
      assertEquals("No wallet file", e.getMessage());
    }
    
    // re-open the wallet
    wallet.openWallet(path, TestUtils.WALLET_RPC_PASSWORD);
    wallet.sync();
    assertEquals(daemon.getHeight(), wallet.getHeight());
    
    // close the wallet
    wallet.close();
    
    // re-open main test wallet for other tests
    wallet.openWallet(TestUtils.WALLET_RPC_NAME_1, TestUtils.WALLET_PASSWORD);
  }
  
  // Can stop the RPC server
  @Test
  @Ignore   // disabled so server not actually stopped
  public void testStop() {
    wallet.stop();
  }
  
  // ---------------------------------- PRIVATE -------------------------------
  
  private static void testAddressBookEntry(MoneroAddressBookEntry entry) {
    assertTrue(entry.getIndex() >= 0);
    assertNotNull(entry.getAddress());
    assertNotNull(entry.getDescription());
  }
  
  // rpc-specific tx tests
  @Override
  protected void testTxWallet(MoneroTxWallet tx, TestContext ctx) {
    ctx = new TestContext(ctx);
    
    // run common tests
    super.testTxWallet(tx, ctx);
  }
  
  protected void testInvalidTxIdException(MoneroException e) {
    super.testInvalidTxIdException(e);
    assertEquals(-8, (int) e.getCode());
  }
  
  protected void testInvalidTxKeyException(MoneroException e) {
    super.testInvalidTxKeyException(e);
    assertEquals(-25, (int) e.getCode());
  }
  
  protected void testInvalidAddressException(MoneroException e) {
    super.testInvalidAddressException(e);
    assertEquals(-2, (int) e.getCode());
  }
  
  protected void testInvalidSignatureException(MoneroException e) {
    super.testInvalidSignatureException(e);
    assertEquals(-1, (int) e.getCode()); // TODO: sometimes comes back bad, sometimes throws exception.  ensure txs come from different addresses?
  }
  
  protected void testNoSubaddressException(MoneroException e) {
    super.testNoSubaddressException(e);
    assertEquals(-1, (int) e.getCode());
  }
  
  protected void testSignatureHeaderCheckException(MoneroException e) {
    super.testSignatureHeaderCheckException(e);
    assertEquals(-1, (int) e.getCode());
  }
  
  // -------------------- OVERRIDES TO BE DIRECTLY RUNNABLE -------------------
  
  @Test
  public void testGetPath() {
    super.testGetPath();
  }

  @Override
  public void testGetHeight() {
    super.testGetHeight();
  }

  @Override
  public void testGetMnemonic() {
    super.testGetMnemonic();
  }

  @Override
  public void testGetLanguages() {
    super.testGetLanguages();
  }

  @Override
  public void testGetPrivateViewKey() {
    super.testGetPrivateViewKey();
  }
  
  @Override
  public void testGetPrivateSpendKey() {
    super.testGetPrivateSpendKey();
  }

  @Override
  public void testGetPrimaryAddress() {
    super.testGetPrimaryAddress();
  }

  @Override
  public void testGetIntegratedAddressFromPaymentId() {
    super.testGetIntegratedAddressFromPaymentId();
  }

  @Override
  public void testDecodeIntegratedAddress() {
    super.testDecodeIntegratedAddress();
  }

  @Override
  public void testSyncWithoutProgress() {
    super.testSyncWithoutProgress();
  }
  
  @Override
  public void testCompareGroundTruth() {
    super.testCompareGroundTruth();
  }

  @Override
  public void testGetAccountsWithoutSubaddresses() {
    super.testGetAccountsWithoutSubaddresses();
  }

  @Override
  public void testGetAccountsWithSubaddresses() {
    super.testGetAccountsWithSubaddresses();
  }

  @Override
  public void testGetAccount() {
    super.testGetAccount();
  }

  @Override
  public void testCreateAccountWithoutLabel() {
    super.testCreateAccountWithoutLabel();
  }

  @Override
  public void testCreateAccountWithLabel() {
    super.testCreateAccountWithLabel();
  }

  @Override
  public void testGetSubaddresses() {
    super.testGetSubaddresses();
  }

  @Override
  public void testGetSubaddressesByIndices() {
    super.testGetSubaddressesByIndices();
  }

  @Override
  public void testGetSubaddressByIndex() {
    super.testGetSubaddressByIndex();
  }

  @Override
  public void testCreateSubaddress() {
    super.testCreateSubaddress();
  }

  @Override
  public void testGetSubaddressAddress() {
    super.testGetSubaddressAddress();
  }

  @Override
  public void testGetAddressIndices() {
    super.testGetAddressIndices();
  }

  @Override
  public void testGetAllBalances() {
    super.testGetAllBalances();
  }

  @Override
  public void testGetTxsWallet() {
    super.testGetTxsWallet();
  }
  
  @Override
  public void testGetTxsById() {
    super.testGetTxsById();
  }

  @Override
  public void testGetTxsWithQuery() {
    super.testGetTxsWithQuery();
  }
  
  @Override
  public void testGetTxsByHeight() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    super.testGetTxsByHeight();
  }

  @Override
  public void testGetTxsWithPaymentIds() {
    super.testGetTxsWithPaymentIds();
  }

  @Override
  public void testGetTxsFieldsWithFiltering() {
    super.testGetTxsFieldsWithFiltering();
  }

  @Override
  public void testGetTxsValidateInputs() {
    super.testGetTxsValidateInputs();
  }

  @Override
  public void testGetTransfers() {
    super.testGetTransfers();
  }

  @Override
  public void testGetTransfersWithQuery() {
    super.testGetTransfersWithQuery();
  }

  @Override
  public void testGetTransfersValidateInputs() {
    super.testGetTransfersValidateInputs();
  }
  
  @Override
  public void testGetIncomingOutgoingTransfers() {
    super.testGetIncomingOutgoingTransfers();
  }

  @Override
  public void testGetOutputs() {
    super.testGetOutputs();
  }

  @Override
  public void testGetOutputsWithQuery() {
    super.testGetOutputsWithQuery();
  }

  @Override
  public void testGetOutputsValidateInputs() {
    super.testGetOutputsValidateInputs();
  }

  @Override
  public void testAccounting() {
    super.testAccounting();
  }

  @Override
  public void testSetTransactionNote() {
    super.testSetTransactionNote();
  }

  @Override
  public void testSetTransactionNotes() {
    super.testSetTransactionNotes();
  }

  @Override
  public void testCheckTxKey() {
    super.testCheckTxKey();
  }

  @Override
  public void testCheckTxProof() {
    super.testCheckTxProof();
  }

  @Override
  public void testCheckSpendProof() {
    super.testCheckSpendProof();
  }

  @Override
  public void testGetReserveProofWallet() {
    super.testGetReserveProofWallet();
  }

  @Override
  public void testGetReserveProofAccount() {
    super.testGetReserveProofAccount();
  }

  @Override
  public void testGetOutputsHex() {
    super.testGetOutputsHex();
  }

  @Override
  public void testImportOutputsHex() {
    super.testImportOutputsHex();
  }

  @Override
  public void testGetSignedKeyImages() {
    super.testGetSignedKeyImages();
  }

  @Override
  public void testGetNewKeyImagesFromLastImport() {
    super.testGetNewKeyImagesFromLastImport();
  }

  @Override
  public void testImportKeyImages() {
    super.testImportKeyImages();
  }

  @Override
  public void testSignAndVerifyMessages() {
    super.testSignAndVerifyMessages();
  }

  @Override
  public void testSetAttributes() {
    super.testSetAttributes();
  }
  
  @Override
  public void testCreatePaymentUri() {
    super.testCreatePaymentUri();
  }

  @Override
  public void testMining() {
    super.testMining();
  }
  
  @Override
  public void testSyncWithPoolSameAccounts() {
    super.testSyncWithPoolSameAccounts();
  }
  
  @Override
  public void testSyncWithPoolSubmitAndDiscard() {
    super.testSyncWithPoolSubmitAndDiscard();
  }
  
  @Override
  public void testSyncWithPoolSubmitAndRelay() {
    super.testSyncWithPoolSubmitAndRelay();
  }
  
  @Override
  public void testSyncWithPoolRelay() {
    super.testSyncWithPoolRelay();
  }
  
  @Override
  public void testSendToExternal() {
    super.testSendToExternal();
  }

  @Override
  public void testSendFromSubaddresses() {
    super.testSendFromSubaddresses();
  }

  @Override
  public void testSendFromSubaddressesSplit() {
    super.testSendFromSubaddressesSplit();
  }

  @Override
  public void testSend() {
    super.testSend();
  }

  @Override
  public void testSendWithPaymentId() {
    super.testSendWithPaymentId();
  }

  @Override
  public void testSendWithRingSize() {
    super.testSendWithRingSize();
  }

  @Override
  public void testSendSplit() {
    super.testSendSplit();
  }

  @Override
  public void testCreateThenRelay() {
    super.testCreateThenRelay();
  }

  @Override
  public void testCreateThenRelaySplit() {
    super.testCreateThenRelaySplit();
  }

  @Override
  public void testSendToMultiple() {
    super.testSendToMultiple();
  }

  @Override
  public void testSendToMultipleSplit() {
    super.testSendToMultipleSplit();
  }
  
  @Override
  public void testSendDustToMultipleSplit() {
    super.testSendDustToMultipleSplit();
  }

  @Override
  public void testUpdateLockedSameAccount() {
    super.testUpdateLockedSameAccount();
  }

  @Override
  public void testUpdateLockedSameAccountSplit() {
    super.testUpdateLockedSameAccountSplit();
  }

  @Override
  public void testUpdateLockedDifferentAccounts() {
    super.testUpdateLockedDifferentAccounts();
  }

  @Override
  public void testUpdateLockedDifferentAccountsSplit() {
    super.testUpdateLockedDifferentAccountsSplit();
  }
  
  @Override
  public void testSweepOutputs() {
    super.testSweepOutputs();
  }

  @Override
  public void testSweepSubaddresses() {
    super.testSweepSubaddresses();
  }

  @Override
  public void testSweepAccounts() {
    super.testSweepAccounts();
  }

  @Override
  public void testSweepWalletByAccounts() {
    super.testSweepWalletByAccounts();
  }
  
  @Override
  public void testSweepWalletBySubaddresses() {
    super.testSweepWalletBySubaddresses();
  }

  @Override
  public void testSweepDustNoRelay() {
    super.testSweepDustNoRelay();
  }

  @Override
  public void testSweepDust() {
    super.testSweepDust();
  }
  
  @Override
  public void testRescanBlockchain() {
    super.testRescanBlockchain();
  }
  
  @Override
  public void testMultisig() {
    super.testMultisig();
  }
  
  @Override
  public void testSaveAndClose() {
    super.testSaveAndClose();
  }
}
