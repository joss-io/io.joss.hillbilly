package com.jive.hillbilly;

import lombok.Value;
import lombok.experimental.Wither;

@Value
@Wither
public class SipStatus
{
  
  public static final SipStatus OK = new SipStatus(200, "OK");
  public static final SipStatus SERVICE_UNAVAILABLE = new SipStatus(503, "Service Unavailable");
  public static final SipStatus SERVER_INTERNAL_ERROR = new SipStatus(500, "Server Internal Error");
  public static final SipStatus REQUEST_TERMINATED = new SipStatus(487, "Request Terminated");
  public static final SipStatus RINGING = new SipStatus(180, "Ringing");
  public static final SipStatus PROGRESS = new SipStatus(183, "Progress");
  
  private int code;
  private String reason;
  
  
  public boolean isFinal()
  {
    return code >= 200;
  }


  public boolean isFailure()
  {
    return code >= 300;
  }


  public boolean isSuccess()
  {
    return code / 100 == 2;
  }
  
}
