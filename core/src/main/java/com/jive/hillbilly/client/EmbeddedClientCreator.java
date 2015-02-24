package com.jive.hillbilly.client;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.UnsignedInteger;
import com.jive.hillbilly.SipReason;
import com.jive.hillbilly.SipStatus;
import com.jive.hillbilly.api.ClientInviteOptions;
import com.jive.hillbilly.client.api.ClientSideCreator;
import com.jive.hillbilly.client.api.ClientSideEarlyDialog;
import com.jive.hillbilly.client.api.Dialog;
import com.jive.hillbilly.client.api.DialogListener;
import com.jive.hillbilly.client.api.DialogTerminationEvent;
import com.jive.hillbilly.client.api.DisconnectEvent;
import com.jive.hillbilly.client.api.OriginationBranchConnectEvent;
import com.jive.hillbilly.client.api.OriginationBranchProgressEvent;
import com.jive.hillbilly.client.api.ServerSideCreator;
import com.jive.hillbilly.client.api.ServerSideEarlyDialog;
import com.jive.sip.auth.DigestAuthUtils;
import com.jive.sip.auth.headers.Authorization;
import com.jive.sip.auth.headers.DigestCredentials;
import com.jive.sip.base.api.Token;
import com.jive.sip.dummer.txn.ClientTransactionListener;
import com.jive.sip.dummer.txn.ClientTransactionOptions;
import com.jive.sip.dummer.txn.SipClientTransaction;
import com.jive.sip.dummer.txn.SipTransactionErrorInfo;
import com.jive.sip.dummer.txn.SipTransactionResponseInfo;
import com.jive.sip.message.api.ContentDisposition;
import com.jive.sip.message.api.DialogId;
import com.jive.sip.message.api.NameAddr;
import com.jive.sip.message.api.Reason;
import com.jive.sip.message.api.SessionExpires;
import com.jive.sip.message.api.SessionExpires.Refresher;
import com.jive.sip.message.api.SipMethod;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponse;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.processor.rfc3261.MutableSipRequest;
import com.jive.sip.transport.api.FlowId;
import com.jive.sip.transport.udp.ListenerId;
import com.jive.sip.transport.udp.UdpFlowId;
import com.jive.sip.uri.api.SipUri;
import com.jive.sip.uri.api.Uri;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends INVITE requests out, and converts the answer into branches and dialogs.
 *
 * @author theo
 *
 */

@Slf4j
public class EmbeddedClientCreator implements ServerSideCreator, ClientTransactionListener
{

  private final EmbeddedNetworkSegment service;
  private final ClientSideCreator handler;
  private final String offer;
  private Optional<Reason> cancelled = null;
  private final ClientInviteOptions options;

  private final String tag = RandomStringUtils.randomAlphanumeric(12);
  private final String callId = RandomStringUtils.randomAlphanumeric(32);

  public Branch winner;

  private final Map<String, Branch> branches = Maps.newHashMap();
  private SipClientTransaction txn;
  private final UnsignedInteger cseq = UnsignedInteger.valueOf(RandomUtils.nextLong(1, 2147483648L));

  public EmbeddedClientCreator(
      final EmbeddedNetworkSegment service,
      final ClientInviteOptions opts,
      final ClientSideCreator handler,
      final String offer)
  {
    this.service = Preconditions.checkNotNull(service);
    this.options = opts;
    this.handler = handler;
    this.offer = offer;
  }

  /**
   * A CANCEL may cross paths with a 200 OK to the original INVITE, in which case we throw {@link IllegalStateException} and leave it to the
   * consumer to do the termination.
   */

  @Override
  public void cancel(final SipReason reason)
  {

    this.cancelled = Optional.ofNullable(ApiUtils.convert(reason));

    log.info("Cancelling with {}", this.cancelled);

    if (this.winner != null)
    {

      // we have a winner, which means a branch has already completed. The CANCEL request must have
      // crossed paths with the 200 OK being dispatched. We don't do anything as it may leae the
      // consumer in a weird state, and instead reject the CANCEL. The consumer is then able to
      // terminate the just-connected dialog.
      //
      // note that if we've not yet received the 200 OK and the CANCEL itself fails, we'll still
      // take care of it by sending a BYE immediately (although we do have to create some crap SDP
      // if it was a delayed offer).
      //

      log.info("Rejecting CANCEL due to already rejected");

      throw new IllegalStateException("INVITE transaction has already completed");

    }

    this.txn.cancel(ApiUtils.convert(reason));

    // we can safely handle terminating this ourselves without confuzing the consumer, to terminate
    // immedaitly.
    this.handler.reject(SipStatus.REQUEST_TERMINATED);

  }

  SipRequest build()
  {

    final MutableSipRequest req = MutableSipRequest.create(SipMethod.INVITE, ApiUtils.uri(this.options.getRequestUri()));

    req.contact(this.getContact());

    req.from(this.localIdentity(), this.tag);

    req.to(this.remoteIdentity());

    req.callId(this.callId);
    req.cseq(this.cseq.longValue(), SipMethod.INVITE);

    if (this.options.getSessionId() != null)
    {
      req.session(this.options.getSessionId());
    }
    else
    {
      req.session(RandomStringUtils.randomAlphanumeric(32));
    }

    if (this.options.getHeaders() != null)
    {
      this.options.getHeaders().forEach((name, value) -> req.add(name, value));
    }

    if (this.options.getMaxForwards() != null)
    {
      req.maxForwards(Math.max(this.options.getMaxForwards().intValue(), 70));
    }
    else
    {
      req.maxForwards(70);
    }

    if (this.authContext != null && !this.authContext.isEmpty())
    {

      final Entry<String, String> creds = this.options.getCredentials().entrySet().iterator().next();

      final DigestCredentials resc = DigestAuthUtils.createResponse(
          SipMethod.INVITE,
          this.options.getRequestUri(),
          (DigestCredentials) this.authContext.get(0),
          RandomStringUtils.randomAlphanumeric(8),
          1,
          creds.getKey(),
          creds.getValue());

      req.proxyAuthorization(Lists.newArrayList(resc));

    }

    req.body("application/sdp", this.offer, ContentDisposition.SessionRequired);

    //
    req.accept(HillbillyRequestEnforcer.getAccept());
    req.allow(this.service.getEnforcer().getMethods());
    req.supported(this.service.getEnforcer().getSupported());
    req.allowEvents(HillbillyRequestEnforcer.getEvents());
    req.userAgent(this.service.getServerName());

    req.sessionExpires(900);
    req.minse(90);

    final SipRequest invite = req.build(this.service.messageManager());

    return invite;

  }

  private Uri getContact()
  {
    return SipUri.fromHost(this.service.getSelf().toString());
  }

  void send()
  {

    if (this.cancelled != null)
    {
      // immediately notify.
      this.handler.reject(ApiUtils.convert(SipResponseStatus.REQUEST_TERMINATED));
      return;
    }

    final ClientTransactionOptions opts = this.options.getTimerA() == null
        ? new ClientTransactionOptions(
            ClientTransactionOptions.DEFAULT.getTimerA(),
            Duration.ofSeconds(5))
        : new ClientTransactionOptions(
            Duration.parse(this.options.getTimerA().toString()),
            Duration.ofSeconds(5));

    try
    {

      // build the INVITE request we are going to send.
      final SipRequest invite = this.build();

      // send it out, and collect our responses.
      this.txn = this.service.sendInvite(invite, this.convert(this.options.getNextHop()), opts, new DispatchingClientTransactionListener(this.service.getExecutor(), this));

    }
    catch (final Exception ex)
    {
      log.warn("Got exception sending INVITE", ex);
      this.handler.reject(ApiUtils.convert(SipResponseStatus.SERVER_INTERNAL_ERROR));
      return;
    }

  }

  private FlowId convert(final InetSocketAddress nextHop)
  {
    if (nextHop == null)
    {
      return null;
    }
    return UdpFlowId.create(new ListenerId(0), nextHop);
  }

  private enum BranchState
  {
    INITIAL, RINGING, SUCCESS, TERMINATED
  }

  private class Branch implements ServerSideEarlyDialog
  {

    private DialogNegotiationState negstate = DialogNegotiationState.WaitingForAnswer;

    public ClientSideEarlyDialog handle;
    private final EmbeddedDialog dialog;
    private BranchState state = BranchState.INITIAL;

    // null until we first one.
    private UnsignedInteger rseq = null;

    private boolean preview;

    // our local and remote SDP.
    private final String local = EmbeddedClientCreator.this.offer;
    private String remote = null;

    public Branch(final EmbeddedDialog dialog)
    {
      this.dialog = dialog;
    }

    public void provisional(final SipTransactionResponseInfo e)
    {

      if (this.state == BranchState.SUCCESS || this.state == BranchState.TERMINATED)
      {
        log.warn("Received provisional after termination/success");
        return;
      }

      this.state = BranchState.RINGING;

      final java.util.Optional<String> sdp = HillbillyHelpers.getSessionDescription(e.getResponse());

      // if it contains SDP, then we've got ourselves an answer.

      if (e.getResponse().getRSeq().isPresent())
      {

        final UnsignedInteger crseq = UnsignedInteger.valueOf(e.getResponse().getRSeq().get());

        if (this.rseq != null && crseq.compareTo(this.rseq) <= 0)
        {
          log.debug("Dropping duplicate 100rel");
          return;
        }
        else if (this.rseq != null && !crseq.equals(this.rseq.plus(UnsignedInteger.ONE)))
        {
          log.debug("Out of order 1xxrel, dropping until the other arrives");
          // TODO: we could optimise and add to a cache, but hey ...
          return;
        }

        this.rseq = crseq;

        log.debug("RSeq {} present, sending PRACK", crseq);

        // if the 1xx contains SDP, we can piggyback a new offer (or the answer) on the PRACK. So
        // find out ..

        if (sdp.isPresent())
        {

          // see if we want to send another offer/answer on this.
          log.debug("1xx-rel contained session SDP in state {}", this.negstate);

          switch (this.negstate)
          {

            case WaitingForOffer:
              // it's an offer
              break;

            case ReceivedOffer:
              // invalid. they need to wait for our answer.
              log.warn("Received SDP before we'd sent an answer");
              break;

            case WaitingForAnswer:
              this.preview = false;
              this.remote = sdp.get();
              this.negstate = DialogNegotiationState.Negotiated;
              this.handle.answer(sdp.get());
              break;

            case Negotiated:
              // invalid. they should use UPDATE instead.
              log.warn("Received more SDP in a 1xx-rel after we'd negotiated");
              break;

          }

        }

        // incase they want to provide a new offer in the PRACK ...
        try
        {
          this.handle.progress(new OriginationBranchProgressEvent(ApiUtils.convert(e.getResponse())));
        }
        catch (final Exception ex)
        {
          log.error("Exception notifying app of progress", ex);
        }

        // send a PRACK for this provisional message.
        this.dialog.prack(e.getResponse());

      }
      else
      {

        // it's unreliable. different logic.

        if (sdp.isPresent())
        {

          if (!this.preview)
          {

            switch (this.negstate)
            {

              case WaitingForOffer:
                this.remote = sdp.get();
                this.preview = true;
                this.negstate = DialogNegotiationState.ReceivedOffer;
                break;

              case ReceivedOffer:
                // errr?
                log.warn("Got another unreliable SDP offer");
                break;

              case WaitingForAnswer:
                this.remote = sdp.get();
                this.preview = true;
                this.negstate = DialogNegotiationState.Negotiated;
                this.handle.answer(sdp.get());
                break;

              case Negotiated:
                log.warn("Attempted to renegotiate with unreliable 1xx");
                // just ignore it.
                break;

              default:
                break;

            }

          }

        }

        this.handle.progress(new OriginationBranchProgressEvent(ApiUtils.convert(e.getResponse())));

      }

    }

    public void success(final SipTransactionResponseInfo e)
    {

      final SipResponse res = e.getResponse();

      if (this.state == BranchState.SUCCESS)
      {
        // nothign to do, it's a retransmit.
        this.dialog.ack(res);
        return;
      }

      final java.util.Optional<String> sdp = HillbillyHelpers.getSessionDescription(e.getResponse());

      log.debug("{} in {} state = {}", e.getResponse().getStatus(), this.negstate, this.state);

      switch (this.negstate)
      {

        case WaitingForOffer:

          if (!sdp.isPresent())
          {
            // err, they didn't give us an answer?
            log.error("2xx without required SDP offer, dropping.");
            return;
          }

          break;

        case ReceivedOffer:
          if (sdp.isPresent())
          {

            if (!this.preview)
            {
              // this shouldn't be done, but turns out most UAs do.
              log.debug("Ignoring SDP in 2xx response");
            }

          }

          // we've not yet sent an answer - must have got a preview of it in a 1xx.

          // TODO: right now, we'll never get here. implement delayed offer?

          break;

        case WaitingForAnswer:

          if (!sdp.isPresent())
          {
            log.warn("2xx without SDP, but waiting for answer.  Ignoring.");
            return;
          }
          this.remote = sdp.get();
          this.negstate = DialogNegotiationState.Negotiated;
          this.handle.answer(sdp.get());
          this.preview = false;
          break;

        case Negotiated:
          if (sdp.isPresent())
          {
            if (!this.preview)
            {
              // ignored, invalid to renegotiate over a 2xx.
              // shouldn't be done, but many UAs seem to send a copy in the 2xx.
              log.debug("Ignoring SDP in 2xx response");
            }
          }
          break;

      }

      this.state = BranchState.SUCCESS;

      // send the ACK.
      this.dialog.ack(res);

      if (EmbeddedClientCreator.this.winner == null)
      {

        // TODO: check we are in an appropriate state.
        EmbeddedClientCreator.this.winner = this;

        EmbeddedClientCreator.this.destroyAllOtherBranches(this);

        if (EmbeddedClientCreator.this.cancelled != null)
        {
          log.warn("Got 200 OK after being cancelled, fakeing completion and sending BYE");
          this.dialog.connected(this.local, this.remote);
          this.dialog.disconnect(new DisconnectEvent(
              ApiUtils.convert(EmbeddedClientCreator.this.cancelled.map(re -> re.asSipStatus().orElse(SipResponseStatus.OK))
                  .orElse(SipResponseStatus.OK.withReason("Cancelled Before OK was received")))));
          return;
        }

        // tell the consumer this dialog won.
        final Dialog dhandle = this.handle.dialog();

        if (dhandle == null)
        {
          this.dialog.connected(this.local, this.remote);
          this.dialog.disconnect(DisconnectEvent.CallCompletedElsewhere);
          return;
        }

        this.handle.accept(new OriginationBranchConnectEvent(ApiUtils.convert(e.getResponse())));

        Preconditions.checkArgument(dhandle != null);

        final Optional<SessionExpires> expires = res.getSessionExpires();

        if (expires.isPresent())
        {
          this.dialog.sessionRefresh(new SessionRefreshConfig(
              expires.get().getRefresher().map(val -> val == Refresher.Client).orElse(true),
              expires.get().getDuration()));
        }
        else
        {
          this.dialog.sessionRefresh(null);
        }

        this.dialog.connected(this.local, this.remote);

      }
      else
      {

        // another branch won, so sent a BYE on this one.
        this.dialog.disconnect(DisconnectEvent.CallCompletedElsewhere);

      }

    }

    /**
     * The consumer is requesting we terminate this specific branch. can only do this by sending a BYE directly.
     */

    @Override
    public void end(final DialogTerminationEvent e)
    {
      this.dialog.disconnect(DisconnectEvent.Unknown);
    }

  }

  private boolean retriedWithAuth = false;
  private List<Authorization> authContext;

  @Override
  public void onResponse(final SipTransactionResponseInfo e)
  {

    log.debug("Response code={}, branches={}, cancelled={}", e.getResponse().getStatus(), this.branches.size(), this.cancelled != null);

    // if we've not yet tried any authentication and this is a 407, resend. but only once for now.
    if (!this.retriedWithAuth && e.getResponse().getStatus().getCode() == 407 && this.options.getCredentials() != null && this.options.getCredentials().isEmpty() == false)
    {
      log.debug("*** Attempting with authentication");
      this.retriedWithAuth = true;
      this.authContext = e.getResponse().getProxyAuthenticate();
      this.send();
      return;
    }

    // dispatch the responses based on what we're getting from them.
    final SipResponse res = e.getResponse();

    if (res.getStatus().isFailure())
    {
      this.destroyAllBranches(new DisconnectEvent(ApiUtils.convert(res.getStatus())));
      this.handler.reject(ApiUtils.convert(res.getStatus()));
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

    Branch branch = this.branches.get(res.getToTag());

    if (branch == null)
    {

      if (res.getStatus().getCode() == 199)
      {
        // don't bother doing anything with it, we didn't even know it existed!
        return;
      }

      if (this.winner != null)
      {

        // should only be getting 2xx here. somethign wrong with the txn state machine.
        Preconditions.checkArgument(res.getStatus().isSuccess());

        // we've already completed, and this is now a response to a different branch which didn't
        // get the CANCEL in time. We destroy all the other dialogs immediatly internally on
        // winning, so there isn't one left for it. Rather than keep track of them we just ACK and
        // BYE unknown dialogs.
        log.info("Auto-ACKing res2xx on pre-won branch");
        HillbillyHelpers.ackAndBye(res, EmbeddedClientCreator.this.service);
        return;

      }

      if (this.cancelled != null)
      {
        log.info("Cancelled, but got {}", res.getStatus());
        // already cancelled, so just ignore it. if they respond with a 200, we'll handle it.
        HillbillyHelpers.ackAndBye(res, EmbeddedClientCreator.this.service);
        return;
      }

      log.debug("Creating dialog for branch {}", res.getToTag());

      // create a dialog for this branch

      final DialogId did = DialogId.fromRemote(res);

      final DialogInfo dinfo = new DialogInfo(
          ApiUtils.uri(this.options.getRequestUri()),
          this.getContact(),
          res.getContacts().get().iterator().next().getAddress(),
          res.getFrom(),
          res.getTo());

      final DialogState dstate = new DialogState(this.cseq, null);

      final EmbeddedDialog dialog = new EmbeddedDialog(
          DialogSide.UAC,
          this.service,
          did,
          dinfo,
          dstate,
          Lists.reverse(res.getRecordRoute()),
          e.getFlowId(),
          new RemoteSipSupport(res));

      // register the dialog so we receive in-dialog requests, and ensure it's unregistered once the
      // dialog is terminated.

      final DialogRegistrationHandle regh = this.service.register(did, dialog);

      dialog.addListener(new DialogListener() {

        @Override
        public void terminated()
        {
          regh.remove();
        }

      }, this.service.getExecutor());

      // create local state container

      branch = new Branch(dialog);

      // notify the consumer. we suspend reception until we get acknowledgement, so it better be
      // quick!

      final ClientSideEarlyDialog bhandle = this.handler.branch(branch, dialog);

      if (bhandle == null)
      {
        // TODO: terminate the dialog.
        log.warn("Terminating branch due to no handler");
        return;
      }

      // use the branch as the dialog handler until we're connected.
      dialog.setLocalDialog(bhandle.dialog());

      branch.handle = bhandle;

      // register it!
      this.branches.put(res.getToTag(), branch);

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

  @Override
  public void onError(final SipTransactionErrorInfo e)
  {
    // an error occured, so back out.
    log.warn("Error transmitting INVITE: {}", e);
    this.destroyAllBranches(new DisconnectEvent(ApiUtils.convert(SipResponseStatus.REQUEST_TIMEOUT)));
    this.handler.reject(ApiUtils.convert(SipResponseStatus.REQUEST_TIMEOUT));
  }

  void destroyAllBranches(final DisconnectEvent e)
  {

    for (final Branch branch : this.branches.values())
    {
      branch.dialog.inviteUsage.disconnected(e);
    }

    this.branches.clear();

  }

  void destroyAllOtherBranches(final Branch winner)
  {

    final Set<Branch> remove = Sets.newHashSet(this.branches.values());

    remove.remove(winner);

    for (final Branch branch : remove)
    {

      branch.dialog.inviteUsage.disconnected(DisconnectEvent.CallCompletedElsewhere);

      // ugly.
      for (final Entry<String, Branch> l : this.branches.entrySet())
      {
        if (l.getValue() == branch)
        {
          this.branches.remove(l.getKey());
          break;
        }
      }

    }

  }

  public NameAddr remoteIdentity()
  {
    return this.options.getTo() == null
        ? new NameAddr(ApiUtils.uri(this.options.getRequestUri()))
        : ApiUtils.convert(this.options.getTo()).withoutParameter(Token.from("tag"));
  }

  public NameAddr localIdentity()
  {
    return this.options.getFrom() == null
        ? new NameAddr("Anonymous", SipUri.ANONYMOUS)
        : ApiUtils.convert(this.options.getFrom());
  }

}
