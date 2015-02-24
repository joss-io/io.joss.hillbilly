package com.jive.hillbilly.api.negotiation;

import com.jive.hillbilly.api.NegotiationEvent;
import com.jive.hillbilly.api.NegotiationEventVisitor;

import lombok.Value;

@Value
public class RemoteSdpChanged implements NegotiationEvent
{

  @Override
  public void apply(NegotiationEventVisitor visitor)
  {
    visitor.visit(this);
  }

}
