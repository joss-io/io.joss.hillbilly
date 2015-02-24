package com.jive.hillbilly.api;

import com.jive.hillbilly.api.negotiation.ProvideSdpAnswer;
import com.jive.hillbilly.api.negotiation.RemoteSdpChanged;
import com.jive.hillbilly.api.negotiation.RequestSdpOffer;
import com.jive.hillbilly.api.negotiation.SdpAnswerRequired;
import com.jive.hillbilly.api.negotiation.SdpOfferRejected;
import com.jive.hillbilly.api.negotiation.SendSdpOffer;

public interface NegotiationEventVisitor
{

  void visit(ProvideSdpAnswer e);

  void visit(RemoteSdpChanged e);

  void visit(RequestSdpOffer e);

  void visit(SdpAnswerRequired e);

  void visit(SdpOfferRejected e);

  void visit(SendSdpOffer e);

}
