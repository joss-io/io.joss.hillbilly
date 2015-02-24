package com.jive.v5.hillbilly.api;

import com.jive.v5.hillbilly.api.dialog.DialogConnected;
import com.jive.v5.hillbilly.api.dialog.DialogDisconnected;
import com.jive.v5.hillbilly.api.dialog.Referred;

public interface DialogEventVisitor
{

  void visit(DialogConnected e);

  void visit(DialogDisconnected e);

  void visit(Referred e);

}
