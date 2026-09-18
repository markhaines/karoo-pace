# ADR-0001: Colour from the 3s smoothed stream, number from the raw one

- **Status:** Accepted
- **Date:** 2026-09-18
- **Supersedes:** none

## Context

The field shows a speed number and colours the card by how that speed compares to the ride
average. Driving both from the same stream fails whichever stream is picked.

Raw GPS speed wanders by 1 to 2 km/h at steady effort. That wander is **wider than the
neutral band** (95 to 105% of average), so a rider holding a constant effort would watch the
card strobe between green, black and orange. Driving the number from a smoothed stream
instead makes the field feel laggy and unlike the native Speed card it replaces.

## Decision

Split the streams. The **number** shown is the raw speed, so the field reacts instantly and
reads exactly like the native card. The **colour** follows the 3s smoothed speed, so it
changes only when the effort genuinely changed.

## Consequences

The number and the colour can briefly disagree, which is correct rather than a bug: the
number is answering "how fast am I right now" and the colour is answering "am I up or down
on my average". Anyone reading the code has to know the two come from different streams.

The 3s window is a tuning constant, not a law. It was chosen to be wider than the observed
GPS wander. Narrowing it reintroduces strobing.

## Options rejected

- **Smooth both.** Makes the field visibly laggier than the native card it is meant to be
  indistinguishable from.
- **Widen the neutral band instead.** Hides real effort changes to solve a display artefact,
  and the band widths are the product.
