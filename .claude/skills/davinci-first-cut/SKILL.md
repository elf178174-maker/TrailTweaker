---
name: davinci-first-cut
description: Edit the currently open DaVinci Resolve project into a polished first cut — remove dead air, false starts, flubs, and repeated takes while keeping natural pacing, then replace the voice with a Qwen3-TTS voice synced to the edited footage. Use whenever the user asks to edit, clean up, tighten, or create a first cut of a video in DaVinci Resolve.
---

# DaVinci Resolve first cut + Qwen3-TTS voice replacement

Goal: a clean, professional first cut that feels intentionally edited rather than
chopped up, with a smooth, consistent replacement voice that keeps the original
script, wording, timing, and intended delivery.

## Requirements (check first, be honest if missing)

This skill drives a **running DaVinci Resolve instance on the user's machine**.
Before doing anything, confirm:

1. Resolve is running with the target project open, and external scripting is
   enabled (Preferences → System → General → "External scripting using": Local).
   Resolve Studio is needed for external scripting in most versions.
2. The scripting module is importable. Typical environment:
   - macOS: `RESOLVE_SCRIPT_API="/Library/Application Support/Blackmagic Design/DaVinci Resolve/Developer/Scripting"`,
     `RESOLVE_SCRIPT_LIB="/Applications/DaVinci Resolve/DaVinci Resolve.app/Contents/Libraries/Fusion/fusionscript.so"`
   - Windows: `RESOLVE_SCRIPT_API="%PROGRAMDATA%\Blackmagic Design\DaVinci Resolve\Support\Developer\Scripting"`,
     `RESOLVE_SCRIPT_LIB="C:\Program Files\Blackmagic Design\DaVinci Resolve\fusionscript.dll"`
   - Linux: `RESOLVE_SCRIPT_API="/opt/resolve/Developer/Scripting"`,
     `RESOLVE_SCRIPT_LIB="/opt/resolve/libs/Fusion/fusionscript.so"`
   - `PYTHONPATH="$PYTHONPATH:$RESOLVE_SCRIPT_API/Modules/"`
3. `ffmpeg` is available, a word-timestamp transcriber is available
   (e.g. `faster-whisper` / `whisperx`), and Qwen3-TTS is available — either the
   local open-weights package/model or the hosted Qwen (DashScope) TTS API with a
   key the user has provided.

If any of these is unavailable (for example, this is a cloud/container session
with no Resolve, no GPU, or no network access to the TTS model), **stop and say
so plainly**. Do not pretend an edit was made. Offer what can be done instead
(e.g. write the scripts for the user to run locally, or produce an EDL/cut list).

## Ground rules

- **Never destroy the original.** Work on a duplicate timeline
  (`<name> - First Cut`). Leave the source timeline, media pool, bins, and project
  settings untouched unless a change is required to complete the edit.
- Mute or disable the original dialogue track; do not delete it.
- Preserve the existing project structure, video assets, and creative intent:
  B-roll, music, titles, effects, and color stay as they are unless a cut forces
  them to move.
- Prefer natural pacing over maximum tightness. When in doubt, keep it.

## Workflow

### 1. Connect and inspect

```python
import DaVinciResolveScript as dvr
resolve = dvr.scriptapp("Resolve")
project = resolve.GetProjectManager().GetCurrentProject()
timeline = project.GetCurrentTimeline()
fps = float(project.GetSetting("timelineFrameRate"))
media_pool = project.GetMediaPool()
```

Record a full inventory before changing anything: timeline name, fps, resolution,
start timecode, every track (`GetTrackCount("video"/"audio"/"subtitle")`), and for
every item on every track: name, `GetStart()`, `GetEnd()`, `GetLeftOffset()`,
`GetDuration()`, media pool item, and file path. Identify which audio track
carries the primary dialogue (ask the user if it is ambiguous). Save this
inventory to a JSON file — it is the reference for the final verification.

### 2. Transcribe with word-level timestamps

Render or export the dialogue audio (or read the source files directly) and
transcribe with word timestamps. Map every word to timeline frames. Also compute
a silence/energy map (e.g. ffmpeg `silencedetect`, roughly -35 dB / 0.3 s, tuned to
the recording's noise floor).

### 3. Decide what to cut (editorial pass)

Build a list of **keep ranges** in timeline frames. Remove:
- Dead air and awkward silences — but trim long pauses *down* (to ~0.25–0.6 s
  depending on the speaker's rhythm) rather than eliminating them.
- False starts, stumbles, and mid-sentence restarts ("So the — so the trail…").
- Failed attempts and repeated takes: when a line is said more than once, keep the
  **last complete, clean** take unless an earlier one is clearly better delivered.
- Explicit self-corrections ("wait, let me say that again", "no, sorry").
- Filler ("um", "uh") only where it disrupts flow; do not strip every one.

Keep, even if silent:
- Intentional pauses for emphasis, comedic timing, or letting a visual land.
- Reactions, laughter, and on-screen actions that carry meaning.
- Any segment containing information not repeated elsewhere.

Cut-point hygiene:
- Snap cuts to word boundaries plus a few frames of handle (~2–4 frames before
  the first word, ~4–8 frames after the last) so consonants are not clipped.
- Prefer cutting in silence / at low audio energy; avoid cutting mid-breath.
- Note likely visual jump cuts (same shot, small time jump on a talking head).
  Flag them for the user, or cover with existing B-roll / a punch-in only if that
  matches the project's existing style.

Write the decision list (kept/removed ranges with transcript text and a reason
for each removal) to a file and summarize it for the user. For long or
high-stakes projects, show it before applying.

### 4. Build the first-cut timeline

Duplicate the timeline (`timeline.DuplicateTimeline("<name> - First Cut")`, or
build a fresh one with `media_pool.CreateEmptyTimeline(...)` and matching
settings), then reconstruct it from the keep ranges:

- Rebuild with `media_pool.AppendToTimeline([{ "mediaPoolItem", "startFrame",
  "endFrame", "trackIndex", "recordFrame", "mediaType" }, ...])` using source
  frames (source frame = item left offset + (timeline frame − item start)), or,
  on Resolve versions that support it, ripple-delete removed ranges with
  `timeline.DeleteClips(items, True)` after splitting.
- Keep linked video/audio together. Shift every other track (B-roll, music,
  graphics) by the same ripple so anything anchored to a moment of dialogue
  stays in sync. Music beds that span cuts should be left continuous or flagged.
- Preserve clip properties (zoom, position, effects) where the API allows;
  re-apply via `SetProperty` if a rebuild drops them.

### 5. Generate the replacement voice with Qwen3-TTS

- Use the **cleaned transcript of the kept ranges** as the script — same wording,
  no rewriting. Only fix words the transcriber clearly got wrong.
- Synthesize per sentence or phrase (one kept segment = one or more TTS calls),
  not the whole script at once, so each piece can be timed independently.
- Use a single fixed voice/speaker setting, sampling settings, and seed for the
  whole video for consistency. If voice cloning from the original speaker is
  used, confirm the user has the right to clone that voice. Check the installed
  Qwen3-TTS package/API docs for the exact call signature rather than guessing.
- Match delivery: pass style/emotion instructions where supported (e.g.
  "excited", "deadpan") derived from the original read.
- Export each clip as WAV at the project's audio sample rate (usually 48 kHz),
  loudness-normalized consistently (e.g. -16 to -14 LUFS for web, or to match
  the original dialogue level).

### 6. Fit and place the new voice

- For each segment, compare TTS duration to the original spoken duration on the
  edited timeline. Time-stretch with a pitch-preserving method (ffmpeg `atempo`
  or rubberband) only within ~0.9–1.1×; beyond that, regenerate with a speed
  hint or adjust the in-segment silence instead of distorting the voice.
- Align each clip's start to the first word of the original segment. Lip-sync
  matters for on-camera speech; aim for within ~2 frames at phrase starts.
- Import with `media_pool.ImportMedia([...])` into a new bin (e.g. `TTS Voice`)
  and place on a new audio track named e.g. `VO - Qwen3-TTS` via
  `AppendToTimeline` with `recordFrame`.
- Ensure no TTS clips overlap each other and no clip extends past the end of its
  segment's video.
- Mute (do not delete) the original dialogue track. Keep room tone / ambience
  from the original under the new voice if it was present, so cuts do not drop
  to digital silence.

### 7. Verify the resulting timeline (required)

Re-read the timeline from Resolve — do not trust your own plan — and check:

- **Gaps:** on the main video track, each item's `GetStart()` equals the previous
  item's `GetEnd()` (unless a gap is intentional and noted).
- **Overlaps:** no two items on the same track overlap.
- **Sync:** linked video/audio items still share start/end; every TTS clip falls
  inside its intended segment; offsets vs. original word timings are within
  tolerance.
- **Audio integrity:** no zero-length or offline clips (`GetMediaPoolItem()` not
  None, media online), no clipped peaks, consistent loudness across TTS clips.
- **Nothing lost:** every non-dialogue asset from the inventory is still present
  on the new timeline (or its removal is explained).
- **Duration:** new timeline duration ≈ original − sum of removed ranges.

If possible, render a low-res preview (`project.AddRenderJob()` /
`StartRendering()`) and spot-check each cut point and the start/end of the voice.
Fix any problem found and re-verify.

### 8. Report

Tell the user:
- The new timeline's name; the original is untouched.
- Original vs. new duration and the number of cuts.
- A short list of the notable removals (retakes, restarts, long pauses) and any
  judgment calls (pauses deliberately kept, possible jump cuts flagged).
- Where the TTS audio lives (bin + track) and that the original dialogue track is
  muted, not deleted.
- Anything that could not be done or verified, stated plainly.
