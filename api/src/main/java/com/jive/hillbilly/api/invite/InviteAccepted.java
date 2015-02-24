package com.jive.hillbilly.api.invite;

import com.jive.hillbilly.api.BranchEvent;
import com.jive.hillbilly.api.BranchEventVisitor;

import lombok.Value;

@Value
public class InviteAccepted implements BranchEvent
{

  @Override
  public void apply(BranchEventVisitor visitor)
  {
    visitor.visit(this);
  }

}
