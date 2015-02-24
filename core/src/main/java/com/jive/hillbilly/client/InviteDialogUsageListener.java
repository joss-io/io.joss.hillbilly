package com.jive.hillbilly.client;

import com.jive.hillbilly.client.api.DialogListener;
import com.jive.hillbilly.client.api.DisconnectEvent;

/**
 * Events related to the INVITE session.
 *
 * @author theo
 */

public interface InviteDialogUsageListener extends DialogListener
{

  /**
   * The INVITE session has become established. this is the result of a 200OK to an INVITE that has
   * been negotiated, or on the ACK for a delayed INVITE.
   */

  default void established()
  {

  }

  /**
   * The INVITE session has has become disconnected.
   *
   * This will be called whenever it ends, for whatever reason.
   *
   * @param e
   *
   */

  default void disconnected(final DisconnectEvent e)
  {

  }

}
