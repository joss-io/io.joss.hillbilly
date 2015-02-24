package com.jive.hillbilly.client;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Maps;
import com.jive.hillbilly.Request;
import com.jive.hillbilly.Response;
import com.jive.hillbilly.SipReason;
import com.jive.hillbilly.SipStatus;
import com.jive.hillbilly.SipWarning;
import com.jive.hillbilly.api.Address;
import com.jive.sip.base.api.RawHeader;
import com.jive.sip.message.api.NameAddr;
import com.jive.sip.message.api.Reason;
import com.jive.sip.message.api.SipMethod;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponse;
import com.jive.sip.message.api.SipResponseStatus;
import com.jive.sip.message.api.headers.Warning;
import com.jive.sip.parameters.api.Parameters;
import com.jive.sip.parameters.impl.DefaultParameters;
import com.jive.sip.processor.rfc3261.MutableSipRequest;
import com.jive.sip.processor.rfc3261.RfcSipMessageManagerBuilder;
import com.jive.sip.processor.rfc3261.SipMessageManager;
import com.jive.sip.uri.api.Uri;

public class ApiUtils
{

  private static SipMessageManager MM = new RfcSipMessageManagerBuilder().build();

  public static final String convert(final Uri uri)
  {
    return uri.toString();
  }

  public static final Address convert(final NameAddr addr)
  {
    if (addr == null)
    {
      return null;
    }
    return new Address(
        addr.getName().orElse(null),
        convert(addr.getAddress()),
        addr.getParameters().orElse(DefaultParameters.EMPTY).toString());
  }

  public static Reason convert(final SipReason reason)
  {
    if (reason == null)
    {
      return null;
    }
    return Reason.fromSipStatus(new SipResponseStatus(reason.getCode(), reason.getReason()));
  }

  public static Uri uri(String uri)
  {
    if (uri == null)
      return null;
    return MM.parseUri(uri);
  }

  public static SipStatus convert(SipResponseStatus status)
  {
    if (status == null)
      return null;
    return new SipStatus(status.getCode(), status.getReason());
  }

  public static Request convert(SipRequest req)
  {
    final Map<String, String> headers = Maps.newLinkedHashMap();
    for (RawHeader hdr : req.getHeaders())
    {
      headers.put(hdr.getName(), hdr.getValue());
    }
    return new Request(req.getMethod().toString(), convert(req.getUri()), headers, req.getBody());
  }

  public static Response convert(SipResponse response)
  {
    Map<String, String> headers = Maps.newLinkedHashMap();
    for (RawHeader hdr : response.getHeaders())
    {
      headers.put(hdr.getName(), hdr.getValue());
    }

    return new Response(convert(response.getStatus()), headers, response.getBody());
  }

  public static NameAddr convert(Address to)
  {
    if (to == null)
      return null;
    return new NameAddr(to.getName(), uri(to.getUri())).withParameters(parameters(to.getProperties()));
  }

  private static Parameters parameters(String properties)
  {
    if (properties == null || properties.length() == 0)
      return DefaultParameters.EMPTY;
    return MM.parseParameters(properties);
  }

  public static SipResponseStatus convert(SipStatus status)
  {
    return new SipResponseStatus(status.getCode(), status.getReason());
  }

  public static Collection<Warning> convert(List<SipWarning> warnings)
  {
    return warnings.stream().map(warn -> new Warning(warn.getCode(), warn.getAgent(), warn.getText())).collect(Collectors.toList());
  }

  public static SipReason convert(Reason reason)
  {
    if (reason == null)
      return null;
    return new SipReason(reason.getProtocol().toString(), reason.getCause().get(), reason.getText().orElse(null));
  }

  public static SipRequest convert(Request req)
  {
    MutableSipRequest mreq = MutableSipRequest.create(SipMethod.fromString(req.getMethod()), uri(req.getUri()));
    req.getHeaders().forEach((k,v) -> mreq.add(k, v));
    return mreq.build();
  }

}
