package com.jive.v5.hillbilly.api.invite;

import lombok.Value;

import com.jive.v5.hillbilly.api.CreatorEvent;
import com.jive.v5.hillbilly.api.CreatorEventVisitor;

@Value
public class AckNewInvite implements CreatorEvent
{

  @Override
  public void apply(CreatorEventVisitor visitor)
  {
    visitor.visit(this);
  }

}
