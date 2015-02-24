package com.jive.v5.hillbilly.client.api;

import com.jive.sip.message.api.SipResponseStatus;

public interface BaseClientSideEarlyDialog
{

  /**
   * Send a progress notification on this early dialog.
   */

  void progress(SipResponseStatus status);

  /**
   * Accept this early dialog, e.g move to connected.
   */

  Dialog accept(Dialog remote);

  /**
   * 
   */

  void end(String reason);

}
