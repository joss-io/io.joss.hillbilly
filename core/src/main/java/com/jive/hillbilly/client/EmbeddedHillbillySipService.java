package com.jive.hillbilly.client;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.jive.hillbilly.Request;
import com.jive.hillbilly.api.ClientInviteOptions;
import com.jive.hillbilly.client.api.ClientSideCreator;
import com.jive.hillbilly.client.api.DelayedClientSideCreator;
import com.jive.hillbilly.client.api.HillbillyHandler;
import com.jive.hillbilly.client.api.HillbillyService;
import com.jive.hillbilly.client.api.ServerSideCreator;
import com.jive.sip.dummer.txn.ClientTransactionListener;
import com.jive.sip.dummer.txn.SipStack;
import com.jive.sip.dummer.txn.SipStackBuilder;
import com.jive.sip.dummer.txn.SipTransactionErrorInfo;
import com.jive.sip.dummer.txn.SipTransactionResponseInfo;
import com.jive.sip.message.api.SipMethod;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.transport.udp.ListenerId;
import com.jive.sip.transport.udp.UdpFlowId;

import lombok.extern.slf4j.Slf4j;

/**
 * A hillbilly client implementation which uses an embedded SIP stack running in the same process.
 *
 * @author theo
 *
 */

@Slf4j
public class EmbeddedHillbillySipService implements HillbillyService
{

  private static final long MAX_DIALOGS = Long.parseLong(System.getProperty("hillbilly.maxdialog", "15000"));

  private HillbillyHandler listener;

  private final Map<String, EmbeddedNetworkSegment> segments = Maps.newHashMap();

  /**
   * Sets the handler which prcesses events destined for the consumer.
   */

  public void setHandler(final HillbillyHandler l)
  {
    this.listener = l;
  }

  /**
   * supports sending of generic SIP requests using the same stack as hillbilly.
   */

  public void send(String netns, Request req, InetSocketAddress nexthop, EmbeddedRequestListener listener)
  {

    segments.get(netns).stack().send(ApiUtils.convert(req), UdpFlowId.create(new ListenerId(0), nexthop), new ClientTransactionListener() {

      @Override
      public void onResponse(SipTransactionResponseInfo e)
      {

        try
        {
          listener.onNext(ApiUtils.convert(e.getResponse()));
        }
        catch (Exception ex)
        {
          log.warn("Client exception", ex);
        }

        if (e.getResponse().getStatus().isFinal())
        {
          listener.onCompleted();
        }

      }

      @Override
      public void onError(SipTransactionErrorInfo e)
      {
        listener.onError(new RuntimeException(e.getCode().toString()));
      }

    });

  }

  /**
   *
   */

  public void addSegment(final String netns, final InetSocketAddress bind, final String self,
      final ScheduledExecutorService elg, boolean support100rel)
  {

    Preconditions.checkNotNull(this.listener, "Must setHandler() before calling addSegment()");
    final SipStack stack = new SipStackBuilder(self)
        .withId(netns)
        .withThreadCount(1)
        .build();

    stack.addListener(bind);

    stack.startAsync();

    final EmbeddedSipSegment segment = new EmbeddedSipSegment(this, netns, stack, this.listener, elg, support100rel);

    segment.setServerName("Jive Redneck/1.0 (" + netns + ")");

    // temp to handle...
    stack.addNonInviteHandler(SipMethod.REGISTER, txn -> txn.respond(SipResponseStatus.OK), elg);

    stack.addNonInviteHandler(SipMethod.REFER, segment, elg);
    stack.addNonInviteHandler(SipMethod.OPTIONS, segment, elg);

    stack.addInviteHandler(segment, elg);

    stack.awaitRunning();

    this.segments.put(netns, segment);

  }

  /**
   * Creates a new INVITE creator which sends an INVITE with an SDP offer out.
   */

  @Override
  public ServerSideCreator createClient(
      final ClientInviteOptions opts,
      final ClientSideCreator handler,
      final String offer)
  {

    Preconditions.checkArgument(opts.getSegment() != null, "no segment");

    final EmbeddedClientCreator impl = new EmbeddedClientCreator(
        this.getSegment(opts.getSegment()),
        opts,
        handler,
        offer);

    impl.send();

    return impl;

  }

  private EmbeddedNetworkSegment getSegment(final String segment)
  {
    return Preconditions.checkNotNull(this.segments.get(segment), segment);
  }

  /**
   * Sends an INVITE out without an SDP offer.
   */

  @Override
  public ServerSideCreator createClient(final ClientInviteOptions opts, final DelayedClientSideCreator client)
  {
    return null;
  }

  public boolean allowNewCall()
  {
    return this.segments.values().stream().mapToLong(seg -> seg.getActiveDialogCount()).sum() < MAX_DIALOGS;
  }

}
