package com.jive.hillbilly.client.threadsafe;

import java.util.List;

import com.jive.hillbilly.SipWarning;
import com.jive.hillbilly.client.api.RenegotiationHandle;

class ThreadSafeRenegotiationHandle implements RenegotiationHandle
{

  private final RenegotiationHandle impl;
  private final ThreadSafeContext ctx;

  ThreadSafeRenegotiationHandle(final ThreadSafeContext ctx, final RenegotiationHandle impl)
  {
    this.impl = impl;
    this.ctx = ctx;
  }

  @Override
  public void answer(final String answer)
  {
    this.ctx.consumer(() -> this.impl.answer(answer));
  }

  @Override
  public void reject(final List<SipWarning> warnings)
  {
    this.ctx.consumer(() -> this.impl.reject(warnings));
  }

}
