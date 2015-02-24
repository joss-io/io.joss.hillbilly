package com.jive.v5.hillbilly.api;

public interface CreatorEvent extends HillbillyEvent
{

  void apply(CreatorEventVisitor visitor);

  default void apply(HillbillyEventVisitor visitor)
  {
    visitor.visit(this);
  }

}
