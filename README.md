# com.jive.v5.hillbilly

Standalone SIP dialog level engine with well defined API that is designed to be usable over a TCP socket.  It avoids all direct leaking of SIP implementation details into the API.

Initial implementation provides an EmbeddedSipStackHickClient, which runs the stack locally.  Future work will move this to a 3rd party component supporting migration of dialogs and leaving the more expensive SIP processing on a seperate (and scalable) service, allowing consumers of hillbilly to get better scale. 

The API is identical for lcient and server, and implemented as interfaces - allowing components that consume hillbilly to easily mock SIP dialog behaviour without needing to use SIP messages directly.

