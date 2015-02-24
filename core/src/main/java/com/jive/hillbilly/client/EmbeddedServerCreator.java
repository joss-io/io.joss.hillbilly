package com.jive.hillbilly.client;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.RandomStringUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.jive.hillbilly.SipStatus;
import com.jive.hillbilly.SipWarning;
import com.jive.hillbilly.client.api.ClientAlreadyConnectedException;
import com.jive.hillbilly.client.api.ClientSideCreator;
import com.jive.hillbilly.client.api.ClientSideEarlyDialog;
import com.jive.hillbilly.client.api.Dialog;
import com.jive.hillbilly.client.api.DialogTerminationEvent;
import com.jive.hillbilly.client.api.ServerSideCreator;
import com.jive.hillbilly.client.api.ServerSideEarlyDialog;
import com.jive.sip.base.api.Token;
import com.jive.sip.message.api.Reason;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.TokenSet;
import com.jive.sip.processor.rfc3261.MutableSipResponse;
import com.jive.sip.transport.api.FlowId;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EmbeddedServerCreator implements ClientSideCreator
{

  private static enum State
  {

    /**
     * we've not yet received a 100
     */

    Trying,

    /**
     * We've received a 100, so are proceeding.
     */

    /**
     * We have at least a single branch currently active. If all branches go away (e,g, due to a 3xx), then we move back to Trying while we
     * re-send the INVITE out.
     */

    Branched,

    /**
     * A dialog has become connected. All others have been terminated.
     */

    Completed,

    /**
     * We've failed for good.
     */

    Failed

  }

  private State state = State.Trying;

  @Getter
  private final HillbillyServerTransaction txn;

  /**
   * Set when the consumer calls cancel.
   */

  private Optional<Reason> cancelled = null;

  private ServerSideCreator handler;
  private final Map<String, ServerBranch> branches = Maps.newHashMap();

  @Getter
  final EmbeddedNetworkSegment service;
  final RemoteSipSupport support;

  private ServerBranch winner;

  private final SipRequest req;

  // the remote SDP.
  public String remote;

  public EmbeddedServerCreator(final HillbillyServerTransaction txn, final EmbeddedNetworkSegment service)
  {
    this.txn = txn;
    this.req = txn.getRequest();
    this.service = service;
    this.support = new RemoteSipSupport(txn.getRequest());
  }

  void process(final ServerSideCreator handler)
  {
    this.handler = Preconditions.checkNotNull(handler);
    this.remote = HillbillyHelpers.getSessionDescription(this.req).orElse(null);
    log.debug("Processing incoming INVITE, {}", this.isReliableProvisional());
  }

  /**
   * Can only be called once based on a CANCEL coming in from the network.
   *
   * @param reason
   *
   * @throws ClientAlreadyConnectedException
   *
   */

  void cancel(final Reason reason) throws ClientAlreadyConnectedException
  {
    Preconditions.checkState(this.cancelled == null, "can only cancel once");
    log.debug("Got CANCEL from wire");
    this.cancelled = Optional.ofNullable(reason);
    if (handler == null)
    {
      log.info("Not cancelling request without handler");
      return;
    }
    this.handler.cancel(ApiUtils.convert(reason));
  }

  boolean isReliableProvisional()
  {
    if (!this.getService().getEnforcer().getSupported().contains(Token.from(HillbillyConstants.TAG_100Rel)))
    {
      return false;
    }

    if (this.req.getSupported().orElse(TokenSet.EMPTY).contains(Token.from(HillbillyConstants.TAG_100Rel)))
    {
      return true;
    }

    if (this.req.getRequire().orElse(TokenSet.EMPTY).contains(Token.from(HillbillyConstants.TAG_100Rel)))
    {
      return true;
    }

    return false;
  }

  @Override
  public void reject(final SipStatus status, final List<SipWarning> warnings)
  {

    log.debug("Rejecting incoming SIP request with {} (Warnings: {})", status, warnings);

    this.state = State.Failed;

    // destroy all branches

    final MutableSipResponse res = MutableSipResponse.createResponse(this.req, ApiUtils.convert(status));

    if (warnings != null)
    {
      res.warning(ApiUtils.convert(warnings).stream().map(w -> w.withAgent(this.service.getSelf().toString())).collect(Collectors.toList()));
    }

    this.txn.respond(res.build(this.service.messageManager()));

    // clear all the branches.
    this.branches.values().forEach(b -> b.destroy());
    this.branches.clear();

  }

  /**
   * Called by the API consumer to create a new branch within this UAS session.
   *
   * Attempting to create a dialog once we've already accepted one will result in the dialog being Immediately terminated with
   * {@link DialogTerminationEvent.OtherDialogConnected}.
   *
   */

  @Override
  public ClientSideEarlyDialog branch(final ServerSideEarlyDialog branch, final Dialog remote)
  {
    Preconditions.checkArgument(branch != null, "branch required");
    Preconditions.checkArgument(remote != null, "dialog required");
    Preconditions.checkArgument((this.state == State.Branched) || (this.state == State.Trying), "invalid state", this.state, this.req.getCallId());
    this.state = State.Branched;
    final String id = RandomStringUtils.randomAlphanumeric(12);
    final ServerBranch b = new ServerBranch(this, id, branch, remote);
    this.branches.put(id, b);
    return b;
  }

  public FlowId getFlowId()
  {
    return this.txn.getFlowId();
  }

  /**
   * called by the branch to indicate it's the winner.
   */

  void winner(final ServerBranch winner)
  {

    Preconditions.checkState((this.state == State.Trying) || (this.state == State.Branched), "invalid state", this.state);

    if (this.branches.remove(winner.id()) == null)
    {
      throw new IllegalStateException("Branch was not registered");
    }

    log.debug("State {} -> Completed", this.state);
    this.state = State.Completed;

    this.branches.values().forEach(b -> b.destroy());
    this.branches.clear();

  }

}
