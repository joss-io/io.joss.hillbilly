package com.jive.hillbilly.client;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.jive.hillbilly.SipWarning;
import com.jive.hillbilly.client.api.DisconnectEvent;
import com.jive.hillbilly.client.api.RenegotiationHandle;
import com.jive.hillbilly.client.api.RequestedOfferHandle;
import com.jive.hillbilly.client.api.RequiredAnswerHandle;
import com.jive.myco.commons.concurrent.PnkyPromise;
import com.jive.sip.dummer.txn.ClientTransactionListener;
import com.jive.sip.dummer.txn.SipTransactionErrorInfo;
import com.jive.sip.dummer.txn.SipTransactionResponseInfo;
import com.jive.sip.message.api.ContentDisposition;
import com.jive.sip.message.api.Reason;
import com.jive.sip.message.api.SipMethod;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.processor.rfc3261.MutableSipRequest;
import com.jive.sip.processor.rfc3261.MutableSipResponse;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EmbeddedInviteUsage
{

  /**
   *
   */

  enum State
  {
    // not yet established negotiation
    Trying,
    // negotiation established, but not connected.
    Early,
    // negotiated and connected.
    Connected,
    // disconnected
    Disconnected
  }

  private State state = State.Early;

  /**
   *
   */

  @Getter
  private final EmbeddedDialog dialog;

  /**
   * INVITE usage listeners.
   */

  private final Map<InviteDialogUsageListener, Executor> listeners = Maps.newLinkedHashMap();

  private String local;

  private String remote;

  public EmbeddedInviteUsage(final EmbeddedDialog dialog)
  {
    this.dialog = dialog;
  }

  /**
   * Processes an incoming reINVITE.
   */

  void processIncomingReinvite(final HillbillyServerTransaction xtxn)
  {

    log.debug("Got reINVITE");

    final AtomicServerRequest txn = new AtomicServerRequest(this.dialog, xtxn);

    final SipRequest req = xtxn.getRequest();

    if (this.dialog.handle == null)
    {
      log.warn("Attempted reINVITE without negotiation session");
      txn.respond(SipResponseStatus.SERVER_INTERNAL_ERROR
          .withReason("Negotiation Not Yet Complete"));
      return;
    }

    // send a trying to keep them happy.
    xtxn.respond(SipResponseStatus.TRYING);

    // TODO: only trigger if the offer hasn't changed.

    // we pass the reINVITE to the negotiation handler.
    final Optional<String> offer = HillbillyHelpers.getSessionDescription(req);

    if (!offer.isPresent())
    {

      log.debug("Received reINVITE with delayed offer");

      // they're asking for an offer.
      this.dialog.handle.requestOffer(new RequestedOfferHandle()
      {

        @Override
        public void rejected()
        {
          txn.respond(SipResponseStatus.NOT_ACCEPTABLE_HERE);
        }

        @Override
        public void answer(final String offer, final RequiredAnswerHandle session)
        {

          // send this as the 2xx. The ACK will contain the SDP answer.

          final MutableSipResponse res = EmbeddedInviteUsage.this.dialog.createResponse(req, SipResponseStatus.OK);

          res.body("application/sdp", offer);

          // transmit until we get an ACK.

          final PnkyPromise<SipRequest> ack =
              EmbeddedInviteUsage.this.dialog.transmit(xtxn, res.build(EmbeddedInviteUsage.this.dialog.getNetwork().messageManager()));

          txn.apply();

          ack.addListener(() ->
          {

            final SipRequest mack;

            try
            {
              mack = ack.get();
            }
            catch (final Exception ex)
            {
              // timed out, ABORT ABORT ABORT!
              log.info("2xx reINVITE transmission timeout");
              // TODO: kill dialog due to error?
              return;
            }

            // now, if there is no SDP answer, then abort.

            final Optional<String> sdp = HillbillyHelpers.getSessionDescription(mack);

            if (!sdp.isPresent())
            {
              // signalling failure, remote side should have given us an SDP answer.
              // TODO: kill dialog due to error, as negotiation state is all messed up.
              return;
            }

            session.answer(sdp.get());

            EmbeddedInviteUsage.this.remote = offer;
            EmbeddedInviteUsage.this.local = sdp.get();

          }, EmbeddedInviteUsage.this.dialog.runtime);

        }

      });

    }
    else
    {

      this.dialog.handle.answer(offer.get(), new RenegotiationHandle()
      {

        @Override
        public void reject(final List<SipWarning> warnings)
        {
          final MutableSipResponse res = MutableSipResponse.createResponse(xtxn.getRequest(), SipResponseStatus.NOT_ACCEPTABLE_HERE);
          if (warnings != null)
          {
            res.warning(
                ApiUtils.convert(warnings).stream()
                .map(e -> e.withAgent(EmbeddedInviteUsage.this.dialog.getNetwork().getSelf().toString()))
                .collect(Collectors.toList()));
          }
          txn.respond(res);
        }

        @Override
        public void answer(final String answer)
        {


          final MutableSipResponse res = EmbeddedInviteUsage.this.dialog.createResponse(req, SipResponseStatus.OK);
          res.body("application/sdp", answer);

          // transmit until we get an ACK.
          EmbeddedInviteUsage.this.dialog.transmit(xtxn, res.build(EmbeddedInviteUsage.this.dialog.getNetwork().messageManager()));
          txn.apply();

          EmbeddedInviteUsage.this.local = answer;
          EmbeddedInviteUsage.this.remote = offer.get();
          // TODO: Relatch here?
        }

      });

    }

  }

  void processIncomingUpdate(final HillbillyServerTransaction xtxn)
  {

    log.debug("Received UPDATE");
    final SipRequest req = xtxn.getRequest();
    final AtomicServerRequest txn = new AtomicServerRequest(this.dialog, xtxn);

    // if there is an SDP offer, we need to process the offer/answer.

    final Optional<String> offer = HillbillyHelpers.getSessionDescription(xtxn.getRequest());

    if (!offer.isPresent())
    {
      log.debug("UPDATE without SDP.  Returning 200 OK.");
      txn.respond(SipResponseStatus.OK);
      return;
    }

    this.dialog.handle.answer(offer.get(), new RenegotiationHandle()
    {

      @Override
      public void reject(final List<SipWarning> warnings)
      {
        final MutableSipResponse res = MutableSipResponse.createResponse(xtxn.getRequest(), SipResponseStatus.NOT_ACCEPTABLE_HERE);
        if (warnings != null)
        {
          res.warning(ApiUtils.convert(warnings).stream().map(e -> e.withAgent(EmbeddedInviteUsage.this.dialog.getNetwork().getSelf().toString())).collect(Collectors.toList()));
        }
        txn.respond(res);
      }

      @Override
      public void answer(final String answer)
      {
        final MutableSipResponse res = EmbeddedInviteUsage.this.dialog.createResponse(req, SipResponseStatus.OK);
        res.body("application/sdp", answer);
        txn.respond(res);
        EmbeddedInviteUsage.this.local = answer;
        EmbeddedInviteUsage.this.remote = offer.get();
      }

    });

  }

  /**
   * stop the timer for a provisional retransmit.
   *
   * Note the PRACK may have an offer or answer in it.
   *
   */

  void processIncomingPrack(final HillbillyServerTransaction xxtxn)
  {

    final SipRequest req = xxtxn.getRequest();

    if (!req.getRAck().isPresent())
    {
      log.warn("PRACK without RAck");
      return;
    }

    final AtomicServerRequest txn = new AtomicServerRequest(this.dialog, xxtxn);

    if (this.dialog.txmit.processPrack(xxtxn))
    {

      final Optional<String> offer = HillbillyHelpers.getSessionDescription(req);

      if (offer.isPresent())
      {

        log.debug("PRACK has SDP (offer?)");

        this.dialog.handle.answer(offer.get(), new RenegotiationHandle()
        {

          @Override
          public void reject(final List<SipWarning> warnings)
          {
            log.debug("Rejecting SDP offer in PRACK: {}", warnings);
            final MutableSipResponse res = MutableSipResponse.createResponse(req, SipResponseStatus.NOT_ACCEPTABLE_HERE);
            if (warnings != null)
            {
              res.warning(ApiUtils.convert(warnings).stream().map(e -> e.withAgent(EmbeddedInviteUsage.this.dialog.getNetwork().getSelf().toString()))
                  .collect(Collectors.toList()));
            }
            txn.respond(res);
          }

          @Override
          public void answer(final String answer)
          {
            log.debug("Sending SDP answer in PRACK response");
            final MutableSipResponse res = EmbeddedInviteUsage.this.dialog.createResponse(req, SipResponseStatus.OK);
            res.body("application/sdp", answer);
            txn.respond(res);
            EmbeddedInviteUsage.this.local = answer;
            EmbeddedInviteUsage.this.remote = offer.get();
          }
        });

      }
      else
      {
        txn.respond(SipResponseStatus.OK);
      }

    }
    else
    {
      txn.respond(SipResponseStatus.SERVER_INTERNAL_ERROR.withReason("Unexpected PRACK"));
    }

  }

  /**
   * Sends a BYE, and moves the usage into a disconnected state.
   *
   * @param e
   */

  void sendBye(final DisconnectEvent e)
  {

    // shouldn't ever send one if we're disconnected.

    if (this.state == State.Disconnected)
    {
      log.error("Tried to send BYE in state disconnected");
      return;
    }

    final MutableSipRequest req = this.dialog.createRequest(SipMethod.BYE);

    req.reason(Reason.fromSipStatus(ApiUtils.convert(e.getStatus())));

    // listeners.dispatch(l -> l.disconnected(new LocallyRequestedDialogTerminationEvent(reason)));

    this.dialog.send(req, new ClientTransactionListener()
    {

      @Override
      public void onResponse(final SipTransactionResponseInfo e)
      {
        log.debug("Removing");
      }

      @Override
      public void onError(final SipTransactionErrorInfo e)
      {
        log.debug("Error terminating session with BYE");
      }

    });

    this.disconnected(e);

  }

  void processBye(final HillbillyServerTransaction btxn)
  {

    final AtomicServerRequest txn = new AtomicServerRequest(this.dialog, btxn);

    // Call is terminated. May need to remain active if we have an active subscription.

    log.info("Dialog terminated by remote initiated BYE");

    txn.respond(SipResponseStatus.OK);

    if (this.dialog.handle != null)
    {
      this.dialog.handle.disconnect(DisconnectEvent.OK);
    }

    this.disconnected(DisconnectEvent.OK);

  }

  /**
   * Notifies listeners the usage has disconnected and clears internal state. Doesn't send any SIP
   * messages, nor notify the handler.
   */

  void disconnected(final DisconnectEvent e)
  {

    log.debug("INVITE usage disconnected: {}", e);
    this.state = State.Disconnected;
    // callback to each listener.

    this.listeners.forEach((l, exec) -> EatAndLog.run(exec, () -> l.disconnected(e)));

    this.listeners.clear();

  }

  /**
   * returns true if this usage is currently active, e.g, not terminated.
   */

  public boolean isActive()
  {
    return this.state != State.Disconnected;
  }

  public void addListener(final InviteDialogUsageListener listener, final Executor executor)
  {
    this.listeners.put(listener, executor);
  }

  /**
   * Attempt to perform a refresh.
   *
   * If the remote side supports UPDATE, then we send that without any SDP to avoid renegotiating.
   *
   * If they don't, then we send an offer and provide the response (if it's changed).
   *
   */

  public void refresh()
  {

    if (this.dialog.canSendUpdate())
    {
      // send an UPDATE, assuming the remote side supports it. far better.
      final MutableSipRequest req = this.dialog.createRequest(SipMethod.UPDATE);
      this.dialog.send(req, null);
    }
    else
    {

      log.debug("Sending reINVITE w/SDP offer for refresh");

      // they don't support UPDATE, so we need to send with our SDP. send with our current local
      // SDP, and pass the
      // answer back.

      this.dialog.handle.requestOffer(new RequestedOfferHandle()
      {

        @Override
        public void rejected()
        {
          // err, hmm?
          log.error("Tried to perform a session refresh, but request for offer was rejected.");
        }

        @Override
        public void answer(final String offer, final RequiredAnswerHandle session)
        {
          Preconditions.checkArgument(offer != null);
          final MutableSipRequest req = EmbeddedInviteUsage.this.dialog.createRequest(SipMethod.INVITE);
          req.body("application/sdp", offer, ContentDisposition.SessionRequired);
          EmbeddedInviteUsage.this.dialog.send(req, new HandleInviteResponse(EmbeddedInviteUsage.this, offer, session));
        }

      });

    }

  }

  /**
   * usage has become connected, this is the current negotiation local/remote SDP.
   */

  public void connected(final String local, final String remote)
  {
    this.negotiated(local, remote);
  }

  public void negotiated(final String local, final String remote)
  {
    this.local = local;
    this.remote = remote;
    log.debug("Negotiated with local and remote");
  }

}
