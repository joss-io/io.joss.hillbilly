package com.jive.v5.hillbilly.client.api;

public interface NegotiatedSession
{

  /**
   * requests that the other side sends an offer.
   * 
   * must result in a new offer being sent.
   * 
   */

  void sendOffer();

  /**
   * called when the other side responds with a new answer to our previous SDP.
   */

  void remoteChanged(String answer);

  /**
   * provides a new offer, which must be answered.
   */

  void answer(String offer, RenegotiationHandle session);

}
