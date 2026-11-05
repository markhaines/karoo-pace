# karoo-pace

## Language

- **Band**: One of the five speed-vs-average ranges that pick the card colour. Percentages of the ride average, not fixed speeds.
- **Neutral band**: 95 to 105%: black card, white text, no chevron. The band GPS wander used to cross on its own.
- **Chevron**: The up/down marker (⌃⌃ ⌃ ⌄ ⌄⌄) shown alongside the colour.
- **Raw vs smoothed**: Two speed streams. Raw drives the number, 3s smoothed drives the colour. See ADR-0001.
- **`PaceBands.kt`**: Pure Kotlin holding every threshold. Unit tested, and the place thresholds change.
- **`SIMULATE_BANDS`**: The off-bike verification switch, so the field can be checked without riding.
- **Hysteresis**: The margin a reading must cross past an edge before the card leaves the band it is showing. 1 percentage point on band edges, 1 km/h on the minimum colour speed, 0.1 m/s on the minimum average. See ADR-0002.
- **No reading**: A speed that is missing, NaN, infinite or negative. The number shows `--` (never `0`), and the card goes neutral if it is the 3s speed or the average.
- **Render window**: The 1s minimum gap between redraws, set by the Karoo's 1Hz limit on extension views. Newest frame wins. See ADR-0003.
