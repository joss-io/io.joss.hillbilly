package com.jive.hillbilly.testing;

import java.util.List;

import org.junit.Assert;

import com.jive.bridje.sdp.SdpDirection;
import com.jive.bridje.sdp.SessionDescription;
import com.jive.hillbilly.SipWarning;
import com.jive.hillbilly.client.api.Dialog;
import com.jive.hillbilly.client.api.IncomingInviteHandle;
import com.jive.hillbilly.client.api.NegotiatedSession;
import com.jive.hillbilly.client.api.ReferHandle;
import com.jive.hillbilly.client.api.RenegotiationHandle;
import com.jive.hillbilly.client.api.RequestedOfferHandle;
import com.jive.myco.commons.concurrent.Pnky;

import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BaseFakeDialog implements NegotiatedSession
{

  @Getter
  protected SessionDescription redneckSession;

  @Getter
  protected SessionDescription endpointSession;

  protected PendingOffer pending;

  protected Dialog dialogHandle;

  protected final HillbillyTestRuntime test;

  private final String id;

  private Pnky<SessionDescription> weInitiated;

  public BaseFakeDialog(final HillbillyTestRuntime test, final String id)
  {
    this.test = test;
    this.id = id;
  }

  public void sendReplaces(final FakeClientSideCreator h)
  {
    this.dialogHandle.replaceWith(h);
  }

  @Override
  public void requestOffer(final RequestedOfferHandle handle)
  {
    // TODO Auto-generated method stub
  }

  /**
   * @return
   *
   */

  public Pnky<SessionDescription> sendOffer(final String sdp)
  {
    return this.sendOffer(SessionDescription.parse(sdp));
  }

  public Pnky<SessionDescription> sendOffer(final SdpDirection direction)
  {

    this.endpointSession = this.endpointSession
        .newVersion()
        .mutateMedia(0, m -> m.withDirection(direction));

    return this.sendOffer(this.endpointSession);

  }

  public void sendAnswer(final SdpDirection direction)
  {
    final SessionDescription clientAnswer = this.endpointSession
        .newVersion()
        .mutateMedia(0, m -> m.withDirection(direction));
    this.sendAnswer(clientAnswer);
  }

  @Value
  private static class PendingOffer
  {
    private String offer;
    private RenegotiationHandle handle;
  }

  @Override
  public void answer(final String offer, final RenegotiationHandle session)
  {
    Assert.assertNull(this.pending);
    // log.debug("\n[{}] GOT OFFER:\n\n{}", id, offer);
    this.pending = new PendingOffer(offer, session);
  }

  public void sendAnswer(final String answer)
  {
    // log.debug("\n[{}] SENDING ANSWER:\n\n{}", id, answer);
    this.test.sync();
    Assert.assertNotNull("Can't send answer when there is no pending offer", this.pending);
    this.pending.handle.answer(answer);
    this.pending = null;
    this.test.sync();
  }

  public void sendAnswer(final SessionDescription answer)
  {
    this.sendAnswer(answer.toString());
  }

  public void sendRefer(final ReferHandle r)
  {
    this.dialogHandle.refer(r);
  }

  public void sendReplaces(final IncomingInviteHandle uas)
  {
    this.dialogHandle.replaceWith(uas);
  }

  public boolean isWaitingForAnswer()
  {
    return this.weInitiated != null && !this.weInitiated.isDone();
  }

  public SessionDescription getPendingOffer()
  {
    Assert.assertNotNull("missing pending offer", this.pending);
    return SessionDescription.parse(this.pending.offer);
  }

  public Pnky<SessionDescription> sendOffer(final SessionDescription sessionDescription)
  {

    final String offer = sessionDescription.toString();


    this.weInitiated = Pnky.create();

    // log.debug("\n[{}] RECEVING OFFER:\n\n{}", id, sessionDescription.toString());

    this.dialogHandle.answer(offer, new RenegotiationHandle()
    {

      @Override
      public void reject(final List<SipWarning> warnings)
      {
        // log.debug("\n[{}] REJECTING ANSWER\n\n", id);
        BaseFakeDialog.this.weInitiated.reject(new RuntimeException("Offer was rejected"));
      }

      @Override
      public void answer(final String answer)
      {
        // log.debug("\n[{}] RECEVED ANSWER:\n\n{}", id, sessionDescription.toString());
        BaseFakeDialog.this.weInitiated.resolve(SessionDescription.parse(answer));
      }

    });

    this.test.sync();

    return this.weInitiated;

  }

  public boolean isPendingOffer()
  {
    return this.pending != null;
  }

}
