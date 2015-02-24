package com.jive.hillbilly.client.api;


/**
 * called when negotiation is completed to establish the local/remote negotiated session.
 */

public interface NegotiationCompletedHandle
{

  void accept(NegotiatedSession session);

}
