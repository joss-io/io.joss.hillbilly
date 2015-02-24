package com.jive.v5.hillbilly.api.invite;

import lombok.Value;

import com.jive.v5.hillbilly.api.BranchEvent;
import com.jive.v5.hillbilly.api.BranchEventVisitor;

@Value
public class InviteAccepted implements BranchEvent
{

  @Override
  public void apply(BranchEventVisitor visitor)
  {
    visitor.visit(this);
  }

}
