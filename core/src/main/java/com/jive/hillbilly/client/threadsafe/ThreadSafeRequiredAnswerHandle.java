package com.jive.hillbilly.client.threadsafe;

import com.jive.hillbilly.client.api.RequiredAnswerHandle;

public class ThreadSafeRequiredAnswerHandle implements RequiredAnswerHandle
{

  private final ThreadSafeContext ctx;
  private final RequiredAnswerHandle handle;

  public ThreadSafeRequiredAnswerHandle(final ThreadSafeContext ctx, final RequiredAnswerHandle handle)
  {
    this.ctx = ctx;
    this.handle = handle;
  }

  @Override
  public void answer(final String answer)
  {
    this.ctx.consumer().execute(() -> this.handle.answer(answer));
  }

  @Override
  public void rejected()
  {
    this.ctx.consumer().execute(() -> this.handle.rejected());
  }

}
