package com.jive.hillbilly.api.invite;

import com.jive.hillbilly.api.HillbillyEvent;
import com.jive.hillbilly.api.HillbillyEventVisitor;

import lombok.Value;

@Value
public class NewInvite implements HillbillyEvent
{

  @Override
  public void apply(HillbillyEventVisitor v)
  {
    v.visit(this);
  }

}
