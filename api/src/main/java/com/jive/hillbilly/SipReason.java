package com.jive.hillbilly;

import lombok.Value;

@Value
public class SipReason
{
  private String protocol;
  private int code;
  private String reason;
}
