package com.jive.hillbilly.api;

import com.jive.hillbilly.api.invite.NewInvite;

public interface HillbillyEventVisitor
{

  void visit(CreatorEvent e);

  void visit(BranchEvent e);

  void visit(NegotiationEvent e);

  void visit(DialogEvent e);

  void visit(NewInvite e);

}
