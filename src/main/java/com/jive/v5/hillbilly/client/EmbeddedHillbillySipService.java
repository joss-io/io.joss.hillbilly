package com.jive.v5.hillbilly.client;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.DispatchQueue;

import com.google.common.collect.Maps;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.MoreExecutors;
import com.jive.ftw.sip.dummer.txn.NonInviteServerTransactionHandler;
import com.jive.ftw.sip.dummer.txn.ServerTransactionHandle;
import com.jive.ftw.sip.dummer.txn.SipStack;
import com.jive.ftw.sip.dummer.txn.SipStackBuilder;
import com.jive.sip.message.api.SipMethod;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.v5.hillbilly.client.api.ClientSideCreator;
import com.jive.v5.hillbilly.client.api.DelayedClientSideCreator;
import com.jive.v5.hillbilly.client.api.HillbillyHandler;
import com.jive.v5.hillbilly.client.api.HillbillyService;
import com.jive.v5.hillbilly.client.api.ServerSideCreator;

/**
 * A hillbilly client implementation which uses an embedded SIP stack running in the same process.
 * 
 * @author theo
 *
 */

@Slf4j
public class EmbeddedHillbillySipService implements HillbillyService
{

  private HillbillyHandler listener;
  private DispatchQueue queue = Dispatch.createQueue("hillbilly");
  private Map<String, EmbeddedNetworkSegment> segments = Maps.newHashMap();

  /**
   * Sets the handler which prcesses events destined for the consumer.
   */

  public void setHandler(HillbillyHandler l)
  {
    this.listener = l;
  }

  /**
   * 
   */

  public void addSegment(String netns, HostAndPort bind)
  {
    addSegment(netns, bind, bind);
  }

  public void addSegment(String netns, HostAndPort bind, HostAndPort self)
  {

    SipStack stack = new SipStackBuilder(self)
        .withId(netns)
        .build();

    stack.addListener(bind);

    stack.startAsync();

    EmbeddedSipSegment segment = new EmbeddedSipSegment(stack, listener);

    // temp to handle...
    stack.addNonInviteHandler(SipMethod.REGISTER, new NonInviteServerTransactionHandler()
    {

      @Override
      public void processRequest(ServerTransactionHandle txn)
      {
        txn.respond(SipResponseStatus.OK);
      }

    }, MoreExecutors.sameThreadExecutor());

    stack.addInviteHandler(segment, this.queue);

    stack.awaitRunning();

    this.segments.put(netns, segment);

  }

  /**
   * Creates a new INVITE creator which sends an INVITE with an SDP offer out.
   */

  @Override
  public ServerSideCreator createClient(
      ClientInviteOptions opts,
      ClientSideCreator handler,
      String offer)
  {

    EmbeddedClientCreator impl =
        new EmbeddedClientCreator(
            getSegment(opts.getSegment()),
            opts,
            handler,
            offer);

    queue.execute(() -> impl.send());

    return impl;

  }

  private EmbeddedNetworkSegment getSegment(String segment)
  {
    return this.segments.get(segment);
  }

  /**
   * Sends an INVITE out without an SDP offer.
   */

  @Override
  public ServerSideCreator createClient(ClientInviteOptions opts, DelayedClientSideCreator client)
  {
    return null;
  }

}
