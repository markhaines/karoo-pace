# ADR-0003: Redraw at most once a second, newest reading wins

- **Status:** Accepted
- **Date:** 2026-11-04
- **Supersedes:** none

## Context

The field listens to four streams (speed, 3s speed, average, profile) and redrew on every
update from any of them. karoo-ext's `ViewEmitter.updateView` accepts one view a second and
silently drops anything within 900ms of the last (documented in its source as "can only be
called at 1Hz"). So bursts produced redraws the Karoo threw away, and the one it kept was not
necessarily the newest. Stream callbacks were also buffered, so a slow redraw let stale
readings queue up (issue #1).

## Decision

Every stream callback is conflated: only its newest value is kept. Redraws go through one
loop that draws a frame, waits a full second (`ViewPipeline.RENDER_WINDOW_MS`), then draws
whatever is newest by then. A lone update after a quiet spell is drawn at once. A failed draw
(dead host binder) ends the loop, which cancels the streams and unregisters every consumer.
The view's setup and its redraw loop share one job, so the emitter's cancel stops both.

## Consequences

The number can trail the newest reading by up to a second, which is no worse than the
Karoo's own limit. Waiting the full second, rather than 900ms, keeps clear of the emitter's
own timing slop. If karoo-ext changes its rate limit, `RENDER_WINDOW_MS` has to follow it.

## Options rejected

- **Debounce (draw only after input goes quiet).** Telemetry never goes quiet mid-ride, so
  the field would rarely draw.
- **Fixed 1Hz tick.** Redraws when nothing changed and adds up to a second of delay to a lone
  update.
