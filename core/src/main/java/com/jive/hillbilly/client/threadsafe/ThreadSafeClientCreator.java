package com.jive.hillbilly.client.threadsafe;

import com.jive.hillbilly.SipReason;
import com.jive.hillbilly.client.api.ClientAlreadyConnectedException;
import com.jive.hillbilly.client.api.ServerSideCreator;
import com.jive.myco.commons.concurrent.PnkyPromise;
import com.jive.sip.message.api.Reason;

class ThreadSafeClientCreator implements ServerSideCreator
{

  private final PnkyPromise<ServerSideCreator> impl;
  private final ThreadSafeContext ctx;

  ThreadSafeClientCreator(final ThreadSafeContext ctx, final PnkyPromise<ServerSideCreator> creator)
  {
    this.ctx = ctx;
    this.impl = creator;
  }

  @Override
  public void cancel(final SipReason reason) throws ClientAlreadyConnectedException
  {
    this.impl.thenAccept((resolved) -> resolved.cancel(reason), this.ctx.hillbilly());
  }

}
