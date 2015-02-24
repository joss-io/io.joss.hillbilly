package com.jive.v5.hillbilly.api.invite;

import lombok.Value;

import com.jive.v5.hillbilly.api.HillbillyEvent;
import com.jive.v5.hillbilly.api.HillbillyEventVisitor;

@Value
public class NewInvite implements HillbillyEvent
{

  @Override
  public void apply(HillbillyEventVisitor v)
  {
    v.visit(this);
  }

}
