package com.jive.v5.hillbilly.client;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.RandomStringUtils;
import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.DispatchQueue;

import com.google.common.base.Charsets;
import com.google.common.primitives.UnsignedInteger;
import com.jive.ftw.sip.dummer.session.DialogInfo;
import com.jive.ftw.sip.dummer.txn.ClientTransactionListener;
import com.jive.ftw.sip.dummer.txn.ServerTransactionHandle;
import com.jive.ftw.sip.dummer.txn.SipTransactionErrorInfo;
import com.jive.ftw.sip.dummer.txn.SipTransactionResponseInfo;
import com.jive.sip.message.api.DialogId;
import com.jive.sip.message.api.SipMethod;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponse;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.processor.rfc3261.MutableSipRequest;
import com.jive.sip.processor.rfc3261.MutableSipResponse;
import com.jive.sip.transport.api.FlowId;
import com.jive.v5.hillbilly.client.api.Dialog;

@Slf4j
public class EmbeddedDialog implements Dialog
{

  private DispatchQueue queue = Dispatch.createQueue("dialog");
  private DialogTransmissionManager txmit = new DialogTransmissionManager(queue);
  private DialogId id;
  private DialogInfo info;
  private DialogState state;
  private EmbeddedNetworkSegment network;

  public EmbeddedDialog(
      EmbeddedNetworkSegment network,
      DialogId id,
      DialogInfo info,
      DialogState state)
  {
    this.network = network;
    this.id = id;
    this.info = info;
    this.state = state;
  }

  MutableSipRequest createRequest(SipMethod method)
  {
    MutableSipRequest req = MutableSipRequest.create(method, info.getRemote());
    req.cseq(allocateSequence(), method);
    applyDialog(req, method);
    return req;
  }

  void applyDialog(MutableSipRequest req, SipMethod method)
  {
    req.callId(id.getCallId().getValue());
    req.to(info.getRemoteName());
    req.from(info.getLocalName());
  }

  private UnsignedInteger allocateSequence()
  {
    UnsignedInteger seq = state.getLocalSequence().plus(UnsignedInteger.ONE);
    state = state.withLocalSequence(seq);
    return seq;
  }

  public void ack(SipResponse res)
  {

    MutableSipRequest ack =
        MutableSipRequest.create(SipMethod.ACK, res.getContacts().get().iterator().next()
            .getAddress());

    applyDialog(ack, SipMethod.ACK);

    ack.cseq(res.getCSeq().getSequence(), SipMethod.ACK);

    log.debug("Transmissing ACK");

    network.transmit(ack, RandomStringUtils.randomAlphanumeric(32));

    // segment.transmit(ack, null, RandomStringUtils.randomAlphanumeric(32));

  }

  /**
   * API requested a REFER.
   */

  @Override
  public void refer()
  {
    // TODO Auto-generated method stub
  }

  /**
   * API requested disconnect.
   */

  @Override
  public void disconnect()
  {

    MutableSipRequest req = createRequest(SipMethod.BYE);

    // req.reason(reason);

    this.txmit.stop();

    // listeners.dispatch(l -> l.disconnected(new LocallyRequestedDialogTerminationEvent(reason)));

    send(req, new ClientTransactionListener()
    {

      @Override
      public void onResponse(SipTransactionResponseInfo e)
      {
      }

      @Override
      public void onError(SipTransactionErrorInfo e)
      {
      }

    });

  }

  public void processAck(SipRequest req, FlowId flow)
  {
    txmit.process(req, flow);
  }

  public void processRequest(ServerTransactionHandle txn)
  {

    log.debug("in-dialog request: {} from {}", txn.getRequest(), txn.getFlowId());

    SipRequest req = txn.getRequest();

    switch (req.getMethod().getMethod())
    {

      case "INVITE":

        // reINVITE. Pass to media engine to provide the answer (or offer, if the INVITE is missing
        // one).

        log.debug("Got reINVITE");

        if (req.getBody() == null || req.getBody().length == 0)
        {
          log.warn("Rejected delayed reINVITE");
          txn.respond(SipResponseStatus.BAD_REQUEST);
          return;
        }

        processIncomingReinvite(txn);

        break;

      case "BYE":
        // Call is terminated. May need to remain active if we have an active subscription.
        log.info("Dialog terminated by remote initiated BYE");
        txn.respond(SipResponseStatus.OK);
        break;
      case "REFER":
        // we're being referred somewhere.
        processIncomingRefer(txn);
        break;
      case "INFO":
        // TODO: in-band DTMF. pfffft.
        txn.respond(SipResponseStatus.METHOD_NOT_ALLOWED);
        break;
      case "NOTIFY":
        // TODO: used for the implicit subscription created by a REFER.
        txn.respond(SipResponseStatus.METHOD_NOT_ALLOWED);
        break;
      default:
        // unknown
        log.warn("Unknown SIP method in request: {}", req.getMethod());
        txn.respond(SipResponseStatus.METHOD_NOT_ALLOWED);
        break;

    }

  }

  private void processIncomingReinvite(ServerTransactionHandle txn)
  {

    MutableSipResponse res =
        MutableSipResponse.createResponse(txn.getRequest(), SipResponseStatus.OK);

    String offer = new String(txn.getRequest().getBody(), Charsets.UTF_8);
    String answer = offer;

    res.body("application/sdp", answer);

    transmit(txn, res.build(network.messageManager()));

  }

  private void processIncomingRefer(ServerTransactionHandle txn)
  {
    txn.respond(SipResponseStatus.METHOD_NOT_ALLOWED);
  }

  public void transmit(ServerTransactionHandle txn, SipResponse res2xx)
  {
    this.txmit.add(txn, res2xx);
  }

  public void send(final MutableSipRequest req, ClientTransactionListener listener)
  {

    SipRequest sreq = req.build(network.messageManager());

    log.info("Sending {}", sreq);

    network.sendInvite(sreq, new ClientTransactionListener()
    {

      @Override
      public void onResponse(SipTransactionResponseInfo arg0)
      {
        log.debug("Got SIP response for {}: {}", sreq.getMethod(), arg0.getResponse());
        queue.execute(() -> listener.onResponse(arg0));
      }

      @Override
      public void onError(SipTransactionErrorInfo arg0)
      {
        log.debug("Got error sending in-dialog: {}", arg0.getCode());
        queue.execute(() -> listener.onError(arg0));
      }

    });

  }
}
