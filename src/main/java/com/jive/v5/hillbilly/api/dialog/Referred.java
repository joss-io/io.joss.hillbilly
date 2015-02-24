package com.jive.v5.hillbilly.api.dialog;

import lombok.Value;

import com.jive.v5.hillbilly.api.DialogEvent;
import com.jive.v5.hillbilly.api.DialogEventVisitor;

@Value
public class Referred implements DialogEvent
{

  @Override
  public void apply(DialogEventVisitor visitor)
  {
    visitor.visit(this);
  }

}
