package com.jive.hillbilly.testing;

import java.util.List;
import java.util.Optional;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jive.hillbilly.SipStatus;
import com.jive.hillbilly.api.Address;
import com.jive.hillbilly.client.api.ReferHandle;
import com.jive.hillbilly.client.api.ReferNotificationHandle;
import com.jive.sip.message.api.NameAddr;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.uri.api.SipUri;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FakeReferHandle implements ReferHandle, ReferNotificationHandle
{

  @Getter
  private SipResponseStatus response;

  @Getter
  private List<SipResponseStatus> notifies = Lists.newLinkedList();

  @Getter
  private boolean closed = false;

  @Override
  public Address referTo()
  {
    return ApiUtils.convert(new NameAddr(SipUri.ANONYMOUS));
  }

  @Override
  public Optional<Address> referredBy()
  {
    return null;
  }

  @Override
  public void reject(SipStatus status)
  {
    Preconditions.checkArgument(status.isFailure());
    this.response = ApiUtils.convert(status);
  }

  @Override
  public ReferNotificationHandle accept(SipStatus status)
  {
    Preconditions.checkArgument(status.isSuccess());
    this.response = ApiUtils.convert(status);
    return this;
  }

  @Override
  public void update(SipStatus status)
  {
    log.debug(" ---> {}", status);
    this.notifies.add(ApiUtils.convert(status));
  }

  @Override
  public void close()
  {
    closed = true;
  }

}
