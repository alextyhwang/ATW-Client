# Mac input foundation

2026-10-06. Workspace `ATW-Client-Mac-Metal`, branch
`mac/metal-lunar-weave-1.8.9`. Source implementation and static review only.
Lunar Client, Weave 1.4.1, Minecraft 1.8.9 and Java 17 remain the target.

**Implemented:** a default-OFF continuity provider around the existing
game-owned `MouseHelper`, with one movement consumer, game-thread state
handling and original-helper fallback. **Not implemented or established:**
Mac native raw input, an arm64 native input backend, complete Windows RawInput
feature parity, physical-input equivalence, latency improvement or Metal support.
The actual Lunar Mac native/input owner and transformed helper/camera remain
unknown. The complete existing renderer is untouched.

## Scope and preserved baseline

Read the [approved handoff](research/MAC_IMPLEMENTATION_HANDOFF.md), root
`AGENTS.md`, `MODS.md`, [portable contract](PORTABLE.md),
[proposal](research/MAC_METAL_PROPOSAL.md), its compatibility/architecture/reference
audits, and the existing RawInput source, continuity fixes and reviews.
The [round-2 continuity review](PVP_RAWINPUT_REVIEW_2.md) and
[round-1 fixes](PVP_RAWINPUT_FIXES_1.md) describe the copied Windows candidate;
their historical builds/tests are not validation of this Mac change.

Only these files belong to this implementation delta:

- `weave-mods/raw-input/src/main/java/com/github/koxx12dev/RawInput.java`:
  one import and the opt-in Mac event-registration branch.
- `weave-mods/raw-input/src/main/java/com/github/koxx12dev/input/MacInputProvider.java`
  (new): request selection, installation restrictions and tick lifecycle.
- `weave-mods/raw-input/src/main/java/com/github/koxx12dev/input/MacContinuityMouseHelper.java`
  (new): delegate ownership, state observation, movement/grab transfer and fallback.
- `weave-mods/raw-input/src/main/java/com/github/koxx12dev/input/LookInputContinuity.java`
  (new): session identity, suppression and drain-before-admission state.
- `weave-mods/raw-input/README.md`: appended Mac candidate documentation.
- `docs/MAC_INPUT_IMPLEMENTATION.md`: this report.

The task-entry owned diff and edited-file contents were captured privately in
session memory before editing. Comparing the new registration removed from
`RawInput.java` with that snapshot gives identical source text; the existing
README is retained as a prefix. Task-entry line endings outside the additions
were preserved as well.
The Windows producer/helper/accumulator/rescan logic and existing tracked and
untracked tests were not edited. The task-entry dirty HEAD diff includes earlier
atomic/continuity work and must not be attributed to this Mac implementation.
No central ledger or other agent's files were edited.

## Selection and admission

Without `-Datw.input.macContinuity=true`, Mac takes the task-entry dormant
RawInput branch: the installed input helper is left alone, with the existing
non-Windows warning/mod-folder registration. Windows takes its unchanged
JInput branch regardless of this property. Linux and other OS names cannot
request this provider. The property is a startup-only mod experiment; no saved
launcher setting, Config default, dependency or packaging change was made.

The experimental request recognizes `Mac OS X`, `macOS` and `Darwin` using
`Locale.ROOT`. Missing/invalid/inaccessible property values do not opt in.
At `StartGameEvent.Post`, a provider attempts installation once. It requires
Minecraft's game-thread check, a non-null helper whose exact class is
`net.minecraft.util.MouseHelper`, and already-created LWJGL Display/Mouse.
An unknown subclass is left intact. Repeated start events do not nest wrappers
or start workers. A declined request retains the original helper and emits a
short reason without paths, device names or exception details. There is no
automatic delayed retry when natives or the helper are unavailable.

These restrictions protect ownership; **they are not a bytecode fingerprint or
Mac qualification**. A transformed base `MouseHelper` can still satisfy the
class check. No protected hook guard was relaxed, no synthetic Mac profile was
added, and no assumed native library/version/architecture was admitted.

## Sole movement source

The provider borrows the *existing helper object*. For every call while its
wrapper owns `Minecraft.mouseHelper`, it invokes that object's
`mouseXYChange()` once, then copies its `deltaX` and `deltaY`. It never also calls
the superclass method or LWJGL `getDX/getDY`, `poll`, `next`, wheel/button getters
or JInput. Grab/ungrab each delegate once to the same original object. Stale
wrapper references after restoration/replacement return without consuming
movement or manipulating the cursor. The provider never reasserts ownership
after another component replaces it.

The retained mapped-helper disassembly documents ordinary LWJGL2 getter-based
integer movement, and the retained LWJGL source documents each getter's
return-and-clear counter. Those are source/reference observations, not proof
that Mac Lunar resolves the same implementation. Delegation avoids creating a
second owner even when the native implementation beneath the base helper is
unknown. It does not establish that transformed code supports wrapper identity
or that no unrelated component consumes movement elsewhere.

Eligible steady-state samples copy original integer axes unchanged. There is
no Windows JInput Y negation, coordinate/Retina rescaling, acceleration change,
new sensitivity calculation, smoothing or camera rotation. Minecraft's existing
sensitivity, inversion and smooth-camera consumers remain downstream. Neither
the atomic Windows accumulator nor a second queue is used on this Mac path.
Stable calls allocate no new sample objects; two session object references and
small booleans describe continuity. No cache, native bridge, timer or polling
thread was added.

## State and deliberate discards

State observation runs on the installing game thread in `TickEvent.Pre` and
again at each wrapper movement call, after the original sample is consumed.
Ticks only observe state; they do not drain or poll input. Look admission
requires a world and player, no GUI screen, Minecraft focus, created Display
and Mouse, an active Display and a grabbed Mouse. Every observed disallowed
state clears readiness and wrapper output. World or player identity changes
also clear readiness. Wrapper grab/ungrab resets continuity before delegating,
so recenter/regrab cannot acknowledge readiness by itself.

| Observed boundary | Output from the next helper call | Admission effect |
| --- | --- | --- |
| Startup or a new world/player | Zero pair after one original read | Establishes a fresh read boundary |
| Disallowed menu/focus/grab/world state | Zero pair after one original read, if called | Remains suppressed |
| First eligible call after an interruption or grab/ungrab | Zero pair after one original read | Subsequent stable samples may pass |
| Stable eligible state | Original X/Y pair | No accumulation, sign or unit change |
| State-query RuntimeException or LinkageError | Already-read original pair, if this is a movement call | Original helper restored |
| Another helper replaces the wrapper | Stale wrapper calls consume nothing | Replacement remains in control |

Readiness is acknowledged only by consuming and discarding an eligible
movement sample; a tick or state observation alone cannot acknowledge it.
This discards the entire interval accumulated while helper calls were absent,
even if it includes valid new movement. It deliberately sacrifices transition
counts and can briefly pause the camera. Repeated interruptions can discard
repeated samples. There is no claim of gapless handover or conservation of
physical motion across these boundaries.

If an optional state query fails, the wrapper restores the exact borrowed
object only while it still owns the game field. It performs no extra movement
read, grab, ungrab or native reinitialization. Original movement/grab exceptions
are not swallowed or retried; they remain original input failures. Calls from
another thread violate a strict owner check before native consumption, raising
an ownership exception rather than authorizing a second consumer. Seamless
recovery from arbitrary native errors or unsupported threading is not promised.

## Genuine blockers and qualification needed

- The actual Mac Lunar 1.8.9 JVM/native architecture, LWJGL/input implementation,
  classloader, transformed base helper and camera composition have not been
  captured. Exact-class and created-state checks cannot resolve those facts.
  A native Cocoa/HID raw provider cannot be implemented honestly without a
  proved native event owner, units/signs, thread/queue contract and fallback.
- Direct Mac camera recenter/grab operations can bypass helper overrides; the
  retained mapped camera contains such a Mac path. A false/true grab or focus
  interruption entirely between observations can be missed. No global screen,
  native focus or recenter hook was fabricated. OS event delivery and the
  timing of Display activity updates remain unqualified.
- This policy discards transition motion and suppresses GUI look input. Its
  parity with Lunar menu/cosmetic/preview behavior, smooth-camera retained
  state and physical movement needs verification. It does not flush an unknown
  native OS queue or certify the beginning of a native event interval.
- Helper construction/delegation and event callbacks need qualification against
  the final composed runtime, including single game-thread ownership and other
  mods replacing or inspecting the helper. Unknown helper subclasses remain
  declined until a separately reviewed provider contract exists.
- Device disconnect/reconnect/hotplug remain the existing native owner's
  responsibility. No Mac `/rescan`, JInput discovery, raw acceleration bypass
  or worker/watchdog is provided. The copied Windows worker lifecycle and
  cross-thread GUI-observation limitations remain unchanged.

Later qualification must identify installed/loaded source and native ownership,
then cover signed and simultaneous axes, sensitivity/inversion/smooth camera,
menus opened and closed between ticks, focus/alt-tab, grab/recenter/outside-window
behavior, session changes, device changes, Retina/external displays, and another
owner replacing the helper. It must verify no second movement consumer and
unchanged buttons/wheel/attack, hotkeys and current visuals under real Mac Lunar
plus pinned Weave. Keep the original path as default until those gates are met.
No FPS, polling-cadence, input-to-display, combat or network improvement is claimed.

## Static review performed

Reviewed source control flow for sole delegation, no alternate input drain,
startup-only selection, unknown-owner rejection, one installation attempt,
session/focus/menu/grab invalidation, actual-read readiness acknowledgment,
state-query fallback without retry and stale-wrapper non-consumption. Compared
the modified entrypoint/README with private task-entry snapshots and checked
owned diff whitespace. Existing API field/method references were checked against
retained mapped-helper/camera/Minecraft disassemblies and LWJGL source; those
historical artifacts are not Mac runtime evidence.

**Not performed:** builds, compilation, installation, runtime jar refresh,
automated tests, game launches, GUI work, benchmarks or Mac runtime qualification.
No copied scripts/caches were invoked, no commits/pushes made, and no original
`ATW-Client-Performance` files or private runtime/account data were read or written.
