package com.jive.hillbilly;

import java.util.Map;

import lombok.Value;

@Value
public class Request extends Message
{
  private String method;
  private String uri;
  private Map<String, String> headers;
  private byte[] body;
}
