package com.jive.hillbilly.api;

import com.jive.hillbilly.api.invite.InviteAccepted;
import com.jive.hillbilly.api.invite.InviteProgress;

public interface BranchEventVisitor
{

  void visit(InviteAccepted e);

  void visit(InviteProgress e);

}
