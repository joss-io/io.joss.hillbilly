package com.jive.v5.hillbilly.client;


import org.junit.Test;

import com.google.common.net.HostAndPort;
import com.jive.sip.message.api.Reason;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.uri.api.SipUri;
import com.jive.v5.hillbilly.client.api.ClientSideCreator;
import com.jive.v5.hillbilly.client.api.ClientSideEarlyDialog;
import com.jive.v5.hillbilly.client.api.DelayedClientSideCreator;
import com.jive.v5.hillbilly.client.api.DelayedClientSideEarlyDialog;
import com.jive.v5.hillbilly.client.api.DelayedServerSideEarlyDialog;
import com.jive.v5.hillbilly.client.api.Dialog;
import com.jive.v5.hillbilly.client.api.HillbillyHandler;
import com.jive.v5.hillbilly.client.api.HillbillyService;
import com.jive.v5.hillbilly.client.api.IncomingInviteHandle;
import com.jive.v5.hillbilly.client.api.ServerSideCreator;
import com.jive.v5.hillbilly.client.api.ServerSideEarlyDialog;

public class EarlyDialogTest
{

  static class TestDialog implements Dialog
  {

    @Override
    public void refer()
    {
      // TODO Auto-generated method stub
    }

    @Override
    public void disconnect()
    {
      // TODO Auto-generated method stub
    }

  }

  private static final ClientInviteOptions INVITE_OPTIONS = ClientInviteOptions.builder()
      .requestUri(SipUri.fromUserAndHost("theo", HostAndPort.fromString("jive.com")))
      .build();

  /**
   * An example client (UAC) that sends an INVITE out.
   */

  @Test
  public void testNonDelayedClient()
  {

    HillbillyService service = new EmbeddedHillbillySipService();

    ClientSideCreator client = new ClientSideCreator()
    {

      @Override
      public ClientSideEarlyDialog branch(final ServerSideEarlyDialog handle)
      {

        ClientSideEarlyDialog branch = new ClientSideEarlyDialog()
        {

          @Override
          public void progress(SipResponseStatus status)
          {
          }

          @Override
          public Dialog accept(Dialog dialog)
          {
            return new TestDialog();
          }

          @Override
          public void answer(String sdp)
          {
            // TODO Auto-generated method stub
          }

          @Override
          public void end(String reason)
          {
            // TODO Auto-generated method stub
          }

        };

        return branch;

      }

      @Override
      public void reject(SipResponseStatus status)
      {
        // TODO Auto-generated method stub
      }

    };

    ServerSideCreator server = service.createClient(INVITE_OPTIONS, client, "[offer]");

    server.cancel(null);

  }

  /**
   * An example client (UAC) that sends an INVITE out without SDP, resulting in a delayed offer.
   */

  @Test
  public void testDelayedClient()
  {

    HillbillyService service = new EmbeddedHillbillySipService();

    DelayedClientSideCreator client = new DelayedClientSideCreator()
    {

      @Override
      public DelayedClientSideEarlyDialog branch(final DelayedServerSideEarlyDialog handle)
      {

        DelayedClientSideEarlyDialog branch = new DelayedClientSideEarlyDialog()
        {

          @Override
          public void progress(SipResponseStatus status)
          {
          }

          @Override
          public void end(String reason)
          {
          }

          @Override
          public Dialog accept(Dialog dialog)
          {
            return new TestDialog();
          }

          @Override
          public void offer(String offer)
          {
            handle.answer("[answer]");
          }

        };

        return branch;

      }

      @Override
      public void reject(SipResponseStatus status)
      {
      }

    };

    ServerSideCreator server = service.createClient(INVITE_OPTIONS, client);

    server.cancel(null);

  }

  /**
   * An example server (UAS) that receives incoming INVITEs.
   */

  @Test
  public void testNonDelayedServer()
  {

    EmbeddedHillbillySipService service = new EmbeddedHillbillySipService();

    HillbillyHandler handler = new HillbillyHandler()
    {

      @Override
      public ServerSideCreator createServer(ClientSideCreator client, IncomingInviteHandle handle)
      {

        ServerSideCreator creator = new ServerSideCreator()
        {

          @Override
          public void cancel(Reason reason)
          {
            // cancelled.
            client.reject(SipResponseStatus.NOT_FOUND);
          }

        };

        handle.process(creator);

        ClientSideEarlyDialog branch = client.branch(new ServerSideEarlyDialog()
        {

          @Override
          public void end(String reason)
          {
          }

        });

        branch.answer("[answer]");

        branch.progress(SipResponseStatus.RINGING);

        branch.accept(new Dialog()
        {

          @Override
          public void refer()
          {
            // TODO Auto-generated method stub
          }

          @Override
          public void disconnect()
          {
            // TODO Auto-generated method stub
          }

        });

        return creator;

      }

      @Override
      public ServerSideCreator createServer(DelayedClientSideCreator client)
      {
        // TODO Auto-generated method stub
        return null;
      }

    };

    service.setHandler(handler);

  }

  /**
   * An example server (UAS) that receives incoming INVITEs.
   */

  @Test
  public void testDelayedServer()
  {

    EmbeddedHillbillySipService service = new EmbeddedHillbillySipService();

    HillbillyHandler handler = new HillbillyHandler()
    {

      @Override
      public ServerSideCreator createServer(ClientSideCreator client, IncomingInviteHandle handle)
      {
        return null;
      }

      @Override
      public ServerSideCreator createServer(DelayedClientSideCreator client)
      {

        ServerSideCreator creator = new ServerSideCreator()
        {

          @Override
          public void cancel(Reason reason)
          {
            // cancelled.
            client.reject(SipResponseStatus.NOT_FOUND);
          }

        };

        DelayedClientSideEarlyDialog branch = client.branch(new DelayedServerSideEarlyDialog()
        {

          @Override
          public void answer(String string)
          {
          }

          @Override
          public void end(String reason)
          {
          }

        }
            );

        branch.offer("[offer]");

        branch.progress(SipResponseStatus.OK);

        branch.accept(new Dialog()
        {

          @Override
          public void refer()
          {
            // TODO Auto-generated method stub
          }

          @Override
          public void disconnect()
          {
            // TODO Auto-generated method stub
          }

        });

        return creator;

      }

    };

    service.setHandler(handler);

  }

}
