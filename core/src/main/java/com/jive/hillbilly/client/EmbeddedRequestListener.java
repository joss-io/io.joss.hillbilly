package com.jive.hillbilly.client;

import com.jive.hillbilly.Response;

public interface EmbeddedRequestListener
{

  void onNext(Response res);

  void onCompleted();
  
  void onError(Throwable t);

}
