package com.jive.hillbilly.client.api;

public interface RequestedOfferHandle
{

  /**
   * failed to request an offer.
   */

  void rejected();

  /**
   * The offer, which must now be answered. Note that the offer can't be rejected, and instead a
   * syntactically valid answer must be provided followed by an update to renegotiate.
   */

  void answer(String offer, RequiredAnswerHandle session);

}
