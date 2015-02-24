package com.jive.hillbilly.client.api;

import com.jive.hillbilly.Request;
import com.jive.hillbilly.api.Address;

public interface IncomingInviteHandle
{

  /**
   * The SIP request that triggered this INVITE.
   */

  Request invite();

  /**
   * The SDP offer.
   */

  String offer();

  /**
   * trigger the 100 trying to indicate the remote side has received the request.
   */

  ClientSideCreator process(final ServerSideCreator creator);

  /**
   * The value of the "To" header in the INVITE.
   */

  Address localIdentity();

  /**
   * The value to of the "From" header in the INVITE.
   */

  Address remoteIdentity();

  /**
   * The request URI.
   */

  String uri();

  /**
   * The network namespace this INVITE was received on.
   */

  String netns();

  ClientSideCreator client();


}
