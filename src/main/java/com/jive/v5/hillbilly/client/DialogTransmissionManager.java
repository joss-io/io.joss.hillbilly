package com.jive.v5.hillbilly.client;

import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.fusesource.hawtdispatch.DispatchQueue;

import com.google.common.collect.Maps;
import com.google.common.primitives.UnsignedInteger;
import com.jive.ftw.sip.dummer.txn.ServerTransactionHandle;
import com.jive.myco.commons.concurrent.Pnky;
import com.jive.myco.commons.concurrent.PnkyPromise;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponse;
import com.jive.sip.transport.api.FlowId;

/**
 * Performs retransmissions of 2xxs, and absorbs incoming ACKs.
 * 
 * @author theo
 *
 */

@Slf4j
public class DialogTransmissionManager
{

  private Map<UnsignedInteger, Pending> pending = Maps.newHashMap();
  private DispatchQueue dispatcher;
  private boolean terminated = false;

  @Data
  @RequiredArgsConstructor
  private class Pending
  {
    final UnsignedInteger cseq;
    final ServerTransactionHandle txn;
    final SipResponse response;
    int iterations = 1;
    Pnky<SipRequest> promise = Pnky.create();

  }

  public DialogTransmissionManager(DispatchQueue dispatcher)
  {
    this.dispatcher = dispatcher;
  }

  public PnkyPromise<SipRequest> add(ServerTransactionHandle handle, SipResponse res2xx)
  {
    Pending pending = new Pending(res2xx.getCSeq().getSequence(), handle, res2xx);
    this.pending.put(pending.cseq, pending);
    handle.respond(res2xx);
    // set timer to retransmit.
    dispatcher.executeAfter(500, TimeUnit.MILLISECONDS, () -> retransmit(pending));
    return pending.promise;
  }

  private void retransmit(Pending pending)
  {

    if (this.terminated || pending.promise.isDone())
    {
      return;
    }

    if (pending.iterations > 8)
    {
      // it's failed. give up.
      this.pending.remove(pending.cseq);
      pending.promise.reject(new TimeoutException());
      return;
    }

    ++pending.iterations;

    log.info("Retransmitting 2xx, as no ACK received in 500ms");
    pending.txn.respond(pending.response);
    dispatcher.executeAfter(500, TimeUnit.MILLISECONDS, () -> retransmit(pending));

  }

  public void process(SipRequest ack, FlowId flow)
  {

    Pending pending = this.pending.remove(ack.getCSeq().getSequence());

    if (pending == null)
    {
      log.info("Dropping ACK with unregistered CSeq, presumably completed ...");
      return;
    }

    log.debug("Got ACK for dialog");

    pending.promise.resolve(ack);

  }

  /**
   * aborts all retransmissions, as the dialog has terminated.
   */

  public void stop()
  {
    this.terminated = true;
    if (!pending.isEmpty())
    {
      log.info("Dialog had {} unACKed 2xx INVITE response(s)", pending.size());
      pending.forEach((k, v) -> v.promise.reject(new CancellationException()));
    }
  }

}
