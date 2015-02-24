package com.jive.hillbilly.testing;

import com.jive.bridje.sdp.SessionDescription;
import com.jive.hillbilly.client.api.ClientSideEarlyDialog;
import com.jive.hillbilly.client.api.Dialog;
import com.jive.hillbilly.client.api.DialogTerminationEvent;
import com.jive.hillbilly.client.api.DisconnectEvent;
import com.jive.hillbilly.client.api.IncomingInviteHandle;
import com.jive.hillbilly.client.api.NegotiatedSession;
import com.jive.hillbilly.client.api.OriginationBranchConnectEvent;
import com.jive.hillbilly.client.api.OriginationBranchProgressEvent;
import com.jive.hillbilly.client.api.ReferHandle;
import com.jive.hillbilly.client.api.ServerSideEarlyDialog;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.processor.rfc3261.MutableSipResponse;
import com.jive.sip.processor.rfc3261.RfcSipMessageManagerBuilder;
import com.jive.sip.processor.rfc3261.SipMessageManager;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FakeServerSideEarlyDialog extends BaseFakeDialog implements ServerSideEarlyDialog,
NegotiatedSession, Dialog
{

  private static SipMessageManager mm = new RfcSipMessageManagerBuilder().build();

  private enum State
  {

    Initial,
    Ringing,
    Connected,
    Terminated
  }

  @Getter
  private State state = State.Initial;
  public ClientSideEarlyDialog handler;

  public FakeServerSideEarlyDialog(final HillbillyTestRuntime test, final FakeServerSideCreator creator,
      final SessionDescription remote, final String id)
  {
    super(test, id);
    this.redneckSession = remote;
  }

  @Override
  public void end(final DialogTerminationEvent e)
  {
    // TODO Auto-generated method stub
  }

  public void progress(final SipResponseStatus status)
  {
    this.state = State.Ringing;
    this.handler.progress(new OriginationBranchProgressEvent(ApiUtils.convert(MutableSipResponse.createResponse(status).build(mm))));
  }

  public void answer(final String remoteAnswer)
  {
    this.endpointSession = SessionDescription.parse(remoteAnswer);
    this.handler.answer(remoteAnswer);

    FakeServerSideEarlyDialog.this.state = State.Connected;
    FakeServerSideEarlyDialog.this.dialogHandle = FakeServerSideEarlyDialog.this.handler.dialog();

    final MutableSipResponse res2xx = MutableSipResponse.createResponse(SipResponseStatus.OK);

    FakeServerSideEarlyDialog.this.handler.accept(new OriginationBranchConnectEvent(ApiUtils.convert(res2xx.build(mm))));
  }

  /**
   * emulates a BYE being received from the network.
   */

  public void bye()
  {
    this.state = State.Terminated;
    this.dialogHandle.disconnect(new DisconnectEvent(ApiUtils.convert(SipResponseStatus.OK)));
  }


  @Override
  public void refer(final ReferHandle h)
  {
    // TODO Auto-generated method stub
  }

  @Override
  public void disconnect(final DisconnectEvent e)
  {
    log.debug(" -> Disconnect");
    this.state = State.Terminated;
  }

  public boolean isOpen()
  {
    switch (this.state)
    {
      case Initial:
      case Ringing:
      case Connected:
        return true;
      case Terminated:
        return false;
    }
    throw new RuntimeException();
  }

  @Override
  public void replaceWith(final IncomingInviteHandle uas)
  {
    uas.process(null);
  }



}
