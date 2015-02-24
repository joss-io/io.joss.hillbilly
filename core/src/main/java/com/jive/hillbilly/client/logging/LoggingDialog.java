package com.jive.hillbilly.client.logging;

import com.jive.hillbilly.client.api.Dialog;
import com.jive.hillbilly.client.api.DisconnectEvent;
import com.jive.hillbilly.client.api.IncomingInviteHandle;
import com.jive.hillbilly.client.api.ReferHandle;
import com.jive.hillbilly.client.api.RenegotiationHandle;
import com.jive.hillbilly.client.api.RequestedOfferHandle;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingDialog implements Dialog
{

  private final Dialog dialog;

  public LoggingDialog(final Dialog dialog)
  {
    this.dialog = dialog;
  }

  @Override
  public void requestOffer(final RequestedOfferHandle handle)
  {
    log.debug("requestOffer");
    this.dialog.requestOffer(handle);
  }

  @Override
  public void answer(final String offer, final RenegotiationHandle session)
  {
    log.debug("answer");
    this.dialog.answer(offer, session);
  }

  @Override
  public void refer(final ReferHandle h)
  {
    log.debug("refer");
    this.dialog.refer(h);
  }

  @Override
  public void disconnect(final DisconnectEvent e)
  {
    log.debug("disconnect");
    this.dialog.disconnect(e);
  }

  @Override
  public void replaceWith(final IncomingInviteHandle h)
  {
    log.debug("replaceWith");
    this.dialog.replaceWith(h);
  }

}
