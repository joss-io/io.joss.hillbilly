package com.jive.v5.hillbilly.client;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Wither;

import com.google.common.primitives.UnsignedInteger;

@Value
@AllArgsConstructor
@Wither
public class DialogState
{
  private UnsignedInteger localSequence;
  private UnsignedInteger remoteSequence;
}
