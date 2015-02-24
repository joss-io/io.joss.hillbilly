package com.jive.v5.hillbilly.api;


public interface BranchEvent extends HillbillyEvent
{

  default void apply(HillbillyEventVisitor visitor)
  {
    visitor.visit(this);
  }

  void apply(BranchEventVisitor visitor);

}
