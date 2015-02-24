package com.jive.v5.hillbilly.client;

import com.google.common.net.HostAndPort;
import com.jive.ftw.sip.dummer.txn.ClientTransactionListener;
import com.jive.ftw.sip.dummer.txn.SipClientTransaction;
import com.jive.sip.message.api.DialogId;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.processor.rfc3261.MutableSipRequest;
import com.jive.sip.processor.rfc3261.SipMessageManager;

public interface EmbeddedNetworkSegment
{

  HostAndPort getSelf();

  DialogRegistrationHandle register(DialogId id, EmbeddedDialog d);


  SipMessageManager messageManager();

  SipClientTransaction sendInvite(SipRequest invite, ClientTransactionListener listener);

  void transmit(MutableSipRequest ack, String randomAlphanumeric);


}
