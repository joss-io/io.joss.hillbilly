package com.jive.hillbilly.api;

public interface HillbillyChannelSink
{

  /**
   * Sends the given event down the channel.
   * 
   * @param e
   */

  void send(HillbillyEvent e);

}
