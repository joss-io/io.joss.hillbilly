package com.jive.hillbilly.client;

import com.jive.hillbilly.client.ApiUtils;
import com.jive.hillbilly.client.api.ClientSideCreator;
import com.jive.hillbilly.client.api.ClientSideEarlyDialog;
import com.jive.hillbilly.client.api.DelayedClientSideCreator;
import com.jive.hillbilly.client.api.Dialog;
import com.jive.hillbilly.client.api.DisconnectEvent;
import com.jive.hillbilly.client.api.HillbillyHandler;
import com.jive.hillbilly.client.api.IncomingInviteHandle;
import com.jive.hillbilly.client.api.OptionsHandle;
import com.jive.hillbilly.client.api.OriginationBranchProgressEvent;
import com.jive.hillbilly.client.api.ReferHandle;
import com.jive.hillbilly.client.api.ServerSideCreator;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.processor.rfc3261.MutableSipResponse;
import com.jive.sip.processor.rfc3261.RfcSipMessageManagerBuilder;
import com.jive.sip.processor.rfc3261.SipMessageManager;

public class TestHillbillyHandler implements HillbillyHandler
{

  public static final SipMessageManager messageManager = new RfcSipMessageManagerBuilder().build();

  @Override
  public ServerSideCreator createServer(final ClientSideCreator xxx, final IncomingInviteHandle handle)
  {

    final ClientSideCreator client = handle.process(new TestServerSideCreator());

    final TestLocalDialog dialog = new TestLocalDialog();

    final ClientSideEarlyDialog branch = client.branch(new TestServerBranch(), dialog);

    branch.answer(handle.offer());

    branch.progress(
        new OriginationBranchProgressEvent(
            ApiUtils.convert(MutableSipResponse.createResponse(SipResponseStatus.RINGING).build(messageManager))
            )
        );

    final Dialog remote = branch.dialog();
    branch.accept();

    remote.disconnect(DisconnectEvent.OK);

    return new TestServerSideCreator();

  }

  @Override
  public ServerSideCreator createServer(final DelayedClientSideCreator client)
  {
    client.reject(ApiUtils.convert(SipResponseStatus.NOT_IMPLEMENTED));
    return null;
  }

  @Override
  public void processRefer(final String uri, final ReferHandle h)
  {
    h.reject(ApiUtils.convert(SipResponseStatus.NOT_IMPLEMENTED));
  }

  @Override
  public void processOptions(final OptionsHandle e)
  {
    e.accept("v=0");
  }

}
