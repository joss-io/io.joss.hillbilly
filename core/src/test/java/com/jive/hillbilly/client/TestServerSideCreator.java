package com.jive.hillbilly.client;

import lombok.extern.slf4j.Slf4j;

import com.jive.hillbilly.SipReason;
import com.jive.hillbilly.client.api.ServerSideCreator;
import com.jive.sip.message.api.Reason;

@Slf4j
public class TestServerSideCreator implements ServerSideCreator
{

  @Override
  public void cancel(SipReason reason)
  {
    log.debug("UAS was cancelled");
  }


}
