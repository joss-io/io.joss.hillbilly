package com.jive.hillbilly.client;

import java.util.function.Consumer;

import lombok.Getter;

import org.joda.time.Instant;

import com.google.common.net.HostAndPort;
import com.jive.ftw.sip.dummer.txn.ClientTransactionListener;
import com.jive.ftw.sip.dummer.txn.SipClientTransaction;
import com.jive.ftw.sip.dummer.txn.SipTransactionResponseInfo;
import com.jive.myco.commons.concurrent.Pnky;
import com.jive.sip.message.api.BranchId;
import com.jive.sip.message.api.Reason;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponse;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.processor.rfc3261.MutableSipResponse;
import com.jive.sip.processor.rfc3261.RfcSipMessageManagerBuilder;
import com.jive.sip.processor.rfc3261.SipMessageManager;
import com.jive.sip.transport.api.FlowId;
import com.jive.sip.transport.udp.ListenerId;
import com.jive.sip.transport.udp.UdpFlowId;
import com.jive.sip.uri.api.SipUri;

public class TestOutgoingTransaction implements SipClientTransaction
{

  private static final FlowId TEST_FLOWID = UdpFlowId.create(
      new ListenerId(0),
      HostAndPort.fromParts("127.0.0.1", 12345));

  @Getter
  private SipRequest request;

  SipMessageManager mm = new RfcSipMessageManagerBuilder().build();

  @Getter
  ClientTransactionListener listener;

  @Getter
  Pnky<Reason> cancel = Pnky.create();

  public TestOutgoingTransaction(SipRequest req, ClientTransactionListener listener)
  {
    this.request = req;
    this.listener = listener;
  }

  @Override
  public BranchId getBranchId()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Instant getCreationTime()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void cancel()
  {
    cancel(null);
  }

  @Override
  public void cancel(Reason arg0)
  {
    this.cancel.resolve(arg0);
  }

  @Override
  public void fromApplication(SipRequest arg0, FlowId arg1)
  {
    // TODO Auto-generated method stub

  }

  @Override
  public void fromNetwork(SipResponse arg0, FlowId arg1)
  {
    // TODO Auto-generated method stub

  }

  public void respond(SipResponseStatus status, Consumer<MutableSipResponse> mutator)
  {
    MutableSipResponse res = MutableSipResponse.createResponse(request, status);
    res.contact(new SipUri(HostAndPort.fromParts("127.0.0.1", 55555)));
    if (mutator != null)
    {
      mutator.accept(res);
      ;
    }
    listener.onResponse(new SipTransactionResponseInfo(TEST_FLOWID, res.build(mm)));
  }

  public void respond(SipResponseStatus status)
  {
    respond(status, null);
  }

}
