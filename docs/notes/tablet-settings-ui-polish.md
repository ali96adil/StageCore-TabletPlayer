# Tablet Settings UI Polish

## Problem

Physical testing showed that the current settings/test panel is too text-heavy and jumpy:

- Result text appears before many controls.
- After pressing scan/test buttons, the user may need to scroll back up to see the result.
- Some result updates change the height of the panel and make the page jump up or down.
- Repeated test presses can feel unclear because the active result is not pinned near the operator's hand.

## Desired behavior

Convert the settings panel into a more operator-friendly testing console.

### Sticky header / status area

Keep a compact always-visible header at the top of the settings panel with:

- Device name / ID short form
- Readiness badge
- Server / heartbeat status
- Last action label
- Last action result

The result area should update in place instead of pushing the full settings page around.

### Action feedback

Every button that runs a test should give immediate local feedback:

- `Running...`
- `READY ✅`
- `CHECK ⚠️`
- `FAILED ❌`

The user should not need to scroll to discover whether the button worked.

### Reduce noisy text before buttons

Move long explanations into collapsible/help sections or place them after the primary controls.

The main test path should stay short:

1. Prepare folder
2. Open folder
3. Reload + scan
4. Cue preview
5. Prepare cue
6. GO cue
7. Test live
8. Enter show mode

### Avoid scroll jumps

- Do not insert large dynamic text blocks above the currently pressed button.
- Update a fixed result/status view instead.
- Keep detailed results in a dedicated details area below, optionally collapsed.

## Suggested implementation

- Replace the current single `info` block with two areas:
  - `statusHeader`: compact top summary
  - `resultDetails`: detailed expandable output
- Make the ScrollView content start with controls, not long text.
- Add helper method such as `showActionResult(actionName, resultText, severity)`.
- Keep `readinessBadge` updated independently from large details text.

## Acceptance criteria

- Pressing `فحص الملفات` shows the result immediately without needing to scroll.
- Pressing `GO Cue` shows success/failure beside or directly under the cue test controls.
- Long cue/media details do not move the main buttons unexpectedly.
- The panel remains usable on small tablet screens in portrait orientation.
