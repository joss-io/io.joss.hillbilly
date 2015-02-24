package com.jive.hillbilly.client.api;


public interface NegotiatedSession
{

  /**
   * requests that the other side sends an offer.
   * 
   * must result in a new offer being provided. The answer will then be delivered.
   * 
   */

  void requestOffer(RequestedOfferHandle handle);

  /**
   * provides a new offer, which must be answered.
   */

  void answer(String offer, RenegotiationHandle session);


}
