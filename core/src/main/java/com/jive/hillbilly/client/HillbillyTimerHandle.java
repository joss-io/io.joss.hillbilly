package com.jive.hillbilly.client;

/**
 * A handle to a timer, which allows a potential cancellation.
 *
 * @author theo
 */

@FunctionalInterface
public interface HillbillyTimerHandle
{

  /**
   * Attempts to cancel this timer. If it can be cancelled, then true is returned, otherwise false
   * (and dispatch should shortly occur).
   */

  boolean cancel();

}
