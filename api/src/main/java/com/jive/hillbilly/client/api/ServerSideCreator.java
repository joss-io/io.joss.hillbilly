package com.jive.hillbilly.client.api;

import com.jive.hillbilly.SipReason;

/**
 * The server side of a creator.
 * 
 * For an incoming INVITE, this is implemented by the hillbilly consumer.
 * 
 * For outgoing INVITE this is the API used by the consumer to drive it.
 * 
 * @author theo
 *
 */

public interface ServerSideCreator extends BaseCreator
{

  /**
   * CANCEL the server side creator.
   * 
   * If this is called while there is a pending notification of a winning dialog (e.g,
   * {@link ClientSideEarlyDialog#accept(Dialog)} has been dispatched or the call has already been
   * notified of a branch winning, then {@link IllegalStateException} will be thrown.
   * 
   * It is then the responsibility of the consumer to hangup the call.
   * 
   */

  void cancel(SipReason reason) throws ClientAlreadyConnectedException;

}
