package com.jive.hillbilly.testing;

import java.util.List;

import lombok.Getter;

import org.apache.commons.lang3.NotImplementedException;

import com.google.common.collect.Lists;
import com.jive.hillbilly.api.ClientInviteOptions;
import com.jive.hillbilly.client.api.ClientSideCreator;
import com.jive.hillbilly.client.api.DelayedClientSideCreator;
import com.jive.hillbilly.client.api.HillbillyService;
import com.jive.hillbilly.client.api.ServerSideCreator;

public class FakeHillBillyService implements HillbillyService
{

  @Getter
  private final List<FakeServerSideCreator> clients = Lists.newLinkedList();
  private final List<FakeClientSideCreator> servers = Lists.newLinkedList();

  private final HillbillyTestRuntime test;

  public FakeHillBillyService(final HillbillyTestRuntime test)
  {
    this.test = test;
  }

  @Override
  public ServerSideCreator createClient(final ClientInviteOptions opts, final DelayedClientSideCreator client)
  {
    throw new NotImplementedException("Delayed offers not yet implemented");
  }

  @Override
  public ServerSideCreator createClient(final ClientInviteOptions opts, final ClientSideCreator handler, final String offer)
  {
    final FakeServerSideCreator client = new FakeServerSideCreator(this.test, opts, handler, offer, "UAC");
    this.clients.add(client);
    return client;
  }

  public FakeClientSideCreator createServer(final String offer)
  {
    final FakeClientSideCreator server = new FakeClientSideCreator(this.test, offer, "UAS");
    this.servers.add(server);
    return server;
  }

  public long openServers()
  {
    return this.servers.stream().filter(o -> o.isOpen()).count();
  }

  public long openClients()
  {
    return this.clients.stream().filter(o -> o.isOpen()).count();
  }

}
