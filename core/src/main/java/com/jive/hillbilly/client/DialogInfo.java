package com.jive.hillbilly.client;


import com.jive.sip.message.api.NameAddr;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponse;
import com.jive.sip.uri.api.Uri;

import lombok.Value;

@Value
public class DialogInfo
{
  
  /**
   * The original target for this dialog - either the R-URI of the request we received or sent.
   */

  private Uri target;

  /**
   * The local Contact for this dialog 
   */
  
  private Uri local;
  
  /**
   * The remove Contact for this dialog.
   */
  private Uri remote;
  
  /**
   * The NameAddr in the To/From header of the local side.
   */
  
  private NameAddr localName;
  
  /**
   * The NameAddr in the To/From header of the remote side.
   */
  
  private NameAddr remoteName;

  public static DialogInfo fromRemoteInitiated(SipRequest req, NameAddr localContact)
  {
    return new DialogInfo(
        req.getUri(),
        localContact.getAddress(),
        req.getContacts().get().iterator().next().getAddress(),
        req.getTo(),
        req.getFrom()
        );
  }


  public static DialogInfo fromLocalInitiated(SipRequest local, SipResponse res)
  {
    return new DialogInfo(
        local.getUri(),
        local.getContacts().get().iterator().next().getAddress(),
        res.getContacts().get().iterator().next().getAddress(),
        local.getFrom(),
        local.getTo()
        );
  }

}