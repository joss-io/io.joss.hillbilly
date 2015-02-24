package com.jive.hillbilly.client.threadsafe;

import com.jive.hillbilly.client.api.RequestedOfferHandle;
import com.jive.hillbilly.client.api.RequiredAnswerHandle;

class ThreadSafeRequestedOfferHandle implements RequestedOfferHandle
{

  private final RequestedOfferHandle impl;
  private final ThreadSafeContext ctx;

  ThreadSafeRequestedOfferHandle(final ThreadSafeContext ctx, final RequestedOfferHandle impl)
  {
    this.ctx = ctx;
    this.impl = impl;
  }

  @Override
  public void rejected()
  {
    this.ctx.consumer().execute(() -> this.impl.rejected());
  }

  @Override
  public void answer(final String offer, final RequiredAnswerHandle session)
  {
    this.ctx.consumer().execute(() -> this.impl.answer(offer, new ThreadSafeRequiredAnswerHandle(this.ctx, session)));
  }

}
