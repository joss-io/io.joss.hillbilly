package com.jive.hillbilly.client;

import com.jive.hillbilly.client.api.DialogTerminationEvent;
import com.jive.hillbilly.client.api.ServerSideEarlyDialog;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FakeServerSideBranch implements ServerSideEarlyDialog
{

  @Override
  public void end(final DialogTerminationEvent e)
  {
    log.debug("server got end");
  }

}
