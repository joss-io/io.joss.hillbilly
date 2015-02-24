package com.jive.hillbilly.client;

import lombok.Value;

@Value
public class SessionRefreshConfig
{
  private boolean localRefresher;
  private long expires;
}
