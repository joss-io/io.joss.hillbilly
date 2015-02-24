package com.jive.hillbilly.client;

import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.common.collect.Maps;
import com.google.common.primitives.UnsignedInteger;
import com.jive.myco.commons.concurrent.Pnky;
import com.jive.myco.commons.concurrent.PnkyPromise;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponse;
import com.jive.sip.transport.api.FlowId;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Performs retransmissions of 2xxs, and absorbs incoming ACKs.
 *
 * @author theo
 *
 */

@Slf4j
public class DialogTransmissionManager
{

  private final Map<UnsignedInteger, Pending> pending = Maps.newHashMap();
  private final Map<UnsignedInteger, Pending> pracks = Maps.newHashMap();
  private final HillbillyRuntimeService dispatcher;
  private boolean terminated = false;
  private EmbeddedDialog dialog;

  @Data
  @RequiredArgsConstructor
  private class Pending
  {
    final UnsignedInteger cseq;
    final HillbillyServerTransaction txn;
    final SipResponse response;
    int iterations = 1;
    Pnky<SipRequest> promise = Pnky.create();
    public HillbillyTimerHandle timer;

    public void reject(Exception ex)
    {
      if (timer != null)
      {
        timer.cancel();
        timer = null;
      }
      promise.reject(ex);
    }

    public void resolve(SipRequest request)
    {
      if (timer != null)
      {
        timer.cancel();
        timer = null;
      }
      promise.resolve(request);
    }

  }

  public DialogTransmissionManager(final HillbillyRuntimeService runtime, EmbeddedDialog dialog)
  {
    this.dispatcher = runtime;
    this.dialog = dialog;
  }

  Map<UnsignedInteger, Pending> getMap(SipResponse res)
  {
    return res.getStatus().isFinal() ? pending : pracks;
  }

  UnsignedInteger getKey(SipResponse res)
  {
    return UnsignedInteger.valueOf(res.getStatus().isFinal() ? res.getCSeq().longValue() : res.getRSeq().get());
  }

  public PnkyPromise<SipRequest> add(final HillbillyServerTransaction handle, final SipResponse res)
  {
    final Pending pending = new Pending(getKey(res), handle, res);
    getMap(res).put(pending.cseq, pending);
    handle.respond(res);
    // set timer to retransmit.
    pending.timer = this.dispatcher.schedule(() -> this.retransmit(pending), 500, TimeUnit.MILLISECONDS);
    return pending.promise;
  }

  private void retransmit(final Pending pending)
  {

    if (this.terminated || pending.promise.isDone())
    {
      return;
    }

    if (pending.iterations > 8)
    {
      // it's failed. give up.
      log.warn("Gicing up on retransmitting");
      getMap(pending.getResponse()).remove(pending.cseq);
      pending.reject(new TimeoutException());
      return;
    }

    ++pending.iterations;

    log.info("Retransmitting {}, as no [PR]ACK received in 500ms", pending.getResponse().getStatus().getCode());
    pending.txn.respond(pending.response);
    pending.timer = this.dispatcher.schedule(() -> this.retransmit(pending), 500, TimeUnit.MILLISECONDS);

  }

  public void process(final SipRequest ack, final FlowId flow)
  {

    final Pending pending = this.pending.remove(ack.getCSeq().getSequence());

    if (pending == null)
    {
      log.info("Dropping ACK with unregistered CSeq, presumably completed ...");
      return;
    }

    log.debug("Got ACK for dialog");

    pending.resolve(ack);

  }

  public boolean processPrack(HillbillyServerTransaction txn)
  {

    final Pending pending = this.pracks.remove(txn.getRequest().getRAck().get().getReliableSequence());

    if (pending == null)
    {
      log.info("Dropping PRACK with unregistered RSeq, presumably completed ...");
      return false;
    }

    log.debug("Got PRACK for dialog");

    pending.resolve(txn.getRequest());

    return true;

  }

  /**
   * aborts all retransmissions, as the dialog has terminated.
   */

  public void stop()
  {
    this.terminated = true;
    if (!this.pending.isEmpty())
    {
      log.info("Dialog had {} unACKed 2xx INVITE response(s)", this.pending.size());
      this.pending.forEach((k, v) -> v.reject(new CancellationException()));
    }
    if (!this.pracks.isEmpty())
    {
      log.info("Dialog had {} unPRACKed 1xx INVITE response(s)", this.pracks.size());
      this.pracks.forEach((k, v) -> v.reject(new CancellationException()));
    }
  }

}
