package com.jive.v5.hillbilly.client;

import lombok.Value;
import lombok.experimental.Builder;

import com.jive.sip.uri.api.Uri;

@Value
@Builder
public class ClientInviteOptions
{
  private Uri requestUri;
  private String segment;
}
