package com.jive.hillbilly.api;

import com.jive.hillbilly.api.invite.AckNewInvite;
import com.jive.hillbilly.api.invite.InviteCancelled;
import com.jive.hillbilly.api.invite.InviteRejected;
import com.jive.hillbilly.api.invite.NewBranch;

public interface CreatorEventVisitor
{

  void visit(NewBranch e);

  void visit(InviteRejected e);

  void visit(InviteCancelled e);

  void visit(AckNewInvite e);

}
