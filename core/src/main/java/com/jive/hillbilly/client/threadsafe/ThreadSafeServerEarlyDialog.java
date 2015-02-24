package com.jive.hillbilly.client.threadsafe;

import com.jive.hillbilly.client.api.ClientSideEarlyDialog;
import com.jive.hillbilly.client.api.Dialog;
import com.jive.hillbilly.client.api.IncomingInviteHandle;
import com.jive.hillbilly.client.api.OriginationBranchConnectEvent;
import com.jive.hillbilly.client.api.OriginationBranchProgressEvent;
import com.jive.hillbilly.client.logging.LoggingDialog;
import com.jive.myco.commons.concurrent.PnkyPromise;

class ThreadSafeServerEarlyDialog implements ClientSideEarlyDialog
{

  private final PnkyPromise<ClientSideEarlyDialog> impl;
  private final ThreadSafeContext ctx;
  private final ThreadSafeDialog dialog;

  ThreadSafeServerEarlyDialog(final ThreadSafeContext ctx, final PnkyPromise<ClientSideEarlyDialog> value)
  {
    this.ctx = ctx;
    this.impl = value;
    this.dialog = new ThreadSafeDialog(this.ctx.swap(), value.thenTransform(resolved -> new LoggingDialog(resolved.dialog())));
  }

  @Override
  public Dialog dialog()
  {
    return this.dialog;
  }

  @Override
  public void progress(final OriginationBranchProgressEvent status)
  {
    this.impl.thenAccept(value -> value.progress(status), this.ctx.consumer());
  }

  @Override
  public void accept(final OriginationBranchConnectEvent e)
  {
    this.impl.thenAccept(value -> value.accept(e), this.ctx.consumer());
  }

  @Override
  public void answer(final String sdp)
  {
    this.impl.thenAccept(value -> value.answer(sdp), this.ctx.consumer());
  }

  @Override
  public void replaceWith(final IncomingInviteHandle h)
  {
    this.impl.thenAccept(value -> value.replaceWith(new ThreadSafeIncomingInviteHandle(this.ctx, h)), this.ctx.consumer());
  }

}
