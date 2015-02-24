package com.jive.hillbilly.client;

import java.util.List;

import lombok.Getter;

import com.google.common.collect.Lists;
import com.jive.ftw.sip.dummer.txn.ServerTransactionListener;
import com.jive.hillbilly.client.HillbillyServerTransaction;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponse;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.processor.rfc3261.MutableSipRequest;
import com.jive.sip.processor.rfc3261.MutableSipResponse;
import com.jive.sip.processor.rfc3261.RfcSipMessageManagerBuilder;
import com.jive.sip.processor.rfc3261.SipMessageManager;
import com.jive.sip.transport.api.FlowId;

public class TestIncomingTransaction implements HillbillyServerTransaction
{

  private final SipRequest request;
  private final SipMessageManager mm = new RfcSipMessageManagerBuilder().build();

  @Getter
  private final List<SipResponse> responses = Lists.newLinkedList();

  public TestIncomingTransaction(final SipRequest req)
  {
    this.request = req;
  }

  public TestIncomingTransaction(final MutableSipRequest req)
  {
    this.request = req.build(this.mm);
  }

  @Override
  public void addListener(final ServerTransactionListener arg0)
  {
    // TODO Auto-generated method stub

  }

  @Override
  public FlowId getFlowId()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public SipRequest getRequest()
  {
    return this.request;
  }

  @Override
  public void respond(final SipResponseStatus status)
  {
    final MutableSipResponse res = MutableSipResponse.createResponse(this.request, status);
    this.respond(res.build(this.mm));
  }



  @Override
  public void respond(final SipResponse res)
  {
    this.responses.add(res);
  }

}
