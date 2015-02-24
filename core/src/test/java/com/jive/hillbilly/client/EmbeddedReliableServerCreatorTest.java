package com.jive.hillbilly.client;

import java.util.Collections;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.net.HostAndPort;
import com.jive.ftw.sip.dummer.SipHelper;
import com.jive.hillbilly.Response;
import com.jive.hillbilly.SipStatus;
import com.jive.hillbilly.client.ApiUtils;
import com.jive.hillbilly.client.EmbeddedDialog;
import com.jive.hillbilly.client.EmbeddedServerCreator;
import com.jive.hillbilly.client.HillbillyHelpers;
import com.jive.hillbilly.client.api.ClientSideEarlyDialog;
import com.jive.hillbilly.client.api.DisconnectEvent;
import com.jive.hillbilly.client.api.OriginationBranchProgressEvent;
import com.jive.sip.base.api.Token;
import com.jive.sip.message.api.SipMethod;
import com.jive.sip.message.api.SipResponse;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.processor.rfc3261.MutableSipRequest;
import com.jive.sip.processor.rfc3261.MutableSipResponse;
import com.jive.sip.transport.udp.ListenerId;
import com.jive.sip.transport.udp.UdpFlowId;
import com.jive.sip.uri.api.SipUri;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EmbeddedReliableServerCreatorTest
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

    req.require("100rel");
    req.cseq(1, SipMethod.INVITE);
    req.to(SipUri.ANONYMOUS);
    req.from(SipUri.ANONYMOUS, "a");
    req.callId("call1");
    req.contact(SipUri.ANONYMOUS);

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

  @Test
  public void testPrack() throws Exception
  {

    final TestLocalDialog client = new TestLocalDialog();

    final ClientSideEarlyDialog branch1 = this.uas.branch(new TestServerBranch(), client);

    branch1.progress(this.makeResponse(SipResponseStatus.RINGING));

    Assert.assertEquals(1, this.txn.getResponses().size());

    //
    SipResponse res1xx = this.txn.getResponses().get(0);

    Assert.assertTrue(this.txn.getResponses().get(0).getSupported().get().contains(Token.from("100rel")));

    // now wait for a bit, make sure it's retransmitted.

    this.segment.getQueued().remove(0).getCommand().run();
    Assert.assertEquals(2, this.txn.getResponses().size());

    // now send a PRACK

    Assert.assertEquals(1, this.segment.getHandles().size());

    final EmbeddedDialog dialog = this.segment.getHandles().get(res1xx.getFromTag()).iterator().next();
    Assert.assertNotNull(dialog);

    MutableSipRequest prack = MutableSipRequest.create(SipMethod.PRACK, SipHelper.getContactUri(res1xx));
    prack.cseq(res1xx.getCSeq().withNextSequence(SipMethod.PRACK));
    prack.rack(res1xx.getRSeq().get(), res1xx.getCSeq());
    TestIncomingTransaction prackTxn = new TestIncomingTransaction(prack.build(this.segment.messageManager()));
    dialog.processRequest(prackTxn);

    // now send 1xxrel with answer.

    branch1.answer("[answer]");

    branch1.progress(new OriginationBranchProgressEvent(new Response(SipStatus.PROGRESS, Collections.emptyMap(), null)));

    Assert.assertEquals(3, this.txn.getResponses().size());

    res1xx = this.txn.getResponses().get(2);

    final String offer = HillbillyHelpers.getSessionDescription(res1xx).get();

    Assert.assertEquals("[answer]", offer);

    // now send another PRACK, but this time with an SDP offer.

    prack = MutableSipRequest.create(SipMethod.PRACK, SipHelper.getContactUri(res1xx));
    prack.cseq(res1xx.getCSeq().withNextSequence(SipMethod.PRACK).withNextSequence(SipMethod.PRACK));
    prack.rack(res1xx.getRSeq().get(), res1xx.getCSeq());
    prack.sdp("[offer2]");
    prackTxn = new TestIncomingTransaction(prack.build(this.segment.messageManager()));
    dialog.processRequest(prackTxn);

    log.debug("----");

    // make sure that we got a new offer.
    Assert.assertEquals("[offer2]", client.getOffer());
    client.getAnswerTo().answer("[answer2]");

    //
    final String answer = HillbillyHelpers.getSessionDescription(prackTxn.getResponses().get(0)).get();
    Assert.assertEquals("[answer2]", answer);

    branch1.accept();

    final SipResponse res200 = this.txn.getResponses().get(3);

    Assert.assertFalse(HillbillyHelpers.getSessionDescription(res200).isPresent());

    //
    prack = MutableSipRequest.create(SipMethod.ACK, SipHelper.getContactUri(res1xx));
    prack.cseq(res1xx.getCSeq().withNextSequence(SipMethod.PRACK).withNextSequence(SipMethod.PRACK));
    prack.rack(res1xx.getRSeq().get(), res1xx.getCSeq());
    prack.sdp("[offer2]");
    dialog.processAck(MutableSipRequest.ack(res200).build(this.segment.messageManager()),
        UdpFlowId.create(new ListenerId(0), HostAndPort.fromString("127.0.0.1:1111")));

    //

    branch1.dialog().disconnect(new DisconnectEvent(ApiUtils.convert(SipResponseStatus.OK)));
    this.segment.getOutgoingTransactions().poll().respond(SipResponseStatus.OK);
  }

}
