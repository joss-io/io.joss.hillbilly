package com.jive.hillbilly.client.api;

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

  /**
   * process an out of dialog REFER.
   */

  void processRefer(String uri, ReferHandle referHandle);

  default void processOptions(OptionsHandle e)
  {
    e.accept(null);
  }

}
