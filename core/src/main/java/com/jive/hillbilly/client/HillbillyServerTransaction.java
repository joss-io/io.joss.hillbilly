package com.jive.hillbilly.client;

import com.jive.sip.dummer.txn.ServerTransactionListener;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponse;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.transport.api.FlowId;

public interface HillbillyServerTransaction
{

  SipRequest getRequest();

  void respond(final SipResponseStatus status);

  void respond(final SipResponse res);

  void addListener(final ServerTransactionListener listener);

  FlowId getFlowId();

}
