package com.jive.hillbilly.client;

import com.jive.hillbilly.client.api.ClientSideEarlyDialog;
import com.jive.hillbilly.client.api.Dialog;
import com.jive.hillbilly.client.api.DisconnectEvent;
import com.jive.hillbilly.client.api.IncomingInviteHandle;
import com.jive.hillbilly.client.api.NegotiatedSession;
import com.jive.hillbilly.client.api.OriginationBranchConnectEvent;
import com.jive.hillbilly.client.api.OriginationBranchProgressEvent;
import com.jive.hillbilly.client.api.ReferHandle;
import com.jive.hillbilly.client.api.RenegotiationHandle;
import com.jive.hillbilly.client.api.RequestedOfferHandle;
import com.jive.hillbilly.client.api.ServerSideEarlyDialog;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestClientBranch implements ClientSideEarlyDialog, Dialog, NegotiatedSession
{

  public enum Status
  {
    INITIAL,
    RINGING,
    PROGRESS,
    ACCEPTED,
    TERMINATED
  }

  @Getter
  private Status status = Status.INITIAL;

  @Getter
  private String remote;

  @Getter
  private final ServerSideEarlyDialog handle;

  @Getter
  private final Dialog dialog;

  @Getter
  private RenegotiationHandle renegotiation;

  public TestClientBranch(final ServerSideEarlyDialog handle, final Dialog dialog)
  {
    this.handle = handle;
    this.dialog = dialog;
  }

  @Override
  public void progress(final OriginationBranchProgressEvent res)
  {
    this.status = Status.RINGING;
  }

  @Override
  public void accept(final OriginationBranchConnectEvent e)
  {
    this.status = Status.ACCEPTED;
  }

  @Override
  public void refer(final ReferHandle h)
  {
    log.debug("Dialog transferred");
  }

  @Override
  public void disconnect(final DisconnectEvent e)
  {
    log.debug("Dialog disconnected");
  }

  @Override
  public void answer(final String sdp)
  {
    log.info("got SDP answer: {}", sdp);
    this.remote = sdp;
  }

  @Override
  public void requestOffer(final RequestedOfferHandle handle)
  {
    log.debug("Offer requested");
    handle.rejected();
  }

  @Override
  public void answer(final String offer, final RenegotiationHandle session)
  {
    log.debug("SDP offered");
    this.remote = offer;
    this.renegotiation = session;
  }

  @Override
  public void replaceWith(final IncomingInviteHandle uas)
  {
    uas.process(null);
  }

  @Override
  public Dialog dialog()
  {
    return this;
  }

}
