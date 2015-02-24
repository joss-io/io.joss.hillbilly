package com.jive.hillbilly;

import lombok.Value;

@Value
public class SipWarning
{
  private int code;
  private String agent;
  private String text;
}
