package com.jive.hillbilly.client;

import com.jive.sip.message.api.SipResponse;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.processor.rfc3261.MutableSipResponse;

public class AtomicServerRequest
{

  private final EmbeddedDialog dialog;
  private final HillbillyServerTransaction txn;

  public AtomicServerRequest(final EmbeddedDialog dialog, final HillbillyServerTransaction txn)
  {
    this.dialog = dialog;
    this.txn = txn;
  }

  public void respond(final MutableSipResponse res)
  {
    this.respond(res.build(this.dialog.getNetwork().messageManager()));
  }

  private void respond(final SipResponse res)
  {


    if (res.getStatus().isSuccess())
    {
      this.txn.respond(this.dialog.refreshRemoteInitiated(this.txn.getRequest(), res));
    }
    else
    {
      this.txn.respond(res);
    }

  }

  public void respond(final SipResponseStatus status)
  {
    this.respond(this.dialog.createResponse(this.txn.getRequest(), status).build(
        this.dialog.getNetwork().messageManager()));
  }

  public void apply()
  {
    ;
  }


}
