package com.jive.v5.hillbilly.client.api;


public interface HillbillyHandler
{

  /**
   * Called when a new incoming INVITE is received.
   * 
   * @param uas
   */


  ServerSideCreator createServer(ClientSideCreator uas, IncomingInviteHandle handle);

  /**
   * 
   */

  ServerSideCreator createServer(DelayedClientSideCreator client);

}
