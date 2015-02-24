package com.jive.v5.hillbilly.api;

public interface NegotiationEvent extends HillbillyEvent
{

  default void apply(HillbillyEventVisitor visitor)
  {
    visitor.visit(this);
  }

  void apply(NegotiationEventVisitor visitor);

}
