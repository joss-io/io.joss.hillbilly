package com.jive.v5.hillbilly.client;

import com.jive.sip.message.api.SipResponseStatus;
import com.jive.v5.hillbilly.client.api.ClientSideCreator;
import com.jive.v5.hillbilly.client.api.ClientSideEarlyDialog;
import com.jive.v5.hillbilly.client.api.DelayedClientSideCreator;
import com.jive.v5.hillbilly.client.api.Dialog;
import com.jive.v5.hillbilly.client.api.HillbillyHandler;
import com.jive.v5.hillbilly.client.api.IncomingInviteHandle;
import com.jive.v5.hillbilly.client.api.ServerSideCreator;

public class TestHillbillyHandler implements HillbillyHandler
{

  @Override
  public ServerSideCreator createServer(ClientSideCreator xxx, IncomingInviteHandle handle)
  {

    ClientSideCreator client = handle.process(new TestServerSideCreator());

    ClientSideEarlyDialog branch = client.branch(new TestServerBranch());

    branch.answer(handle.offer());
    branch.progress(SipResponseStatus.RINGING);

    Dialog dialog = branch.accept(new TestLocalDialog());

    dialog.disconnect();

    return new TestServerSideCreator();

  }

  @Override
  public ServerSideCreator createServer(DelayedClientSideCreator client)
  {
    client.reject(SipResponseStatus.NOT_IMPLEMENTED);
    return null;
  }

}
