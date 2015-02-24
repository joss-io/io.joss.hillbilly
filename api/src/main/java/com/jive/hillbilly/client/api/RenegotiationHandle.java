package com.jive.hillbilly.client.api;

import java.util.Collections;
import java.util.List;

import com.jive.hillbilly.SipWarning;

public interface RenegotiationHandle
{

  void answer(String answer);

  void reject(List<SipWarning> warnings);

  default void reject()
  {
    reject(Collections.emptyList());
  }

}
