package com.jive.hillbilly.client;

import org.apache.commons.lang3.RandomUtils;

import com.google.common.base.Preconditions;
import com.google.common.primitives.UnsignedInteger;
import com.jive.hillbilly.client.api.ClientSideEarlyDialog;
import com.jive.hillbilly.client.api.Dialog;
import com.jive.hillbilly.client.api.DialogListener;
import com.jive.hillbilly.client.api.DisconnectEvent;
import com.jive.hillbilly.client.api.IncomingInviteHandle;
import com.jive.hillbilly.client.api.OriginationBranchConnectEvent;
import com.jive.hillbilly.client.api.OriginationBranchProgressEvent;
import com.jive.hillbilly.client.api.ServerSideEarlyDialog;
import com.jive.sip.message.api.DialogId;
import com.jive.sip.message.api.SessionExpires;
import com.jive.sip.message.api.SessionExpires.Refresher;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.processor.rfc3261.MutableSipResponse;
import com.jive.sip.processor.uri.SipUriExtractor;
import com.jive.sip.uri.api.SipUri;
import com.jive.sip.uri.api.Uri;
import com.jive.sip.uri.api.UserInfo;

import lombok.extern.slf4j.Slf4j;

/**
 * A single branch for an incoming INVITE.
 *
 * @author theo
 *
 */

@Slf4j
class ServerBranch implements ClientSideEarlyDialog
{

  /**
   * The related creator.
   */

  private final EmbeddedServerCreator creator;

  /**
   * The server side handler, we only use this for dispatching the notification of termination.
   */

  private final ServerSideEarlyDialog handler;

  /**
   * Our local tag.
   */

  private final String tag;

  /**
   * The local SDP answer we are goign to send.
   */

  private String local;

  /**
   * The real SIP dialog implementation.
   */

  private EmbeddedDialog dialog;

  /**
   * Handle for registration of the dialog.
   */

  private DialogRegistrationHandle handle;

  /**
   * The RSeq next value to use for PRACK.
   */

  private UnsignedInteger rseq = UnsignedInteger.valueOf(RandomUtils.nextLong(1, 2147483648L));

  /**
   * The SIP request (INVITE) we are responding to.
   */

  private final SipRequest req;

  private final Dialog remoteDialog;

  private boolean answered;

  public ServerBranch(
      final EmbeddedServerCreator embeddedServerCreator,
      final String tag,
      final ServerSideEarlyDialog handler,
      final Dialog remote)
  {
    this.creator = embeddedServerCreator;
    this.handler = handler;
    this.tag = tag;
    this.remoteDialog = remote;
    this.req = embeddedServerCreator.getTxn().getRequest();
  }

  @Override
  public Dialog dialog()
  {
    return this.getDialog();
  }

  /**
   * returns the dialog.
   */

  EmbeddedDialog getDialog()
  {

    if (this.dialog == null)
    {

      final DialogId did = new DialogId(this.req.getCallId(), this.tag, this.req.getFromTag());

      final DialogInfo dinfo = new DialogInfo(
          this.req.getUri(),
          this.getLocalContactUri(this.req),
          this.req.getContacts().get().iterator().next().getAddress(),
          this.req.getTo().withTag(this.tag),
          this.req.getFrom()
          );

      final DialogState dstate = new DialogState(null, UnsignedInteger.valueOf(this.req.getCSeq().longValue()));

      this.dialog = new EmbeddedDialog(
          DialogSide.UAS,
          this.creator.service,
          did,
          dinfo,
          dstate,
          this.req.getRecordRoute(),
          this.creator.getFlowId(),
          this.creator.support);

      this.dialog.setLocalDialog(this.remoteDialog);

      this.handle = this.creator.service.register(did, this.dialog);

      this.dialog.addListener(new DialogListener()
      {

        @Override
        public void terminated()
        {
          if (ServerBranch.this.handle != null)
          {
            ServerBranch.this.handle.remove();
          }
        }

      }, this.creator.service.getExecutor());

    }

    return this.dialog;

  }

  /**
   * Set our local contact to our self, but set the user part to the content of the R-URI if there
   * was one.
   */

  private Uri getLocalContactUri(final SipRequest req)
  {

    final SipUri sip = req.getUri().apply(SipUriExtractor.getInstance());

    if (sip == null)
    {
      return  SipUri.fromHost(this.creator.service.getSelf().toString());
    }
    else if (sip.getUsername().isPresent())
    {
      return new SipUri(new UserInfo(sip.getUsername().get()), this.creator.service.getSelf().toString());
    }

    return SipUri.fromHost(this.creator.service.getSelf().toString());

  }

  @Override
  public void answer(final String sdp)
  {
    log.debug("Got SDP answer");
    this.local = sdp;
  }

  /**
   * If 100rel support is enabled, then we can't sent an overlapped provisional until the first has
   * been acknowledged. We also can't respond with a 200 if we sent the answer in the reliable
   * provisional until it's been acknowledged.
   */

  @Override
  public void progress(final OriginationBranchProgressEvent e)
  {

    final SipResponseStatus status = ApiUtils.convert(e.getResponse().getStatus());

    Preconditions.checkArgument(status.getCode() != 100);
    Preconditions.checkArgument(!status.isFinal());

    final MutableSipResponse res = this.createResponse(status);

    if ((this.local != null) && (status.getCode() == 183))
    {

      if (!this.answered)
      {
        res.body("application/sdp", this.local);
      }

      if (this.creator.isReliableProvisional())
      {
        this.answered = true;
      }

    }

    res.accept(HillbillyRequestEnforcer.getAccept());
    res.allow(this.creator.getService().getEnforcer().getMethods());
    res.supported(this.creator.getService().getEnforcer().getSupported());
    res.allowEvents(HillbillyRequestEnforcer.getEvents());
    res.server(this.creator.service.getServerName());

    if (this.creator.isReliableProvisional())
    {

      log.debug("Remote endpoint supports 100rel");

      // woop.
      res.require("100rel");

      // increment reliable sequence.
      res.rseq(this.rseq.longValue());
      this.rseq = this.rseq.plus(UnsignedInteger.ONE);

      this.getDialog().respondReliably(this.creator.getTxn(), res.build(this.creator.service.messageManager()));

    }
    else
    {

      // need to force dialog to be created now.
      this.getDialog();

      // TODO: retransmit the last unreliable response after 2 seconds, 5 seconds, then every 60
      // seconds.

      this.respond(res);

    }

  }

  private void respond(final MutableSipResponse res)
  {
    this.creator.getTxn().respond(res.build(this.creator.service.messageManager()));
  }

  private MutableSipResponse createResponse(final SipResponseStatus status)
  {
    final MutableSipResponse res = this.getDialog().createResponse(this.req, status);
    return res;
  }

  @Override
  public void accept(final OriginationBranchConnectEvent e)
  {

    log.debug("Accepting branch {}", this.tag);

    Preconditions.checkNotNull(this.local, "Local SDP must be provided before answering");

    final MutableSipResponse res = this.createResponse(SipResponseStatus.OK);

    // process session-expires.

    if (this.req.getSessionExpires().isPresent() && this.creator.support.supports("timer"))
    {

      final SessionExpires expires = this.req.getSessionExpires().orElse(null);

      final Refresher refresher = expires.getRefresher().orElse(Refresher.Server);
      res.sessionExpires(expires.getDuration(), refresher);

      this.dialog.sessionRefresh(new SessionRefreshConfig(refresher == Refresher.Server, expires.getDuration()));

      switch (refresher)
      {
        case Client:
          res.require("timer");
          break;
        case Server:
          break;
      }

    }

    if (!this.answered)
    {
      res.body("application/sdp", this.local);
      this.answered = true;
    }

    this.getDialog().connected(this.local, this.creator.remote);

    this.getDialog().transmit(this.creator.getTxn(), res.build(this.creator.service.messageManager()));

    // now clean up the others.
    this.creator.winner(this);

  }

  /**
   * We received an INVITE w/Replaces referencing this dialog. Shouldn't happen?
   */

  @Override
  public void replaceWith(final IncomingInviteHandle uas)
  {
    log.warn("Invalid state for INVITE w/Replaces");
    uas.process(null);
  }

  public String id()
  {
    return this.tag;
  }

  /**
   * destroy due to another branch winning.
   */

  public void destroy()
  {

    log.debug("Destrying early dialog");

    if (this.dialog != null)
    {
      this.dialog.inviteUsage.disconnected(DisconnectEvent.CallCompletedElsewhere);
    }

  }

}
