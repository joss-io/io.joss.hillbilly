package com.jive.hillbilly.client;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.fusesource.hawtdispatch.DispatchQueue;
import org.fusesource.hawtdispatch.Metrics;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.primitives.UnsignedInteger;
import com.jive.hillbilly.SipWarning;
import com.jive.hillbilly.api.ClientInviteOptions;
import com.jive.hillbilly.client.ApiUtils;
import com.jive.hillbilly.client.EmbeddedClientCreator;
import com.jive.hillbilly.client.EmbeddedDialog;
import com.jive.hillbilly.client.TestClientBranch.Status;
import com.jive.hillbilly.client.api.DisconnectEvent;
import com.jive.hillbilly.client.api.RenegotiationHandle;
import com.jive.sip.message.api.Reason;
import com.jive.sip.message.api.SipMethod;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponse;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.processor.rfc3261.MutableSipRequest;
import com.jive.sip.uri.api.SipUri;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EmbeddedClientCreatorTest
{

  private static final ClientInviteOptions INVITE_OPTS = ClientInviteOptions.builder()
      .requestUri(ApiUtils.convert(SipUri.ANONYMOUS))
      .segment("test")
      .build();
  private TestEmbeddedNetworkSegment segment;
  private TestClientSideCreator handler;
  private EmbeddedClientCreator uac;
  private TestOutgoingTransaction txn;

  @Before
  public void setup() throws InterruptedException
  {
    this.segment = new TestEmbeddedNetworkSegment();
    this.handler = new TestClientSideCreator();
    this.uac = new EmbeddedClientCreator(this.segment, INVITE_OPTS, this.handler, "[offer]");
    this.uac.send();
    this.txn = this.segment.getOutgoingTransactions().poll(5, TimeUnit.SECONDS);
  }

  @After
  public void checkTerminated()
  {
    Assert.assertEquals(0, this.segment.getActiveDialogCount());
  }

  @Test
  public void testRejection() throws Exception
  {
    Assert.assertEquals(SipMethod.INVITE, this.txn.getRequest().getMethod());
    this.txn.respond(SipResponseStatus.NOT_FOUND);
    final SipResponseStatus status = this.handler.getRejected().get(5, TimeUnit.SECONDS);
    Assert.assertEquals(SipResponseStatus.NOT_FOUND, status);
  }

  @Test
  public void testCancel() throws Exception
  {

    Assert.assertEquals(SipMethod.INVITE, this.txn.getRequest().getMethod());

    this.txn.respond(SipResponseStatus.TRYING);

    this.uac.cancel(ApiUtils.convert(Reason.fromSipStatus(SipResponseStatus.OK.withReason("Call Completed Elsewhere"))));
    this.sync();

    Assert.assertTrue(this.txn.getCancel().isDone());

    // send the request terminated.
    this.txn.respond(SipResponseStatus.REQUEST_TERMINATED);
    this.sync();

    final SipResponseStatus status = this.handler.getRejected().get(5, TimeUnit.SECONDS);

    Assert.assertEquals(SipResponseStatus.REQUEST_TERMINATED, status);

  }

  /**
   * make sure cancelling with a ringing branch closes all dialogs.
   */

  @Test
  public void testCancelWithRingingBranch() throws Exception
  {

    Assert.assertEquals(SipMethod.INVITE, this.txn.getRequest().getMethod());

    this.uac.cancel(ApiUtils.convert(Reason.fromSipStatus(SipResponseStatus.OK.withReason("Call Completed Elsewhere"))));
    this.sync();

    this.txn.respond(SipResponseStatus.TRYING);

    this.txn.respond(SipResponseStatus.RINGING, res -> res.toTag("a").sdp("answer"));
    this.txn.respond(SipResponseStatus.RINGING, res -> res.toTag("b").sdp("answer"));

    this.uac.cancel(ApiUtils.convert(Reason.fromSipStatus(SipResponseStatus.OK.withReason("Call Completed Elsewhere"))));
    this.sync();

    Assert.assertTrue(this.txn.getCancel().isDone());

    // send the request terminated.
    this.txn.respond(SipResponseStatus.REQUEST_TERMINATED);
    this.sync();

    final SipResponseStatus status = this.handler.getRejected().get(5, TimeUnit.SECONDS);

    Assert.assertEquals(SipResponseStatus.REQUEST_TERMINATED, status);

  }

  @Test
  public void testCancelCrossingPathsWith200OK() throws Exception
  {

    Assert.assertEquals(SipMethod.INVITE, this.txn.getRequest().getMethod());

    this.txn.respond(SipResponseStatus.TRYING);

    this.uac.cancel(ApiUtils.convert(Reason.fromSipStatus(SipResponseStatus.OK.withReason("Call Completed Elsewhere"))));

    this.sync();

    Assert.assertTrue(this.txn.getCancel().isDone());

    // send the request terminated.
    this.txn.respond(SipResponseStatus.OK, res -> res.toTag("a").sdp("answer"));

    this.sync();

    final SipResponseStatus status = this.handler.getRejected().get(5, TimeUnit.SECONDS);

    Assert.assertEquals(SipResponseStatus.REQUEST_TERMINATED, status);

    final SipRequest ack = this.segment.getOutgoingRequests().poll();

    // we should have an ACK
    Assert.assertNotNull(ack);

    final TestOutgoingTransaction bye = this.segment.getOutgoingTransactions().poll();

    Assert.assertNotNull(bye);

    Assert.assertEquals(SipMethod.BYE, bye.getRequest().getMethod());

    this.sync();

  }

  @Test
  public void testPrack() throws Exception
  {

    Assert.assertEquals(SipMethod.INVITE, this.txn.getRequest().getMethod());

    this.txn.respond(SipResponseStatus.TRYING);
    this.txn.respond(SipResponseStatus.RINGING, res ->
    res.toTag("a").supported("100rel").allow(SipMethod.INVITE, SipMethod.UPDATE, SipMethod.BYE).rseq(1));

    this.sync();

    // should have a single branch.
    Assert.assertEquals(1, this.handler.getBranches().size());

    final TestOutgoingTransaction prack = this.segment.getOutgoingTransactions().poll();
    Assert.assertNotNull(prack);
    Assert.assertTrue(prack.getRequest().getRAck().isPresent());
    Assert.assertEquals(UnsignedInteger.valueOf(1), prack.getRequest().getRAck().get().getReliableSequence());

    // send another 180
    this.txn.respond(SipResponseStatus.RINGING, res -> res.toTag("a").supported("100rel").allow(SipMethod.INVITE, SipMethod.UPDATE, SipMethod.BYE).rseq(2));
    this.txn.respond(SipResponseStatus.RINGING, res -> res.toTag("a").supported("100rel").allow(SipMethod.INVITE, SipMethod.UPDATE, SipMethod.BYE).rseq(2));

    Assert.assertNotNull(this.segment.getOutgoingTransactions().poll());
    Assert.assertNull(this.segment.getOutgoingTransactions().poll());

    this.sendBye("a");

  }

  @Test
  public void testPrackRenegotiation() throws Exception
  {

    Assert.assertEquals(SipMethod.INVITE, this.txn.getRequest().getMethod());

    this.txn.respond(SipResponseStatus.TRYING);
    this.txn.respond(SipResponseStatus.RINGING, res ->
    res.toTag("a").supported("100rel").allow(SipMethod.INVITE, SipMethod.UPDATE, SipMethod.BYE).rseq(1).sdp("[answer]"));

    this.sync();

    // should have a single branch.
    Assert.assertEquals(1, this.handler.getBranches().size());

    final TestOutgoingTransaction prack = this.segment.getOutgoingTransactions().poll(1, TimeUnit.SECONDS);
    Assert.assertNotNull(prack);
    Assert.assertTrue(prack.getRequest().getRAck().isPresent());
    Assert.assertEquals(UnsignedInteger.valueOf(1), prack.getRequest().getRAck().get().getReliableSequence());

    Assert.assertNotNull(this.handler.getBranches().get(0).getRemote());

    this.sendBye("a");

  }

  @Test
  public void testSingleBranchRingThenReject() throws Exception
  {

    Assert.assertEquals(SipMethod.INVITE, this.txn.getRequest().getMethod());

    this.txn.respond(SipResponseStatus.TRYING);
    this.txn.respond(SipResponseStatus.RINGING, res -> res.toTag("a"));

    this.sync();

    // should have a single branch.
    Assert.assertEquals(1, this.handler.getBranches().size());

    // should be ringing.
    Assert.assertEquals(Status.RINGING, this.handler.getBranches().get(0).getStatus());

    // now fail.
    this.txn.respond(SipResponseStatus.NOT_IMPLEMENTED);

    this.sync();

    final SipResponseStatus status = this.handler.getRejected().get(5, TimeUnit.SECONDS);

    Assert.assertEquals(SipResponseStatus.NOT_IMPLEMENTED, status);

  }

  @Test
  public void testMultiBranchRingThenReject() throws Exception
  {

    Assert.assertEquals(SipMethod.INVITE, this.txn.getRequest().getMethod());

    this.txn.respond(SipResponseStatus.TRYING);
    this.txn.respond(SipResponseStatus.RINGING, res -> res.toTag("a"));
    this.txn.respond(SipResponseStatus.RINGING, res -> res.toTag("b"));

    this.sync();

    // should have a single branch.
    Assert.assertEquals(2, this.handler.getBranches().size());

    // should be ringing.
    Assert.assertEquals(Status.RINGING, this.handler.getBranches().get(0).getStatus());
    Assert.assertEquals(Status.RINGING, this.handler.getBranches().get(1).getStatus());

    // now fail.
    this.txn.respond(SipResponseStatus.NOT_IMPLEMENTED);

    this.sync();

    final SipResponseStatus status = this.handler.getRejected().get(5, TimeUnit.SECONDS);

    Assert.assertEquals(SipResponseStatus.NOT_IMPLEMENTED, status);

  }

  @Test
  public void testMultiBranchRingThenAnswer() throws Exception
  {

    Assert.assertEquals(SipMethod.INVITE, this.txn.getRequest().getMethod());

    this.txn.respond(SipResponseStatus.TRYING);
    this.txn.respond(SipResponseStatus.RINGING, res -> res.toTag("a"));
    this.txn.respond(SipResponseStatus.RINGING, res -> res.toTag("b"));

    this.sync();

    // should have a single branch.
    Assert.assertEquals(2, this.handler.getBranches().size());

    // should be ringing.
    Assert.assertEquals(Status.RINGING, this.handler.getBranches().get(0).getStatus());
    Assert.assertEquals(Status.RINGING, this.handler.getBranches().get(1).getStatus());

    // now answer.
    this.txn.respond(SipResponseStatus.OK, res ->
    {
      res.toTag("a");
      res.body("application/sdp", "[answer]");
    });

    this.sync();

    // should be answered
    Assert.assertEquals(Status.ACCEPTED, this.handler.getBranches().get(0).getStatus());

    // SDP should be correct.
    Assert.assertEquals("[answer]", this.handler.getBranches().get(0).getRemote());

    // dialog should be connected.
    Assert.assertNotNull(this.handler.getBranches().get(0).getDialog());

    // we should now have just a single dialog, as we're not going to allow the other to continue.
    Assert.assertEquals(1, this.segment.getHandles().size());

    log.debug(" ------ ");
    // emulate getting another response branch which should result in ACK and BYE.
    // now answer.
    this.txn.respond(SipResponseStatus.OK, res ->
    {
      res.toTag("b");
      res.body("application/sdp", "[answer]");
    });

    this.sendBye("a");

    this.sync();

  }

  @Test
  public void testSingleBranchRingThenAnswer() throws Exception
  {

    Assert.assertEquals(SipMethod.INVITE, this.txn.getRequest().getMethod());

    this.txn.respond(SipResponseStatus.TRYING);
    this.txn.respond(SipResponseStatus.RINGING, res -> res.toTag("a"));

    this.sync();

    // should have a single branch.
    Assert.assertEquals(1, this.handler.getBranches().size());

    // should be ringing.
    Assert.assertEquals(Status.RINGING, this.handler.getBranches().get(0).getStatus());

    // now answer.
    this.txn.respond(SipResponseStatus.OK, res ->
    {
      res.toTag("a");
      res.body("application/sdp", "[answer]");
    });

    this.sync();

    // should be answered
    Assert.assertEquals(Status.ACCEPTED, this.handler.getBranches().get(0).getStatus());

    // SDP should be correct.
    Assert.assertEquals("[answer]", this.handler.getBranches().get(0).getRemote());

    // dialog should be connected.
    Assert.assertNotNull(this.handler.getBranches().get(0).getDialog());

    // kill it
    Assert.assertEquals(1, this.segment.getHandles().size());

    this.sendBye("a");

  }

  private void sendBye(final String id)
  {
    final MutableSipRequest req = MutableSipRequest.create(SipMethod.BYE, new SipUri(this.segment.getSelf()));
    req.cseq(2, SipMethod.BYE);
    final TestIncomingTransaction r = new TestIncomingTransaction(req.build(this.segment.messageManager()));
    this.segment.getRemoteDialog(id).inviteUsage.processBye(r);
  }

  @Test
  public void testSingleBranchImmediateAnswer() throws Exception
  {

    Assert.assertEquals(SipMethod.INVITE, this.txn.getRequest().getMethod());

    // now answer.
    this.txn.respond(SipResponseStatus.OK, res ->
    {
      res.toTag("a");
      res.body("application/sdp", "[answer]");
    });

    this.sync();

    // should have a single branch.
    Assert.assertEquals(1, this.handler.getBranches().size());

    // should be answered
    Assert.assertEquals(Status.ACCEPTED, this.handler.getBranches().get(0).getStatus());

    // SDP should be correct.
    Assert.assertEquals("[answer]", this.handler.getBranches().get(0).getRemote());

    // dialog should be connected.
    Assert.assertNotNull(this.handler.getBranches().get(0).getDialog());

    this.sendBye("a");

  }

  @Test
  public void testReceiveReinvite() throws Exception
  {

    final TestClientBranch branch = this.answer();

    final EmbeddedDialog a = this.segment.getRemoteDialog("a");

    Assert.assertNotNull(a);

    final MutableSipRequest req =
        MutableSipRequest.create(SipMethod.INVITE, new SipUri(this.segment.getSelf()));

    req.cseq(1, SipMethod.INVITE);
    req.body("application/sdp", "[offer2]");

    final TestIncomingTransaction r = new TestIncomingTransaction(req.build(this.segment.messageManager()));

    a.processRequest(r);

    this.sync();

    Assert.assertEquals("[offer2]", branch.getRemote());

    branch.getRenegotiation().answer("[answer2]");

    this.sync();

    final SipResponse res = r.getResponses().get(1);

    Assert.assertEquals(200, res.getStatus().getCode());
    Assert.assertEquals("[answer2]", new String(res.getBody(), Charsets.UTF_8));

    this.sendBye("a");

  }

  @Test
  public void testSendReinvite() throws Exception
  {

    final TestClientBranch branch = this.answer();

    final EmbeddedDialog a = this.segment.getRemoteDialog("a");

    Assert.assertNotNull(a);

    final AtomicReference<String> ranswer = new AtomicReference<>();

    branch.getDialog().answer("[offer2]", new RenegotiationHandle()
    {

      @Override
      public void reject(final List<SipWarning> warnings)
      {
        // TODO Auto-generated method stub
      }

      @Override
      public void answer(final String answer)
      {
        ranswer.set(answer);
      }

    });

    this.sync();

    final TestOutgoingTransaction reinvite = this.segment.getOutgoingTransactions().poll();

    Assert.assertEquals(SipMethod.INVITE, reinvite.getRequest().getMethod());

    final String sent = new String(reinvite.getRequest().getBody(), Charsets.UTF_8);

    Assert.assertEquals("[offer2]", sent);

    reinvite.respond(SipResponseStatus.OK, res -> res.body("application/sdp", "[answer2]"));

    Assert.assertEquals("[answer2]", ranswer.get());

    this.sendBye("a");

  }

  @Test
  public void testRemoteHangup() throws Exception
  {

    final TestClientBranch branch = this.answer();

    final EmbeddedDialog a = this.segment.getRemoteDialog("a");

    final MutableSipRequest req =
        MutableSipRequest.create(SipMethod.BYE, new SipUri(this.segment.getSelf()));

    req.cseq(1, SipMethod.BYE);

    final TestIncomingTransaction r = new TestIncomingTransaction(req.build(this.segment.messageManager()));

    a.processRequest(r);

    this.sync();

  }

  @Test
  public void testLocalHangup() throws Exception
  {
    final TestClientBranch branch = this.answer();
    final EmbeddedDialog a = this.segment.getRemoteDialog("a");

    branch.getDialog().disconnect(DisconnectEvent.OK);

    this.sync();

    final TestOutgoingTransaction bye = this.segment.getOutgoingTransactions().poll();
    Assert.assertEquals(SipMethod.BYE, bye.getRequest().getMethod());
    bye.respond(SipResponseStatus.OK);

  }

  // sets up call so it's answered.

  private TestClientBranch answer() throws Exception
  {

    Assert.assertEquals(SipMethod.INVITE, this.txn.getRequest().getMethod());

    // now answer.
    this.txn.respond(SipResponseStatus.OK, res ->
    {
      res.toTag("a");
      res.body("application/sdp", "[answer]");
    });

    this.sync();

    // should have a single branch.
    Assert.assertEquals(1, this.handler.getBranches().size());

    // should be answered
    Assert.assertEquals(Status.ACCEPTED, this.handler.getBranches().get(0).getStatus());

    // SDP should be correct.
    Assert.assertEquals("[answer]", this.handler.getBranches().get(0).getRemote());

    // dialog should be connected.
    Assert.assertNotNull(this.handler.getBranches().get(0).getDialog());

    return this.handler.getBranches().get(0);

  }

  private void sync(final DispatchQueue queue) throws InterruptedException
  {
    do
    {
      final CountDownLatch latch = new CountDownLatch(1);
      queue.execute(() -> latch.countDown());
      latch.await(5, TimeUnit.SECONDS);
    }
    while (!this.empty(queue));

  }

  // waits for the work queue to be empty.

  private void sync() throws InterruptedException
  {
  }

  private boolean empty(final DispatchQueue queue)
  {
    final Metrics m = queue.metrics();
    return (m.enqueued - m.dequeued) == 0;
  }

}
