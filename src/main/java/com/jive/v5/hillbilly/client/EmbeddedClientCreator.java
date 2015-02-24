package com.jive.v5.hillbilly.client;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.RandomStringUtils;
import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.DispatchQueue;

import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.primitives.UnsignedInteger;
import com.jive.ftw.sip.dummer.session.DialogInfo;
import com.jive.ftw.sip.dummer.txn.ClientTransactionListener;
import com.jive.ftw.sip.dummer.txn.SipTransactionErrorInfo;
import com.jive.ftw.sip.dummer.txn.SipTransactionResponseInfo;
import com.jive.sip.message.api.DialogId;
import com.jive.sip.message.api.Reason;
import com.jive.sip.message.api.SipMethod;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponse;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.processor.rfc3261.MutableSipRequest;
import com.jive.sip.uri.api.SipUri;
import com.jive.sip.uri.api.Uri;
import com.jive.v5.hillbilly.client.api.ClientSideCreator;
import com.jive.v5.hillbilly.client.api.ClientSideEarlyDialog;
import com.jive.v5.hillbilly.client.api.Dialog;
import com.jive.v5.hillbilly.client.api.ServerSideCreator;
import com.jive.v5.hillbilly.client.api.ServerSideEarlyDialog;

@Slf4j
public class EmbeddedClientCreator implements ServerSideCreator
{

  private EmbeddedNetworkSegment service;
  private ClientSideCreator handler;
  private String offer;
  private boolean cancelled = false;
  private ClientInviteOptions options;

  private String tag = RandomStringUtils.randomAlphanumeric(12);
  private String callId = RandomStringUtils.randomAlphanumeric(32);

  private DispatchQueue queue = Dispatch.createQueue();
  public Branch winner;

  public EmbeddedClientCreator(
      EmbeddedNetworkSegment service,
      ClientInviteOptions opts,
      ClientSideCreator handler,
      String offer)
  {
    this.service = service;
    this.options = opts;
    this.handler = handler;
    this.offer = offer;
  }

  @Override
  public void cancel(Reason reason)
  {
    this.cancelled = true;
  }

  SipRequest build()
  {

    MutableSipRequest req = MutableSipRequest.create(SipMethod.INVITE, options.getRequestUri());

    req.contact(getContact());
    req.from(options.getRequestUri(), this.tag);
    req.to(options.getRequestUri());
    req.callId(this.callId);
    req.cseq(1, SipMethod.INVITE);
    req.body("application/sdp", this.offer);

    SipRequest invite = req.build(service.messageManager());

    return invite;

  }

  private Uri getContact()
  {
    return SipUri.create(service.getSelf());
  }

  void send()
  {

    if (cancelled)
    {
      // immediatly notify.
      handler.reject(SipResponseStatus.REQUEST_TERMINATED);
      return;
    }

    try
    {

      // build the INVITE request we are going to send.
      SipRequest invite = build();

      // send it out, and collect our responses.
      service.sendInvite(invite, new ClientTransactionListener()
      {

        @Override
        public void onResponse(SipTransactionResponseInfo e)
        {
          queue.execute(() -> processResponse(e));
        }

        @Override
        public void onError(SipTransactionErrorInfo e)
        {
          queue.execute(() -> processError(e));
        }

      });

    }
    catch (Exception ex)
    {
      handler.reject(SipResponseStatus.SERVER_INTERNAL_ERROR);
      return;
    }

  }

  private enum BranchState
  {
    INITIAL,
    RINGING,
    SUCCESS,
    TERMINATED
  }

  private class Branch implements ServerSideEarlyDialog
  {

    public ClientSideEarlyDialog handle;
    private String remote;
    private EmbeddedDialog dialog;
    private BranchState state = BranchState.INITIAL;

    public Branch(EmbeddedDialog dialog)
    {
      this.dialog = dialog;
    }

    public void provisional(SipTransactionResponseInfo e)
    {
      // if it contains SDP, then we've got ourselves an answer.
      state = BranchState.RINGING;
      body(e.getResponse());
      handle.progress(SipResponseStatus.RINGING);
    }

    private void body(SipResponse response)
    {

      if (remote != null)
      {

        byte[] body = response.getBody();

        if (body != null)
        {
          this.remote = new String(body, Charsets.UTF_8);
          handle.answer(remote);
          // TODO: also, now mark the negotiation as complete.
        }

      }

    }

    public void success(SipTransactionResponseInfo e)
    {

      SipResponse res = e.getResponse();

      // always ACK.
      dialog.ack(res);

      if (state == BranchState.SUCCESS)
      {
        // nothign to do, it's a retransmit.
        return;
      }

      state = BranchState.SUCCESS;

      body(res);

      if (winner == null)
      {

        // TODO: check we are in an appropriate state.
        EmbeddedClientCreator.this.winner = this;

        // set timer for 32 secons to absorb any other ones which didn't succeed for whatever
        // reason.
        queue.executeAfter(32, TimeUnit.SECONDS, () -> killBranches());

        // tell the consumer this dialog won.
        Dialog dhandle = handle.accept(dialog);

      }
      else
      {

        // another branch won.
        dialog.disconnect();

      }

    }

    /**
     * The consumer is requesting we terminate this specific branch. can only do this by sending a
     * BYE directly.
     */

    @Override
    public void end(String reason)
    {
      dialog.disconnect();
    }

  }

  private Map<String, Branch> branches = Maps.newHashMap();

  private void processResponse(SipTransactionResponseInfo e)
  {

    // dispatch the responses based on what we're getting from them.
    SipResponse res = e.getResponse();

    if (res.getStatus().isFailure())
    {
      handler.reject(res.getStatus());
      return;
    }
    else if (res.getStatus().getCode() == 100)
    {
      // nothing to do
      return;
    }

    if (res.getToTag() == null)
    {
      // TODO: perhaps treat as a "null" dialog for talking to RFC 2543 clients?
      log.warn("Dropping response without to tag");
      return;
    }

    Branch branch = branches.get(res.getToTag());

    if (branch == null)
    {

      if (res.getStatus().getCode() == 199)
      {
        // don't bother doing anything with it.
        return;
      }

      // create a dialog for this branch

      DialogId did = DialogId.fromRemote(res);

      DialogInfo dinfo = new DialogInfo(
          this.options.getRequestUri(),
          this.getContact(),
          res.getContacts().get().iterator().next().getAddress(),
          res.getFrom(),
          res.getTo()
          );

      DialogState dstate = new DialogState(UnsignedInteger.ONE, null);

      EmbeddedDialog dialog = new EmbeddedDialog(service, did, dinfo, dstate);

      // register the dialog so we receive in-dialog requests, and ensure it's unregistered once the
      // dialog is terminated.

      branch = new Branch(dialog);

      // notify the consumer.

      ClientSideEarlyDialog bhandle = handler.branch(branch);

      if (bhandle == null)
      {
        // TODO: terminate the dialog.
        log.info("Terminating branch due to no handler");
        return;
      }

      branch.handle = bhandle;

      // register it!
      branches.put(res.getToTag(), branch);

    }

    if (res.getStatus().isSuccess())
    {
      branch.success(e);
    }
    else
    {
      branch.provisional(e);
    }

  }

  // called 32 seconds after a branch wins, so we can destroy the other ones.
  private void killBranches()
  {

  }

  private void processError(SipTransactionErrorInfo e)
  {
    // an error occured, so back out.
    handler.reject(SipResponseStatus.REQUEST_TIMEOUT);
  }

}
