package com.jive.hillbilly.client;

import java.util.concurrent.Executor;

import lombok.extern.slf4j.Slf4j;

/**
 * om nom nom.
 *
 * @author theo
 */

@Slf4j
public class EatAndLog
{

  public static void run(final Executor executor, final Runnable command)
  {
    try
    {
      executor.execute(() -> command.run());
    }
    catch (final Exception ex)
    {
      log.error(String.format("Exception caught processing callback '%s'", ex.getClass()), ex);
    }
  }
}
