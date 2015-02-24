package com.jive.hillbilly.client;

import com.jive.sip.dummer.txn.ServerTransactionHandle;
import com.jive.sip.dummer.txn.ServerTransactionListener;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponse;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.transport.api.FlowId;

public class WrappedServerTransaction implements HillbillyServerTransaction
{

  private final ServerTransactionHandle txn;

  public WrappedServerTransaction(final ServerTransactionHandle e)
  {
    this.txn = e;
  }

  @Override
  public SipRequest getRequest()
  {
    return this.txn.getRequest();
  }

  @Override
  public void respond(final SipResponseStatus status)
  {
    this.txn.respond(status);
  }

  @Override
  public void respond(final SipResponse res)
  {
    this.txn.respond(res);
  }

  @Override
  public void addListener(final ServerTransactionListener listener)
  {
    this.txn.addListener(listener);
  }

  @Override
  public FlowId getFlowId()
  {
    return this.txn.getFlowId();
  }

}
