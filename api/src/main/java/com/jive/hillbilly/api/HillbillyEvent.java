package com.jive.hillbilly.api;

public interface HillbillyEvent
{

  void apply(HillbillyEventVisitor visitor);

}
