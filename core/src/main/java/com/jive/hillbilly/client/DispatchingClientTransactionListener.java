package com.jive.hillbilly.client;

import java.util.concurrent.Executor;

import com.jive.sip.dummer.txn.ClientTransactionListener;
import com.jive.sip.dummer.txn.SipTransactionErrorInfo;
import com.jive.sip.dummer.txn.SipTransactionResponseInfo;

class DispatchingClientTransactionListener implements ClientTransactionListener
{

  private final Executor executor;
  private final ClientTransactionListener listener;

  DispatchingClientTransactionListener(final Executor executor, final ClientTransactionListener listener)
  {
    this.executor = executor;
    this.listener = listener;
  }

  @Override
  public void onResponse(final SipTransactionResponseInfo res)
  {
    this.executor.execute(() -> this.listener.onResponse(res));
  }

  @Override
  public void onError(final SipTransactionErrorInfo err)
  {
    this.executor.execute(() -> this.listener.onError(err));
  }

}
