package com.jive.v5.hillbilly.client;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.RandomStringUtils;
import org.fusesource.hawtdispatch.Dispatch;
import org.fusesource.hawtdispatch.DispatchQueue;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.primitives.UnsignedInteger;
import com.jive.ftw.sip.dummer.session.DialogInfo;
import com.jive.ftw.sip.dummer.txn.ServerTransactionHandle;
import com.jive.sip.message.api.DialogId;
import com.jive.sip.message.api.Reason;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.processor.rfc3261.MutableSipResponse;
import com.jive.sip.uri.api.SipUri;
import com.jive.v5.hillbilly.client.api.ClientSideCreator;
import com.jive.v5.hillbilly.client.api.ClientSideEarlyDialog;
import com.jive.v5.hillbilly.client.api.Dialog;
import com.jive.v5.hillbilly.client.api.ServerSideCreator;
import com.jive.v5.hillbilly.client.api.ServerSideEarlyDialog;

@Slf4j
public class EmbeddedServerCreator implements ClientSideCreator
{

  private ServerTransactionHandle txn;
  private boolean cancelled = false;
  private ServerSideCreator handler;
  private Map<String, Branch> branches = Maps.newHashMap();

  @Getter
  private DispatchQueue queue = Dispatch.createQueue("UAS");
  private EmbeddedNetworkSegment service;

  public EmbeddedServerCreator(ServerTransactionHandle txn, EmbeddedNetworkSegment service)
  {
    this.txn = txn;
    this.service = service;

    // suspend until creator starts processing.
    queue.suspend();
  }

  void process(ServerSideCreator handler)
  {
    this.handler = Preconditions.checkNotNull(handler);
    log.info("Processing incoming INVITE");
    queue.resume();
  }

  void cancel(Reason reason)
  {
    queue.assertExecuting();
    log.debug("Got CANCEL from wire");
    this.cancelled = true;
    this.handler.cancel(reason);
  }

  private class Branch implements ClientSideEarlyDialog
  {

    private ServerSideEarlyDialog handler;
    private String tag;
    private String local;
    private EmbeddedDialog dialog;
    private DialogRegistrationHandle handle;

    public Branch(String id, ServerSideEarlyDialog handler)
    {
      this.handler = handler;
      this.tag = id;
    }

    EmbeddedDialog getDialog()
    {

      if (this.dialog == null)
      {

        DialogId did =
            new DialogId(txn.getRequest().getCallId(), tag, txn.getRequest().getFromTag());

        SipRequest req = txn.getRequest();

        DialogInfo dinfo = new DialogInfo(
            txn.getRequest().getUri(),
            new SipUri(service.getSelf()),
            req.getContacts().get().iterator().next().getAddress(),
            req.getTo().withTag(tag),
            req.getFrom()
            );

        DialogState dstate = new DialogState(UnsignedInteger.ONE, null);

        this.dialog = new EmbeddedDialog(service, did, dinfo, dstate);

        this.handle = service.register(did, dialog);

      }

      return this.dialog;

    }

    @Override
    public void answer(String sdp)
    {
      queue.assertExecuting();
      log.debug("Got SDP answer");
      this.local = sdp;
    }

    @Override
    public void progress(SipResponseStatus status)
    {

      queue.assertExecuting();
      Preconditions.checkArgument(!status.isFinal());

      MutableSipResponse res = createResponse(status);

      res.body("application/sdp", local);

      respond(res);

    }

    private void respond(MutableSipResponse res)
    {
      txn.respond(res.build(service.messageManager()));
    }

    private MutableSipResponse createResponse(SipResponseStatus status)
    {
      MutableSipResponse res = MutableSipResponse.createResponse(txn.getRequest(), status);
      res.toTag(tag);
      return res;
    }

    @Override
    public Dialog accept(Dialog remote)
    {

      queue.assertExecuting();

      log.debug("Accepting branch {}", tag);

      Preconditions.checkNotNull(local);

      MutableSipResponse res = createResponse(SipResponseStatus.OK);

      res.body("application/sdp", local);

      getDialog().transmit(txn, res.build(service.messageManager()));

      return getDialog();

    }

    @Override
    public void end(String reason)
    {

      queue.assertExecuting();

      // send a 199?

    }

  }

  @Override
  public void reject(SipResponseStatus status)
  {

    queue.assertExecuting();

    // destroy all branches

    txn.respond(status);

  }

  @Override
  public ClientSideEarlyDialog branch(ServerSideEarlyDialog branch)
  {

    String id = RandomStringUtils.randomAlphanumeric(12);
    Branch b = new Branch(id, branch);
    this.branches.put(id, b);

    return new ClientSideEarlyDialog()
    {

      @Override
      public void progress(SipResponseStatus status)
      {
        queue.execute(() -> b.progress(status));
      }

      @Override
      public void end(String reason)
      {
        queue.execute(() -> b.end(reason));
      }

      @Override
      public Dialog accept(Dialog local)
      {

        AtomicReference<Dialog> remote = new AtomicReference<>();

        queue.execute(() -> remote.set(b.accept(local)));

        return new Dialog()
        {

          @Override
          public void refer()
          {
            queue.execute(() -> remote.get().refer());
          }

          @Override
          public void disconnect()
          {
            queue.execute(() -> remote.get().disconnect());
          }

        };
      }

      @Override
      public void answer(String sdp)
      {
        queue.execute(() -> b.answer(sdp));
      }

    };

  }


}
