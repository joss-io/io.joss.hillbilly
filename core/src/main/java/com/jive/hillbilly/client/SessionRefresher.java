package com.jive.hillbilly.client;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import com.jive.hillbilly.client.EmbeddedDialog.State;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SessionRefresher
{

  private final EmbeddedDialog dialog;

  @Getter
  private final SessionRefreshConfig config;

  private HillbillyTimerHandle timer;

  public SessionRefresher(final EmbeddedDialog dialog, final SessionRefreshConfig config)
  {
    this.dialog = dialog;
    this.config = config;
  }

  public void start()
  {

    if (this.config == null)
    {
      return;
    }

    this.update(this.config);

  }

  public void stop()
  {
    log.debug("Stopping Session-Expires");
    if (this.timer != null)
    {
      this.timer.cancel();
      this.timer = null;
    }
  }

  public void update(@NonNull final SessionRefreshConfig config)
  {

    log.debug("Session-Expires updated: {}", config);

    if (this.timer != null)
    {
      this.timer.cancel();
      this.timer = null;
    }

    if (this.config.isLocalRefresher())
    {
      log.debug("Session Refresh: {}, sending in {}", this.config, this.config.getExpires() / 2);
      this.timer = this.dialog.getNetwork().getExecutor().schedule(this::runExpires, this.config.getExpires() / 2, TimeUnit.SECONDS);
      // set timer to send
    }
    else
    {
      log.debug("Session Refresh: {}, hanging up in {}", this.config, this.config.getExpires());
      this.timer = this.dialog.getNetwork().getExecutor().schedule(this::runExpires, this.config.getExpires(), TimeUnit.SECONDS);
    }

  }

  private void runExpires()
  {

    if ((this.dialog.getStatus() == State.TERMINATED) || (this.timer == null))
    {
      // dialog is already terminated.
      this.timer = null;
      return;
    }

    this.timer = null;

    if (this.config == null)
    {
      return;
    }

    if (!this.config.isLocalRefresher())
    {

      final Instant now = this.dialog.getNetwork().getClock().instant();

      final Instant last = this.dialog.getLastValidMessageFromRemote();

      // our job is a lot easier, we just need to disconnect if we expire.

      if (last == null)
      {
        log.warn("Remote refresher, but haven't received message from remote side, sending BYE", this.config.getExpires());
      }
      else
      {
        log.warn("Remote refresher, but haven't seem message in {}, sending BYE", Duration.between(last, now));
      }

      return;

    }

    log.debug("Sending Session-Expires refresh");

    // this will either cancel or update, so sets a new timer.

    this.dialog.inviteUsage.refresh();

  }

}
