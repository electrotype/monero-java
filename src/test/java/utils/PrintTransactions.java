package utils;

import java.util.ArrayList;
import java.util.List;

import monero.daemon.model.MoneroTx;
import monero.wallet.MoneroWallet;
import monero.wallet.model.MoneroTxQuery;
import monero.wallet.model.MoneroTxWallet;

/**
 * Prints transactions in a wallet.
 */
public class PrintTransactions {

  public static void main(String[] args) {
    MoneroWallet wallet = TestUtils.getWalletRpc();
    MoneroTxQuery query = new MoneroTxQuery();
    List<String> ids = new ArrayList<String>();
    ids.add("d16e603efed00a3d2e14085d0ebe96734145d9eba851f6afd02ab76f9d5b62c0");
    query.setTxIds(ids);
    List<MoneroTxWallet> txs = wallet.getTxs(query);
    for (MoneroTx tx : txs) {
      System.out.println(tx);
    }
  }
}
