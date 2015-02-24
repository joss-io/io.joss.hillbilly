package com.jive.hillbilly.client;

import com.jive.hillbilly.client.api.Dialog;
import com.jive.hillbilly.client.api.DisconnectEvent;
import com.jive.hillbilly.client.api.IncomingInviteHandle;
import com.jive.hillbilly.client.api.ReferHandle;
import com.jive.hillbilly.client.api.RenegotiationHandle;
import com.jive.hillbilly.client.api.RequestedOfferHandle;
import com.jive.hillbilly.client.api.RequiredAnswerHandle;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestLocalDialog implements Dialog
{

  @Getter
  private String offer;

  @Getter
  private RenegotiationHandle answerTo;

  @Override
  public void refer(final ReferHandle h)
  {
    // TODO Auto-generated method stub
  }

  @Override
  public void disconnect(final DisconnectEvent e)
  {
    // TODO Auto-generated method stub
  }

  @Override
  public void replaceWith(final IncomingInviteHandle uas)
  {
    uas.process(null);
  }

  @Override
  public void requestOffer(final RequestedOfferHandle handle)
  {
    handle.answer("[offer]", new RequiredAnswerHandle()
    {

      @Override
      public void rejected()
      {
        log.debug("offer we sent was rejected");
      }

      @Override
      public void answer(final String answer)
      {
        log.debug("got SDP answer");
      }

    });

  }

  @Override
  public void answer(final String offer, final RenegotiationHandle session)
  {
    this.offer = offer;
    this.answerTo = session;
  }

}
