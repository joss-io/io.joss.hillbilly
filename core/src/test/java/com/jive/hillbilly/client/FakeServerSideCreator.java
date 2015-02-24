package com.jive.hillbilly.client;

import com.jive.hillbilly.SipReason;
import com.jive.hillbilly.api.ClientInviteOptions;
import com.jive.hillbilly.client.ApiUtils;
import com.jive.hillbilly.client.api.ClientAlreadyConnectedException;
import com.jive.hillbilly.client.api.ClientSideCreator;
import com.jive.hillbilly.client.api.ServerSideCreator;
import com.jive.sip.message.api.SipResponseStatus;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FakeServerSideCreator implements ServerSideCreator
{

  private final ClientSideCreator handler;

  public FakeServerSideCreator(final ClientInviteOptions opts, final ClientSideCreator handler, final String offer)
  {
    this.handler = handler;
  }

  @Override
  public void cancel(final SipReason reason) throws ClientAlreadyConnectedException
  {
    log.debug("Cancelling");
    this.handler.reject(ApiUtils.convert(SipResponseStatus.REQUEST_TERMINATED));
  }

}
