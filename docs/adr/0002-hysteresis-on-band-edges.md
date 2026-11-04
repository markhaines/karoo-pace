# ADR-0002: Hold the shown band until a reading is clear of its edge

- **Status:** Accepted
- **Date:** 2026-11-04
- **Supersedes:** none

## Context

ADR-0001 stops raw GPS wander from strobing the colour, but the 3s smoothed stream still
moves a little. A rider holding an effort that sits right on an edge (105% of average, say)
saw the whole card flip between black and green every update, because each frame was judged
on its own against exact edges (issue #2). The same happens at the 5 km/h cut-off on a slow
climb.

## Decision

The band decision takes the band currently shown. Entering a band from neutral uses the plain
edges, unchanged. Leaving a band needs the reading to be past that band's edges by a margin:
1 percentage point of the average on band edges (`BAND_HYSTERESIS`), 1 km/h on the minimum
colour speed, 0.1 m/s on the minimum average. Invalid input is always neutral, and neutral
carries no hysteresis, so after a dropout the band is decided afresh.

## Consequences

The field now holds one band of state per view. A band change from a neighbouring band lands
up to 1 point later than the documented edge, in the direction of travel; the edges a rider
reads in the README are still where colour first appears. Each margin must stay under half the
narrowest band (5 points) or a band could become unreachable; a unit test holds that.

## Options rejected

- **Minimum dwell time before a change.** Delays every real change, not just the ones on an
  edge, and makes the colour lag the number further than ADR-0001 already accepts.
- **Widen the neutral band.** Rejected in ADR-0001 for the same reason: the band widths are
  the product.
- **Smooth over a longer window.** Laggier everywhere to fix a problem that only exists on
  edges.
