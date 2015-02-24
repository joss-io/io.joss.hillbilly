package com.jive.v5.hillbilly.client;


import com.jive.sip.message.api.Reason;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.v5.hillbilly.client.api.ClientSideCreator;
import com.jive.v5.hillbilly.client.api.ClientSideEarlyDialog;
import com.jive.v5.hillbilly.client.api.Dialog;
import com.jive.v5.hillbilly.client.api.ServerSideCreator;
import com.jive.v5.hillbilly.client.api.ServerSideEarlyDialog;

/**
 * A server endpoint which send a 183 (wuth SDP), then answers. It waits until it's instructed to
 * disconneted.
 * 
 * @author theo
 *
 */

public class FakeSimpleAnswerServerEndpoint implements ServerSideCreator
{

  private ClientSideCreator creator;
  private ClientSideEarlyDialog cbranch;
  private Dialog cdialog;

  void run(ClientSideCreator creator)
  {
    this.creator = creator;
    ServerSideEarlyDialog branch = new FakeServerSideBranch();
    this.cbranch = creator.branch(branch);
    cbranch.answer("[answer]");
    cbranch.progress(SipResponseStatus.NOT_FOUND);
    Dialog dialog = new FakeDialog();
    this.cdialog = cbranch.accept(dialog);
  }

  @Override
  public void cancel(Reason reason)
  {
  }

  void end()
  {
    cdialog.disconnect();
  }

}
