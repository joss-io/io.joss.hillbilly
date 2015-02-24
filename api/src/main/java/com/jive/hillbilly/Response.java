package com.jive.hillbilly;

import java.util.Map;

import lombok.Value;

@Value
public class Response extends Message
{  
  private SipStatus status;
  private Map<String, String> headers;
  private byte[] body;
}
