package com.jive.hillbilly.api;

import lombok.Value;

/**
 * Maps to a SIP name-addr.
 * @author theo
 *
 */

@Value
public class Address
{
  private String name;
  private String uri;
  private String properties;  
}
