package com.jive.hillbilly.client;

import java.time.Duration;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.net.HostAndPort;
import com.jive.sip.message.api.SessionExpires.Refresher;
import com.jive.hillbilly.client.ApiUtils;
import com.jive.hillbilly.client.EmbeddedDialog;
import com.jive.hillbilly.client.EmbeddedServerCreator;
import com.jive.hillbilly.client.api.ClientSideEarlyDialog;
import com.jive.hillbilly.client.api.DisconnectEvent;
import com.jive.hillbilly.client.api.OriginationBranchProgressEvent;
import com.jive.sip.message.api.SipMethod;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponse;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.processor.rfc3261.MutableSipRequest;
import com.jive.sip.processor.rfc3261.MutableSipResponse;
import com.jive.sip.transport.udp.ListenerId;
import com.jive.sip.transport.udp.UdpFlowId;
import com.jive.sip.uri.api.SipUri;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EmbeddedServerCreatorTest
{

  private TestEmbeddedNetworkSegment segment;
  private EmbeddedServerCreator uas;
  private TestServerSideCreator handler;
  private TestIncomingTransaction txn;

  @Before
  public void setup() throws InterruptedException
  {

    this.segment = new TestEmbeddedNetworkSegment();
    this.handler = new TestServerSideCreator();

    // create the INVITE.

    final MutableSipRequest req = MutableSipRequest.create(SipMethod.INVITE, SipUri.ANONYMOUS);

    req.cseq(1, SipMethod.INVITE);
    req.to(SipUri.ANONYMOUS);
    req.from(SipUri.ANONYMOUS, "a");
    req.callId("call1");
    req.contact(SipUri.ANONYMOUS);
    req.sessionExpires(120, Refresher.Server);
    req.supported("timer");

    this.txn = new TestIncomingTransaction(req.build(this.segment.messageManager()));

    this.uas = new EmbeddedServerCreator(this.txn, this.segment);

    this.uas.process(this.handler);

  }

  OriginationBranchProgressEvent makeResponse(final SipResponseStatus status)
  {
    return new OriginationBranchProgressEvent(
        ApiUtils.convert(MutableSipResponse.createResponse(status).build(TestHillbillyHandler.messageManager))
        );
  }

  @After
  public void after()
  {
    Assert.assertEquals("dialogs still active", 0, this.segment.getHandles().size());
    Assert.assertTrue("timers still active", this.segment.getQueued().isEmpty());
  }

  @Test
  public void testRejection() throws Exception
  {
    this.uas.reject(ApiUtils.convert(SipResponseStatus.NOT_FOUND));
    Assert.assertEquals(SipResponseStatus.NOT_FOUND, this.txn.getResponses().get(0).getStatus());
  }

  @Test
  public void testBranchThenRejection() throws Exception
  {
    final ClientSideEarlyDialog branch = this.uas.branch(new TestServerBranch(), new TestLocalDialog());
    branch.progress(this.makeResponse(SipResponseStatus.RINGING));
    this.uas.reject(ApiUtils.convert(SipResponseStatus.NOT_FOUND));
    Assert.assertEquals(SipResponseStatus.RINGING, this.txn.getResponses().get(0).getStatus());
    Assert.assertEquals(SipResponseStatus.NOT_FOUND, this.txn.getResponses().get(1).getStatus());
  }

  @Test
  public void testBranchThenSuccess() throws Exception
  {
    final ClientSideEarlyDialog branch1 = this.uas.branch(new TestServerBranch(), new TestLocalDialog());
    final ClientSideEarlyDialog branch2 = this.uas.branch(new TestServerBranch(), new TestLocalDialog());

    branch1.progress(this.makeResponse(SipResponseStatus.RINGING));
    branch2.progress(this.makeResponse(SipResponseStatus.RINGING));
    branch1.answer("xxxx");
    branch1.accept();

    this.segment.sync();

    //
    final EmbeddedDialog dialog = this.segment.getHandles().values().iterator().next();
    final SipResponse res2xx = this.txn.getResponses().get(this.txn.getResponses().size() - 1);
    final SipRequest ack = this.segment.messageManager().createAck(res2xx, Lists.newArrayList());
    dialog.processAck(ack, UdpFlowId.create(new ListenerId(0), HostAndPort.fromParts("127.0.0.1", 1111)));

    // fast forward 10 seconds.
    this.segment.fastForward(Duration.ofSeconds(10));

    // send a INFO
    dialog.processRequest(new TestIncomingTransaction(MutableSipRequest.create(SipMethod.INFO, SipUri.ANONYMOUS).cseq(10, SipMethod.INFO)));

    // make sure that we have a session timer scheduled.
    this.segment.fastForward(Duration.ofSeconds(80));

    // we should have got an UPDATE
    final TestOutgoingTransaction update = this.segment.getOutgoingTransactions().poll();

    update.respond(SipResponseStatus.OK, res -> res.sessionExpires(120));

    branch1.dialog().disconnect(new DisconnectEvent(ApiUtils.convert(SipResponseStatus.OK)));
    this.segment.getOutgoingTransactions().poll().respond(SipResponseStatus.OK);
  }

  @Test
  public void testBranchThenSuccessWithOtherBranchBeingNone() throws Exception
  {
    final ClientSideEarlyDialog branch1 = this.uas.branch(new TestServerBranch(), new TestLocalDialog());
    final ClientSideEarlyDialog branch2 = this.uas.branch(new TestServerBranch(), new TestLocalDialog());
    branch1.progress(this.makeResponse(SipResponseStatus.RINGING));
    branch1.answer("xxxx");
    branch1.accept();
    branch1.dialog().disconnect(new DisconnectEvent(ApiUtils.convert(SipResponseStatus.OK)));
    this.segment.getOutgoingTransactions().poll().respond(SipResponseStatus.OK);
  }

}
