package com.jive.hillbilly.client;

import java.util.List;

import com.google.common.collect.Lists;
import com.jive.hillbilly.SipStatus;
import com.jive.hillbilly.SipWarning;
import com.jive.hillbilly.client.ApiUtils;
import com.jive.hillbilly.client.api.ClientSideCreator;
import com.jive.hillbilly.client.api.ClientSideEarlyDialog;
import com.jive.hillbilly.client.api.Dialog;
import com.jive.hillbilly.client.api.ServerSideEarlyDialog;
import com.jive.myco.commons.concurrent.Pnky;
import com.jive.sip.message.api.SipResponseStatus;

import lombok.Getter;

public class TestClientSideCreator implements ClientSideCreator
{

  @Getter
  private final Pnky<SipResponseStatus> rejected = Pnky.create();

  @Getter
  private final List<TestClientBranch> branches = Lists.newLinkedList();

  @Override
  public void reject(final SipStatus status, final List<SipWarning> warnings)
  {
    this.rejected.resolve(ApiUtils.convert(status));
  }

  @Override
  public ClientSideEarlyDialog branch(final ServerSideEarlyDialog handle, final Dialog dialog)
  {
    final TestClientBranch branch = new TestClientBranch(handle, dialog);
    this.branches.add(branch);
    return branch;
  }


}
