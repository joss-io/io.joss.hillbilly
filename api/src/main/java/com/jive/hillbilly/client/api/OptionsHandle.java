package com.jive.hillbilly.client.api;

import com.jive.hillbilly.SipStatus;

public interface OptionsHandle
{

  void accept(String body);

  void reject(SipStatus status);

}
