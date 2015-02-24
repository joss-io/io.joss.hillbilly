package com.jive.v5.hillbilly.client.api;

import com.jive.sip.message.api.SipResponseStatus;

public interface BaseClientSideCreator
{

  /**
   * Reject the INVITE, terminating all branches.
   */

  void reject(SipResponseStatus status);

}
