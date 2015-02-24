package com.jive.hillbilly.client;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.UnsignedInteger;
import com.jive.hillbilly.Request;
import com.jive.hillbilly.SipStatus;
import com.jive.hillbilly.api.Address;
import com.jive.hillbilly.client.api.ClientSideCreator;
import com.jive.hillbilly.client.api.Dialog;
import com.jive.hillbilly.client.api.DialogListener;
import com.jive.hillbilly.client.api.DisconnectEvent;
import com.jive.hillbilly.client.api.IncomingInviteHandle;
import com.jive.hillbilly.client.api.ReferHandle;
import com.jive.hillbilly.client.api.ReferNotificationHandle;
import com.jive.hillbilly.client.api.RemoteNegotiatedSession;
import com.jive.hillbilly.client.api.RenegotiationHandle;
import com.jive.hillbilly.client.api.RequestedOfferHandle;
import com.jive.hillbilly.client.api.ServerSideCreator;
import com.jive.myco.commons.concurrent.PnkyPromise;
import com.jive.sip.base.api.RawHeader;
import com.jive.sip.base.api.Token;
import com.jive.sip.dummer.txn.ClientTransactionListener;
import com.jive.sip.dummer.txn.ClientTransactionOptions;
import com.jive.sip.dummer.txn.SipTransactionErrorInfo;
import com.jive.sip.dummer.txn.SipTransactionErrorInfo.ErrorCode;
import com.jive.sip.dummer.txn.SipTransactionResponseInfo;
import com.jive.sip.message.api.ActiveSubscriptionState;
import com.jive.sip.message.api.DialogId;
import com.jive.sip.message.api.NameAddr;
import com.jive.sip.message.api.SessionExpires;
import com.jive.sip.message.api.SessionExpires.Refresher;
import com.jive.sip.message.api.SipMethod;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponse;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.message.api.TerminatedSubscriptionState;
import com.jive.sip.processor.rfc3261.MutableSipRequest;
import com.jive.sip.processor.rfc3261.MutableSipResponse;
import com.jive.sip.processor.rfc3261.serializing.RfcSerializerManager;
import com.jive.sip.processor.rfc3261.serializing.RfcSerializerManagerBuilder;
import com.jive.sip.processor.uri.SipUriExtractor;
import com.jive.sip.transport.api.FlowId;
import com.jive.sip.uri.api.SipUri;

import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Core dialog implementation.
 *
 * A dialog can be terminated for one of a number of reasons:
 *
 * <ul>
 * <li>BYE received</li>
 * <li>API requested termination</li>
 * <li>Protocol error</li>
 * <li>REFER w/Target-Dialog, and Refer-To method=BYE</li>
 * <li>Media Timeout</li>
 * </ul>
 *
 * @author theo
 *
 */
@Slf4j
public class EmbeddedDialog implements Dialog, RemoteNegotiatedSession
{

  private static final RfcSerializerManager serializer = new RfcSerializerManagerBuilder().build();

  final DialogTransmissionManager txmit;
  private final DialogId id;
  private DialogInfo info;
  private DialogState state;
  private List<NameAddr> routeSet = Lists.newLinkedList();

  @Getter
  private final EmbeddedNetworkSegment network;

  enum State
  {

    /**
     * UAC: we've received a 1xx UAS: we've sent a 1xx
     */

    EARLY,

    /**
     * UAC: we've received a 2xx UAS: we've sent a 2xx
     */

    CONNECTED,

    /**
     * Dialog has terminated.
     */

    TERMINATED

  }

  @Getter
  private State status = State.EARLY;

  /**
   * Local side dialog, used for propagating events to it. This will be the creator until it's
   * marked at connected at which point the consumer takes over directly.
   *
   * Be careful when dispatching things to this - call on a separate thread but ensure we don't
   * dispatch multiple events at the same time.
   *
   */

  Dialog handle;

  private final RemoteSipSupport remote;
  private final DialogSide role;

  /**
   * Tiem of the last request being sent.
   */

  @Getter
  private Instant lastRequestSent;

  /**
   * Time of the last 2xx class response.
   */

  @Getter
  private Instant lastSuccessResponseSeen;

  /**
   * Time of the last response (including SIP failures).
   */

  @Getter
  private Instant lastResponseSeen;

  /**
   * Time we saw the last SIP request.
   */

  @Getter
  private Instant lastRequestSeen;

  /**
   * Time this dialog became connected.
   */

  @Getter
  private Instant connectTime;

  @Getter
  private final FlowId flow;

  Executor runtime;

  /**
   * dialog listeners.
   */

  private final Map<DialogListener, Executor> dlisteners = Maps.newLinkedHashMap();

  /**
   * INVITE usage state.
   *
   * Note: internal shared.
   */

  final EmbeddedInviteUsage inviteUsage = new EmbeddedInviteUsage(this);

  @Value
  private static class OutstandingRequest
  {
    SipRequest req;
  }

  private final Set<OutstandingRequest> outstanding = Sets.newHashSet();

  /**
   * If set, the local refresher.
   */

  private SessionRefresher refresher;

  /**
   * construct new dialog. many params, urgh.
   *
   * @param role
   * @param network
   * @param id
   * @param info
   * @param state
   * @param routeSet
   * @param model
   */

  EmbeddedDialog(
      final DialogSide role,
      final EmbeddedNetworkSegment network,
      final DialogId id,
      final DialogInfo info,
      final DialogState state,
      final List<NameAddr> routeSet,
      final FlowId flow,
      final RemoteSipSupport model)
      {
    this.txmit = new DialogTransmissionManager(network.getExecutor(), this);
    this.role = role;
    this.network = network;
    this.id = id;
    this.info = info;
    this.state = state;
    this.routeSet = routeSet;
    this.remote = model;
    this.flow = flow;

    // add listener to map events from the usage.
    this.inviteUsage.addListener(new InviteDialogUsageListener()
    {

      @Override
      public void disconnected(final DisconnectEvent e)
      {
        EmbeddedDialog.this.tryClose();
      }

    }, network.getExecutor());

      }

  public Instant getLastValidMessageFromRemote()
  {

    final Instant x = this.lastRequestSeen;
    final Instant y = this.lastSuccessResponseSeen;

    if (x == null)
    {
      return y;
    }

    if (y == null)
    {
      return x;
    }

    if (x.isAfter(y))
    {
      return x;
    }

    return y;

  }

  /**
   * true if the remote side supports REFER.
   */

  boolean canSendRefer()
  {
    return this.remote.allows(SipMethod.REFER);
  }

  public boolean canSendUpdate()
  {
    return this.remote.allows(SipMethod.UPDATE);
  }

  /**
   * Create a mutable sip request for sending within the dialog. allocates a sequence number.
   */

  MutableSipRequest createRequest(final SipMethod method)
  {

    final MutableSipRequest req = MutableSipRequest.create(method, this.info.getRemote());
    req.cseq(this.allocateSequence().longValue(), method);
    this.applyDialog(req);
    req.userAgent(this.network.getServerName());

    if (HillbillyHelpers.isDialogRefreshing(method))
    {
      if (this.refresher != null)
      {
        req.sessionExpires(
            this.refresher.getConfig().getExpires(),
            this.refresher.getConfig().isLocalRefresher() ? Refresher.Server : Refresher.Client);
      }
    }

    return req;
  }

  /**
   * create a response to send in this dialog.
   */

  MutableSipResponse createResponse(final SipRequest req, final SipResponseStatus status)
  {

    final MutableSipResponse res = MutableSipResponse.createResponse(req, status);

    if (req.getTo() != null)
    {
      res.to(req.getTo().withoutParameter(Token.from("tag")).withTag(this.id.getLocalTag()));
    }

    if (HillbillyHelpers.isDialogRefreshing(req.getMethod()))
    {
      res.contact(this.info.getLocal());
    }

    // Attempt to add a reasonable Record-Route step to the response
    res.recordRoute(req.getRecordRoute());

    res.server(this.network.getServerName());

    return res;

  }

  /**
   * Sets the dialog state for this request to be sent (To, From, and Call-ID).
   */

  void applyDialog(final MutableSipRequest req)
  {
    req.callId(this.id.getCallId().getValue());
    req.to(this.info.getRemoteName());
    req.from(this.info.getLocalName());
    req.route(this.routeSet);

  }

  /**
   * Allocates a new sequence number for sending a request to the remote side, and increments the
   * local sequence number.
   */

  private UnsignedInteger allocateSequence()
  {
    final UnsignedInteger seq = this.state.getLocalSequence() == null
        ? UnsignedInteger.valueOf(RandomUtils.nextInt(1, 2 ^ (31 - 1)))
            : this.state.getLocalSequence().plus(UnsignedInteger.ONE);
        this.state = this.state.withLocalSequence(seq);
        return seq;
  }

  /**
   * sends an in-dialog ACK for the given INVITE 2xx response.
   *
   * Note that this will only transmit a single ACK. Each 2xx response retransmit should call this
   * method.
   */

  public void ack(final SipResponse res)
  {

    final MutableSipRequest ack =
        MutableSipRequest.create(SipMethod.ACK, res.getContacts().get().iterator().next()
            .getAddress());

    // apply session refresh
    ack.contact(this.info.getLocal());
    ack.userAgent(this.network.getServerName());

    this.applyDialog(ack);

    ack.cseq(res.getCSeq().getSequence(), SipMethod.ACK);

    this.network.transmit(ack, this.flow, RandomStringUtils.randomAlphanumeric(32));

  }

  /**
   * API requested a REFER, send to the other side and arrange for notifications to be delivered.
   *
   * Throws if we've not received indication of supporting REFER (or it's been overriden on the
   * segment).
   *
   */

  @Override
  public void refer(final ReferHandle h)
  {

    Preconditions.checkState(this.canSendRefer());
    final MutableSipRequest refer = this.createRequest(SipMethod.REFER);
    refer.referTo(ApiUtils.convert(h.referTo()));
    refer.referredBy(ApiUtils.convert(h.referredBy().orElse(null)));

    this.send(refer, new ClientTransactionListener()
    {

      @Override
      public void onResponse(final SipTransactionResponseInfo res)
      {

        if (res.getResponse().getStatus().isFailure())
        {
          h.reject(ApiUtils.convert(res.getResponse().getStatus()));
        }
        else if (res.getResponse().getStatus().isSuccess())
        {
          final ReferNotificationHandle notifier = h.accept(ApiUtils.convert(res.getResponse().getStatus()));
          // TODO: create subscription handle.
          notifier.close();
        }

      }

      @Override
      public void onError(final SipTransactionErrorInfo err)
      {
        h.reject(ApiUtils.convert(SipResponseStatus.REQUEST_TIMEOUT));
      }

    });

  }

  /**
   * API requested disconnect.
   *
   * Although we immediately terminate the INVITE session, we stay around for a few seconds to
   * absorb any BYE requests sent by the remote side at the same time.
   *
   */

  @Override
  public void disconnect(final DisconnectEvent e)
  {
    log.debug("Hangup requested through API: {}", e);
    this.inviteUsage.sendBye(e);
  }

  /**
   * number of active usages.
   *
   * @return
   */

  int usages()
  {
    return this.inviteUsage.isActive() ? 1 : 0;
  }

  /**
   * terminates this transaction if there are no usages.
   */

  void tryClose()
  {

    if (this.status == State.TERMINATED)
    {
      return;
    }

    if (this.usages() > 0)
    {
      log.debug("Have active usages");
      // still some usages, so not yet.
      return;
    }

    this.status = State.TERMINATED;

    if (this.refresher != null)
    {
      this.refresher.stop();
      this.refresher = null;
    }

    // stop transmitting anything, no point anymore.
    this.txmit.stop();

    // notify listeners
    this.dlisteners.forEach((listener, ex) -> EatAndLog.run(ex, () -> listener.terminated()));

    // GC
    this.handle = null;
    this.dlisteners.clear();

  }

  /**
   * Processes an incoming transaction.
   */

  public void processRequest(final HillbillyServerTransaction itxn)
  {

    switch (this.status)
    {

      case TERMINATED:

        if (itxn.getRequest().getMethod().isBye())
        {
          log.debug("Absorbing BYE in already dead txn");
          // just consume it.
          itxn.respond(SipResponseStatus.OK);
          return;
        }

        log.warn("Got in-dialog request {} while terminated", itxn.getRequest().getMethod());
        itxn.respond(SipResponseStatus.CALL_DOES_NOT_EXIST.withReason("Dialog has been terminated"));
        return;

      case EARLY:

        if (itxn.getRequest().getMethod().isInvite())
        {
          log.warn("Rejecting INVITE while in early");
          // we can't have another INVITE while in EARLY.
          itxn.respond(SipResponseStatus.SERVICE_UNAVAILABLE.withReason("Invalid state for reINVITE"));
          return;
        }
        break;

      case CONNECTED:
      default:
        break;
    }

    final HillbillyServerTransaction txn = new DialogWrappedTransaction(this, itxn);

    log.debug("in-dialog request: {} from {}", txn.getRequest(), txn.getFlowId());

    final SipRequest req = txn.getRequest();

    if (req.getCSeq() == null)
    {
      log.error("Ignoring request with missing CSeq");
      txn.respond(SipResponseStatus.BAD_REQUEST.withReason("Missing CSeq"));
      return;
    }

    if (this.state.getRemoteSequence() != null)
    {
      if (UnsignedInteger.valueOf(req.getCSeq().longValue()).compareTo(this.state.getRemoteSequence()) <= 0)
      {
        log.warn("Dropping out of order request {} <= {}", req.getCSeq().getSequence(), this.state.getRemoteSequence());
        txn.respond(SipResponseStatus.SERVER_INTERNAL_ERROR.withReason("CSeq out of order"));
        return;
      }
    }

    this.state = this.state.withRemoteSequence(UnsignedInteger.valueOf(req.getCSeq().longValue()));

    this.lastRequestSeen = this.now();

    switch (req.getMethod().getMethod())
    {

      case "INVITE":
        this.inviteUsage.processIncomingReinvite(txn);
        break;

      case "UPDATE":
        this.inviteUsage.processIncomingUpdate(txn);
        break;

      case "BYE":
        this.inviteUsage.processBye(txn);
        break;

      case "REFER":
        // we're being referred somewhere.
        this.processIncomingRefer(txn);
        break;

      case "PRACK":
        // our provisional response got acknoleged.
        this.inviteUsage.processIncomingPrack(txn);
        break;

      case "INFO":
        // TODO: in-band DTMF. pfffft.
        txn.respond(SipResponseStatus.METHOD_NOT_ALLOWED);
        break;

      case "NOTIFY":
        // TODO: used for the implicit subscription created by a REFER.
        txn.respond(SipResponseStatus.METHOD_NOT_ALLOWED);
        break;

      default:
        // unknown
        log.warn("Unknown SIP method in request: {}", req.getMethod());
        txn.respond(SipResponseStatus.METHOD_NOT_ALLOWED);
        break;

    }

  }

  /**
   * API consumer hung up.
   */

  private void hangup(final boolean shouldSendBye)
  {

    if (this.status == State.TERMINATED)
    {
      // nothign to do.
      return;
    }

    this.status = State.TERMINATED;

    if (this.handle != null)
    {
      this.handle.disconnect(DisconnectEvent.ClientFailed);
    }

  }

  /**
   * Pass the ACK to the txmitter so it can stop sending 200s.
   */

  public void processAck(final SipRequest req, final FlowId flow)
  {
    this.txmit.process(req, flow);
  }

  /**
   * Handles an incoming REFER.
   *
   * @param txn
   */

  private void processIncomingRefer(final HillbillyServerTransaction txn)
  {

    if (!txn.getRequest().getReferTo().isPresent())
    {
      txn.respond(SipResponseStatus.BAD_REQUEST.withReason("Refer-To missing"));
      return;
    }

    final String id = RandomStringUtils.randomAlphanumeric(12);

    final ReferHandle h = new ReferHandle()
    {

      @Override
      public java.util.Optional<Address> referredBy()
      {
        return Optional.ofNullable(ApiUtils.convert(txn.getRequest().getReferTo().orElse(null)));
      }

      @Override
      public Address referTo()
      {
        return ApiUtils.convert(txn.getRequest().getReferTo().orElse(null));
      }

      @Override
      public ReferNotificationHandle accept(final SipStatus status)
      {

        txn.respond(ApiUtils.convert(status));

        // now create a NOTIFY schedule.

        return new ReferNotificationHandle()
        {

          @Override
          public void update(final SipStatus status)
          {
            log.debug("Got notification about REFER {}", status);
            final MutableSipRequest notify = EmbeddedDialog.this.createRequest(SipMethod.NOTIFY);
            notify.event("refer", id);
            if (status.isFinal())
            {
              notify.subscriptionState(new TerminatedSubscriptionState());
            }
            else
            {
              notify.subscriptionState(new ActiveSubscriptionState(Duration.ofMinutes(5)));
            }
            notify.body("message/sipfrag", String.format("SIP/2.0 %s", status.toString()));
            EmbeddedDialog.this.send(notify, null);
          }

          @Override
          public void close()
          {
            log.debug("No more notifications about REFER");
          }

        };
      }

      @Override
      public void reject(final SipStatus status)
      {
        txn.respond(ApiUtils.convert(status));
      }

    };

    this.handle.refer(h);

  }

  /**
   * Transmits a provided 2xx response until the associated ACK is received.
   *
   * @return
   */

  public PnkyPromise<SipRequest> transmit(final HillbillyServerTransaction txn, final SipResponse res2xx)
  {
    Preconditions.checkArgument(res2xx.getStatus().isSuccess());
    return this.txmit.add(txn, res2xx);
  }

  /**
   * send the given response reliably, retransmitting it until we get a PRACK.
   */

  public void respondReliably(final HillbillyServerTransaction txn, final SipResponse res)
  {
    Preconditions.checkArgument(!res.getStatus().isFinal());
    Preconditions.checkArgument(res.getStatus().getCode() != 100);
    this.txmit.add(txn, res);
  }

  /**
   * returns true if there is an outstanding request of the given method.
   */

  boolean hasOutstandingRequest(final SipMethod method)
  {
    return this.outstanding.stream().filter(o -> o.getReq().getMethod().equals(method)).findAny().isPresent();
  }

  /**
   * Sends a SIP request to the remote side, soliciting a response.
   */

  public void send(final MutableSipRequest req, final ClientTransactionListener listener)
  {

    this.lastRequestSent = this.now();

    final SipRequest sreq = req.build(this.network.messageManager());

    log.debug("Sending {} ({} outstanding txns)", sreq.getMethod(), this.outstanding.size());

    final OutstandingRequest out = new OutstandingRequest(sreq);

    this.outstanding.add(out);

    try
    {
      // in-dialog stays at 32 seconds.
      this.network.sendInvite(
          sreq,
          this.flow,
          ClientTransactionOptions.DEFAULT,
          new DispatchingClientTransactionListener(this.network.getExecutor(), new ClientTransactionListener()
          {

            @Override
            public void onResponse(final SipTransactionResponseInfo arg0)
            {

              if (arg0.getResponse().getStatus().isFinal())
              {
                EmbeddedDialog.this.outstanding.remove(out);
              }

              log.debug("Got SIP response for {}: {}", sreq.getMethod(), arg0.getResponse());

              EmbeddedDialog.this.lastResponseSeen = EmbeddedDialog.this.now();

              final SipResponseStatus status = arg0.getResponse().getStatus();

              if (status.isSuccess())
              {
                EmbeddedDialog.this.lastSuccessResponseSeen = EmbeddedDialog.this.now();
                EmbeddedDialog.this.refreshLocallyInitiated(sreq, arg0.getResponse());
              }
              else if (status.isFailure())
              {
                switch (status.getCode())
                {
                  case 430: // flow failed
                  case 481: // call leg does not exist
                  case 404: // not found
                    EmbeddedDialog.this.hangup(false);
                    break;
                  case 482: // loop detected
                  case 483: // too many hops
                    EmbeddedDialog.this.hangup(true);
                    break;
                  default:
                    break;
                }

              }

              if (listener != null)
              {

                listener.onResponse(arg0);

              }

            }

            @Override
            public void onError(final SipTransactionErrorInfo arg0)
            {

              log.debug("Got error sending in-dialog: {}", arg0.getCode());

              EmbeddedDialog.this.outstanding.remove(out);

              EmbeddedDialog.this.hangup(false);

              if (listener != null)
              {
                listener.onError(arg0);
              }

            }

          }));
    }
    catch (final Exception ex)
    {
      log.error("Failed to send request", ex);
      this.outstanding.remove(out);
      if (listener != null)
      {
        listener.onError(new SipTransactionErrorInfo(ErrorCode.Timeout));
      }
    }

  }

  /**
   * Sends an offer to the other side using whatever mechanism we support in this state.
   */

  @Override
  public void answer(final String offer, final RenegotiationHandle session)
  {

    log.debug("Sending SDP offer to remote side");

    final boolean update = this.remote.allows(SipMethod.UPDATE);

    final MutableSipRequest req = this.createRequest(update ? SipMethod.UPDATE : SipMethod.INVITE);

    req.body("application/sdp", offer);

    this.send(req, new ClientTransactionListener()
    {

      boolean received = false;

      @Override
      public void onResponse(final SipTransactionResponseInfo e)
      {

        if (!e.getResponse().getStatus().isFinal())
        {
          // TODO: handle early?
          // TODO: 100rel
          return;
        }

        if (e.getResponse().getStatus().isSuccess())
        {
          if (!update)
          {
            EmbeddedDialog.this.ack(e.getResponse());
          }
          if (!this.received)
          {
            this.received = true;
            // todo: freak out if we're not expecting here?
            final String answer = HillbillyHelpers.getSessionDescription(e.getResponse()).get();
            session.answer(answer);
          }
        }
        else
        {
          session.reject();
        }

      }

      @Override
      public void onError(final SipTransactionErrorInfo e)
      {
        session.reject();
      }

    });

  }

  /**
   * Sends an INVITE without SDP, and when the offer is received in the 200, passed to the handle to
   * get an answer.
   */

  @Override
  public void requestOffer(final RequestedOfferHandle handle)
  {
    throw new NotImplementedException("UAC delayed reINVITE not yet supported.");
  }

  void setLocalDialog(final Dialog dhandle)
  {
    Preconditions.checkState(this.handle == null);
    Preconditions.checkState(this.handle != this);
    this.handle = Preconditions.checkNotNull(dhandle);
  }

  /**
   * called when the initial creator has become connected, either because we received a 2xx (UAC),
   * or sent one (UAS).
   *
   * note that the consumer may be null.
   *
   * @param handle2
   *
   */

  void connected(final String local, final String remote)
  {
    Preconditions.checkState(this.handle != null, "requrire dialog handle");
    this.status = State.CONNECTED;
    this.connectTime = this.now();
    log.debug("Dialog became connected");
    this.inviteUsage.connected(local, remote);
    if (this.refresher != null)
    {
      this.refresher.start();
    }
  }

  /**
   * Sends a PRACK.
   *
   * We only send for the first instance of the sequence we see. Any further instances will be
   * handled by the SIP layer retransmit mechanism.
   *
   * Note that there may be an offer or answer in this response.
   *
   */

  public void prack(final SipResponse provisional)
  {

    final MutableSipRequest prack = this.createRequest(SipMethod.PRACK);

    prack.rack(provisional.getRSeq().get(), provisional.getCSeq());

    this.send(prack, new ClientTransactionListener()
    {

      @Override
      public void onResponse(final SipTransactionResponseInfo res)
      {
        // TODO Auto-generated method stub
      }

      @Override
      public void onError(final SipTransactionErrorInfo err)
      {
        // TODO Auto-generated method stub
      }

    });
  }

  /**
   * handle an incoming REFER w/Target-Dialog.
   */

  public void processTargettedRefer(final ReferHandle h)
  {

    final SipUri uri = ApiUtils.convert(h.referTo()).getAddress().apply(SipUriExtractor.getInstance());

    if (uri != null)
    {

      final SipRequest refer = this.network.messageManager().fromUri(uri);

      if (refer.getMethod().isBye())
      {
        // trigger a hangup.
        // TODO: should we validate URI?
        log.debug("Hanging up due to REFER w/Target-Dialog method=BYE");
        this.disconnect(DisconnectEvent.ApiHangup);
        if (this.handle != null)
        {
          this.handle.disconnect(DisconnectEvent.OK);
        }
        h.accept(ApiUtils.convert(SipResponseStatus.OK)).close();
        return;
      }
      else if (!refer.getMethod().isInvite())
      {
        h.reject(ApiUtils.convert(SipResponseStatus.NOT_IMPLEMENTED));
        return;
      }

    }

    if (this.handle == null)
    {
      h.reject(ApiUtils.convert(SipResponseStatus.SERVICE_UNAVAILABLE.withReason("No handler")));
      return;
    }

    // do we need more authorization?
    this.handle.refer(h);

  }

  /**
   * Process incoming INVITE with a Replaces which references this dialog.
   *
   * We don't actually do anything ourselves except pass to the API consumer to provide us with an
   * SDP answer (or offer), and create a dialog from it, skipping all the funky parts of a normal
   * INVITE.
   *
   */

  public void processReplaces(final EmbeddedServerCreator uas)
  {
    log.info("Dialog received INVITE w/Replaces referencing this dialog, passing to handler.");
    final SipRequest req = uas.getTxn().getRequest();
    this.handle.replaceWith(new IncomingInviteHandle()
    {

      @Override
      public String uri()
      {
        return ApiUtils.convert(req.getUri());
      }

      @Override
      public Address remoteIdentity()
      {
        return ApiUtils.convert(req.getFrom());
      }

      @Override
      public ClientSideCreator process(final ServerSideCreator creator)
      {

        if (creator == null)
        {
          log.warn("createServer() returned null handler, rejecting incoming INVITE w/Replaces");
          uas.reject(ApiUtils.convert(SipResponseStatus.SERVER_INTERNAL_ERROR));
          return null;
        }

        uas.process(creator);

        uas.getTxn().respond(SipResponseStatus.TRYING);

        return uas;

      }

      @Override
      public String offer()
      {
        return HillbillyHelpers.getSessionDescription(req).orElse(null);
      }

      @Override
      public String netns()
      {
        return uas.getService().getId();
      }

      @Override
      public Address localIdentity()
      {
        return ApiUtils.convert(req.getTo());
      }

      @Override
      public ClientSideCreator client()
      {
        return uas;
      }

      @Override
      public Request invite()
      {
        return ApiUtils.convert(req);
      }

    });
  }

  /**
   * refresh based on a positive response we're about to send, giving us the opertunity to fix up
   * the response.
   */

  public SipResponse refreshRemoteInitiated(final SipRequest req, SipResponse res)
  {

    if (HillbillyHelpers.isDialogRefreshing(req.getMethod()))
    {

      this.info = DialogInfo.fromRemoteInitiated(req, new NameAddr(this.info.getLocal()));
      //this.routeSet = req.getRecordRoute();

      final SessionRefreshConfig se;

      if (req.getSessionExpires().isPresent())
      {
        final SessionExpires seh = req.getSessionExpires().get();
        se = new SessionRefreshConfig(seh.getRefresher().orElse(Refresher.Server) == Refresher.Client, seh.getDuration());
      }
      else
      {
        se = null;
      }

      this.sessionRefresh(se);

      if (this.refresher != null)
      {
        final SessionRefreshConfig resolved = this.refresher.getConfig();
        final SessionExpires value = new SessionExpires(resolved.getExpires(), resolved.isLocalRefresher() ? Refresher.Client : Refresher.Server);
        res = (SipResponse) res.withHeader(new RawHeader("Session-Expires", serializer.serialize(value)));
      }

    }

    return res;
  }

  public void refreshLocallyInitiated(final SipRequest req, final SipResponse res)
  {

    if (HillbillyHelpers.isDialogRefreshing(req.getMethod()))
    {

      this.info =
          new DialogInfo(
              this.info.getTarget(),
              this.info.getLocal(),
              res.getContacts().get().iterator().next().getAddress(),
              this.info.getLocalName(),
              this.info.getRemoteName()
              );

      //this.routeSet = Lists.reverse(Lists.newLinkedList(res.getRecordRoute()));

      // apply result
      final SessionRefreshConfig se;

      if (res.getSessionExpires().isPresent())
      {
        final SessionExpires seh = res.getSessionExpires().get();
        se = new SessionRefreshConfig(seh.getRefresher().orElse(Refresher.Server) == Refresher.Server, seh.getDuration());
      }
      else
      {
        se = null;
      }

      this.sessionRefresh(se);

    }

  }

  @Override
  public void replaceWith(final IncomingInviteHandle h)
  {
    throw new RuntimeException("Not Implemented");
  }

  /**
   * sets the configured session refresh configuration. replaces any previous configuration.
   *
   * if null, disabled the negotiated session refreshing. we're on our own then!
   *
   */

  public void sessionRefresh(final SessionRefreshConfig config)
  {

    if (config == null)
    {
      // disable.
      if (this.refresher != null)
      {
        this.refresher.stop();
        this.refresher = null;
      }
    }
    else
    {
      if (this.refresher != null)
      {
        this.refresher.update(config);
      }
      else
      {
        this.refresher = new SessionRefresher(this, config);
        if (this.connectTime != null)
        {
          this.refresher.start();
        }
      }
    }

  }

  public void addListener(final DialogListener listener, final Executor executor)
  {
    this.dlisteners.put(listener, executor);
  }

  private Instant now()
  {
    return this.network.getClock().instant();
  }

}
