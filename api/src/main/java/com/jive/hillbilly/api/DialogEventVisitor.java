package com.jive.hillbilly.api;

import com.jive.hillbilly.api.dialog.DialogConnected;
import com.jive.hillbilly.api.dialog.DialogDisconnected;
import com.jive.hillbilly.api.dialog.Referred;

public interface DialogEventVisitor
{

  void visit(DialogConnected e);

  void visit(DialogDisconnected e);

  void visit(Referred e);

}
