package com.jive.hillbilly.client.api;



/**
 * Interface for controlling/receiving events for an early dialog, e.g branch.
 *
 * This interface is implemented for outgoing INVITE instances which contain an initial SDP offer.
 *
 * @author theo
 *
 */

public interface ClientSideEarlyDialog extends BaseClientSideEarlyDialog
{

  /**
   * called when the SDP answer is provided.
   *
   * @param sdp
   * @param session
   * @param handle
   */

  void answer(final String sdp);

  /**
   * This UAC creation got an INVITE w/Replaces. It should probably be accepted.
   */

  void replaceWith(final IncomingInviteHandle h);


}
