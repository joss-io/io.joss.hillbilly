package com.jive.hillbilly.api;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;

import lombok.Singular;
import lombok.Value;
import lombok.experimental.Builder;

@Value
@Builder
public class ClientInviteOptions
{

  /**
   * Request URI.
   */

  private String requestUri;

  /**
   * To header.
   */

  private Address to;

  /**
   * From header.
   */

  private Address from;

  /**
   * The Max-Forwards value.
   */

  private Long maxForwards;

  /**
   * The segment to use.
   */

  private String segment;
  
  /**
   * Override next-hop to send the request via.
   */
  
  private InetSocketAddress nextHop;

  /**
   * The Session-ID value (if not specified, one will be generated).
   */

  private String sessionId;

  /**
   * extra headers to set.
   */

  @Singular
  private Map<String, String> headers;
  
  /**
   * Any credentials we'd like to use.
   */
  
  @Singular
  private Map<String, String> credentials;

  /**
   * The Timer A value (until we get a 100 Trying from the INVITE).
   */

  private Duration timerA;

  /**
   * do we allow this dialog to be referred?
   */

  boolean referrable = true;

  /**
   * Do we allow this leg to be picked up with INVITE w/Replaces.
   */

  boolean replacable = true;

  /**
   * Do we allow this leg to be joined up with INVITE w/Join.
   */

  boolean joinable = true;

}
