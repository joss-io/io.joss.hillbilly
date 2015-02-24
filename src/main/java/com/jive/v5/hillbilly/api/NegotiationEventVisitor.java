package com.jive.v5.hillbilly.api;

import com.jive.v5.hillbilly.api.negotiation.ProvideSdpAnswer;
import com.jive.v5.hillbilly.api.negotiation.RemoteSdpChanged;
import com.jive.v5.hillbilly.api.negotiation.RequestSdpOffer;
import com.jive.v5.hillbilly.api.negotiation.SdpAnswerRequired;
import com.jive.v5.hillbilly.api.negotiation.SdpOfferRejected;
import com.jive.v5.hillbilly.api.negotiation.SendSdpOffer;

public interface NegotiationEventVisitor
{

  void visit(ProvideSdpAnswer e);

  void visit(RemoteSdpChanged e);

  void visit(RequestSdpOffer e);

  void visit(SdpAnswerRequired e);

  void visit(SdpOfferRejected e);

  void visit(SendSdpOffer e);

}
