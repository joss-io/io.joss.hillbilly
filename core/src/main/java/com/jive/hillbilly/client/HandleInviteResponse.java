package com.jive.hillbilly.client;

import java.util.Optional;

import com.jive.hillbilly.client.api.RequiredAnswerHandle;
import com.jive.sip.dummer.txn.ClientTransactionListener;
import com.jive.sip.dummer.txn.SipTransactionErrorInfo;
import com.jive.sip.dummer.txn.SipTransactionResponseInfo;
import com.jive.sip.message.api.SipResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HandleInviteResponse implements ClientTransactionListener
{

  private final EmbeddedInviteUsage usage;
  private final String offer;
  private final RequiredAnswerHandle session;
  private boolean completed = false;

  public HandleInviteResponse(final EmbeddedInviteUsage usage, final String offer, final RequiredAnswerHandle session)
  {
    this.usage = usage;
    this.offer = offer;
    this.session = session;
  }

  @Override
  public void onResponse(final SipTransactionResponseInfo ctx)
  {

    final SipResponse res = ctx.getResponse();

    if (!res.getStatus().isFinal())
    {
      log.debug("Ignoring provisional in-dialog to INVITE {}", res);
      return;
    }

    // failure, erp.
    if (res.getStatus().isFailure())
    {
      log.warn("Failure {} for local initiated INVITE w/Offer", res.getStatus());
      this.session.rejected();
      return;
    }

    if (res.getStatus().isSuccess())
    {

      final Optional<String> sdp = HillbillyHelpers.getSessionDescription(res);

      if (!sdp.isPresent())
      {
        log.warn("INVITE 2xx without SDP answer?");
        // don't bother ACKing, so it times out.
        return;
      }

      this.usage.getDialog().ack(res);

      if (this.completed)
      {
        return;
      }

      this.completed = true;

      this.session.answer(sdp.get());

      this.usage.negotiated(this.offer, sdp.get());

    }

  }

  @Override
  public void onError(final SipTransactionErrorInfo err)
  {
    this.session.rejected();
  }

}
