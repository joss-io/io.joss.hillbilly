package com.jive.v5.hillbilly.client;

import com.jive.v5.hillbilly.client.api.LocalNegotiatedSession;
import com.jive.v5.hillbilly.client.api.RemoteNegotiatedSession;
import com.jive.v5.hillbilly.client.api.RenegotiationHandle;

public class TestNegotiatedSession implements LocalNegotiatedSession
{

  private RemoteNegotiatedSession remote;

  public TestNegotiatedSession(RemoteNegotiatedSession remote)
  {
    this.remote = remote;
  }

  @Override
  public void sendOffer()
  {

    remote.answer("[offer]", new RenegotiationHandle()
    {

      @Override
      public void reject()
      {
      }

      @Override
      public void answer(String answer)
      {
      }

    });

  }

  @Override
  public void remoteChanged(String answer)
  {
  }

  @Override
  public void answer(String offer, RenegotiationHandle session)
  {
    session.answer("[local]");
  }

}
