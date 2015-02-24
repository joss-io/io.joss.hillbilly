package com.jive.hillbilly.client;

import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jive.sip.base.api.Token;
import com.jive.sip.message.api.ContentDisposition;
import com.jive.sip.message.api.SipMethod;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.message.api.TokenSet;
import com.jive.sip.message.api.headers.MIMEType;
import com.jive.sip.processor.rfc3261.MutableSipResponse;

import lombok.Getter;

/**
 * Provides checking for an incoming request to make sure it meets whatever requirements are
 * specified.
 *
 * If the checks fail, a suitable {@link MutableSipResponse} is returned, which contains all the
 * error messaging.
 * 
 * @author theo
 *
 */

public class HillbillyRequestEnforcer
{

  @Getter
  private final TokenSet supported;

  @Getter
  private static TokenSet events = TokenSet.fromList(Lists.newArrayList(
      "refer"
      // "call-info",
      // "hold"
      ));

  @Getter
  private static Set<MIMEType> accept = Sets.newHashSet(
      MIMEType.APPLICATION_SDP
      );

  @Getter
  private final Set<SipMethod> methods;

  public HillbillyRequestEnforcer(boolean support100rel) {
    TokenSet supported = TokenSet.fromList(Lists.newArrayList(
        "199",
        // "norefersub",
        "from-change",
        // "join",
        "tdialog",
        "timer",
        "histinfo",
        "replaces"));

    Set<SipMethod> methods = Sets.newHashSet(
        SipMethod.INVITE,
        SipMethod.ACK,
        SipMethod.BYE,
        SipMethod.CANCEL,
        SipMethod.REFER,
        SipMethod.NOTIFY,
        SipMethod.UPDATE,
        SipMethod.OPTIONS);

    if (support100rel) {
      supported = supported.with(Token.from("100rel"));
      methods.add(SipMethod.PRACK);
    }

    this.supported = supported;
    this.methods = methods;
  }

  public MutableSipResponse enforce(SipRequest req)
  {

    if (!methods.contains(req.getMethod()))
    {
      // something in Require we don't support.
      MutableSipResponse res =
          MutableSipResponse.createResponse(req, SipResponseStatus.METHOD_NOT_ALLOWED);
      res.allow(methods);
      res.accept(MIMEType.APPLICATION_SDP);
      res.supported(supported);
      return res;
    }

    TokenSet require =
        req.getRequire().orElse(TokenSet.EMPTY).except(supported);

    if (!require.isEmpty())
    {
      // something in Require we don't support.
      MutableSipResponse res =
          MutableSipResponse.createResponse(req, SipResponseStatus.BAD_EXTENSION);
      res.allow(methods);
      res.accept(MIMEType.APPLICATION_SDP);
      res.unsupported(require);
      return res;
    }

    if (HillbillyHelpers.hasBody(req))
    {

      ContentDisposition cd = req.getContentDisposition().orElse(ContentDisposition.SessionRequired);
      Token handling = cd.getHandling().orElse(ContentDisposition.Required);

      if (handling.equals(ContentDisposition.Required))
      {

        // fail if we have no idea what it is.

        if (!cd.getValue().toLowerCase().equals("session"))
        {
          MutableSipResponse res =
              MutableSipResponse.createResponse(req, SipResponseStatus.UNSUPPORTED_MEDIA_TYPE
                  .withReason("Unknown Content-Disposition value"));
          res.allow(methods);
          res.accept(MIMEType.APPLICATION_SDP);
          res.unsupported(require);
          return res;
        }

        if (!req.getContentType().orElse("application/sdp").toLowerCase().equals("application/sdp"))
        {
          MutableSipResponse res =
              MutableSipResponse.createResponse(req, SipResponseStatus.UNSUPPORTED_MEDIA_TYPE
                  .withReason("Unsupported Content-Type"));
          res.allow(methods);
          res.accept(MIMEType.APPLICATION_SDP);
          res.unsupported(require);
          return res;
        }

      }
      else if (handling.equals(ContentDisposition.Optional))
      {
        // we don't care if we have no idea what this is.
      }
      else
      {
        MutableSipResponse res =
            MutableSipResponse.createResponse(req, SipResponseStatus.UNSUPPORTED_MEDIA_TYPE
                .withReason("Unknown Content-Disposition 'handling' value"));
        res.allow(methods);
        res.accept(MIMEType.APPLICATION_SDP);
        res.unsupported(require);
        return res;
      }

    }

    return null;
  }

}
