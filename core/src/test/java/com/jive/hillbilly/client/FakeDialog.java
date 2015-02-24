package com.jive.hillbilly.client;

import com.jive.hillbilly.client.ApiUtils;
import com.jive.hillbilly.client.api.Dialog;
import com.jive.hillbilly.client.api.DisconnectEvent;
import com.jive.hillbilly.client.api.IncomingInviteHandle;
import com.jive.hillbilly.client.api.NegotiatedSession;
import com.jive.hillbilly.client.api.ReferHandle;
import com.jive.hillbilly.client.api.RenegotiationHandle;
import com.jive.hillbilly.client.api.RequestedOfferHandle;
import com.jive.sip.message.api.SipResponseStatus;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FakeDialog implements Dialog, NegotiatedSession
{

  protected NegotiatedSession remoteSession;

  @Override
  public void refer(final ReferHandle h)
  {
    log.debug("refer");
    h.accept(ApiUtils.convert(SipResponseStatus.OK));
  }

  @Override
  public void disconnect(final DisconnectEvent e)
  {
    log.debug("disconnect");
  }

  @Override
  public void requestOffer(final RequestedOfferHandle handle)
  {
    log.debug("requested offer");
  }

  @Override
  public void answer(final String offer, final RenegotiationHandle session)
  {
    log.debug("answer");
  }

  @Override
  public void replaceWith(final IncomingInviteHandle uas)
  {
    uas.process(null);
  }

}
