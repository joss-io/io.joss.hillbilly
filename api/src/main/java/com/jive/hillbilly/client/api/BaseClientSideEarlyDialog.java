package com.jive.hillbilly.client.api;


public interface BaseClientSideEarlyDialog
{

  /**
   * The reference to the dialog related to this branch.
   */

  Dialog dialog();

  /**
   * Send a progress notification on this early dialog.
   */

  void progress(final OriginationBranchProgressEvent e);

  /**
   * Accept this early dialog, e.g move to connected.
   */

  void accept(final OriginationBranchConnectEvent e);

  /**
   *
   */

  default void accept()
  {
    accept(new OriginationBranchConnectEvent(null));
  }

}
