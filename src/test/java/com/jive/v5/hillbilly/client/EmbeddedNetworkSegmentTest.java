package com.jive.v5.hillbilly.client;

import org.junit.Test;

import com.google.common.net.HostAndPort;

public class EmbeddedNetworkSegmentTest
{

  @Test
  public void test() throws InterruptedException
  {

    EmbeddedHillbillySipService service = new EmbeddedHillbillySipService();

    service.setHandler(new TestHillbillyHandler());

    service.addSegment("test", HostAndPort.fromParts("10.0.1.5", 5060));

    while (true)
    {
      Thread.sleep(1000);
    }

  }

}
