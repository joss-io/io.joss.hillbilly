package com.jive.hillbilly.client.api;

public interface Dialog extends NegotiatedSession
{

  /**
   * Send REFER.
   *
   * This can only be done once the dialog is connected. Any attempt to do it before will result in
   * an {@link IllegalStateException}. If thw dialog has terminated, then it will be rejected with
   * call does not exist.
   *
   */

  void refer(final ReferHandle h);

  /**
   * The dialog has become disconnected (for INVITE session).
   *
   * Note that a dialog may remain active even after it has become disconnected, due to active
   * subscription dialogs.
   *
   * This will always be called after a dialog is created, whatever the termination reason - and if
   * it was connected or not.
   *
   */

  void disconnect(final DisconnectEvent e);

  /**
   * called when we receive an INVITE w/Replaces. The handle provides a mechanism to accept/reject
   * it.
   */

  void replaceWith(final IncomingInviteHandle h);

}
