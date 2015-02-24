package com.jive.v5.hillbilly.api;

public interface DialogEvent extends HillbillyEvent
{

  default void apply(HillbillyEventVisitor visitor)
  {
    visitor.visit(this);
  }

  void apply(DialogEventVisitor visitor);

}
