package com.jive.hillbilly.client;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.DispatchQueue;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.net.HostAndPort;
import com.jive.sip.parameters.api.RawParameter;
import com.jive.sip.parameters.api.TokenParameterValue;
import com.jive.sip.parameters.impl.DefaultParameters;
import com.jive.hillbilly.Request;
import com.jive.hillbilly.SipStatus;
import com.jive.hillbilly.api.Address;
import com.jive.hillbilly.client.api.ClientSideCreator;
import com.jive.hillbilly.client.api.HillbillyHandler;
import com.jive.hillbilly.client.api.IncomingInviteHandle;
import com.jive.hillbilly.client.api.OptionsHandle;
import com.jive.hillbilly.client.api.ReferHandle;
import com.jive.hillbilly.client.api.ReferNotificationHandle;
import com.jive.hillbilly.client.api.ServerSideCreator;
import com.jive.sip.base.api.Token;
import com.jive.sip.dummer.txn.ClientTransactionListener;
import com.jive.sip.dummer.txn.ClientTransactionOptions;
import com.jive.sip.dummer.txn.InviteServerTransactionHandler;
import com.jive.sip.dummer.txn.ServerTransactionHandle;
import com.jive.sip.dummer.txn.SipClientTransaction;
import com.jive.sip.dummer.txn.SipStack;
import com.jive.sip.message.api.DialogId;
import com.jive.sip.message.api.Replaces;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponse;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.message.api.Via;
import com.jive.sip.message.api.ViaProtocol;
import com.jive.sip.message.api.headers.MIMEType;
import com.jive.sip.processor.rfc3261.MutableSipRequest;
import com.jive.sip.processor.rfc3261.MutableSipResponse;
import com.jive.sip.processor.rfc3261.SipMessageManager;
import com.jive.sip.processor.uri.SipUriExtractor;
import com.jive.sip.transport.api.FlowId;
import com.jive.sip.transport.udp.ListenerId;
import com.jive.sip.transport.udp.UdpFlowId;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EmbeddedSipSegment implements EmbeddedNetworkSegment, InviteServerTransactionHandler
{

  /**
   * Number of dialogs allowed before we start rejecting new incoming dialogs on a specific segment.
   */

  private final DispatchQueue queue = Dispatch.createQueue("segment");
  private final SipStack stack;
  private final Map<DialogId, EmbeddedDialog> dialogs = Maps.newHashMap();
  private final ListenerId udpListener = new ListenerId(0);
  private final HillbillyHandler handler;
  private final HillbillyRequestEnforcer enforcer;

  private String serverName;

  @Getter
  private final String id;
  private final ScheduledExecutorService executor;
  private final EmbeddedHillbillySipService hillbilly;

  EmbeddedSipSegment(final EmbeddedHillbillySipService hillbilly, final String id, final SipStack stack,
      final HillbillyHandler handler, final ScheduledExecutorService executor, final boolean support100rel)
  {
    this.hillbilly = hillbilly;
    this.id = id;
    this.stack = stack;
    this.handler = handler;
    this.executor = executor;
    this.enforcer = new HillbillyRequestEnforcer(support100rel);
  }
  
  @Override
  public SipStack stack() {
    return this.stack;
  }

  @Override
  public SipMessageManager messageManager()
  {
    return this.stack.getMessageManager();
  }

  public void setServerName(final String name)
  {
    this.serverName = name;
    this.stack.setServerName(this.serverName);
  }

  @Override
  public String getServerName()
  {
    return this.serverName;
  }

  @Override
  public HostAndPort getSelf()
  {
    return HostAndPort.fromString(this.stack.getSelf().toString());
  }

  @Override
  public DialogRegistrationHandle register(final DialogId id, final EmbeddedDialog d)
  {

    this.dialogs.put(id, d);

    log.info("[{}] Registered dialog {} (count={})", this.id, id, this.dialogs.size());

    return () -> {
      if (this.dialogs.remove(id) == null)
      {
        log.error("Failed to find dialog to remove in {}, id={}", this.id, id);
      }
      log.info("[{}] Unregistered dialog {} (size={})", this.id, id, this.dialogs.size());
    };

  }

  @Override
  public SipClientTransaction sendInvite(
      final SipRequest invite,
      final FlowId flowId,
      final ClientTransactionOptions opts,
      final ClientTransactionListener listener)
  {

    final UdpFlowId nextHop = this.getNextHop(invite, flowId);

    return this.stack.send(
        invite,
        nextHop,
        listener,
        opts);

  }

  @Override
  public void transmit(final MutableSipRequest req, final FlowId flowId, final String branchId)
  {

    // this is hardly ideal, but SIP makes it messy. boo.
    final List<RawParameter> params = Lists.newLinkedList();
    params.add(new RawParameter(Via.BRANCH, new TokenParameterValue(branchId)));
    final Via via = new Via(
        ViaProtocol.UDP,
        this.getSelf().toString().toString(), 
        DefaultParameters.from(params));
    req.via(via);

    final SipRequest msg = req.build(this.messageManager());

    final UdpFlowId nextHop = this.getNextHop(msg, flowId);

    this.stack.sendAck(msg, nextHop);

  }

  public UdpFlowId getNextHop(final SipRequest req, final FlowId flowId)
  {

    if (!req.getRoute().isEmpty())
    {
      return UdpFlowId.create(this.udpListener, req.getRoute().get(0).getAddress().apply(SipUriExtractor.getInstance()).getHost());
    }
    else if (flowId != null)
    {
      return (UdpFlowId) flowId;
    }
    else
    {
      return UdpFlowId.create(this.udpListener, req.getUri().apply(SipUriExtractor.getInstance()).getHost());
    }

  }

  /**
   * Incoming request.
   */

  @Override
  public void processRequest(final ServerTransactionHandle tex)
  {

    final WrappedServerTransaction e = new WrappedServerTransaction(tex);

    this.queue.assertExecuting();

    final MutableSipResponse error = this.enforcer.enforce(e.getRequest());

    if (error != null)
    {
      log.info("Enforcer Rejected Request");
      e.respond(error.build(this.messageManager()));
      return;
    }

    if (!StringUtils.isBlank(e.getRequest().getToTag()))
    {

      final DialogId did = DialogId.fromRemote(e.getRequest());

      final EmbeddedDialog dialog = this.dialogs.get(did);

      if (dialog == null)
      {
        log.warn("In-dialog request for unknown dialog {}", did);
        e.respond(SipResponseStatus.CALL_DOES_NOT_EXIST.withReason("Dialog not found"));
        return;
      }

      dialog.processRequest(e);

      return;

    }

    if (e.getRequest().getMethod().isOptions())
    {

      this.handler.processOptions(new OptionsHandle() {

        @Override
        public void reject(final SipStatus status)
        {
          e.respond(ApiUtils.convert(status));
        }

        @Override
        public void accept(final String body)
        {
          final MutableSipResponse res = MutableSipResponse.createResponse(e.getRequest(), SipResponseStatus.OK);
          res.allow(EmbeddedSipSegment.this.enforcer.getMethods());
          res.accept(MIMEType.APPLICATION_SDP);
          res.supported(EmbeddedSipSegment.this.enforcer.getSupported());
          res.allowEvents(HillbillyRequestEnforcer.getEvents());
          res.server(EmbeddedSipSegment.this.serverName);
          if (body != null)
          {
            res.body("application/sdp", body);
          }
          e.respond(res.build(EmbeddedSipSegment.this.stack.getMessageManager()));
        }

      });
      return;
    }

    if (e.getRequest().getMethod().isRefer())
    {

      final ReferHandle h = new ReferHandle() {

        @Override
        public void reject(final SipStatus status)
        {
          e.respond(ApiUtils.convert(status));
        }

        @Override
        public Optional<Address> referredBy()
        {
          return Optional.ofNullable(ApiUtils.convert(e.getRequest().getReferredBy().orElse(null)));
        }

        @Override
        public Address referTo()
        {
          return ApiUtils.convert(e.getRequest().getReferTo().get());
        }

        @Override
        public ReferNotificationHandle accept(final SipStatus status)
        {
          e.respond(ApiUtils.convert(status));
          return new NullReferNotificationSink();
        }

      };

      if (!e.getRequest().getTargetDialog().isPresent())
      {
        log.debug("Got out of dialog REFER");
        this.handler.processRefer(ApiUtils.convert(e.getRequest().getUri()), h);
        return;
      }

      // find the specified dialog, so we can tell the client application about this.
      final DialogId did = e.getRequest().getTargetDialog().get().asDialogId().orElse(null);

      if (did == null)
      {
        e.respond(SipResponseStatus.BAD_REQUEST.withReason("Target-Dialog invalid"));
        return;
      }

      log.debug("Got OOD REFER with Target-Dialog {}", did);

      final EmbeddedDialog d = this.dialogs.get(did);

      if (d == null)
      {
        e.respond(SipResponseStatus.CALL_DOES_NOT_EXIST.withReason("Target-Dialog not found"));
        return;
      }

      d.processTargettedRefer(h);

      return;
    }

    if (!e.getRequest().getMethod().isInvite())
    {
      log.warn("Method {} not supported out of dialog", e.getRequest().getMethod());
      e.respond(SipResponseStatus.METHOD_NOT_ALLOWED);
      return;
    }

    if (this.handler == null)
    {
      log.warn("Rejecting INVITE due to missing handler");
      e.respond(SipResponseStatus.SERVICE_UNAVAILABLE);
      return;
    }

    // now use the handler to dispatch somethign to accept it.

    if (!this.hillbilly.allowNewCall())
    {
      log.warn("Rejecting call on segment due to {} active dialogs", this.dialogs.size());
      e.respond(SipResponseStatus.SERVICE_UNAVAILABLE.withReason("Insufficient Resources on SIP segment"));
      return;
    }

    final EmbeddedServerCreator uas = new EmbeddedServerCreator(e, this);

    e.addListener(cancel -> {

      try
      {
        uas.cancel(cancel.getReason().orElse(null));
      }
      catch (final Exception ex)
      {
        // crossed paths, erp.
        // TODO: we need to fix the API to allow CANCEL to be rejected.
        log.error("CANCEL crossed paths with 200 OK going out", ex);
      }

    });

    // look for replaces header

    final Replaces replaces = e.getRequest().getReplaces().orElse(null);

    if (replaces != null)
    {

      final EmbeddedDialog d = this.dialogs.get(
          new DialogId(replaces.getCallId(), replaces.getToTag(), replaces.getFromTag()));

      if (d == null)
      {
        e.respond(SipResponseStatus.CALL_DOES_NOT_EXIST.withReason("Target-Dialog not found"));
        return;
      }

      d.processReplaces(uas);

      return;

    }

    final IncomingInviteHandle handle = new IncomingInviteHandle() {

      @Override
      public ClientSideCreator process(final ServerSideCreator creator)
      {

        if (creator == null)
        {
          log.warn("createServer() returned null handler, rejecting incoming INVITE");
          uas.reject(ApiUtils.convert(SipResponseStatus.SERVER_INTERNAL_ERROR));
          return null;
        }

        uas.process(creator);

        e.respond(SipResponseStatus.TRYING);

        return uas;

      }

      @Override
      public String offer()
      {
        return new String(e.getRequest().getBody(), Charsets.UTF_8);
      }

      @Override
      public String uri()
      {
        return ApiUtils.convert(e.getRequest().getUri());
      }

      @Override
      public Address localIdentity()
      {
        return ApiUtils.convert(e.getRequest().getTo().withoutParameter(Token.from("tag")));
      }

      @Override
      public Address remoteIdentity()
      {
        return ApiUtils.convert(e.getRequest().getFrom());
      }

      @Override
      public String netns()
      {
        return EmbeddedSipSegment.this.id;
      }

      @Override
      public ClientSideCreator client()
      {
        return uas;
      }

      @Override
      public Request invite()
      {
        return ApiUtils.convert(e.getRequest());
      }

    };

    this.handler.createServer(uas, handle);

  }

  @Override
  public void processAck(final SipRequest req, final FlowId flow)
  {
    this.queue.assertExecuting();
    log.info("Incoming ACK");
    final EmbeddedDialog dialog = this.dialogs.get(DialogId.fromRemote(req));
    if (dialog == null)
    {
      log.warn("Dropping ACK for unknown dialog");
      return;
    }
    dialog.processAck(req, flow);
  }

  @Override
  public HillbillyRuntimeService getExecutor()
  {

    return new HillbillyRuntimeService() {

      @Override
      public void execute(final Runnable command)
      {
        EmbeddedSipSegment.this.executor.execute(command);
      }

      @Override
      public HillbillyTimerHandle schedule(final Runnable command, final long i, final TimeUnit unit)
      {
        final ScheduledFuture<?> handle = EmbeddedSipSegment.this.executor.schedule(command, i, unit);
        return () -> handle.cancel(false);
      }

    };

  }

  @Override
  public HillbillyRequestEnforcer getEnforcer()
  {
    return this.enforcer;
  }

  @Override
  public long getActiveDialogCount()
  {
    return this.dialogs.size();
  }

  @Override
  public Clock getClock()
  {
    return Clock.systemUTC();
  }

  /**
   * Auto-Kill this unknown (or already killed) INVITE 2xx response we don't care about.
   */

  @Override
  public void autoKill(final SipResponse res)
  {
    log.warn("Got unknown (or undesired) 2xx response");
  }

}
