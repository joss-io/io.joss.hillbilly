package com.jive.v5.hillbilly.api;

import com.jive.v5.hillbilly.api.invite.AckNewInvite;
import com.jive.v5.hillbilly.api.invite.InviteCancelled;
import com.jive.v5.hillbilly.api.invite.InviteRejected;
import com.jive.v5.hillbilly.api.invite.NewBranch;

public interface CreatorEventVisitor
{

  void visit(NewBranch e);

  void visit(InviteRejected e);

  void visit(InviteCancelled e);

  void visit(AckNewInvite e);

}
