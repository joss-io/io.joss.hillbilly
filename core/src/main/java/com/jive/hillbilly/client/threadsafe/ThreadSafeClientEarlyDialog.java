package com.jive.hillbilly.client.threadsafe;

import com.jive.hillbilly.client.api.DialogTerminationEvent;
import com.jive.hillbilly.client.api.ServerSideEarlyDialog;
import com.jive.myco.commons.concurrent.PnkyPromise;

public class ThreadSafeClientEarlyDialog implements ServerSideEarlyDialog
{

  private final ThreadSafeContext ctx;
  private final PnkyPromise<ServerSideEarlyDialog> impl;

  public ThreadSafeClientEarlyDialog(final ThreadSafeContext ctx, final PnkyPromise<ServerSideEarlyDialog> branch)
  {
    this.ctx = ctx;
    this.impl = branch;
  }

  @Override
  public void end(final DialogTerminationEvent e)
  {
    this.impl.thenAccept(value -> value.end(e), this.ctx.consumer());
  }

}
