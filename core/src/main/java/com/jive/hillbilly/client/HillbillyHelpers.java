package com.jive.hillbilly.client;

import java.util.Optional;

import com.google.common.base.Charsets;
import com.jive.sip.message.api.ContentDisposition;
import com.jive.sip.message.api.SipMessage;
import com.jive.sip.message.api.SipMethod;
import com.jive.sip.message.api.SipResponse;

public class HillbillyHelpers
{

  /**
   * returns true if this SIP message contains a SIP request with Content-Disposition: session (or
   * default), and the content type is application/sdp.
   */

  public static Optional<String> getSessionDescription(final SipMessage msg)
  {

    final byte[] body = msg.getBody();

    if ((body == null) || (body.length == 0))
    {
      return Optional.empty();
    }

    if (!msg.getContentType().orElse("application/sdp").toLowerCase().equals("application/sdp"))
    {
      return Optional.empty();
    }

    if (!msg.getContentDisposition().orElse(ContentDisposition.SessionRequired).getValue()
        .toLowerCase()
        .equals("session"))
    {
      return Optional.empty();
    }

    return Optional.of(new String(body, Charsets.UTF_8));

  }

  public static boolean hasBody(final SipMessage msg)
  {

    final byte[] body = msg.getBody();

    if ((body == null) || (body.length == 0))
    {
      return false;
    }

    return true;
  }

  public static boolean isDialogRefreshing(final SipMethod method)
  {
    return method.isInvite() || method.isUpdate() || method.isSubscribe() || method.isAck();
  }

  /**
   * sends an ACK followed by a BYE.
   *
   * @param res
   * @param service
   */

  public static void ackAndBye(final SipResponse res, final EmbeddedNetworkSegment service)
  {
    service.autoKill(res);
  }

}
