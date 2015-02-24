package com.jive.v5.hillbilly.api;

public interface HillbillyChannelSink
{

  /**
   * Sends the given event down the channel.
   * 
   * @param e
   */

  void send(HillbillyEvent e);

}
