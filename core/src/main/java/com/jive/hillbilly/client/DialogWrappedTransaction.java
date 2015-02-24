package com.jive.hillbilly.client;

import com.jive.sip.dummer.txn.ServerTransactionListener;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponse;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.processor.rfc3261.MutableSipResponse;
import com.jive.sip.transport.api.FlowId;

public class DialogWrappedTransaction implements HillbillyServerTransaction
{

  private final EmbeddedDialog dialog;
  private final HillbillyServerTransaction txn;

  public DialogWrappedTransaction(final EmbeddedDialog dialog, final HillbillyServerTransaction itxn)
  {
    this.dialog = dialog;
    this.txn = itxn;
  }

  @Override
  public void respond(final SipResponse res)
  {
    this.txn.respond(res);
  }

  @Override
  public void respond(final SipResponseStatus status)
  {
    final MutableSipResponse res = this.dialog.createResponse(this.txn.getRequest(), status);
    this.respond(res.build(this.dialog.getNetwork().messageManager()));
  }


  @Override
  public void addListener(final ServerTransactionListener listener)
  {
    this.txn.addListener(listener);
  }

  @Override
  public SipRequest getRequest()
  {
    return this.txn.getRequest();
  }

  @Override
  public FlowId getFlowId()
  {
    return this.txn.getFlowId();
  }


}
