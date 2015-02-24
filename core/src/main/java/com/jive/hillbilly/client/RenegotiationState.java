package com.jive.hillbilly.client;

public interface RenegotiationState
{

  public enum Initiator
  {
    Local,
    Remote
  }

  /**
   * Who initiated the renegotiation?
   */

  Initiator initiator();


}
