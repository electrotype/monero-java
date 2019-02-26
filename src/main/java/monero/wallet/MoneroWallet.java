/**
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package monero.wallet;

import java.math.BigInteger;
import java.util.List;

import monero.daemon.model.MoneroKeyImage;
import monero.wallet.config.MoneroSendConfig;
import monero.wallet.model.MoneroAccount;
import monero.wallet.model.MoneroAccountTag;
import monero.wallet.model.MoneroAddressBookEntry;
import monero.wallet.model.MoneroCheckReserve;
import monero.wallet.model.MoneroCheckTx;
import monero.wallet.model.MoneroIntegratedAddress;
import monero.wallet.model.MoneroKeyImageImportResult;
import monero.wallet.model.MoneroSendPriority;
import monero.wallet.model.MoneroSubaddress;
import monero.wallet.model.MoneroSyncProgressListener;
import monero.wallet.model.MoneroSyncResult;
import monero.wallet.model.MoneroTransfer;
import monero.wallet.model.MoneroWalletOutput;
import monero.wallet.model.MoneroWalletTx;

/**
 * Monero wallet interface.
 */
public interface MoneroWallet {
  
  /**
   * Get the wallet's seed.
   * 
   * @return the wallet's seed
   */
  public String getSeed();
  
  /**
   * Get the wallet's mnemonic phrase derived from the seed.
   * 
   * @return the wallet's mnemonic phrase
   */
  public String getMnemonic();
  
  /**
   * Get the wallet's public view key.
   * 
   * @return the wallet's public view key
   */
  public String getPublicViewKey();
  
  /**
   * Get the wallet's private view key.
   * 
   * @return the wallet's private view key
   */
  public String getPrivateViewKey();
  
  /**
   * Get a list of available languages for the wallet's seed.
   * 
   * @return the available languages for the wallet's seed
   */
  public List<String> getLanguages();
  
  /**
   * Get the height of the last block processed by the wallet (its index + 1).
   * 
   * @return the height of the last block processed by the wallet
   */
  public int getHeight();
  
  /**
   * Get the blockchain's height.
   * 
   * @return the block chain's height
   */
  public int getChainHeight();
  
  /**
   * Get the wallet's primary address.
   * 
   * @return the wallet's primary address
   */
  public String getPrimaryAddress();
  
  /**
   * Get an integrated address based on this wallet's primary address and the
   * given payment ID.  Generates a random payment ID if none is given.
   * 
   * @param paymentId is the payment ID to generate an integrated address from (randomly generated if null)
   * @return the integrated address
   */
  public MoneroIntegratedAddress getIntegratedAddress(String paymentId);
  
  /**
   * Decode an integrated address to get its standard address and payment id.
   * 
   * @param integratedAddress is an integrated address to decode
   * @return the decoded integrated address including standard address and payment id
   */
  public MoneroIntegratedAddress decodeIntegratedAddress(String integratedAddress);
  
  /**
   * Synchronizes the wallet with the block chain.
   * 
   * @param startHeight is the start height to sync from, syncs from the last synced block by default
   * @param endHeight is the end height to sync to, syncs to the current chain height by default
   * @param progressListener is invoked as sync progress is made
   * @return the sync result
   */
  public MoneroSyncResult sync(Integer startHeight, Integer endHeight, MoneroSyncProgressListener progressListener);
  
  /**
   * Indicates if importing multisig data is needed for returning a correct balance.
   * 
   * @return true if importing multisig data is needed for returning a correct balance, false otherwise
   */
  public boolean isMultisigImportNeeded();
  
  /**
   * Get accounts without subaddress information.
   * 
   * @return the retrieved accounts
   */
  public List<MoneroAccount> getAccounts();
  
  /**
   * Get accounts.
   * 
   * @param includeSubaddresses specifies if subaddresses should be included
   * @return the retrieved accounts
   */
  public List<MoneroAccount> getAccounts(boolean includeSubaddresses);
  
  /**
   * Get an account without subaddress information.
   * 
   * @param accountIdx specifies the account to get
   * @return the retrieved account
   */
  public MoneroAccount getAccount(int accountIdx);
  
  /**
   * Get an account.
   * 
   * @param accountIdx specifies the account to get
   * @param includeSubaddresses specifies if subaddresses should be included
   * @return the retrieved account
   */
  public MoneroAccount getAccount(int accountIdx, boolean includeSubaddresses);

  /**
   * Create a new account.
   * 
   * @param label specifies the label for the account (optional)
   * @return the created account
   */
  public MoneroAccount createAccount(String label);
  
  /**
   * Get all subaddresses in an account.
   * 
   * @param accountIdx specifies the account to get subaddresses within
   * @return List<MoneroSubaddress> are the retrieved subaddresses
   */
  public List<MoneroSubaddress> getSubaddresses(int accountIdx);
  
  /**
   * Get subaddresses in an account.
   * 
   * @param accountIdx specifies the account to get subaddresses within
   * @param subaddressIndices are specific subaddresses to get (optional)
   * @return the retrieved subaddresses
   */
  public List<MoneroSubaddress> getSubaddresses(int accountIdx, List<Integer> subaddressIndices);
  
  /**
   * Get a subaddress.
   * 
   * @param accountIdx specifies the index of the subaddress's account
   * @param subaddressIdx specifies index of the subaddress within the account
   * @return the retrieved subaddress
   */
  public MoneroSubaddress getSubaddress(int accountIdx, int subaddressIdx);
  
  /**
   * Create a subaddress within an account and without a label.
   * 
   * @param accountIdx specifies the index of the account to create the subaddress within
   * @return the created subaddress
   */
  public MoneroSubaddress createSubaddress(int accountIdx);
  
  /**
   * Create a subaddress within an account.
   * 
   * @param accountIdx specifies the index of the account to create the subaddress within
   * @param label specifies the the label for the subaddress (optional)
   * @return the created subaddress
   */
  public MoneroSubaddress createSubaddress(int accountIdx, String label);
  
  /**
   * Get the address of a specific subaddress.
   * 
   * @param accountIdx specifies the account index of the address's subaddress
   * @param subaddressIdx specifies the subaddress index within the account
   * @return the receive address of the specified subaddress
   */
  public String getAddress(int accountIdx, int subaddressIdx);
  
  /**
   * Get the account and subaddress index of the given address.
   * 
   * @param address is the address to get the account and subaddress index from
   * @return the account and subaddress indices
   * @throws exception if address is not a wallet address
   */
  public MoneroSubaddress getAddressIndex(String address);
  
  /**
   * Get the wallet's balance.
   * 
   * @return the wallet's balance
   */
  public BigInteger getBalance();
  
  /**
   * Get an account's balance.
   * 
   * @param accountIdx is the index of the account to get the balance of
   * @return the account's balance
   */
  public BigInteger getBalance(int accountIdx);
  
  /**
   * Get a subaddress's balance.
   * 
   * @param accountIdx is the index of the subaddress's account to get the balance of
   * @param subaddressIdx is the index of the subaddress to get the balance of
   * @return the subaddress's balance
   */
  public BigInteger getBalance(int accountIdx, int subaddressIdx);
  
  /**
   * Get the wallet's unlocked balance.
   * 
   * @return the wallet's unlocked balance
   */
  public BigInteger getUnlockedBalance();
  
  /**
   * Get an account's unlocked balance.
   * 
   * @param accountIdx is the index of the account to get the unlocked balance of
   * @return the account's unlocked balance
   */
  public BigInteger getUnlockedBalance(int accountIdx);
  
  /**
   * Get a subaddress's unlocked balance.
   * 
   * @param accountIdx is the index of the subaddress's account to get the unlocked balance of
   * @param subaddressIdx is the index of the subaddress to get the unlocked balance of
   * @return the subaddress's balance
   */
  public BigInteger getUnlockedBalance(int accountIdx, int subaddressIdx);
  
  /**
   * Get all wallet transactions.  Wallet transactions contain one or more
   * transfers that are either incoming or outgoing to the wallet.

   * @return all wallet transactions
   */
  public List<MoneroWalletTx> getTxs();
  
  /**
   * Get wallet transactions.  Wallet transactions contain one or more
   * transfers that are either incoming or outgoing to the wallet.
   * 
   * Query results can be filtered by passing in a transaction filter.
   * Transactions must meet every criteria defined in the filter in order to be
   * returned.  All filtering is optional and no filtering is applied when not
   * defined.
   * 
   * @param filter filters query results (optional)
   * @return wallet transactions per the filter
   */
  public List<MoneroWalletTx> getTxs(MoneroTxFilter filter);
  
  /**
   * Get all incoming and outgoing transfers to and from this wallet.  An
   * outgoing transfer represents a total amount sent from one or more
   * subaddresses within an account to individual destination addresses, each
   * with their own amount.  An incoming transfer represents a total amount
   * received into a subaddress within an account.  Transfers belong to
   * transactions which are stored on the blockchain.
   * 
   * @return all wallet transfers
   */
  public List<MoneroTransfer> getTransfers();
  
  /**
   * Get incoming and outgoing transfers to and from this wallet.  An outgoing
   * transfer represents a total amount sent from one or more subaddresses
   * within an account to individual destination addresses, each with their
   * own amount.  An incoming transfer represents a total amount received into
   * a subaddress within an account.  Transfers belong to transactions which
   * are stored on the blockchain.
   * 
   * Query results can be filtered by passing in a transfer filter.  Transfers
   * must meet every criteria defined in the filter in order to be returned.
   * All filtering is optional and no filtering is applied when not defined.
   * 
   * @param filter filters query results (optional)
   * @return wallet transfers per the filter
   */
  public List<MoneroTransfer> getTransfers(MoneroTransferFilter filter);
  
  /**
   * Get all wallet vouts.  A wallet vout is an output created from a previous
   * transaction that the wallet can spend one time.  Vouts belong to
   * transactions which are stored on the blockchain.
   * 
   * @return all wallet vouts
   */
  public List<MoneroWalletOutput> getVouts();
  
  /**
   * Get wallet vouts.  A wallet vout is an output created from a previous
   * transaction that the wallet can spend one time.  Vouts belong to
   * transactions which are stored on the blockchain.
   * 
   * Query results can be filtered by passing in a vout filter.  Vouts must
   * meet every criteria defined in the filter in order to be returned.  All
   * filtering is optional and no filtering is applied when not defined.
   * 
   * @param filter filters query results (optional)
   * @return wallet vouts per the filter
   */
  public List<MoneroWalletOutput> getVouts(MoneroVoutFilter filter);
  
  /**
   * Get all signed key images.
   * 
   * @return the wallet's signed key images
   */
  public List<MoneroKeyImage> getKeyImages();
  
  /**
   * Import signed key images and verify their spent status.
   * 
   * @param keyImages are key images to import and verify (requires hex and signature)
   * @return results of the import
   */
  public MoneroKeyImageImportResult importKeyImages(List<MoneroKeyImage> keyImages);
  
  /**
   * Get new key images from the last imported outputs.
   * 
   * @return the key images from the last imported outputs
   */
  public List<MoneroKeyImage> getNewKeyImagesFromLastImport();
  
  /**
   * Create and relay (depending on configuration) a transaction which
   * transfers funds from this wallet to one or more destination addresses.
   * 
   * @param config configures the transaction
   * @return the resulting transaction
   */
  public MoneroWalletTx send(MoneroSendConfig config);
  
  /**
   * Create and relay a transaction which transfers funds from this wallet to
   * a destination address.
   * 
   * @param address is the destination address to send funds to
   * @param sendAmount is the amount being sent
   * @param priority is the transaction priority (optional)
   * @return the resulting transaction
   */
  public MoneroWalletTx send(String address, BigInteger sendAmount, MoneroSendPriority priority);
  
  /**
   * Create and relay (depending on configuration) one or more transactions
   * which transfer funds from this wallet to one or more destination.
   * 
   * @param config configures the transactions
   * @return the resulting transactions
   */
  public List<MoneroWalletTx> sendSplit(MoneroSendConfig config);
  
  /**
   * Create and relay one or more transactions which transfer funds from this
   * wallet to one or more destination.
   * 
   * @param address is the destination address to send funds to
   * @param sendAmount is the amount being sent
   * @param priority is the transaction priority (optional)
   * @return the resulting transactions
   */
  public List<MoneroWalletTx> sendSplit(String address, BigInteger sendAount, MoneroSendPriority priority);
  
  /**
   * Sweep the wallet's unlocked funds to an address.
   * 
   * @param address is the address to sweep the wallet's funds to
   * @return the resulting transactions
   */
  public List<MoneroWalletTx> sweepWallet(String address);

  /**
   * Sweep an acount's unlocked funds to an address.
   * 
   * @param accountIdx is the index of the account
   * @param address is the address to sweep the account's funds to
   * @return the resulting transactions
   */
  public List<MoneroWalletTx> sweepAccount(int accountIdx, String address);

  /**
   * Sweep a subaddress's unlocked funds to an address.
   * 
   * @param accountIdx is the index of the account
   * @param subaddressIdx is the index of the subaddress
   * @param address is the address to sweep the subaddress's funds to
   * @return the resulting transactions
   */
  public List<MoneroWalletTx> sweepSubaddress(int accountIdx, int subaddressIdx, String address);

  /**
   * Sweep unlocked funds.
   * 
   * @param config is the sweep configuration
   * @return the resulting transactions
   */
  public List<MoneroWalletTx> sweepUnlocked(MoneroSendConfig config);
  
  /**
   * Sweep all unmixable dust outputs back to the wallet to make them easier to spend and mix.
   * 
   * @return the resulting transactions from sweeping dust
   */
  public List<MoneroWalletTx> sweepDust();
  
  /**
   * Sweep all unmixable dust outputs back to the wallet to make them easier to spend and mix.
   * 
   * @param doNotRelay specifies if the resulting transaction should not be relayed (defaults to false i.e. relayed)
   * @return the resulting transactions from sweeping dust
   */
  public List<MoneroWalletTx> sweepDust(boolean doNotRelay);
  
  /**
   * Sweep an output with a given key image.
   * 
   * @param config configures the sweep transaction
   * @return the resulting transaction from sweeping an output 
   */
  public MoneroWalletTx sweepOutput(MoneroSendConfig config);
  
  /**
   * Sweep an output with a given key image.
   * 
   * @param address is the destination address to send to
   * @param keyImage is the key image hex of the output to sweep
   * @param priority is the transaction priority (optional)
   * @return the resulting transaction from sweeping an output 
   */
  public MoneroWalletTx sweepOutput(String address, String keyImage, MoneroSendPriority priority);
  
  /**
   * Relay a transaction previously created without relaying.
   * 
   * @param txMetadata is transaction metadata previously created without relaying
   * @return the relayed tx
   */
  public MoneroWalletTx relayTx(String txMetadata);
  
  /**
   * Relay transactions previously created without relaying.
   * 
   * @param txMetadatas are transaction metadata previously created without relaying
   * @return the relayed txs
   */
  public List<MoneroWalletTx> relayTxs(List<String> txMetadatas);
  
  /**
   * Get a transaction note.
   * 
   * @param txId specifies the transaction to get the note of
   * @return the tx note
   */
  public String getTxNote(String txId);
  
  /**
   * Set a note for a specific transaction.
   * 
   * @param txId specifies the transaction
   * @param note specifies the note
   */
  public void setTxNote(String txId, String note);
  
  /**
   * Get notes for multiple transactions.
   * 
   * @param txIds identify the transactions to get notes for
   * @preturns notes for the transactions
   */
  public List<String> getTxNotes(List<String> txIds);
  
  /**
   * Set notes for multiple transactions.
   * 
   * @param txIds specify the transactions to set notes for
   * @param notes are the notes to set for the transactions
   */
  public void setTxNotes(List<String> txIds, List<String> notes);
  
  /**
   * Sign a message.
   * 
   * @param msg is the message to sign
   * @return the signature
   */
  public String sign(String msg);
  
  /**
   * Verify a signature on a message.
   * 
   * @param msg is the signed message
   * @param address is the signing address
   * @param signature is the signature
   * @return true if the signature is good, false otherwise
   */
  public boolean verify(String msg, String address, String signature);
  
  /**
   * Get a transaction's secret key from its id.
   * 
   * @param txId is the transaction's id
   * @return is the transaction's secret key
   */
  public String getTxKey(String txId);
  
  /**
   * Check a transaction in the blockchain with its secret key.
   * 
   * @param txId specifies the transaction to check
   * @param txKey is the transaction's secret key
   * @param address is the destination public address of the transaction
   * @return the result of the check
   */
  public MoneroCheckTx checkTxKey(String txId, String txKey, String address);
  
  /**
   * Get a transaction signature to prove it.
   * 
   * @param txId specifies the transaction to prove
   * @param address is the destination public address of the transaction
   * @param message is a message to include with the signature to further authenticate the proof (optional)
   * @return the transaction signature
   */
  public String getTxProof(String txId, String address, String message);
  
  /**
   * Prove a transaction by checking its signature.
   * 
   * @param txId specifies the transaction to prove
   * @param address is the destination public address of the transaction
   * @param message is a message included with the signature to further authenticate the proof (optional)
   * @param signature is the transaction signature to confirm
   * @return the result of the check
   */
  public MoneroCheckTx checkTxProof(String txId, String address, String message, String signature);
  
  /**
   * Generate a signature to prove a spend. Unlike proving a transaction, it does not require the destination public address.
   * 
   * @param txId specifies the transaction to prove
   * @param message is a message to include with the signature to further authenticate the proof (optional)
   * @return the transaction signature
   */
  public String getSpendProof(String txId, String message);
  
  /**
   * Prove a spend using a signature. Unlike proving a transaction, it does not require the destination public address.
   * 
   * @param txId specifies the transaction to prove
   * @param message is a message included with the signature to further authenticate the proof (optional)
   * @param signature is the transaction signature to confirm
   * @return true if the signature is good, false otherwise
   */
  public boolean checkSpendProof(String txId, String message, String signature);
  
  /**
   * Generate a signature to prove the entire balance of the wallet.
   * 
   * @param message is a message included with the signature to further authenticate the proof (optional)
   * @return the reserve proof signature
   */
  public String getReserveProofWallet(String message);
  
  /**
   * Generate a signature to prove an available amount in an account.
   * 
   * @param accountIdx specifies the account to prove contains an available amount
   * @param amount is the minimum amount to prove as available in the account
   * @param message is a message to include with the signature to further authenticate the proof (optional)
   * @return the reserve proof signature
   */
  public String getReserveProofAccount(int accountIdx, BigInteger amount, String message);

  /**
   * Proves a wallet has a disposable reserve using a signature.
   * 
   * @param address is the public wallet address
   * @param message is a message included with the signature to further authenticate the proof (optional)
   * @param signature is the reserve proof signature to check
   * @return the result of checking the signature proof
   */
  public MoneroCheckReserve checkReserveProof(String address, String message, String signature);
  
  /**
   * Get address book entries.
   * 
   * @param entryIndices are indices of the entries to get
   * @return the address book entries
   */
  public List<MoneroAddressBookEntry> getAddressBookEntries(List<Integer> entryIndices);
  
  /**
   * Add an address book entry.
   * 
   * @param address is the entry address
   * @param description is the entry description (optional)
   * @param paymentId is the entry paymet id (optional)
   * @return the index of the added entry
   */
  public int addAddressBookEntry(String address, String description, String paymentId);
  
  /**
   * Delete an address book entry.
   * 
   * @param entryIdx is the index of the entry to delete
   */
  public void deleteAddressBookEntry(int entryIdx);
  
  /**
   * Tag accounts.
   * 
   * @param tag is the tag to apply to the specified accounts
   * @param accountIndices are the indices of the accounts to tag
   */
  public List<Integer> tagAccounts(String tag, List<Integer> accountIndices);

  /**
   * Untag acconts.
   * 
   * @param accountIndices are the indices of the accounts to untag
   */
  public void untagAccounts(List<Integer> accountIndices);

  /**
   * Return all account tags.
   * 
   * @return the wallet's account tags
   */
  public List<MoneroAccountTag> getAccountTags();

  /**
   * Sets a human-readable description for a tag.
   * 
   * @param tag is the tag to set a description for
   * @param label is the label to set for the tag
   */
  public void setAccountTagLabel(String tag, String label);
  
  /**
   * Creates a payment URI from a send configuration.
   * 
   * @param sendConfig specifies configuration for a potential tx
   * @return is the payment uri
   */
  public String createPaymentUri(MoneroSendConfig sendConfig);
  
  /**
   * Parses a payment URI to a send configuration.
   * 
   * @param uri is the payment uri to parse
   * @return the send configuration parsed from the uri
   */
  public MoneroSendConfig parsePaymentUri(String uri);
  
  /**
   * Export all outputs in hex format.
   * 
   * @return all outputs in hex format, null if no outputs
   */
  public String getOutputsHex();
  
  /**
   * Import outputs in hex format.
   * 
   * @param outputsHex are outputs in hex format
   * @return the number of outputs imported
   */
  public int importOutputsHex(String outputsHex);
  
  /**
   * Set an arbitrary attribute.
   * 
   * @param key is the attribute key
   * @param val is the attribute value
   */
  public void setAttribute(String key, String val);
  
  /**
   * Get an attribute.
   * 
   * @param key is the attribute to get the value of
   * @return the attribute's value
   */
  public String getAttribute(String key);
  
  /**
   * Start mining.
   * 
   * @param numThreads is the number of threads created for mining (optional)
   * @param backgroundMining specifies if mining should occur in the background (optional)
   * @param ignoreBattery specifies if the battery should be ignored for mining (optional)
   */
  public void startMining(Integer numThreads, Boolean backgroundMining, Boolean ignoreBattery);
  
  /**
   * Stop mining.
   */
  public void stopMining();
}