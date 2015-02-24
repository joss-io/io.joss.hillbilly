package com.jive.v5.hillbilly.client;

import com.jive.v5.hillbilly.client.api.ClientSideCreator;
import com.jive.v5.hillbilly.client.api.DelayedClientSideCreator;
import com.jive.v5.hillbilly.client.api.HillbillyService;
import com.jive.v5.hillbilly.client.api.ServerSideCreator;

public class FakeHillbillyService implements HillbillyService
{

  @Override
  public ServerSideCreator createClient(
      ClientInviteOptions opts,
      DelayedClientSideCreator client)
  {
    return null;
  }

  @Override
  public ServerSideCreator createClient(
      ClientInviteOptions opts,
      ClientSideCreator handler,
      String offer)
  {
    return null;
  }

}
