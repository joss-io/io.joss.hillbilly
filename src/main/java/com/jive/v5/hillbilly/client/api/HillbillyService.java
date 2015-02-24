package com.jive.v5.hillbilly.client.api;

import com.jive.v5.hillbilly.client.ClientInviteOptions;

public interface HillbillyService
{

  /**
   * Creates a new outgoing INVITE using non-delayed media, meaning that the INVITE will contain an
   * offer.
   */



  ServerSideCreator createClient(ClientInviteOptions opts, DelayedClientSideCreator client);

  ServerSideCreator createClient(ClientInviteOptions opts, ClientSideCreator handler, String offer);

}
