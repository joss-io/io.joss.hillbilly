package com.jive.hillbilly.client.threadsafe;

import com.jive.hillbilly.Request;
import com.jive.hillbilly.api.Address;
import com.jive.hillbilly.client.api.ClientSideCreator;
import com.jive.hillbilly.client.api.IncomingInviteHandle;
import com.jive.hillbilly.client.api.ServerSideCreator;
import com.jive.myco.commons.concurrent.Pnky;
import com.jive.sip.message.api.SipRequest;

class ThreadSafeIncomingInviteHandle implements IncomingInviteHandle
{

  private final IncomingInviteHandle impl;
  private final ThreadSafeContext ctx;
  private final ThreadSafeServerCreator client;

  ThreadSafeIncomingInviteHandle(final ThreadSafeContext ctx, final IncomingInviteHandle impl)
  {
    this.impl = impl;
    this.ctx = ctx;
    this.client = new ThreadSafeServerCreator(this.ctx, Pnky.immediatelyComplete(this.impl.client()));
  }

  @Override
  public String offer()
  {
    return this.impl.offer();
  }

  @Override
  public ClientSideCreator process(final ServerSideCreator creator)
  {

    final Pnky<ClientSideCreator> res = Pnky.create();

    final ThreadSafeClientCreator tcreator = new ThreadSafeClientCreator(this.ctx, Pnky.immediatelyComplete(creator));

    this.ctx.consumer().execute(() -> res.resolve(this.impl.process(tcreator)));

    return new ThreadSafeServerCreator(this.ctx, res);

  }

  @Override
  public Address localIdentity()
  {
    return this.impl.localIdentity();
  }

  @Override
  public Address remoteIdentity()
  {
    return this.impl.remoteIdentity();
  }

  @Override
  public String uri()
  {
    return this.impl.uri();
  }

  @Override
  public String netns()
  {
    return this.impl.netns();
  }

  @Override
  public ClientSideCreator client()
  {
    return this.client;
  }

  @Override
  public Request invite()
  {
    return this.impl.invite();
  }

}
