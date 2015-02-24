package com.jive.hillbilly.client.threadsafe;

import com.jive.hillbilly.SipStatus;
import com.jive.hillbilly.client.api.ReferNotificationHandle;
import com.jive.myco.commons.concurrent.Pnky;

class ThreadSafeReferNotificationHandle implements ReferNotificationHandle
{

  private final Pnky<ReferNotificationHandle> impl;
  private final ThreadSafeContext ctx;

  ThreadSafeReferNotificationHandle(final ThreadSafeContext ctx, final Pnky<ReferNotificationHandle> impl)
  {
    this.impl = impl;
    this.ctx = ctx;
  }

  @Override
  public void update(final SipStatus trying)
  {
    this.impl.thenAccept(val -> val.update(trying), this.ctx.hillbilly());
  }

  @Override
  public void close()
  {
    this.impl.thenAccept(val -> val.close(), this.ctx.hillbilly());
  }

}
