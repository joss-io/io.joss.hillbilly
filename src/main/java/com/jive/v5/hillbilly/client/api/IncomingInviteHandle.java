package com.jive.v5.hillbilly.client.api;

public interface IncomingInviteHandle
{

  String offer();

  ClientSideCreator process(ServerSideCreator creator);

}
