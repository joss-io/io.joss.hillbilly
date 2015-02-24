package com.jive.hillbilly.client.api;

public interface DelayedClientSideCreator extends BaseClientSideCreator
{

  DelayedClientSideEarlyDialog branch(DelayedServerSideEarlyDialog handle);

}
