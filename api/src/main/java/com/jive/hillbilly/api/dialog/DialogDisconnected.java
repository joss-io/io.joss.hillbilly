package com.jive.hillbilly.api.dialog;

import com.jive.hillbilly.api.DialogEvent;
import com.jive.hillbilly.api.DialogEventVisitor;

import lombok.Value;

@Value
public class DialogDisconnected implements DialogEvent
{

  @Override
  public void apply(DialogEventVisitor visitor)
  {
    visitor.visit(this);
  }

}
