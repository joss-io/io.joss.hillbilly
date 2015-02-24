package com.jive.hillbilly.testing;

import lombok.extern.slf4j.Slf4j;

import com.jive.bridje.sdp.SessionDescription;
import com.jive.hillbilly.client.api.ClientSideEarlyDialog;
import com.jive.hillbilly.client.api.Dialog;
import com.jive.hillbilly.client.api.DisconnectEvent;
import com.jive.hillbilly.client.api.IncomingInviteHandle;
import com.jive.hillbilly.client.api.NegotiatedSession;
import com.jive.hillbilly.client.api.OriginationBranchConnectEvent;
import com.jive.hillbilly.client.api.OriginationBranchProgressEvent;
import com.jive.hillbilly.client.api.ReferHandle;
import com.jive.hillbilly.client.api.ServerSideEarlyDialog;

@Slf4j
public class FakeClientSideEarlyDialog extends BaseFakeDialog implements ClientSideEarlyDialog,
Dialog, NegotiatedSession
{

  private enum State
  {
    Initial,
    Trying,
    Ringing,
    Connected,
    Terminated
  }

  private State state = State.Initial;
  private boolean negotiated;

  public FakeClientSideEarlyDialog(
      final HillbillyTestRuntime test,
      final ServerSideEarlyDialog branch,
      final SessionDescription local,
      final String id,
      final Dialog remote)
  {
    super(test, id);
    this.endpointSession = local;
    this.dialogHandle = remote;
  }


  @Override
  public void progress(final OriginationBranchProgressEvent status)
  {
    log.debug("progress");
    this.state = State.Ringing;
  }

  @Override
  public void accept(final OriginationBranchConnectEvent e)
  {
    log.debug("accept");
    this.state = State.Connected;
  }


  @Override
  public void answer(final String sdp)
  {
    log.debug("answer");
    this.negotiated = true;
    this.redneckSession = SessionDescription.parse(sdp);
  }

  public boolean isNegotiated()
  {
    return this.negotiated;
  }

  public boolean isRinging()
  {
    return this.state == State.Ringing;
  }

  public boolean isConnected()
  {
    return this.state == State.Connected;
  }

  public boolean isTerminated()
  {
    return this.state == State.Terminated;
  }

  @Override
  public void refer(final ReferHandle h)
  {
    log.debug("refer");
  }

  @Override
  public void disconnect(final DisconnectEvent e)
  {
    log.debug("disconnect");
    this.state = State.Terminated;
  }

  public boolean isOpen()
  {
    return !this.isTerminated();
  }

  public void bye()
  {
    log.debug("bye");
    this.state = State.Terminated;
    this.dialogHandle.disconnect(DisconnectEvent.OK);
  }

  @Override
  public void replaceWith(final IncomingInviteHandle uas)
  {
    log.debug("replaceWith");
    uas.process(null);
  }

  @Override
  public Dialog dialog()
  {
    return this;
  }


}
