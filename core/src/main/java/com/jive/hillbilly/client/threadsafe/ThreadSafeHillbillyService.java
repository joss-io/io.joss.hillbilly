package com.jive.hillbilly.client.threadsafe;

import org.apache.commons.lang3.NotImplementedException;

import com.jive.hillbilly.SipStatus;
import com.jive.hillbilly.api.ClientInviteOptions;
import com.jive.hillbilly.client.api.ClientSideCreator;
import com.jive.hillbilly.client.api.DelayedClientSideCreator;
import com.jive.hillbilly.client.api.HillbillyService;
import com.jive.hillbilly.client.api.ServerSideCreator;
import com.jive.myco.commons.concurrent.Pnky;

public class ThreadSafeHillbillyService implements HillbillyService
{

  private final HillbillyService impl;

  public ThreadSafeHillbillyService(final HillbillyService impl)
  {
    this.impl = impl;
  }

  @Override
  public ServerSideCreator createClient(final ClientInviteOptions opts, final DelayedClientSideCreator client)
  {
    throw new NotImplementedException("");
  }

  @Override
  public ServerSideCreator createClient(final ClientInviteOptions opts, final ClientSideCreator handler, final String offer)
  {

    final ThreadSafeContext ctx = new ThreadSafeContext();

    final Pnky<ServerSideCreator> creator = Pnky.create();

    ctx.hillbilly().execute(() ->
    {
      try
      {
        creator.resolve(
            this.impl.createClient(
                opts,
                new ThreadSafeServerCreator(ctx, Pnky.immediatelyComplete(handler)), offer)
            );
      }
      catch (final Exception ex)
      {
        ctx.consumer(() -> handler.reject(SipStatus.SERVER_INTERNAL_ERROR));
      }
    });

    return new ThreadSafeClientCreator(ctx, creator);

  }


}
