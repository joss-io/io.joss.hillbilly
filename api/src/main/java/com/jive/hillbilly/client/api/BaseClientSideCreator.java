package com.jive.hillbilly.client.api;

import java.util.Collections;
import java.util.List;

import com.jive.hillbilly.SipStatus;
import com.jive.hillbilly.SipWarning;

public interface BaseClientSideCreator extends BaseCreator
{

  /**
   * @see BaseClientSideCreator#reject(SipResponseStatus, List)
   */

  default void reject(final SipStatus status)
  {
    reject(status, Collections.emptyList());
  }

  /**
   * Reject the INVITE, terminating all branches.
   *
   * Once this is called, it's guaranteed that no further events will be dispatches on this
   * listener, or it's related branches.
   *
   * If there are any early dialogs/branches, none of them will notified.
   *
   * @param status
   *          The status code to reject with (or Reason header)
   * @param warnings
   *          The Warning headers to add to the response.
   */

  void reject(final SipStatus status, final List<SipWarning> warnings);

}
