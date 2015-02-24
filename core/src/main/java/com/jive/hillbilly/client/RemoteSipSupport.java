package com.jive.hillbilly.client;

import com.jive.sip.base.api.Token;
import com.jive.sip.message.api.SipMessage;
import com.jive.sip.message.api.SipMethod;
import com.jive.sip.message.api.TokenSet;
import com.jive.sip.processor.rfc3261.DefaultSipMessage;

public class RemoteSipSupport
{

  private TokenSet allow;
  private TokenSet supported;
  private TokenSet require;

  public RemoteSipSupport(SipMessage msg)
  {
    this.allow = msg.getAllow().orElse(TokenSet.EMPTY);
    this.supported = msg.getHeader(DefaultSipMessage.SUPPORTED).orElse(TokenSet.EMPTY);
    this.require = msg.getHeader(DefaultSipMessage.REQUIRE).orElse(TokenSet.EMPTY);
  }

  /**
   * returns true if the remote side supports the given method (in the Allow header).
   */

  boolean allows(SipMethod method)
  {
    return allow.contains(Token.from(method.toString()));
  }

  /**
   * returns true if the remote side supports the given extension (in the Supported header).
   */

  boolean supports(String ext)
  {
    return supported.contains(Token.from(ext));
  }

  /**
   * returns true if the remote side supports requires given extension (in the Require header).
   */

  boolean requires(String ext)
  {
    return require.contains(Token.from(ext));
  }

}
