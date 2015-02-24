package com.jive.v5.hillbilly.api.negotiation;

import lombok.Value;

import com.jive.v5.hillbilly.api.NegotiationEvent;
import com.jive.v5.hillbilly.api.NegotiationEventVisitor;

@Value
public class SendSdpOffer implements NegotiationEvent
{

  @Override
  public void apply(NegotiationEventVisitor visitor)
  {
    visitor.visit(this);
  }

}
