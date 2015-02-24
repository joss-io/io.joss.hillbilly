package com.jive.hillbilly.client.api;

import java.util.Optional;

import com.jive.hillbilly.SipStatus;
import com.jive.hillbilly.api.Address;

public interface ReferHandle
{

  /**
   * The Refer-To header value.
   */

  Address referTo();

  /**
   * The Referred-By value.
   */

  Optional<Address> referredBy();

  /**
   * Reject this REFER.
   */

  void reject(final SipStatus status);

  /**
   * Accept this REFER. Once accepted, the client must deliver updates using the provided
   * notification handle.
   */

  ReferNotificationHandle accept(final SipStatus status);

}
