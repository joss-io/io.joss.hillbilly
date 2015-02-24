package com.jive.v5.hillbilly.api;

public interface HillbillyEvent
{

  void apply(HillbillyEventVisitor visitor);

}
