package com.jive.hillbilly.api.invite;

import com.jive.hillbilly.api.CreatorEvent;
import com.jive.hillbilly.api.CreatorEventVisitor;

import lombok.Value;

@Value
public class AckNewInvite implements CreatorEvent
{

  @Override
  public void apply(CreatorEventVisitor visitor)
  {
    visitor.visit(this);
  }

}
