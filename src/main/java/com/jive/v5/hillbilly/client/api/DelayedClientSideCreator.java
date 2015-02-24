package com.jive.v5.hillbilly.client.api;

public interface DelayedClientSideCreator extends BaseClientSideCreator
{

  DelayedClientSideEarlyDialog branch(DelayedServerSideEarlyDialog handle);

}
