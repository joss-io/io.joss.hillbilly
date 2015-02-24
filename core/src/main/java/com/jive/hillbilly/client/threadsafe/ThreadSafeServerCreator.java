package com.jive.hillbilly.client.threadsafe;

import java.util.List;

import com.jive.hillbilly.SipStatus;
import com.jive.hillbilly.SipWarning;
import com.jive.hillbilly.client.api.ClientSideCreator;
import com.jive.hillbilly.client.api.ClientSideEarlyDialog;
import com.jive.hillbilly.client.api.Dialog;
import com.jive.hillbilly.client.api.ServerSideEarlyDialog;
import com.jive.hillbilly.client.logging.LoggingDialog;
import com.jive.myco.commons.concurrent.Pnky;
import com.jive.myco.commons.concurrent.PnkyPromise;

class ThreadSafeServerCreator implements ClientSideCreator
{

  private final PnkyPromise<ClientSideCreator> impl;
  private final ThreadSafeContext ctx;

  ThreadSafeServerCreator(final ThreadSafeContext ctx, final PnkyPromise<ClientSideCreator> impl)
  {
    this.impl = impl;
    this.ctx = ctx;
  }

  @Override
  public void reject(final SipStatus status, final List<SipWarning> warnings)
  {
    this.impl.thenAccept(creator -> creator.reject(status, warnings), this.ctx.hillbilly());
  }

  @Override
  public ClientSideEarlyDialog branch(final ServerSideEarlyDialog branch, final Dialog dialog)
  {

    final ThreadSafeClientEarlyDialog tbranch = new ThreadSafeClientEarlyDialog(this.ctx, Pnky.immediatelyComplete(branch));
    final ThreadSafeDialog tdialog = new ThreadSafeDialog(this.ctx.swap(), Pnky.immediatelyComplete(new LoggingDialog(dialog)));

    final Pnky<ClientSideEarlyDialog> value = Pnky.create();

    this.impl.thenAccept(creator ->
    {
      value.resolve(new ThreadSafeServerEarlyDialog(this.ctx.swap(), Pnky.immediatelyComplete(creator.branch(tbranch, tdialog))));
    }
    , this.ctx.hillbilly());

    // this.ctx.consumer().execute(() -> branch.end(new DialogTerminationEvent("internal error")));

    return new ThreadSafeServerEarlyDialog(this.ctx, value);

  }

}
