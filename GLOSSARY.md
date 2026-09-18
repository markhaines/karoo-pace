# Glossary

| Term | Here it means |
|---|---|
| **Band** | One of the five speed-vs-average ranges that pick the card colour. Percentages of the ride average, not fixed speeds. |
| **Neutral band** | 95 to 105%: black card, white text, no chevron. The band GPS wander used to cross on its own. |
| **Chevron** | The up/down marker (⌃⌃ ⌃ ⌄ ⌄⌄) shown alongside the colour. |
| **Raw vs smoothed** | Two speed streams. Raw drives the number, 3s smoothed drives the colour. See ADR-0001. |
| **`PaceBands.kt`** | Pure Kotlin holding every threshold. Unit tested, and the place thresholds change. |
| **`SIMULATE_BANDS`** | The off-bike verification switch, so the field can be checked without riding. |
