package com.jive.v5.hillbilly.client.api;

/**
 * Interface for controlling/receiving events for an early dialog, e.g branch.
 * 
 * @author theo
 *
 */

public interface ClientSideEarlyDialog extends BaseClientSideEarlyDialog
{

  void answer(String sdp);

}
