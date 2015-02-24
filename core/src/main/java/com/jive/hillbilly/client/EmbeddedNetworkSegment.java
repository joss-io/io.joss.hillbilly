package com.jive.hillbilly.client;

import java.time.Clock;

import com.google.common.net.HostAndPort;
import com.jive.sip.dummer.txn.ClientTransactionListener;
import com.jive.sip.dummer.txn.ClientTransactionOptions;
import com.jive.sip.dummer.txn.SipClientTransaction;
import com.jive.sip.dummer.txn.SipStack;
import com.jive.sip.message.api.DialogId;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponse;
import com.jive.sip.processor.rfc3261.MutableSipRequest;
import com.jive.sip.processor.rfc3261.SipMessageManager;
import com.jive.sip.transport.api.FlowId;

public interface EmbeddedNetworkSegment
{

  HostAndPort getSelf();

  DialogRegistrationHandle register(final DialogId id, final EmbeddedDialog d);

  SipMessageManager messageManager();

  void transmit(final MutableSipRequest ack, final FlowId flowId, final String randomAlphanumeric);

  String getServerName();

  String getId();

  HillbillyRuntimeService getExecutor();

  HillbillyRequestEnforcer getEnforcer();

  SipClientTransaction sendInvite(final SipRequest invite, final FlowId flowId, final ClientTransactionOptions opts, final ClientTransactionListener listener);

  long getActiveDialogCount();

  /**
   * clock this segment uses. default is the system clock.
   */

  Clock getClock();

  /**
   * takes an INVITE 2xx, and ACKs it followed by sending BYE.
   *
   * can be called multiple times.
   *
   */

  void autoKill(final SipResponse res);

  SipStack stack();

}
