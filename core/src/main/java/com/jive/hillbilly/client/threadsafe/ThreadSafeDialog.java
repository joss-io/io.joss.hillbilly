package com.jive.hillbilly.client.threadsafe;

import com.jive.hillbilly.client.api.Dialog;
import com.jive.hillbilly.client.api.DisconnectEvent;
import com.jive.hillbilly.client.api.IncomingInviteHandle;
import com.jive.hillbilly.client.api.ReferHandle;
import com.jive.hillbilly.client.api.RenegotiationHandle;
import com.jive.hillbilly.client.api.RequestedOfferHandle;
import com.jive.myco.commons.concurrent.PnkyPromise;

class ThreadSafeDialog implements Dialog
{

  private final PnkyPromise<Dialog> impl;
  private final ThreadSafeContext ctx;

  ThreadSafeDialog(final ThreadSafeContext ctx, final PnkyPromise<Dialog> dialog)
  {
    this.ctx = ctx;
    this.impl = dialog;
  }

  @Override
  public void requestOffer(final RequestedOfferHandle handle)
  {
    this.impl.thenAccept(dialog -> dialog.requestOffer(new ThreadSafeRequestedOfferHandle(this.ctx, handle)), this.ctx.hillbilly());
  }

  @Override
  public void answer(final String offer, final RenegotiationHandle session)
  {
    this.impl.thenAccept(dialog -> dialog.answer(offer, new ThreadSafeRenegotiationHandle(this.ctx, session)), this.ctx.hillbilly());
  }

  @Override
  public void refer(final ReferHandle h)
  {
    this.impl.thenAccept(dialog -> dialog.refer(new ThreadSafeReferHandle(this.ctx.swap(), h)), this.ctx.hillbilly());
  }

  @Override
  public void disconnect(final DisconnectEvent e)
  {
    this.impl.thenAccept(dialog -> dialog.disconnect(e), this.ctx.hillbilly());
  }

  @Override
  public void replaceWith(final IncomingInviteHandle h)
  {
    this.impl.thenAccept(dialog -> dialog.replaceWith(new ThreadSafeIncomingInviteHandle(this.ctx, h)), this.ctx.hillbilly());
  }

}
