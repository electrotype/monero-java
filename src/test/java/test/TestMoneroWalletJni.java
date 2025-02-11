package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import monero.daemon.model.MoneroKeyImage;
import monero.daemon.model.MoneroMiningStatus;
import monero.daemon.model.MoneroNetworkType;
import monero.rpc.MoneroRpcConnection;
import monero.utils.MoneroException;
import monero.utils.MoneroUtils;
import monero.wallet.MoneroWallet;
import monero.wallet.MoneroWalletJni;
import monero.wallet.model.MoneroAccount;
import monero.wallet.model.MoneroDestination;
import monero.wallet.model.MoneroMultisigInfo;
import monero.wallet.model.MoneroMultisigInitResult;
import monero.wallet.model.MoneroOutputQuery;
import monero.wallet.model.MoneroOutputWallet;
import monero.wallet.model.MoneroSendRequest;
import monero.wallet.model.MoneroSyncListener;
import monero.wallet.model.MoneroSyncResult;
import monero.wallet.model.MoneroTransfer;
import monero.wallet.model.MoneroTransferQuery;
import monero.wallet.model.MoneroTxWallet;
import monero.wallet.model.MoneroWalletListener;
import monero.wallet.model.MoneroWalletListenerI;
import utils.StartMining;
import utils.TestUtils;

/**
 * Tests specific to the JNI wallet.
 */
public class TestMoneroWalletJni extends TestMoneroWalletCommon {

  protected MoneroWalletJni wallet;

  public TestMoneroWalletJni() {
    this.wallet = (MoneroWalletJni) getTestWallet();
  }

  @BeforeClass
  public static void beforeClass() throws Exception {
    //Assume.assumeTrue(false); // ignore entire class
  }

  @Override
  protected MoneroWallet getTestWallet() {
    return TestUtils.getWalletJni();
  }
  

  @Override
  protected MoneroWallet createRandomWallet() {
    MoneroWalletJni wallet = MoneroWalletJni.createWalletRandom(TestUtils.TEST_WALLETS_DIR + "/" + UUID.randomUUID().toString(), TestUtils.WALLET_PASSWORD, TestUtils.NETWORK_TYPE, daemon.getRpcConnection());
    //wallet.sync();
    //wallet.save();
    wallet.startSyncing();
    return wallet;
  }

  @Override
  protected MoneroWallet openWallet(String path) {
    MoneroWalletJni wallet = MoneroWalletJni.openWallet(path, TestUtils.WALLET_PASSWORD, TestUtils.NETWORK_TYPE, daemon.getRpcConnection());
    //wallet.sync();
    //wallet.save();
    wallet.startSyncing();
    return wallet;
  }
  
  // --------------- DEMONSTRATION OF MONERO CORE ISSUES ----------------------
  
  /**
   * This test demonstrates that importing key images erases incoming transfers.
   */
  @Ignore // TODO monero-core: fix this https://github.com/monero-project/monero/issues/5812
  @Test
  public void testImportKeyImagesAndTransfers() {
    MoneroWalletJni wallet = null; // create a wallet for this test since it becomes corrupt TODO: use common wallet and move to common tests when fixed
    try {
      
      // create and sync a new wallet
      String path = getRandomWalletPath();
      wallet = MoneroWalletJni.createWalletFromMnemonic(path, TestUtils.WALLET_PASSWORD, MoneroNetworkType.STAGENET, TestUtils.MNEMONIC, TestUtils.getDaemonRpc().getRpcConnection(), TestUtils.FIRST_RECEIVE_HEIGHT);
      wallet.sync();
      
      // repeatedly export and re-import key images and test transfer size
      for (int i = 0; i < 3; i++) {
        
        // get incoming transfers before importing
        List<MoneroTransfer> inTransfers1 = wallet.getTransfers(new MoneroTransferQuery().setIsIncoming(true));
        
        // export and re-import key images
        List<MoneroKeyImage> keyImages = wallet.getKeyImages();
        wallet.importKeyImages(keyImages);
        
        // get incoming transfers after importing
        List<MoneroTransfer> inTransfers2 = wallet.getTransfers(new MoneroTransferQuery().setIsIncoming(true));
        
        // incoming transfers should be equal
        assertEquals(inTransfers1.size(), inTransfers2.size());
        assertEquals(inTransfers1, inTransfers2);
      }
    } finally {
      wallet.close();
    }
  }
  
  /**
   * Test the daemon's ability to not hang from wallets which are continuously
   * syncing, have registered listeners, and which are not closed.
   */
  @Test
  @Ignore // TODO monero core: disabled because observing memory leak behavior when all tests run together
  public void testCreateWalletsWithoutClose() {
    
    // lets make some wallets and then go away
    for (int i = 0; i < 20; i++) {
      String path = getRandomWalletPath();
      MoneroWalletJni willLeaveYouHanging = MoneroWalletJni.createWalletRandom(path, TestUtils.WALLET_PASSWORD, MoneroNetworkType.STAGENET, TestUtils.getDaemonRpc().getRpcConnection());
      willLeaveYouHanging.startSyncing();
      willLeaveYouHanging.addListener(new MoneroWalletListener());  // listen for wallet events which could aggrevate hanging
    }
    
    // check in on the daemon
    daemon.getHeight();
    
    // start mining
    try { StartMining.startMining(); }
    catch (MoneroException e) { }
    
    // wait for a block
    daemon.getNextBlockHeader();
    
    // stop mining
    try { daemon.stopMining(); }
    catch (MoneroException e) { }
    
    // check in on the daemon
    daemon.getHeight();
    
    // wallet's intentionally not closed (daemon da man)
  }
  
  // ------------------------------- BEGIN TESTS ------------------------------
  
  @Test
  public void testDaemon() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    assertTrue(wallet.isConnected());
    long daemonHeight = wallet.getDaemonHeight();
    assertTrue(daemonHeight > 0);

  }
  
  @Test
  public void testGetDaemonMaxPeerHeight() {
    long height = wallet.getDaemonMaxPeerHeight();
    assertTrue(height > 0);
  }
  
//  @Test
//  public void getApproximateChainHeight() {
//    long height = wallet.getApproximateChainHeight();
//    assertTrue(height > 0);
//  }
  
  @Test
  public void testSetDaemonConnection() {
    
    // create random wallet with defaults
    String path = getRandomWalletPath();
    MoneroWalletJni wallet = MoneroWalletJni.createWalletRandom(path, TestUtils.WALLET_PASSWORD);
    assertEquals(null, wallet.getDaemonConnection());
    
    // set daemon uri
    wallet.setDaemonConnection(TestUtils.DAEMON_RPC_URI);
    assertEquals(new MoneroRpcConnection(TestUtils.DAEMON_RPC_URI), wallet.getDaemonConnection());
    assertTrue(daemon.isConnected());
    
    // nullify daemon connection
    wallet.setDaemonConnection((String) null);
    assertEquals(null, wallet.getDaemonConnection());
    wallet.setDaemonConnection(TestUtils.DAEMON_RPC_URI);
    assertEquals(new MoneroRpcConnection(TestUtils.DAEMON_RPC_URI), wallet.getDaemonConnection());
    wallet.setDaemonConnection((MoneroRpcConnection) null);
    assertEquals(null, wallet.getDaemonConnection());
    
    // set daemon uri to non-daemon
    wallet.setDaemonConnection("www.getmonero.org");
    assertEquals(new MoneroRpcConnection("www.getmonero.org"), wallet.getDaemonConnection());
    assertFalse(wallet.isConnected());
    
    // set daemon to invalid uri
    wallet.setDaemonConnection("abc123");
    assertFalse(wallet.isConnected());
    
    // attempt to sync
    try {
      wallet.sync();
      fail("Exception expected");
    } catch (MoneroException e) {
      assertEquals("Wallet is not connected to daemon", e.getMessage());
    } finally {
      wallet.close();
    }
  }
  
  @Test
  public void testCreateWalletRandom() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);

    // create random wallet with defaults
    String path = getRandomWalletPath();
    MoneroWalletJni wallet = MoneroWalletJni.createWalletRandom(path, TestUtils.WALLET_PASSWORD);
    MoneroUtils.validateMnemonic(wallet.getMnemonic());
    MoneroUtils.validateAddress(wallet.getPrimaryAddress());
    assertEquals(MoneroNetworkType.MAINNET, wallet.getNetworkType());
    assertEquals(null, wallet.getDaemonConnection());
    assertFalse(wallet.isConnected());
    assertEquals("English", wallet.getLanguage());
    assertEquals(path, wallet.getPath());
    assertFalse(wallet.isSynced());
    assertEquals(1, wallet.getHeight()); // TODO monero core: why does height of new unsynced wallet start at 1?
    assertTrue(wallet.getRestoreHeight() >= 0);
    
    // cannot get daemon chain height
    try {
      wallet.getDaemonHeight();
    } catch (MoneroException e) {
      assertEquals("Wallet is not connected to daemon", e.getMessage());
    }
    
    // set daemon connection and check chain height
    wallet.setDaemonConnection(daemon.getRpcConnection());
    assertEquals(daemon.getHeight(), wallet.getDaemonHeight());
    
    // close wallet which releases resources
    wallet.close();

    // create random wallet with non defaults
    path = getRandomWalletPath();
    wallet = MoneroWalletJni.createWalletRandom(path, TestUtils.WALLET_PASSWORD, MoneroNetworkType.TESTNET, daemon.getRpcConnection(), "Spanish");
    MoneroUtils.validateMnemonic(wallet.getMnemonic());
    MoneroUtils.validateAddress(wallet.getPrimaryAddress());
    assertEquals(MoneroNetworkType.TESTNET, wallet.getNetworkType());
    assertNotNull(wallet.getDaemonConnection());
    assertTrue(daemon.getRpcConnection() != wallet.getDaemonConnection());
    assertTrue(daemon.getRpcConnection().equals(wallet.getDaemonConnection()));
    assertTrue(wallet.isConnected());
    assertEquals("Spanish", wallet.getLanguage());
    assertEquals(path, wallet.getPath());
    assertFalse(wallet.isSynced());
    assertEquals(1, wallet.getHeight()); // TODO monero core: why is height of unsynced wallet 1?
    if (daemon.isConnected()) assertEquals(daemon.getHeight(), wallet.getRestoreHeight());
    else assertTrue(wallet.getRestoreHeight() >= 0);
    wallet.close();
  }
  
  @Test
  public void testCreateWalletFromMnemonic() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    
    // create wallet with mnemonic and defaults
    String path = getRandomWalletPath();
    MoneroWalletJni wallet = MoneroWalletJni.createWalletFromMnemonic(path, TestUtils.WALLET_PASSWORD, TestUtils.NETWORK_TYPE, TestUtils.MNEMONIC, null, null);
    assertEquals(TestUtils.MNEMONIC, wallet.getMnemonic());
    assertEquals(TestUtils.ADDRESS, wallet.getPrimaryAddress());
    assertEquals(TestUtils.NETWORK_TYPE, wallet.getNetworkType());
    assertEquals(null, wallet.getDaemonConnection());
    assertFalse(wallet.isConnected());
    assertEquals("English", wallet.getLanguage());
    assertEquals(path, wallet.getPath());
    assertFalse(wallet.isSynced());
    assertEquals(1, wallet.getHeight());
    assertEquals(0, wallet.getRestoreHeight());
    try { wallet.startSyncing(); } catch (MoneroException e) { assertEquals("Wallet is not connected to daemon", e.getMessage()); }
    wallet.close();
    
    // create wallet without restore height
    path = getRandomWalletPath();
    wallet = MoneroWalletJni.createWalletFromMnemonic(path, TestUtils.WALLET_PASSWORD, TestUtils.NETWORK_TYPE, TestUtils.MNEMONIC, daemon.getRpcConnection(), null);
    assertEquals(TestUtils.MNEMONIC, wallet.getMnemonic());
    assertEquals(TestUtils.ADDRESS, wallet.getPrimaryAddress());
    assertEquals(TestUtils.NETWORK_TYPE, wallet.getNetworkType());
    assertNotNull(wallet.getDaemonConnection());
    assertTrue(daemon.getRpcConnection() != wallet.getDaemonConnection());
    assertTrue(daemon.getRpcConnection().equals(wallet.getDaemonConnection()));
    assertTrue(wallet.isConnected());
    assertEquals("English", wallet.getLanguage());
    assertEquals(path, wallet.getPath());
    assertFalse(wallet.isSynced());
    assertEquals(1, wallet.getHeight()); // TODO monero core: why does height of new unsynced wallet start at 1?
    assertEquals(0, wallet.getRestoreHeight());
    wallet.close();
    
    // create wallet with mnemonic, no connection, and restore height
    long restoreHeight = 10000;
    path = getRandomWalletPath();
    wallet = MoneroWalletJni.createWalletFromMnemonic(path, TestUtils.WALLET_PASSWORD, TestUtils.NETWORK_TYPE, TestUtils.MNEMONIC, null, restoreHeight);
    assertEquals(TestUtils.MNEMONIC, wallet.getMnemonic());
    assertEquals(TestUtils.ADDRESS, wallet.getPrimaryAddress());
    assertEquals(TestUtils.NETWORK_TYPE, wallet.getNetworkType());
    assertNull(wallet.getDaemonConnection());
    assertFalse(wallet.isConnected());
    assertEquals("English", wallet.getLanguage());
    assertEquals(path, wallet.getPath());
    assertEquals(1, wallet.getHeight()); // TODO monero core: why does height of new unsynced wallet start at 1?
    assertEquals(restoreHeight, wallet.getRestoreHeight());
    wallet.save();
    wallet.close();
    wallet = MoneroWalletJni.openWallet(path, TestUtils.WALLET_PASSWORD, TestUtils.NETWORK_TYPE);
    assertFalse(wallet.isConnected());
    assertFalse(wallet.isSynced());
    assertEquals(1, wallet.getHeight());
    assertEquals(0, wallet.getRestoreHeight()); // restore height is lost after closing
    wallet.close();

    // create wallet with mnemonic, connection, and restore height
    path = getRandomWalletPath();
    wallet = MoneroWalletJni.createWalletFromMnemonic(path, TestUtils.WALLET_PASSWORD, TestUtils.NETWORK_TYPE, TestUtils.MNEMONIC, daemon.getRpcConnection(), restoreHeight);
    assertEquals(TestUtils.MNEMONIC, wallet.getMnemonic());
    assertEquals(TestUtils.ADDRESS, wallet.getPrimaryAddress());
    assertEquals(TestUtils.NETWORK_TYPE, wallet.getNetworkType());
    assertNotNull(wallet.getDaemonConnection());
    assertTrue(daemon.getRpcConnection() != wallet.getDaemonConnection());
    assertTrue(daemon.getRpcConnection().equals(wallet.getDaemonConnection()));
    assertTrue(wallet.isConnected());
    assertEquals("English", wallet.getLanguage());
    assertEquals(path, wallet.getPath());
    assertFalse(wallet.isSynced());
    assertEquals(1, wallet.getHeight()); // TODO monero core: why does height of new unsynced wallet start at 1?
    assertEquals(restoreHeight, wallet.getRestoreHeight());
    wallet.close();
  }

  @Test
  public void testCreateWalletFromKeys() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    
    // recreate test wallet from keys
    String path = getRandomWalletPath();
    MoneroWalletJni walletKeys = MoneroWalletJni.createWalletFromKeys(path, TestUtils.WALLET_PASSWORD, wallet.getNetworkType(), wallet.getPrimaryAddress(), wallet.getPrivateViewKey(), wallet.getPrivateSpendKey(), wallet.getDaemonConnection(), TestUtils.FIRST_RECEIVE_HEIGHT, null);
    try {
      assertEquals(wallet.getMnemonic(), walletKeys.getMnemonic());
      assertEquals(wallet.getPrimaryAddress(), walletKeys.getPrimaryAddress());
      assertEquals(wallet.getPrivateViewKey(), walletKeys.getPrivateViewKey());
      assertEquals(wallet.getPublicViewKey(), walletKeys.getPublicViewKey());
      assertEquals(wallet.getPrivateSpendKey(), walletKeys.getPrivateSpendKey());
      assertEquals(wallet.getPublicSpendKey(), walletKeys.getPublicSpendKey());
      assertEquals(TestUtils.FIRST_RECEIVE_HEIGHT, walletKeys.getRestoreHeight());
      assertTrue(walletKeys.isConnected());
      assertFalse(walletKeys.isSynced());
    } finally {
      walletKeys.close();
    }
  }
  
  // Can get the public view key
  @Test
  public void testGetPublicViewKey() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    String publicViewKey = wallet.getPublicViewKey();
    MoneroUtils.validatePublicViewKey(publicViewKey);
  }
  
  // Can get the public spend key
  @Test
  public void testGetPublicSpendKey() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    String publicSpendKey = wallet.getPublicSpendKey();
    MoneroUtils.validatePublicSpendKey(publicSpendKey);
  }
  
  // Can re-sync an existing wallet from scratch
  @Test
  @Ignore // TODO monero core: cannot re-sync from lower block height after wallet saved
  public void testResyncExisting() {
    assertTrue(MoneroWalletJni.walletExists(TestUtils.WALLET_JNI_PATH_1));
    MoneroWalletJni wallet = MoneroWalletJni.openWallet(TestUtils.WALLET_JNI_PATH_1, TestUtils.WALLET_PASSWORD, MoneroNetworkType.STAGENET);
    wallet.setDaemonConnection(TestUtils.getDaemonRpc().getRpcConnection());
    //long startHeight = TestUtils.TEST_RESTORE_HEIGHT;
    long startHeight = 0;
    SyncProgressTester progressTester = new SyncProgressTester(wallet, startHeight, wallet.getDaemonHeight());
    wallet.setRestoreHeight(1);
    MoneroSyncResult result = wallet.sync(1l, progressTester);
    progressTester.onDone(wallet.getDaemonHeight());
    
    // test result after syncing
    assertTrue(wallet.isConnected());
    assertTrue(wallet.isSynced());
    assertEquals(wallet.getDaemonHeight() - startHeight, (long) result.getNumBlocksFetched());
    assertTrue(result.getReceivedMoney());
    assertEquals(daemon.getHeight(), wallet.getHeight());
    wallet.close(true);
  }

  // Can sync a wallet with a randomly generated seed
  @Test
  public void testSyncRandom() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    assertTrue("Not connected to daemon", daemon.isConnected());

    // create test wallet
    long restoreHeight = daemon.getHeight();
    MoneroWalletJni wallet = MoneroWalletJni.createWalletRandom(getRandomWalletPath(), TestUtils.WALLET_PASSWORD, TestUtils.NETWORK_TYPE, TestUtils.getDaemonRpc().getRpcConnection(), null);

    // test wallet's height before syncing
    assertEquals(TestUtils.getDaemonRpc().getRpcConnection(), wallet.getDaemonConnection());
    assertEquals(restoreHeight, wallet.getDaemonHeight());
    assertTrue(wallet.isConnected());
    assertFalse(wallet.isSynced());
    assertEquals(1, wallet.getHeight());
    assertEquals(restoreHeight, wallet.getRestoreHeight());
    assertEquals(daemon.getHeight(), wallet.getDaemonHeight());

    // sync the wallet
    SyncProgressTester progressTester = new SyncProgressTester(wallet, wallet.getRestoreHeight(), wallet.getDaemonHeight());
    MoneroSyncResult result = wallet.sync(null, progressTester);
    progressTester.onDone(wallet.getDaemonHeight());
    
    // test result after syncing
    MoneroWalletJni walletGt = TestUtils.createWalletGroundTruth(TestUtils.NETWORK_TYPE, wallet.getMnemonic(), restoreHeight);
    try {
      assertTrue(wallet.isConnected());
      assertTrue(wallet.isSynced());
      assertEquals(0, (long) result.getNumBlocksFetched());
      assertFalse(result.getReceivedMoney());
      assertEquals(daemon.getHeight(), wallet.getHeight());

      // sync the wallet with default params
      wallet.sync();
      assertTrue(wallet.isSynced());
      assertEquals(daemon.getHeight(), wallet.getHeight());
      
      // compare wallet to ground truth
      testWalletsEqualOnChain(walletGt, wallet);
    } finally {
      walletGt.close();
      wallet.close();
    }
    
    // attempt to sync unconnected wallet
    wallet = MoneroWalletJni.createWalletRandom(getRandomWalletPath(), TestUtils.WALLET_PASSWORD, TestUtils.NETWORK_TYPE, null, null);
    try {
      wallet.sync();
      fail("Should have thrown exception");
    } catch (MoneroException e) {
      // exception expected
    } finally {
      wallet.close();
    }
  }
  
  // Can sync a wallet with a mnemonic
  @Test
  public void testSyncMnemonicFromGenesis() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS && !LITE_MODE);
    testSyncMnemonic(null, null, true, false);
  }
  
  @Test
  public void testSyncMnemonicFromRestoreHeight() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    testSyncMnemonic(null, TestUtils.FIRST_RECEIVE_HEIGHT);
  }
  
  @Test
  public void testSyncMnemonicFromStartHeight() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS && !LITE_MODE);
    testSyncMnemonic(TestUtils.FIRST_RECEIVE_HEIGHT, null, false, true);
  }
  
  @Test
  public void testSyncMnemonicStartHeightLTRestoreHeight() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS && !LITE_MODE);
    testSyncMnemonic(TestUtils.FIRST_RECEIVE_HEIGHT, TestUtils.FIRST_RECEIVE_HEIGHT + 3l);
  }
  
  @Test
  public void testSyncMnemonicStartHeightGTRestoreHeight() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS && !LITE_MODE);
    testSyncMnemonic(TestUtils.FIRST_RECEIVE_HEIGHT + 3l, TestUtils.FIRST_RECEIVE_HEIGHT);
  }
  
  private void testSyncMnemonic(Long startHeight, Long restoreHeight) { testSyncMnemonic(startHeight, restoreHeight, false, false); }
  private void testSyncMnemonic(Long startHeight, Long restoreHeight, boolean skipGtComparison, boolean testPostSyncNotifications) {
    assertTrue("Not connected to daemon", daemon.isConnected());
    if (startHeight != null && restoreHeight != null) assertTrue(startHeight <= TestUtils.FIRST_RECEIVE_HEIGHT || restoreHeight <= TestUtils.FIRST_RECEIVE_HEIGHT);
    
    // create wallet from mnemonic
    MoneroWalletJni wallet = MoneroWalletJni.createWalletFromMnemonic(getRandomWalletPath(), TestUtils.WALLET_PASSWORD, TestUtils.NETWORK_TYPE, TestUtils.MNEMONIC, TestUtils.getDaemonRpc().getRpcConnection(), restoreHeight);
    
    // sanitize expected sync bounds
    if (restoreHeight == null) restoreHeight = 0l;
    long startHeightExpected = startHeight == null ? restoreHeight : startHeight;
    if (startHeightExpected == 0) startHeightExpected = 1;
    long endHeightExpected = wallet.getDaemonMaxPeerHeight();
    
    // test wallet and close as final step
    MoneroWalletJni walletGt = null;
    try {
      
      // test wallet's height before syncing
      assertTrue(wallet.isConnected());
      assertFalse(wallet.isSynced());
      assertEquals(1, wallet.getHeight());
      assertEquals((long) restoreHeight, (long) wallet.getRestoreHeight());
      
      // register a wallet listener which tests notifications throughout the sync
      WalletSyncTester walletSyncTester = new WalletSyncTester(wallet, startHeightExpected, endHeightExpected);
      wallet.addListener(walletSyncTester);
      
      // sync the wallet with a listener which tests sync notifications
      SyncProgressTester progressTester = new SyncProgressTester(wallet, startHeightExpected, endHeightExpected);
      MoneroSyncResult result = wallet.sync(startHeight, progressTester);
      
      // test completion of the wallet and sync listeners
      progressTester.onDone(wallet.getDaemonHeight());
      walletSyncTester.onDone(wallet.getDaemonHeight());
      
      // test result after syncing
      assertTrue(wallet.isSynced());
      assertEquals(wallet.getDaemonHeight() - startHeightExpected, (long) result.getNumBlocksFetched());
      assertTrue(result.getReceivedMoney());
      assertEquals(daemon.getHeight(), wallet.getHeight());
      assertEquals(daemon.getHeight(), wallet.getDaemonHeight());
      if (startHeightExpected > TestUtils.FIRST_RECEIVE_HEIGHT) assertTrue(wallet.getTxs().get(0).getHeight() > TestUtils.FIRST_RECEIVE_HEIGHT);  // wallet is partially synced so first tx happens after true restore height
      else assertEquals(TestUtils.FIRST_RECEIVE_HEIGHT, (long) wallet.getTxs().get(0).getHeight());  // wallet should be fully synced so first tx happens on true restore height
      
      // sync the wallet with default params
      result = wallet.sync();
      assertTrue(wallet.isSynced());
      assertEquals(daemon.getHeight(), wallet.getHeight());
      assertEquals(0, (long) result.getNumBlocksFetched());
      assertFalse(result.getReceivedMoney());
      
      // compare with ground truth
      if (!skipGtComparison) {
        walletGt = TestUtils.createWalletGroundTruth(TestUtils.NETWORK_TYPE, wallet.getMnemonic(), startHeightExpected);
        testWalletsEqualOnChain(walletGt, wallet);
      }
      
      // if testing post-sync notifications, wait for a block to be added to the chain
      // then test that sync arg listener was not invoked and registered wallet listener was invoked
      if (testPostSyncNotifications) {
        
        // start automatic syncing
        wallet.startSyncing();
        
        // attempt to start mining to push the network along  // TODO: TestUtils.tryStartMining() : reqId, TestUtils.tryStopMining(reqId)
        boolean startedMining = false;
        MoneroMiningStatus miningStatus = daemon.getMiningStatus();
        if (!miningStatus.isActive()) {
          try {
            wallet.startMining(7l, false, true);
            startedMining = true;
          } catch (Exception e) {
            // no problem
          }
        }
        
        try {
          
          // wait for block
          System.out.println("Waiting for next block to test post sync notifications");
          daemon.getNextBlockHeader();
          
          // ensure wallet has time to detect new block
          try {
            TimeUnit.MILLISECONDS.sleep(MoneroUtils.WALLET2_REFRESH_INTERVAL);
          } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
          }
          
          // test that wallet listener's onSyncProgress() and onNewBlock() were invoked after previous completion
          assertTrue(walletSyncTester.getOnSyncProgressAfterDone());
          assertTrue(walletSyncTester.getOnNewBlockAfterDone());
        } finally {
          if (startedMining) wallet.stopMining();
        }
      }
    } finally {
      if (walletGt != null) walletGt.close();
      wallet.close();
    }
  }
  
  // TODO: factor out common code with other testSync() methods
  @Test
  public void testSyncWalletFromKeys() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    
    // recreate test wallet from keys
    String path = getRandomWalletPath();
    MoneroWalletJni walletKeys = MoneroWalletJni.createWalletFromKeys(path, TestUtils.WALLET_PASSWORD,  wallet.getNetworkType(), wallet.getPrimaryAddress(), wallet.getPrivateViewKey(), wallet.getPrivateSpendKey(), wallet.getDaemonConnection(), TestUtils.FIRST_RECEIVE_HEIGHT, null);
    
    // create ground truth wallet for comparison
    MoneroWalletJni walletGt = TestUtils.createWalletGroundTruth(TestUtils.NETWORK_TYPE, TestUtils.MNEMONIC, TestUtils.FIRST_RECEIVE_HEIGHT);
    
    // test wallet and close as final step
    try {
      assertEquals(walletKeys.getMnemonic(), walletKeys.getMnemonic());
      assertEquals(walletKeys.getPrimaryAddress(), walletKeys.getPrimaryAddress());
      assertEquals(walletKeys.getPrivateViewKey(), walletKeys.getPrivateViewKey());
      assertEquals(walletKeys.getPublicViewKey(), walletKeys.getPublicViewKey());
      assertEquals(walletKeys.getPrivateSpendKey(), walletKeys.getPrivateSpendKey());
      assertEquals(walletKeys.getPublicSpendKey(), walletKeys.getPublicSpendKey());
      assertEquals(TestUtils.FIRST_RECEIVE_HEIGHT, walletKeys.getRestoreHeight());
      assertTrue(walletKeys.isConnected());
      assertFalse(walletKeys.isSynced());
      
      // sync the wallet
      SyncProgressTester progressTester = new SyncProgressTester(walletKeys, TestUtils.FIRST_RECEIVE_HEIGHT, walletKeys.getDaemonMaxPeerHeight());
      MoneroSyncResult result = walletKeys.sync(progressTester);
      progressTester.onDone(walletKeys.getDaemonHeight());
      
      // test result after syncing
      assertTrue(walletKeys.isSynced());
      assertEquals(walletKeys.getDaemonHeight() - TestUtils.FIRST_RECEIVE_HEIGHT, (long) result.getNumBlocksFetched());
      assertTrue(result.getReceivedMoney());
      assertEquals(daemon.getHeight(), walletKeys.getHeight());
      assertEquals(daemon.getHeight(), walletKeys.getDaemonHeight());
      assertEquals(TestUtils.FIRST_RECEIVE_HEIGHT, (long) walletKeys.getTxs().get(0).getHeight());  // wallet should be fully synced so first tx happens on true restore height
      
      // compare with ground truth
      testWalletsEqualOnChain(walletGt, walletKeys);
    } finally {
      walletGt.close();
      walletKeys.close();
    }
    
    // TODO monero core: importing key images can cause erasure of incoming transfers per wallet2.cpp:11957 which causes this test to fail
//  // sync the wallets until same height
//  while (wallet.getHeight() != walletKeys.getHeight()) {
//    wallet.sync();
//    walletKeys.sync(new WalletSyncPrinter());
//  }
//  
//  List<MoneroKeyImage> keyImages = walletKeys.getKeyImages();
//  walletKeys.importKeyImages(keyImages);
  }
  
  // TODO: test start syncing, notification of syncs happening, stop syncing, no notifications, etc
  @Test
  public void testStartStopSyncing() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    
    // test unconnected wallet
    String path = getRandomWalletPath();
    MoneroWalletJni wallet = MoneroWalletJni.createWalletRandom(path, TestUtils.WALLET_PASSWORD, TestUtils.NETWORK_TYPE, null, null);
    try {
      assertNotNull(wallet.getMnemonic());
      assertEquals(1, wallet.getHeight());
      assertEquals(BigInteger.valueOf(0), wallet.getBalance());
      wallet.startSyncing();
    } catch (MoneroException e) {
      assertEquals("Wallet is not connected to daemon", e.getMessage());
    } finally {
      wallet.close();
    }
    
    // test connected wallet
    path = getRandomWalletPath();
    wallet = MoneroWalletJni.createWalletRandom(path, TestUtils.WALLET_PASSWORD, TestUtils.NETWORK_TYPE, null, null);
    try {
      assertNotNull(wallet.getMnemonic());
      wallet.setDaemonConnection(daemon.getRpcConnection());
      wallet.startSyncing();
      assertEquals(1, wallet.getHeight());
      long chainHeight = wallet.getDaemonHeight();
      assertFalse(wallet.isSynced());
      assertEquals(BigInteger.valueOf(0), wallet.getBalance());
      wallet.setRestoreHeight(chainHeight - 3);
      assertEquals(chainHeight - 3, wallet.getRestoreHeight());
      assertEquals(daemon.getRpcConnection(), wallet.getDaemonConnection());
      wallet.stopSyncing();
      wallet.sync();
    } finally {
      wallet.close();
    }
    
    // test that sync starts automatically
    long restoreHeight = daemon.getHeight() - 100;
    path = getRandomWalletPath();
    wallet = MoneroWalletJni.createWalletFromMnemonic(path, TestUtils.WALLET_PASSWORD, TestUtils.NETWORK_TYPE, TestUtils.MNEMONIC, daemon.getRpcConnection(), restoreHeight);
    try {
      
      // start syncing
      assertEquals(restoreHeight, wallet.getRestoreHeight());
      wallet.startSyncing();
      
      // pause for sync to complete automatically
      try {
        System.out.println("Sleeping to test that sync starts automatically...");
        TimeUnit.MILLISECONDS.sleep(15000);
      } catch (InterruptedException e) {
        e.printStackTrace();
        throw new RuntimeException(e.getMessage());
      }
      
      // test that wallet is synced
      assertTrue(wallet.isSynced());
      assertEquals(daemon.getHeight(), wallet.getHeight());
      
      // stop syncing
      wallet.stopSyncing();
      assertTrue(wallet.isSynced()); // wallet is still synced
      
   // TODO monero core: wallet.cpp m_synchronized only ever set to true, never false
//      // wait for block to be added to chain
//      daemon.getNextBlockHeader();
//      
//      // wallet is no longer synced
//      assertFalse(wallet.isSynced());  
    } finally {
      wallet.close();
    }
  }
  
  // Is equal to the RPC wallet
  @Test
  public void testCompareRpcWallet() {
    testWalletsEqualOnChain(TestUtils.getWalletRpc(), wallet);
  }
  
  @Test
  public void testSave() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    
    // create unique path for new test wallet
    String path = TestUtils.TEST_WALLETS_DIR + "/test_wallet_" + System.currentTimeMillis();
    
    // wallet does not exist
    assertFalse(MoneroWalletJni.walletExists(path));
    
    // cannot open non-existant wallet
    try {
      MoneroWalletJni.openWallet(path, TestUtils.WALLET_PASSWORD, TestUtils.NETWORK_TYPE);
      fail("Cannot open non-existant wallet");
    } catch (MoneroException e) {
      assertEquals("Wallet does not exist at path: " + path, e.getMessage());
    }
    
    // create wallet at the path
    long restoreHeight = daemon.getHeight() - 200;
    MoneroWalletJni wallet = MoneroWalletJni.createWalletFromMnemonic(path, TestUtils.WALLET_PASSWORD, TestUtils.NETWORK_TYPE, TestUtils.MNEMONIC, null, restoreHeight);
    
    // test wallet is at newly created state
    assertTrue(MoneroWalletJni.walletExists(path));
    assertEquals(TestUtils.MNEMONIC, wallet.getMnemonic());
    assertEquals(TestUtils.NETWORK_TYPE, wallet.getNetworkType());
    assertNull(wallet.getDaemonConnection());
    assertEquals(restoreHeight, wallet.getRestoreHeight());
    assertEquals("English", wallet.getLanguage());
    assertEquals(1, wallet.getHeight());
    assertEquals(restoreHeight, wallet.getRestoreHeight());
    
    // set the wallet's connection and sync
    wallet.setDaemonConnection(TestUtils.getDaemonRpc().getRpcConnection());
    wallet.sync();
    assertEquals(wallet.getDaemonHeight(), wallet.getHeight());
    
    // close the wallet without saving
    wallet.close();
    
    // re-open the wallet
    wallet = MoneroWalletJni.openWallet(path, TestUtils.WALLET_PASSWORD, TestUtils.NETWORK_TYPE);
    
    // test wallet is at newly created state
    assertTrue(MoneroWalletJni.walletExists(path));
    assertEquals(TestUtils.MNEMONIC, wallet.getMnemonic());
    assertEquals(TestUtils.NETWORK_TYPE, wallet.getNetworkType());
    assertNull(wallet.getDaemonConnection());
    assertFalse(wallet.isConnected());
    assertEquals("English", wallet.getLanguage());
    assertFalse(wallet.isSynced());
    assertEquals(1, wallet.getHeight());
    assertEquals(0, wallet.getRestoreHeight()); // TODO monero-core: restoreHeight is reset to 0 after closing
    
    // set the wallet's connection and sync
    wallet.setDaemonConnection(TestUtils.getDaemonRpc().getRpcConnection());
    assertTrue(wallet.isConnected());
    wallet.setRestoreHeight(restoreHeight);
    wallet.sync();
    assertTrue(wallet.isSynced());
    assertEquals(wallet.getDaemonHeight(), wallet.getHeight());
    long prevHeight = wallet.getHeight();
    
    // save and close the wallet
    wallet.save();
    wallet.close();
    
    // re-open the wallet
    wallet = MoneroWalletJni.openWallet(path, TestUtils.WALLET_PASSWORD, TestUtils.NETWORK_TYPE);
    
    // test wallet state is saved
    assertFalse(wallet.isConnected());
    wallet.setDaemonConnection(TestUtils.getDaemonRpc().getRpcConnection());  // TODO monero core: daemon connection not stored in wallet files so must be explicitly set each time
    assertEquals(TestUtils.getDaemonRpc().getRpcConnection(), wallet.getDaemonConnection());
    assertTrue(wallet.isConnected());
    assertEquals(prevHeight, wallet.getHeight());
    assertEquals(0, wallet.getRestoreHeight()); // TODO monero core: restoreHeight is reset to 0 after closing
    assertTrue(MoneroWalletJni.walletExists(path));
    assertEquals(TestUtils.MNEMONIC, wallet.getMnemonic());
    assertEquals(TestUtils.NETWORK_TYPE, wallet.getNetworkType());
    assertEquals("English", wallet.getLanguage());
    
    // sync
    wallet.sync();
    
    // save and close the wallet
    wallet.close(true);
  }
  
  @Test
  public void testMoveTo() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    
    // create unique name for test wallet
    String walletName = "test_wallet_" + System.currentTimeMillis();
    String path = TestUtils.TEST_WALLETS_DIR + "/" + walletName;
    
    // wallet does not exist
    assertFalse(MoneroWalletJni.walletExists(path));
    
    // create wallet at the path
    long restoreHeight = daemon.getHeight() - 200;
    MoneroWalletJni wallet = MoneroWalletJni.createWalletFromMnemonic(path, TestUtils.WALLET_PASSWORD, TestUtils.NETWORK_TYPE, TestUtils.MNEMONIC, null, restoreHeight);
    String subaddressLabel = "Move test wallet subaddress!";
    MoneroAccount account = wallet.createAccount(subaddressLabel);
    wallet.save();
    
    // wallet exists
    assertTrue(MoneroWalletJni.walletExists(path));
    
    // move wallet to a subdirectory
    String movedPath = TestUtils.TEST_WALLETS_DIR + "/moved/" + walletName;
    wallet.moveTo(movedPath, TestUtils.WALLET_PASSWORD);
    assertFalse(MoneroWalletJni.walletExists(path));
    assertFalse(MoneroWalletJni.walletExists(movedPath)); // wallet does not exist until saved
    wallet.save();
    assertFalse(MoneroWalletJni.walletExists(path));
    assertTrue(MoneroWalletJni.walletExists(movedPath));
    wallet.close();
    assertFalse(MoneroWalletJni.walletExists(path));
    assertTrue(MoneroWalletJni.walletExists(movedPath));
    
    // re-open and test wallet
    wallet = MoneroWalletJni.openWallet(movedPath, TestUtils.WALLET_PASSWORD, TestUtils.NETWORK_TYPE);
    assertEquals(subaddressLabel, wallet.getSubaddress(account.getIndex(), 0).getLabel());
    
    // move wallet back
    wallet.moveTo(path, TestUtils.WALLET_PASSWORD);
    assertFalse(MoneroWalletJni.walletExists(path));  // wallet does not exist until saved
    assertFalse(MoneroWalletJni.walletExists(movedPath));
    wallet.save();
    assertTrue(MoneroWalletJni.walletExists(path));
    assertFalse(MoneroWalletJni.walletExists(movedPath));
    wallet.close();
    assertTrue(MoneroWalletJni.walletExists(path));
    assertFalse(MoneroWalletJni.walletExists(movedPath));
  }
  
  // TODO: this version assumes a wallet can be saved after creation which is not currently supported in wallet2
//  // Can save the wallet
//  @Test
//  public void testSave() {
//    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
//    
//    // create unique path for new test wallet
//    String path = TestUtils.TEST_WALLETS_DIR + "/test_wallet_" + UUID.randomUUID().toString();
//    
//    // wallet does not exist
//    assertFalse(MoneroWalletJni.walletExists(path));
//    
//    // cannot open non-existant wallet
//    try {
//      new MoneroWalletJni(path, TestUtils.WALLET_JNI_PW, TestUtils.NETWORK_TYPE);
//      fail("Cannot open non-existant wallet");
//    } catch (MoneroException e) {
//      assertEquals("Wallet does not exist at path: " + path, e.getMessage());
//    }
//    
//    // create in-memory wallet to test (no connection, english)
//    MoneroWalletJni walletMemory = new MoneroWalletJni(TestUtils.NETWORK_TYPE, null, null);
//    assertEquals(TestUtils.NETWORK_TYPE, walletMemory.getNetworkType());
//    assertNull(walletMemory.getDaemonConnection());
//    assertEquals("English", walletMemory.getLanguage());
//    assertEquals(1, walletMemory.getHeight());
//    //assertEquals(0, walletMemory.getChainHeight()); // TODO: this causes dylib runtime_error; test default state of unconnected wallet 
//    //assertEquals(1, walletMemory.getRestoreHeight()); // TODO; new wallet() without connection but restoreHeight is checkpointed; where is that data cached?
//    // attempt to save wallet without a path which hasn't been saved before
//    try {
//      walletMemory.save();
//      fail("Must specify path to save wallet because wallet has not been previously saved");
//    } catch (MoneroException e) {
//      assertEquals("Must specify path to save wallet because wallet has not been previously saved", e.getMessage());
//    }
//    
//    // save wallet to test_wallets directory
//    walletMemory.save(path, TestUtils.WALLET_JNI_PW);
//    
//    // read wallet saved to disk
//    System.out.println("Attempting to read at path: " + path);
//    MoneroWalletJni walletDisk1 = new MoneroWalletJni(path, TestUtils.WALLET_JNI_PW, TestUtils.NETWORK_TYPE);
//    testJniWalletEquality(walletMemory, walletDisk1);
//    
//    // sync wallet which isn't connected
//    try {
//      walletDisk1.sync();
//    } catch (MoneroException e) {
//      assertEquals("Wallet has no daemon connection", e.getMessage());
//      assertEquals(0, walletMemory.getHeight());
//      assertEquals(1, walletMemory.getRestoreHeight());
//    }
//    
//    // set daemon connection
//    walletDisk1.setDaemonConnection(TestUtils.getDaemonRpc().getRpcConnection());
//    assertNull(walletMemory.getDaemonConnection());
//    
//    // save wallet to default path
//    wallet.save();
//    
//    // read wallet saved to disk
//    MoneroWalletJni walletDisk2 = new MoneroWalletJni(path, TestUtils.WALLET_JNI_PW, TestUtils.NETWORK_TYPE);
//    testJniWalletEquality(walletDisk1, walletDisk2);
//    
//    // sync wallet
//    long chainHeight = daemon.getHeight();
//    walletDisk2.sync();
//    assertEquals(chainHeight, walletDisk2.getHeight());
//    assertEquals(chainHeight, walletDisk2.getRestoreHeight());
//    
//    // save and re-open wallet
//    walletDisk2.save();
//    MoneroWalletJni walletDisk3 = new MoneroWalletJni(path, TestUtils.WALLET_JNI_PW, TestUtils.NETWORK_TYPE);
//    testJniWalletEquality(walletDisk2, walletDisk3);
//    
//    // close wallets to release (c++) resources
//    walletMemory.close();
//    walletDisk1.close();
//    walletDisk2.close();
//    walletDisk3.close();
//  }

  // Can close a wallet
  @Test
  public void testClose() {
    org.junit.Assume.assumeTrue(TEST_NON_RELAYS);
    
    // create a test wallet
    String path = getRandomWalletPath();
    MoneroWalletJni wallet = MoneroWalletJni.createWalletRandom(path, TestUtils.WALLET_RPC_PASSWORD, TestUtils.NETWORK_TYPE, TestUtils.DAEMON_RPC_URI);
    wallet.sync();
    assertTrue(wallet.getHeight() > 1);
    assertTrue(wallet.isSynced());
    assertFalse(wallet.isClosed());
    
    // close the wallet
    wallet.close();
    assertTrue(wallet.isClosed());
    
    // attempt to interact with the wallet
    try { wallet.getHeight(); }
    catch (MoneroException e) { assertEquals("Wallet is closed", e.getMessage()); }
    try { wallet.getMnemonic(); }
    catch (MoneroException e) { assertEquals("Wallet is closed", e.getMessage()); }
    try { wallet.sync(); }
    catch (MoneroException e) { assertEquals("Wallet is closed", e.getMessage()); }
    try { wallet.startSyncing(); }
    catch (MoneroException e) { assertEquals("Wallet is closed", e.getMessage()); }
    try { wallet.stopSyncing(); }
    catch (MoneroException e) { assertEquals("Wallet is closed", e.getMessage()); }
    
    // re-open the wallet
    wallet = MoneroWalletJni.openWallet(path, TestUtils.WALLET_RPC_PASSWORD, TestUtils.NETWORK_TYPE, TestUtils.getDaemonRpc().getRpcConnection());
    wallet.sync();
    assertEquals(wallet.getDaemonHeight(), wallet.getHeight());
    assertFalse(wallet.isClosed());
    
    // close the wallet
    wallet.close();
    assertTrue(wallet.isClosed());
  }
  
  // ----------------------------- NOTIFICATION TESTS -------------------------
  
  /**
   * 4 output notification tests are considered when transferring within one wallet.  // TODO: multi-wallet tests
   * 
   * 1. with local wallet data, transfer from/to same account
   * 2. with local wallet data, transfer from/to different accounts
   * 3. without local wallet data, transfer from/to same account
   * 4. without local wallet data, transfer from/to different accounts
   * 
   * For example, two wallets may be instantiated with the same mnemonic,
   * so neither is privy to the local wallet data of the other.
   */

  // Test #1: notifies listeners of outputs sent from/to the same account using local wallet data
  @Test
  public void testOutputNotificationsSameAccounts() {
    List<String> issues = testOutputNotifications(true);
    if (issues == null) return;
    String msg = "testOutputNotificationsSameAccounts() generated " + issues.size() + " issues:\n" + issuesToStr(issues);
    System.out.println(msg);
    assertFalse(msg, msg.contains("ERROR:"));
  }
  
  // Test #2: notifies listeners of outputs sent from/to different accounts using local wallet data
  @Test
  public void testOutputNotificationsDifferentAccounts() {
    List<String> issues = testOutputNotifications(false);
    if (issues == null) return;
    String msg = "testOutputNotificationsDifferentAccounts() generated " + issues.size() + " issues:\n" + issuesToStr(issues);
    System.out.println(msg);
    assertFalse(msg, msg.contains("ERROR:"));
  }
  
  private List<String> testOutputNotifications(boolean sameAccount) {
    org.junit.Assume.assumeTrue(TEST_NOTIFICATIONS);
    
    // collect errors and warnings
    List<String> errors = new ArrayList<String>();
    
    // wait for wallet txs in the pool in case they were sent from another wallet and therefore will not fully sync until confirmed // TODO monero core
    TestUtils.TX_POOL_WALLET_TRACKER.waitForWalletTxsToClearPool(wallet);
    
    // create send request
    MoneroSendRequest request = new MoneroSendRequest();
    request.setAccountIndex(0);
    int[] destinationAccounts = sameAccount ? new int[] {0, 1, 2} : new int[] {1, 2, 3};
    for (int destinationAccount : destinationAccounts) {
      request.addDestination(new MoneroDestination(wallet.getAddress(destinationAccount, 0), TestUtils.MAX_FEE));
    }
    
    // get balances before for later comparison
    BigInteger balanceBefore = wallet.getBalance();
    BigInteger unlockedBalanceBefore = wallet.getUnlockedBalance();
    
    // register a listener to collect notifications
    OutputNotificationCollector listener = new OutputNotificationCollector();
    wallet.addListener(listener);
    
    // send tx
    MoneroTxWallet tx = wallet.send(request).getTxs().get(0);
    
    // test wallet's balance
    BigInteger balanceAfter = wallet.getBalance();
    BigInteger unlockedBalanceAfter = wallet.getUnlockedBalance();
    BigInteger balanceAfterExpected = balanceBefore.subtract(tx.getFee());  // txs sent from/to same wallet so only decrease in balance is tx fee
    if (!balanceAfterExpected.equals(balanceAfter)) errors.add("WARNING: wallet balance immediately after send expected to be " + balanceAfterExpected + " but was " + balanceAfter);
    if (unlockedBalanceBefore.compareTo(unlockedBalanceAfter) <= 0 && !unlockedBalanceBefore.equals(BigInteger.valueOf(0))) errors.add("WARNING: Wallet unlocked balance immediately after send was expected to decrease but changed from " + unlockedBalanceBefore + " to " + unlockedBalanceAfter);
        
    // wait for wallet to send notifications
    if (listener.getOutputsSpent().isEmpty()) {
      errors.add("WARNING: wallet does not notify listeners of outputs when tx sent directly through wallet or when refreshed from the pool; must wait for confirmation to receive notifications and have correct balance");
      try {
        
        // mine until next block
        try { StartMining.startMining(); } catch (MoneroException e) { }
        daemon.getNextBlockHeader();  
        try { daemon.stopMining(); } catch (MoneroException e) { }
        
        // sleep to ensure wallet has time to see added block
        TimeUnit.MILLISECONDS.sleep(MoneroUtils.WALLET2_REFRESH_INTERVAL);
      } catch (InterruptedException e) {
        e.printStackTrace();
        throw new RuntimeException(e.getMessage());
      }
    }
    
    // test sent output notifications
    if (listener.getOutputsSpent().isEmpty()) {
      errors.add("ERROR: did not receive any sent output notifications");
      return errors;
    }
    
    // test received output notifications
    if (listener.getOutputsReceived().size() < 4) {  // 3+ outputs received from transfers + 1 change output (very unlikely to send exact output amount)
      errors.add("ERROR: received " + listener.getOutputsReceived().size() + " output notifications when at least 4 were expected");
      return errors;
    }
    
    // must receive outputs with known subaddresses and amounts
    for (int destinationAccount : destinationAccounts) {
      if (!hasOutput(listener.getOutputsReceived(), destinationAccount, 0, TestUtils.MAX_FEE)) {
        errors.add("ERROR: missing expected received output to subaddress [" + destinationAccount + ", 0] of amount " + TestUtils.MAX_FEE);
        return errors;
      }
    }
    
    // since sending from/to the same wallet, the net amount spent = tx fee = outputs spent - outputs received
    BigInteger netAmount = BigInteger.valueOf(0);
    for (MoneroOutputWallet outputSpent : listener.getOutputsSpent()) netAmount = netAmount.add(outputSpent.getAmount());
    for (MoneroOutputWallet outputReceived : listener.getOutputsReceived()) netAmount = netAmount.subtract(outputReceived.getAmount());
    if (tx.getFee().compareTo(netAmount) != 0) {
      errors.add("ERROR: net output amount must equal tx fee");
      return errors;
    }
    
    // test wallet's balance
    balanceAfter = wallet.getBalance();
    unlockedBalanceAfter = wallet.getUnlockedBalance();
    if (!balanceAfterExpected.equals(balanceAfter)) errors.add("WARNING: Wallet balance after confirmation expected to be " + balanceAfterExpected + " but was " + balanceAfter);
    if (unlockedBalanceBefore.compareTo(unlockedBalanceAfter) <= 0 && !unlockedBalanceBefore.equals(BigInteger.valueOf(0))) errors.add("WARNING: Wallet unlocked balance immediately after send was expected to decrease but changed from " + unlockedBalanceBefore + " to " + unlockedBalanceAfter);

    // return all errors and warnings as single string
    return errors;
  }
  
  private static String issuesToStr(List<String> issues) {
    if (issues.isEmpty()) return null;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < issues.size(); i++) {
      sb.append((i + 1) + ": " + issues.get(i));
      if (i < issues.size() - 1) sb.append('\n');
    }
    return sb.toString();
  }
  
  private static boolean hasOutput(List<MoneroOutputWallet> outputs, int accountIdx, int subaddressIdx, BigInteger amount) { // TODO: use comon filter?
    MoneroOutputQuery query = new MoneroOutputQuery().setAccountIndex(accountIdx).setSubaddressIndex(subaddressIdx).setAmount(amount);
    for (MoneroOutputWallet output : outputs) {
      if (query.meetsCriteria(output)) return true;
    }
    return false;
  }
  
  @Test
  public void testCreateAndReceive() {
    org.junit.Assume.assumeTrue(TEST_NOTIFICATIONS);
    
    // create a random stagenet wallet
    String path = getRandomWalletPath();
    MoneroWalletJni myWallet = MoneroWalletJni.createWalletRandom(path, "mysupersecretpassword123", MoneroNetworkType.STAGENET, TestUtils.getDaemonRpc().getRpcConnection());
    myWallet.startSyncing();
    
    // listen for received outputs
    OutputNotificationCollector myListener = new OutputNotificationCollector();
    myWallet.addListener(myListener);
    
    // send funds to the created wallet
    MoneroTxWallet sentTx = wallet.send(0, myWallet.getPrimaryAddress(), TestUtils.MAX_FEE).getTxs().get(0);
    
    // wait until block added to the chain
    // TODO monero core: notify on refresh from pool instead instead of confirmation
    try { StartMining.startMining(); } catch (Exception e) { }
    daemon.getNextBlockHeader();
    try { daemon.stopMining(); } catch (Exception e) { }
    
    // give wallets time to observe block
    try {
      TimeUnit.MILLISECONDS.sleep(MoneroUtils.WALLET2_REFRESH_INTERVAL);
    } catch (InterruptedException e) {
      e.printStackTrace();
      throw new RuntimeException(e.getMessage());
    }
    
    // tx is now confirmed
    assertTrue(wallet.getTx(sentTx.getId()).isConfirmed());
    
    // created wallet should have notified listeners of received outputs
    assertFalse(myListener.getOutputsReceived().isEmpty());
    myWallet.close();
  }
  
  @Test
  public void testMultisigSample() {
    createMultisigWallet(2, 2);
    createMultisigWallet(2, 3);
    createMultisigWallet(2, 4);
  }
  
  @SuppressWarnings("unused")
  private void createMultisigWallet(int M, int N) {
    System.out.println("Creating " + M + "/" + N + " multisig wallet");
    
    // create participating wallets
    List<MoneroWallet> wallets = new ArrayList<MoneroWallet>();
    for (int i = 0; i < N; i++) {
      wallets.add(createRandomWallet());  // TODO: switch to use utility
    }
    
    // prepare and collect multisig hex from each participant
    List<String> preparedMultisigHexes = new ArrayList<String>();
    for (MoneroWallet wallet : wallets) preparedMultisigHexes.add(wallet.prepareMultisig());
    
    // make each wallet multsig and collect results
    List<String> madeMultisigHexes = new ArrayList<String>();
    for (int i = 0; i < wallets.size(); i++) {
      
      // collect prepared multisig hexes from wallet's peers
      List<String> peerMultisigHexes = new ArrayList<String>();
      for (int j = 0; j < wallets.size(); j++) if (j != i) peerMultisigHexes.add(preparedMultisigHexes.get(j));
    
      // make wallet multisig and collect result hex
      MoneroMultisigInitResult result = wallets.get(i).makeMultisig(peerMultisigHexes, M, TestUtils.WALLET_PASSWORD);
      madeMultisigHexes.add(result.getMultisigHex());
    }
    
    // if wallet is not N/N, exchange multisig keys N-M times
    if (M != N) {
      List<String> multisigHexes = madeMultisigHexes;
      for (int i = 0; i < N - M; i++) {
        
        // exchange multisig keys among participants and collect results for next round if applicable
        List<String> resultMultisigHexes = new ArrayList<String>();
        for (MoneroWallet wallet : wallets) {
          
          // import the multisig hex of other participants and collect results
          MoneroMultisigInitResult result = wallet.exchangeMultisigKeys(multisigHexes, TestUtils.WALLET_PASSWORD);
          resultMultisigHexes.add(result.getMultisigHex());
        }
        
        // use resulting multisig hex for next round of exchange if applicable
        multisigHexes = resultMultisigHexes;
      }
    }
    
    // wallets are now multisig
    for (MoneroWallet wallet : wallets) {
      String primaryAddress = wallet.getAddress(0, 0);
      MoneroMultisigInfo info = wallet.getMultisigInfo();
      assertTrue(info.isMultisig());
      assertTrue(info.isReady());
      assertEquals(M, (int) info.getThreshold());
      assertEquals(N, (int) info.getNumParticipants());
      wallet.close(true);
    }
  }
  
  // ---------------------------------- HELPERS -------------------------------
  
  /**
   * Wallet listener to collect output notifications.
   */
  private class OutputNotificationCollector extends MoneroWalletListener {
    
    private List<MoneroOutputWallet> outputsReceived;
    private List<MoneroOutputWallet> outputsSpent;
    
    public OutputNotificationCollector() {
      outputsReceived = new ArrayList<MoneroOutputWallet>();
      outputsSpent = new ArrayList<MoneroOutputWallet>();
    }
    
    @Override
    public void onOutputReceived(MoneroOutputWallet output) {
      outputsReceived.add(output);
    }
    
    @Override
    public void onOutputSpent(MoneroOutputWallet output) {
      outputsSpent.add(output);
    }
    
    public List<MoneroOutputWallet> getOutputsReceived() {
      return outputsReceived;
    }
    
    public List<MoneroOutputWallet> getOutputsSpent() {
      return outputsSpent;
    }
  }
  
  public static String getRandomWalletPath() {
    return TestUtils.TEST_WALLETS_DIR + "/test_wallet_" + System.currentTimeMillis();
  }
  
  /**
   * Internal class to test progress updates.
   */
  private class SyncProgressTester implements MoneroSyncListener {
    
    private static final long PRINT_INCREMENT = 2500; // print every 2500 blocks
    
    protected MoneroWalletJni wallet;
    private Long prevHeight;
    private long startHeight;
    private long prevEndHeight;
    private Long prevCompleteHeight;
    protected boolean isDone;
    private Boolean onSyncProgressAfterDone;
    
    public SyncProgressTester(MoneroWalletJni wallet, long startHeight, long endHeight) {
      this.wallet = wallet;
      assertTrue(startHeight >= 0);
      assertTrue(endHeight >= 0);
      this.startHeight = startHeight;
      this.prevEndHeight = endHeight;
      this.isDone = false;
    }
    
    @SuppressWarnings("unlikely-arg-type")
    @Override
    public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) {
      if ((height - startHeight) % PRINT_INCREMENT == 0 || percentDone == 1.0) System.out.println("onSyncProgress(" + height + ", " + startHeight + ", " + endHeight + ", " + percentDone + ", " + message + ")");
      
      // registered wallet listeners will continue to get sync notifications after the wallet's initial sync
      if (isDone) {
        assertTrue("Listener has completed and is not registered so should not be called again", wallet.getListeners().contains(this));
        onSyncProgressAfterDone = true;
      }
      
      // update tester's start height if new sync session
      if (prevCompleteHeight != null && startHeight == prevCompleteHeight) this.startHeight = startHeight;  
      
      // if sync is complete, record completion height for subsequent start heights
      if (Double.compare(percentDone, 1) == 0) prevCompleteHeight = endHeight;
      
      // otherwise start height is equal to previous completion height
      else if (prevCompleteHeight != null) assertEquals((long) prevCompleteHeight, startHeight);
      
      assertTrue("end height > start height", endHeight > startHeight);
      assertEquals(this.startHeight, startHeight);
      assertTrue(endHeight >= prevEndHeight);  // chain can grow while syncing
      prevEndHeight = endHeight;
      assertTrue(height >= startHeight);
      assertTrue(height < endHeight);
      double expectedPercentDone = (double) (height - startHeight + 1) / (double) (endHeight - startHeight);
      assertTrue(Double.compare(expectedPercentDone, percentDone) == 0);
      if (prevHeight == null) assertEquals(startHeight, height);
      else assertEquals(height, prevHeight + 1);
      prevHeight = height;
    }
    
    public void onDone(long chainHeight) {
      assertFalse(isDone);
      this.isDone = true;
      if (prevHeight == null) {
        assertNull(prevCompleteHeight);
      } else {
        assertEquals(chainHeight - 1, (long) prevHeight);  // otherwise last height is chain height - 1
        assertEquals(chainHeight, (long) prevCompleteHeight);
      }
      onSyncProgressAfterDone = false;  // test subsequent onSyncProgress() calls
    }
    
    public Boolean getOnSyncProgressAfterDone() {
      return onSyncProgressAfterDone;
    }
  }
  
  /**
   * Internal class to test all wallet notifications on sync. 
   */
  private class WalletSyncTester extends SyncProgressTester implements MoneroWalletListenerI {
    
    private Long prevHeight;  // independent from super
    private MoneroOutputWallet prevOutputReceived;
    private MoneroOutputWallet prevOutputSpent;
    private BigInteger incomingTotal;
    private BigInteger outgoingTotal;
    private Boolean onNewBlockAfterDone;
    
    public WalletSyncTester(MoneroWalletJni wallet, long startHeight, long endHeight) {
      super(wallet, startHeight, endHeight);
      assertTrue(startHeight >= 0);
      assertTrue(endHeight >= 0);
      incomingTotal = BigInteger.valueOf(0);
      outgoingTotal = BigInteger.valueOf(0);
    }
    
    @Override
    public void onNewBlock(long height) {
      if (isDone) {
        assertTrue("Listener has completed and is not registered so should not be called again", wallet.getListeners().contains(this));
        onNewBlockAfterDone = true;
      }
      if (prevHeight != null) assertEquals(prevHeight + 1, height);
      prevHeight = height;
    }

    @Override
    public void onOutputReceived(MoneroOutputWallet output) {
      assertNotNull(output);
      prevOutputReceived = output;
      
      // test output
      assertNotNull(output.getAmount());
      assertTrue(output.getAccountIndex() >= 0);
      assertTrue(output.getSubaddressIndex() >= 0);
      
      // test output's tx
      assertNotNull(output.getTx());
      assertNotNull(output.getTx().getId());
      assertEquals(64, output.getTx().getId().length());
      assertTrue(output.getTx().getVersion() >= 0);
      assertTrue(output.getTx().getUnlockTime() >= 0);
      assertNull(output.getTx().getVins());
      assertEquals(1, output.getTx().getVouts().size());
      assertTrue(output.getTx().getVouts().get(0) == output);
      
      // extra is not sent over the jni bridge
      assertNull(output.getTx().getExtra());
      
      // add incoming amount to running total
      incomingTotal = incomingTotal.add(output.getAmount());
    }

    @Override
    public void onOutputSpent(MoneroOutputWallet output) {
      assertNotNull(output);
      prevOutputSpent = output;
      
      // test output
      assertNotNull(output.getAmount());
      assertTrue(output.getAccountIndex() >= 0);
      assertTrue(output.getSubaddressIndex() >= 0);
      
      // test output's tx
      assertNotNull(output.getTx());
      assertNotNull(output.getTx().getId());
      assertEquals(64, output.getTx().getId().length());
      assertTrue(output.getTx().getVersion() >= 0);
      assertNull(output.getTx().getUnlockTime());
      assertEquals(1, output.getTx().getVins().size());
      assertTrue(output.getTx().getVins().get(0) == output);
      assertNull(output.getTx().getVouts());
      
      // extra is not sent over the jni bridge
      assertNull(output.getTx().getExtra());
      
      // add outgoing amount to running total
      outgoingTotal = outgoingTotal.add(output.getAmount());
    }
    
    public void onDone(long chainHeight) {
      super.onDone(chainHeight);
      assertNotNull(prevHeight);
      assertNotNull(prevOutputReceived);
      assertNotNull(prevOutputSpent);
      BigInteger balance = incomingTotal.subtract(outgoingTotal);
      assertEquals(balance, wallet.getBalance());
      onNewBlockAfterDone = false;  // test subsequent onNewBlock() calls
    }
    
    public Boolean getOnNewBlockAfterDone() {
      return onNewBlockAfterDone;
    }
  }
  
  // jni-specific tx tests
  @Override
  protected void testTxWallet(MoneroTxWallet tx, TestContext ctx) {
    if (ctx == null) ctx = new TestContext();
    
    // run common tests
    super.testTxWallet(tx, ctx);
  }
  
  // possible configuration: on chain xor local wallet data ("strict"), txs ordered same way? TBD
  protected static void testWalletsEqualOnChain(MoneroWalletJni wallet1, MoneroWalletJni wallet2) {
    TestMoneroWalletCommon.testWalletsEqualOnChain(wallet1, wallet2);
    assertEquals(wallet1.getNetworkType(), wallet2.getNetworkType());
    //assertEquals(wallet1.getRestoreHeight(), wallet2.getRestoreHeight()); // TODO monero-core: restore height is lost after close
    assertEquals(wallet1.getDaemonConnection(), wallet2.getDaemonConnection());
    assertEquals(wallet1.getLanguage(), wallet2.getLanguage());
    // TODO **: more jni-specific extensions
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
  public void testSetTransactionNote() {
    super.testSetTransactionNote();
  }

  @Override
  public void testSetTransactionNotes() {
    super.testSetTransactionNotes();
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
