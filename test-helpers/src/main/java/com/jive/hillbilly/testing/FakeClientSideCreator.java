package com.jive.hillbilly.testing;

import java.util.List;

import com.google.common.collect.Lists;
import com.jive.bridje.sdp.SessionDescription;
import com.jive.hillbilly.Request;
import com.jive.hillbilly.SipStatus;
import com.jive.hillbilly.SipWarning;
import com.jive.hillbilly.api.Address;
import com.jive.hillbilly.client.api.ClientAlreadyConnectedException;
import com.jive.hillbilly.client.api.ClientSideCreator;
import com.jive.hillbilly.client.api.ClientSideEarlyDialog;
import com.jive.hillbilly.client.api.Dialog;
import com.jive.hillbilly.client.api.IncomingInviteHandle;
import com.jive.hillbilly.client.api.ServerSideCreator;
import com.jive.hillbilly.client.api.ServerSideEarlyDialog;
import com.jive.sip.message.api.NameAddr;
import com.jive.sip.message.api.Reason;
import com.jive.sip.message.api.SipMethod;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.processor.rfc3261.MutableSipRequest;
import com.jive.sip.processor.rfc3261.RfcSipMessageManagerBuilder;
import com.jive.sip.processor.rfc3261.SipMessageManager;
import com.jive.sip.uri.api.SipUri;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FakeClientSideCreator implements ClientSideCreator, IncomingInviteHandle
{

  private final SipMessageManager mm = new RfcSipMessageManagerBuilder().build();

  private final String offer;

  @Getter
  private ServerSideCreator handle;

  @Getter
  private final List<FakeClientSideEarlyDialog> branches = Lists.newLinkedList();

  @Getter
  private SipResponseStatus rejected;

  private final HillbillyTestRuntime test;

  private final String id;

  public FakeClientSideCreator(final HillbillyTestRuntime test, final String offer, final String id)
  {
    this.offer = offer;
    this.test = test;
    this.id = id;
  }

  @Override
  public void reject(final SipStatus status, final List<SipWarning> warnings)
  {
    log.debug("rejected");
    this.rejected = ApiUtils.convert(status);
  }

  @Override
  public ClientSideEarlyDialog branch(final ServerSideEarlyDialog handler, final Dialog remote)
  {
    log.debug("branched");
    final FakeClientSideEarlyDialog branch =
        new FakeClientSideEarlyDialog(this.test, handler, SessionDescription.parse(this.offer), this.id, remote);
    this.branches.add(branch);
    return branch;
  }

  @Override
  public String offer()
  {
    return this.offer;
  }

  @Override
  public ClientSideCreator process(final ServerSideCreator creator)
  {
    log.debug("Processing");
    this.handle = creator;
    return this;
  }

  public void cancel(final Reason reason) throws ClientAlreadyConnectedException
  {
    this.handle.cancel(ApiUtils.convert(reason));
  }

  public boolean isRejected()
  {
    return this.rejected != null;
  }

  public boolean isOpen()
  {
    if (this.rejected != null)
    {
      return false;
    }
    return this.branches.stream().anyMatch(b -> b.isOpen());
  }

  @Override
  public Address localIdentity()
  {
    return ApiUtils.convert(new NameAddr(SipUri.ANONYMOUS));
  }

  @Override
  public Address remoteIdentity()
  {
    return ApiUtils.convert(new NameAddr(SipUri.ANONYMOUS));
  }

  @Override
  public String uri()
  {
    return ApiUtils.convert(SipUri.ANONYMOUS);
  }

  @Override
  public String netns()
  {
    return "test1";
  }

  @Override
  public ClientSideCreator client()
  {
    return this;
  }

  @Override
  public Request invite()
  {
    final MutableSipRequest req = MutableSipRequest.create(SipMethod.INVITE, ApiUtils.uri(this.uri()));
    req.callId("test-callid");
    req.session("xxxx");
    req.from(SipUri.ANONYMOUS, "a");
    req.to(SipUri.ANONYMOUS);
    return ApiUtils.convert(req.build(this.mm));
  }

}
