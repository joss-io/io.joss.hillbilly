package com.jive.v5.hillbilly.client.api;


/**
 * Used to drive the client side of a creator.
 * 
 * @author theo
 *
 */

public interface ClientSideCreator extends BaseClientSideCreator
{


  /**
   * A new branch has been received.
   */

  ClientSideEarlyDialog branch(ServerSideEarlyDialog branch);



}
