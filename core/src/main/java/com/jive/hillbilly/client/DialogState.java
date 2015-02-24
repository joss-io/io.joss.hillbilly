package com.jive.hillbilly.client;

import com.google.common.primitives.UnsignedInteger;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Wither;

@Value
@AllArgsConstructor
@Wither
public class DialogState
{
  private UnsignedInteger localSequence;
  private UnsignedInteger remoteSequence;
}
