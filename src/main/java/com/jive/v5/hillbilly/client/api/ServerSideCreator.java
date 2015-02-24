package com.jive.v5.hillbilly.client.api;

import com.jive.sip.message.api.Reason;

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

public interface ServerSideCreator
{

  /**
   * CANCEL the server side creator.
   */

  void cancel(Reason reason);

}
