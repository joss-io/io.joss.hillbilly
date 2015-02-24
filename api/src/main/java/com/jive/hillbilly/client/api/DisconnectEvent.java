package com.jive.hillbilly.client.api;

import com.jive.hillbilly.SipStatus;

import lombok.Value;

@Value
public class DisconnectEvent
{

  public static final DisconnectEvent OK = new DisconnectEvent(SipStatus.OK);
  public static final DisconnectEvent CallCompletedElsewhere = new DisconnectEvent(SipStatus.OK.withReason("Call Completed Elswhere"));
  public static final DisconnectEvent Unknown = new DisconnectEvent(SipStatus.SERVER_INTERNAL_ERROR.withReason("Unknown Reason"));
  public static final DisconnectEvent ApiHangup = new DisconnectEvent(SipStatus.OK);
  public static final DisconnectEvent ClientFailed = new DisconnectEvent(SipStatus.SERVICE_UNAVAILABLE.withReason("Client Failed"));

  private SipStatus status;

}
