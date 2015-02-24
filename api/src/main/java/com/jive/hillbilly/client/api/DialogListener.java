package com.jive.hillbilly.client.api;

public interface DialogListener
{

  /**
   * The dialog has become terminated, meaning it will no longer access incoming requests, or able
   * to send any external ones. all resources should be freed.
   */

  default void terminated()
  {

  }

}
