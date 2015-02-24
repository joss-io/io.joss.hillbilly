package com.jive.hillbilly.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.threeten.extra.Temporals;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.MoreExecutors;
import com.jive.ftw.sip.dummer.txn.ClientTransactionListener;
import com.jive.ftw.sip.dummer.txn.ClientTransactionOptions;
import com.jive.ftw.sip.dummer.txn.SipClientTransaction;
import com.jive.hillbilly.client.DialogRegistrationHandle;
import com.jive.hillbilly.client.EmbeddedDialog;
import com.jive.hillbilly.client.EmbeddedNetworkSegment;
import com.jive.hillbilly.client.HillbillyRequestEnforcer;
import com.jive.hillbilly.client.HillbillyRuntimeService;
import com.jive.hillbilly.client.HillbillyTimerHandle;
import com.jive.sip.message.api.DialogId;
import com.jive.sip.message.api.SipMethod;
import com.jive.sip.message.api.SipRequest;
import com.jive.sip.message.api.SipResponse;
import com.jive.sip.processor.rfc3261.MutableSipRequest;
import com.jive.sip.processor.rfc3261.RfcSipMessageManagerBuilder;
import com.jive.sip.processor.rfc3261.SipMessageManager;
import com.jive.sip.transport.api.FlowId;
import com.jive.sip.uri.api.SipUri;

@Slf4j
public class TestEmbeddedNetworkSegment implements EmbeddedNetworkSegment
{

  private final SipMessageManager mm = new RfcSipMessageManagerBuilder().build();

  @Getter
  private final Multimap<String, EmbeddedDialog> handles = HashMultimap.create();

  @Getter
  private final ArrayBlockingQueue<TestOutgoingTransaction> outgoingTransactions = new ArrayBlockingQueue<>(100);

  @Getter
  private final ArrayBlockingQueue<SipRequest> outgoingRequests = new ArrayBlockingQueue<>(100);

  private Instant now = Instant.now();
  
  @Override
  public HostAndPort getSelf()
  {
    return HostAndPort.fromParts("127.0.0.1", 12345);
  }

  @Override
  public DialogRegistrationHandle register(final DialogId id, final EmbeddedDialog dialog)
  {

    log.debug("Registered dialog {}", id);

    this.handles.put(id.getRemoteTag(), dialog);

    return () ->
    {
      log.debug("Removing registration for {}", id);
      TestEmbeddedNetworkSegment.this.handles.remove(id.getRemoteTag(), dialog);
    };

  }

  @Override
  public SipMessageManager messageManager()
  {
    return this.mm;
  }

  @Override
  public SipClientTransaction sendInvite(final SipRequest invite, final FlowId flowId, final ClientTransactionOptions opts,
      final ClientTransactionListener listener)
  {
    final TestOutgoingTransaction txn = new TestOutgoingTransaction(invite, listener);
    this.outgoingTransactions.add(txn);
    return txn;
  }

  @Override
  public void transmit(final MutableSipRequest ack, final FlowId flowId, final String branchId)
  {
    this.outgoingRequests.add(ack.build(this.mm));
  }

  public void sync()
  {
    // TODO Auto-generated method stub

  }

  @Override
  public String getServerName()
  {
    return "test/hillbilly";
  }

  @Override
  public String getId()
  {
    return "test1";
  }

  ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

  @Data
  @AllArgsConstructor
  public class ScheduledRunnable
  {
    private Runnable command;
    private Duration delay;
  }

  @Getter
  private List<ScheduledRunnable> queued = Lists.newLinkedList();

  @Override
  public HillbillyRuntimeService getExecutor()
  {
    return new HillbillyRuntimeService()
    {

      @Override
      public void execute(final Runnable command)
      {
        MoreExecutors.sameThreadExecutor().execute(command);
      }

      @Override
      public HillbillyTimerHandle schedule(final Runnable command, final long duration, final TimeUnit unit)
      {
        Duration d = Duration.of(duration, Temporals.chronoUnit(unit));
        log.debug("TEST: Queued command to run in {}", d);
        ScheduledRunnable cmd = new ScheduledRunnable(command, d);
        queued.add(cmd);
        return () ->
        {
          log.debug("TEST: Cancelling scheduled timer");
          return queued.remove(cmd);
        };
      }

    };
  }

  @Override
  public HillbillyRequestEnforcer getEnforcer()
  {
    return new HillbillyRequestEnforcer(true);
  }

  void fastForward(Duration time)
  {

    log.debug("TEST: fast forwarding {}", time);

    for (ScheduledRunnable s : queued)
    {
      s.setDelay(s.getDelay().minus(time));
    }

    queued.sort((a, b) -> a.getDelay().compareTo(b.getDelay()));

    now = now.plus(time);
    
    while (!queued.isEmpty())
    {

      ScheduledRunnable res = queued.get(0);

      if (!res.getDelay().isNegative() && !res.getDelay().isZero())
      {
        return;
      }

      queued.remove(0);

      log.debug("TEST: removing scheduled run");

      getExecutor().execute(res.getCommand());

    }

  }

  @Override
  public long getActiveDialogCount()
  {
    return handles.size();
  }

  public EmbeddedDialog getRemoteDialog(String string)
  {
    return this.handles.get(string).iterator().next();
  }

  @Override
  public Clock getClock()
  {
    return Clock.fixed(now, ZoneId.systemDefault());
  }

  @Override
  public void autoKill(SipResponse res)
  {
    this.outgoingRequests.add(MutableSipRequest.ack(res).build(mm));
    this.outgoingTransactions.add(new TestOutgoingTransaction(MutableSipRequest.create(SipMethod.BYE, SipUri.ANONYMOUS).build(mm), null));
  }

}
