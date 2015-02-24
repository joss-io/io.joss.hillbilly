package com.jive.hillbilly.client;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public interface HillbillyRuntimeService extends Executor
{

  /**
   * Schedules a command to be run.
   */

  HillbillyTimerHandle schedule(final Runnable command, final long duration, final TimeUnit unit);


}
