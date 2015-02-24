package com.jive.v5.hillbilly.client.api;


public interface RemoteInitiatedNegotiationSession
{

  RemoteNegotiatedSession answer(String string, LocalNegotiatedSession local);

  void reject();

}
