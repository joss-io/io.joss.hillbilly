package com.jive.v5.hillbilly.api;

import com.jive.v5.hillbilly.api.invite.NewInvite;

public interface HillbillyEventVisitor
{

  void visit(CreatorEvent e);

  void visit(BranchEvent e);

  void visit(NegotiationEvent e);

  void visit(DialogEvent e);

  void visit(NewInvite e);

}
