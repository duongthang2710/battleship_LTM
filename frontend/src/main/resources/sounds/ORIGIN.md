# Audio credits and source information

This project includes 34 prepared WAV assets downloaded from the sources below.
No assets were extracted from Steam, trailers or Hasbro's BATTLESHIP.
The earlier nine procedural placeholder sounds have been replaced.

## Required music credits — CC BY 4.0

- **"Signal to Noise" by Scott Buckley** — released under **CC BY 4.0**.
  Source: https://www.scottbuckley.com.au/library/signal-to-noise/
  Composer: https://www.scottbuckley.com.au
  Prepared as `bgm_menu.wav`: excerpt from the beginning, resampled to PCM16
  stereo 44.1 kHz, level adjusted, circular crossfade; final length 64 seconds.
- **"Intervention" by Scott Buckley** — released under **CC BY 4.0**.
  Source: https://www.scottbuckley.com.au/library/intervention/
  Composer: https://www.scottbuckley.com.au
  Uses the composer's **No Piano Melody** alternative mix. Prepared as
  `bgm_placement.wav`: excerpt from the beginning, resampled to PCM16 stereo
  44.1 kHz, level adjusted, circular crossfade; final length 64 seconds.

License: https://creativecommons.org/licenses/by/4.0/
Full license text is included in `licenses/CC-BY-4.0.txt`.
Retain these credits and the license when distributing the game or its assets.
For videos using this music, the composer requests attribution in the description.

## CC0 sources

| Author | Original pack/work | Prepared assets |
| --- | --- | --- |
| Kenney | https://kenney.nl/assets/interface-sounds | Click, Ready, UI, turn/timer and social notification sounds; exact selections in `manifest.json` |
| Kenney | https://kenney.nl/assets/ui-audio | `ui_hover.wav`, `ship_rotate.wav` |
| Kenney | https://kenney.nl/assets/impact-sounds | `place.wav` from `impactWood_light_000.ogg` |
| Kenney | https://kenney.nl/assets/music-jingles | `victory.wav` (PIZZI00), `defeat.wav` (PIZZI01), `match_start.wav` (PIZZI02) |
| Thimras | https://opengameart.org/content/battle-at-sea | `cannon.wav`, `hit.wav`, `splash.wav`, `sunk.wav` |
| yd | https://opengameart.org/content/enemy-ship-approaching | `bgm_battle.wav` from `esa.ogg` |
| jasinski, submitted by qubodup | https://opengameart.org/content/beach-ocean-waves | `amb_ocean.wav` |
| Luke.RUSTLTD | https://opengameart.org/content/wind1 | `amb_wind.wav` from `wind1.wav` |

CC0: https://creativecommons.org/publicdomain/zero/1.0/
Pack license notices and the full CC0 text are included in `licenses/`.

## Preparation and reproduction

All effects are mono signed PCM16 little-endian WAV at 44.1 kHz. Backgrounds are
stereo in the same format. Edits include silence trimming, short edge fades,
peak normalization to 0.78, and circular crossfades for loops. Very short UI
sounds have a padded tail. No encoded audio decoder is required at runtime.

`manifest.json` records the original filename, source page, author, license,
duration, edits and SHA-256 for every prepared asset.

Run `python tools/audio/import_assets.py` with requests, numpy and scipy installed.
On Windows the script downloads a checksum-verified FFmpeg wheel into the ignored
`build/audio-sources/` directory. Set `FFMPEG` to use your own converter instead.
Original downloads and source/license page snapshots stay in that directory.
The old procedural placeholder generator has been removed. Use only the importer
above to reproduce the current audio set.
