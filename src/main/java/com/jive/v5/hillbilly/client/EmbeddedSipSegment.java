package com.jive.v5.hillbilly.client;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.DispatchQueue;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.net.HostAndPort;
import com.jive.ftw.sip.dummer.txn.ClientTransactionListener;
import com.jive.ftw.sip.dummer.txn.ClientTransactionOptions;
import com.jive.ftw.sip.dummer.txn.InviteServerTransactionHandler;
import com.jive.ftw.sip.dummer.txn.ServerTransactionHandle;
import com.jive.ftw.sip.dummer.txn.ServerTransactionListener;
import com.jive.ftw.sip.dummer.txn.SipClientTransaction;
import com.jive.ftw.sip.dummer.txn.SipStack;
import com.jive.ftw.sip.parameters.api.RawParameter;
import com.jive.ftw.sip.parameters.api.TokenParameterValue;
import com.jive.ftw.sip.parameters.impl.DefaultParameters;
import com.jive.sip.message.api.DialogId;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.message.api.Via;
import com.jive.sip.message.api.ViaProtocol;
import com.jive.sip.processor.rfc3261.MutableSipRequest;
import com.jive.sip.processor.rfc3261.SipMessageManager;
import com.jive.sip.processor.uri.SipUriExtractor;
import com.jive.sip.transport.api.FlowId;
import com.jive.sip.transport.udp.ListenerId;
import com.jive.sip.transport.udp.UdpFlowId;
import com.jive.v5.hillbilly.client.api.ClientSideCreator;
import com.jive.v5.hillbilly.client.api.HillbillyHandler;
import com.jive.v5.hillbilly.client.api.IncomingInviteHandle;
import com.jive.v5.hillbilly.client.api.ServerSideCreator;

@Slf4j
public class EmbeddedSipSegment implements EmbeddedNetworkSegment, InviteServerTransactionHandler
{

  private DispatchQueue queue = Dispatch.createQueue("segment");
  private SipStack stack;
  private Map<DialogId, EmbeddedDialog> dialogs = Maps.newHashMap();
  private ListenerId udpListener = new ListenerId(0);
  private HillbillyHandler handler;

  EmbeddedSipSegment(SipStack stack, HillbillyHandler handler)
  {
    this.stack = stack;
    this.handler = handler;
  }

  @Override
  public SipMessageManager messageManager()
  {
    return stack.getMessageManager();
  }

  @Override
  public SipClientTransaction sendInvite(SipRequest invite, ClientTransactionListener listener)
  {

    HostAndPort nextHop = invite.getUri().apply(SipUriExtractor.getInstance()).getHost();

    return stack.send(
        invite,
        UdpFlowId.create(this.udpListener, nextHop),
        listener,
        ClientTransactionOptions.DEFAULT);

  }

  @Override
  public HostAndPort getSelf()
  {
    return stack.getSelf();
  }

  @Override
  public DialogRegistrationHandle register(DialogId id, EmbeddedDialog d)
  {

    log.debug("Registered dialog {}", id);
    dialogs.put(id, d);

    return () ->
    {
      dialogs.remove(id);
      log.debug("Unregistered dialog {}", id);
    };

  }

  @Override
  public void transmit(MutableSipRequest req, String branchId)
  {

    // this is hardly ideal, but SIP makes it messy. boo.
    final List<RawParameter> params = Lists.newLinkedList();
    params.add(new RawParameter(Via.BRANCH, new TokenParameterValue(branchId)));
    Via via = new Via(ViaProtocol.UDP, getSelf(), DefaultParameters.from(params));
    req.via(via);

    SipRequest msg = req.build(this.messageManager());

    HostAndPort nextHop = msg.getUri().apply(SipUriExtractor.getInstance()).getHost();

    stack.sendAck(msg, UdpFlowId.create(udpListener, nextHop));

  }

  /**
   * Incoming request.
   */

  @Override
  public void processRequest(ServerTransactionHandle e)
  {

    queue.assertExecuting();

    if (!StringUtils.isBlank(e.getRequest().getToTag()))
    {
      EmbeddedDialog dialog = dialogs.get(DialogId.fromRemote(e.getRequest()));

      if (dialog == null)
      {
        log.warn("In-dialog request for unknown dialog");
        e.respond(SipResponseStatus.CALL_DOES_NOT_EXIST.withReason("Dialog not found"));
        return;
      }

      dialog.processRequest(e);

      return;

    }

    if (!e.getRequest().getMethod().isInvite())
    {
      log.warn("Method {} not supported out of dialog", e.getRequest().getMethod());
      e.respond(SipResponseStatus.METHOD_NOT_ALLOWED);
      return;
    }

    if (handler == null)
    {
      log.warn("Rejecting INVITE due to missing handler");
      e.respond(SipResponseStatus.SERVICE_UNAVAILABLE);
      return;
    }

    // now use the handler to dispatch somethign to accept it.

    EmbeddedServerCreator uas = new EmbeddedServerCreator(e, this);

    e.addListener(new ServerTransactionListener()
    {

      @Override
      public void onCancelled(SipRequest cancel)
      {
        uas.getQueue().execute(() -> uas.cancel(cancel.getReason().orNull()));
      }

    });

    IncomingInviteHandle handle = new IncomingInviteHandle()
    {

      @Override
      public ClientSideCreator process(ServerSideCreator creator)
      {

        if (creator == null)
        {
          log.warn("createServer() returned null handler, rejecting incoming INVITE");
          uas.reject(SipResponseStatus.SERVER_INTERNAL_ERROR);
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

    };



    handler.createServer(uas, handle);


  }

  @Override
  public void processAck(SipRequest req, FlowId flow)
  {
    queue.assertExecuting();
    log.info("Incoming ACK");
    EmbeddedDialog dialog = dialogs.get(DialogId.fromRemote(req));
    if (dialog == null)
    {
      log.warn("Dropping ACK for unknown dialog");
      return;
    }
    dialog.processAck(req, flow);
  }

}
