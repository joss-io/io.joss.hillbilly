package com.jive.hillbilly.client.api;

/**
 * Used to drive the client side of a creator.
 * 
 * @author theo
 *
 */

public interface ClientSideCreator extends BaseClientSideCreator
{

  /**
   * A new branch has been received. creates the dialog state and returns a handle that can be used
   * to control the status of the dialog progress.
   * 
   * Because SIP supports the concept of early dialogs which have full state, can be terminated
   * independent (using BYE), may be updated etc, the dialog handle must be passed in here to handle
   * any events.
   * 
   */

  ClientSideEarlyDialog branch(ServerSideEarlyDialog branch, Dialog dialog);

}
