package com.jive.hillbilly.client.threadsafe;

import java.util.Optional;

import com.jive.hillbilly.SipStatus;
import com.jive.hillbilly.api.Address;
import com.jive.hillbilly.client.api.ReferHandle;
import com.jive.hillbilly.client.api.ReferNotificationHandle;
import com.jive.myco.commons.concurrent.Pnky;

class ThreadSafeReferHandle implements ReferHandle
{

  private final ReferHandle impl;
  private final ThreadSafeContext ctx;

  ThreadSafeReferHandle(final ThreadSafeContext ctx, final ReferHandle impl)
  {
    this.ctx = ctx;
    this.impl = impl;
  }

  @Override
  public Address referTo()
  {
    return this.impl.referTo();
  }

  @Override
  public Optional<Address> referredBy()
  {
    return this.impl.referredBy();
  }

  @Override
  public void reject(final SipStatus status)
  {
    this.ctx.consumer().execute(() -> this.impl.reject(status));
  }

  @Override
  public ReferNotificationHandle accept(final SipStatus status)
  {
    final Pnky<ReferNotificationHandle> ref = Pnky.create();
    this.ctx.consumer(() -> ref.resolve(this.impl.accept(status)));
    return new ThreadSafeReferNotificationHandle(this.ctx, ref);
  }

}
