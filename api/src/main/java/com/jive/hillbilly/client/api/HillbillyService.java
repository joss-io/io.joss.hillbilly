package com.jive.hillbilly.client.api;

import com.jive.hillbilly.api.ClientInviteOptions;

public interface HillbillyService
{

  /**
   * Creates a new outgoing INVITE using non-delayed media, meaning that the INVITE will contain an
   * offer.
   */



  ServerSideCreator createClient(final ClientInviteOptions opts, final DelayedClientSideCreator client);

  ServerSideCreator createClient(final ClientInviteOptions opts, final ClientSideCreator handler, final String offer);


}
