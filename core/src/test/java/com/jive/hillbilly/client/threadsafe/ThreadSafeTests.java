package com.jive.hillbilly.client.threadsafe;

import org.junit.Test;

import com.jive.hillbilly.api.ClientInviteOptions;
import com.jive.hillbilly.client.api.ServerSideCreator;
import com.jive.hillbilly.client.threadsafe.ThreadSafeHillbillyService;
import com.jive.hillbilly.testing.FakeClientSideCreator;
import com.jive.hillbilly.testing.FakeHillBillyService;
import com.jive.hillbilly.testing.HillbillyTestRuntime;

public class ThreadSafeTests implements HillbillyTestRuntime
{

  @Test
  public void test1() throws Exception
  {

    final FakeHillBillyService hillbilly = new FakeHillBillyService(this);

    final ThreadSafeHillbillyService service = new ThreadSafeHillbillyService(hillbilly);

    final FakeClientSideCreator dialog = new FakeClientSideCreator(this, "v=0", "a");

    final ServerSideCreator uac = service.createClient(ClientInviteOptions.builder().build(), dialog, "v=0");

    // uac.cancel(Reason.fromSipStatus(SipResponseStatus.OK));

    Thread.sleep(100);

    // control:
    hillbilly.getClients().get(0).branch().answer("v=0");

    Thread.sleep(100);

    // dialog.getBranches().get(0).getDialogHandle().disconnect(new
    // DisconnectEvent(SipResponseStatus.NOT_ACCEPTABLE_HERE));

    Thread.sleep(100);

  }

  @Override
  public void sync()
  {
    // TODO Auto-generated method stub

  }

}
