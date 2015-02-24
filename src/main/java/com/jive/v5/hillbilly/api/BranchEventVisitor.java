package com.jive.v5.hillbilly.api;

import com.jive.v5.hillbilly.api.invite.InviteAccepted;
import com.jive.v5.hillbilly.api.invite.InviteProgress;

public interface BranchEventVisitor
{

  void visit(InviteAccepted e);

  void visit(InviteProgress e);

}
