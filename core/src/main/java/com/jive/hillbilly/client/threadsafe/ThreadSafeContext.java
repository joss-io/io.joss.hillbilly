package com.jive.hillbilly.client.threadsafe;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * Hillbilly side if views as the API being the one initiating a client transaction.
 *
 * @author theo
 *
 */
public class ThreadSafeContext
{

  private final Executor hillbilly;
  private final Executor consumer;

  public ThreadSafeContext()
  {
    this(Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("hillbilly").build()),
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("api").build()));

  }

  public ThreadSafeContext(final Executor hillbilly, final Executor consumer)
  {
    this.hillbilly = hillbilly;
    this.consumer = consumer;
  }

  Executor hillbilly()
  {
    return this.hillbilly;
  }

  Executor consumer()
  {
    return this.consumer;
  }

  void hillbilly(final Runnable command)
  {
    this.hillbilly.execute(command);
  }

  void consumer(final Runnable command)
  {
    this.consumer.execute(command);
  }

  public ThreadSafeContext swap()
  {
    return new ThreadSafeContext(this.consumer, this.hillbilly);
  }

}
