package com.jive.hillbilly.testing;

import java.util.List;

import com.google.common.collect.Lists;
import com.jive.bridje.sdp.SessionDescription;
import com.jive.hillbilly.SipReason;
import com.jive.hillbilly.api.ClientInviteOptions;
import com.jive.hillbilly.client.api.ClientSideCreator;
import com.jive.hillbilly.client.api.ServerSideCreator;
import com.jive.myco.commons.concurrent.Pnky;
import com.jive.sip.message.api.Reason;
import com.jive.sip.message.api.SipResponseStatus;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FakeServerSideCreator implements ServerSideCreator
{

  private enum State
  {
    Initial,
    Trying,
    Terminated
  }

  private State state = State.Initial;
  private final ClientInviteOptions opts;
  private final ClientSideCreator handler;
  private final String offer;
  private final Pnky<Reason> cancelled = Pnky.create();
  private boolean closed = false;
  private final List<FakeServerSideEarlyDialog> branches = Lists.newLinkedList();
  private final HillbillyTestRuntime test;
  private final String id;

  public FakeServerSideCreator(final HillbillyTestRuntime test, final ClientInviteOptions opts,
      final ClientSideCreator handler, final String offer, final String id)
  {
    this.test = test;
    this.opts = opts;
    this.handler = handler;
    this.offer = offer;
    this.id = id;
  }

  @Override
  public void cancel(final SipReason reason)
  {
    this.cancelled.resolve(ApiUtils.convert(reason));
  }

  public boolean isCancelled()
  {
    return this.cancelled.isDone();
  }

  public void reject(final SipResponseStatus status)
  {
    this.handler.reject(ApiUtils.convert(status));
    this.state = State.Terminated;
    this.closed = true;
  }

  public boolean isOpen()
  {
    switch (this.state)
    {
      case Initial:
      case Trying:
        return this.branches.stream().anyMatch(b -> b.isOpen());
      case Terminated:
        return false;
    }
    throw new RuntimeException();
  }

  public FakeServerSideEarlyDialog branch()
  {
    final FakeServerSideEarlyDialog branch =
        new FakeServerSideEarlyDialog(this.test, this, SessionDescription.parse(this.offer), this.id);
    branch.handler = this.handler.branch(branch, branch);
    return branch;
  }

}
