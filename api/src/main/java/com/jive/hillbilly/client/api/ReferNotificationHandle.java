package com.jive.hillbilly.client.api;

import com.jive.hillbilly.SipStatus;

public interface ReferNotificationHandle
{

  void update(SipStatus trying);

  void close();


}
