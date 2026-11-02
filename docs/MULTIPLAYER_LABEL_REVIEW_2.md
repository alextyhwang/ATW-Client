# MP-03 independent review, round 2

2026-10-06. **Verdict: no blocking findings; ready for controlled runtime
qualification.** No source changes are required by this review. Runtime admission,
installation, measured game benefit and performance promotion remain separate
gates; none is established here.

## Scope and baseline

Reviewed the working-copy MP-03 diff against HEAD
`0ac5f2d73a50127ac8092db9b14163df8dc84efe` on
`performance/weave-1.4.1-mc1.8.9`, the complete label tests, relevant consumer
calls, existing test XML, candidate bytecode/sources jar, and retained cost-screen
evidence. Read root `AGENTS.md`, `MODS.md`, `MULTIPLAYER_EXECUTION_TASKS.md`,
`MULTIPLAYER_LABEL_IMPLEMENTATION.md`, `MULTIPLAYER_LABEL_REVIEW_1.md`,
`MULTIPLAYER_LOCAL_COST_SCREEN.md` and `MULTIPLAYER_LABEL_REFINEMENT_2.md`.

The code-review skill's standards and specification checks were applied directly
under the explicit no-nested-agents restriction. The scoped tracked module diff
contains only `LevelTag.java`; its only untracked source is `LevelTagTest.java`.
No renderer, provider, network, width, NPC, TTL or build-configuration changes
appear in that scope. This reviewer wrote only this report. No builds, test or
benchmark reruns, game/GUI operation, installation, settings changes, network
requests, nested agents or git mutations were performed. Shared prior work was
preserved.

## Standards assessment

No blocking standards findings. The private final text and explicit flag, named
byte states, exact-class guard and private volatile memo form a small local
change consistent with the repository's immutable-derivation scope. There is no
global cache, lock, new dependency or invalidation mechanism. The production
comments correctly describe the permitted duplicate first computation.

## Specification and correctness assessment

- `weave-mods/atw-levelhead/src/main/java/com/atw/levelhead/data/LevelTag.java:22`:
  construction normalizes the same two fields, combines their private values,
  and stores the explicit factory flag. It invokes no overridable getter and
  performs no classification. Null normalization and unconditional ampersand
  replacement remain identical to HEAD. Classification still combines fields
  before the original regex, preserving boundary-spanning formatting, default-dot
  line-terminator/surrogate behavior and Java Unicode `equalsIgnoreCase` semantics.
- `LevelTag.java:54`: the explicit flag returns true before subclass dispatch or
  memo access. Both factories retain their values and force true for any footer.
  At `:58`, every subclass bypasses the memo, including subclasses that inherit
  `getText()`. Inherited `isNicked()` calls virtual `getText()` exactly once per
  invocation, retains null/raw-text handling and getter side effects, and
  propagates getter exceptions each time. Public signatures and overrides remain
  valid. A normal subclass cannot invoke the private explicit-flag constructor.
- `LevelTag.java:63`: the memo has an initially zero unknown state and only
  writes a settled false/true byte. Its dependency is correctly constructed final
  immutable text; the constructor does not escape `this`. The volatile scalar
  read/write supplies visibility and ordering without a separate mutable payload.
  Racing unknown readers can independently compute, but all successful writers
  derive the same state. A late duplicate write cannot reverse the result or
  restore unknown. No compare-and-set or lock is needed for this contract.
- `LevelTag.java:65`: classification finishes before the memo write. If that
  computation throws, this invocation publishes no settled state and propagates
  the failure; it does not memoize failure as false. The fixed regex introduces
  no input-dependent syntax exception. First-use regex/allocation work occurs on
  the calling thread, as with HEAD, rather than round 1's constructor. Successful
  later base calls deliberately skip that work. Subclass exception placement is
  unchanged. Eager concatenation and its allocation/failure placement remain an
  intentional round 1 tradeoff.
- `LevelTag.java:50`: repeated inherited text reads return one immutable reference.
  Contents and field-based getter behavior match HEAD. Fresh-reference behavior
  intentionally changes, including for subclasses calling `super.getText()`;
  identity comparisons, identity hashes or synchronization on returned strings
  could observe it. Inspected repository consumers use values/rendering/parsing,
  and no production subclass was found. The round 1 identity observation remains
  applicable without becoming a blocker for the authorized reuse contract.

## Test quality and artifact correspondence

The oracle at `LevelTagTest.java:233` independently reproduces HEAD's operations.
Existing cross-products/splits, factories, Unicode/null cases, BedWars consumers
and subclass call/exception tests remain meaningful. New tests at `:78`, `:103`
and `:115` cover deferred true/false classification, explicit bypass, and six
readers sharing 256 initially unknown tags for 16 rounds. Executor submission
and latches provide proper fixture publication; futures surface worker failures.
The bounded waits and shutdown avoid an unbounded test hang.

Private-state reflection guards unknown/settled states and the volatile byte
declaration. This is appropriate for the internal deferral contract, although
neither stress nor a modifier assertion proves JMM correctness. The stress test
does not force duplicate computation or isolate an unsynchronized publication
litmus. Stable-state assertions alone would not detect redundant recomputation;
source and bytecode inspection confirm the settled path skips classification.
These are evidence limits, not missing tests that block this change.

Read-only XML inspection corroborates **17 passed tests: 11 LevelTag and 6
Compatibility, zero failures/errors/skips**. Timestamps match the report:
`2026-10-06T04:34:19.760Z` and `2026-10-06T04:34:18.950Z`. The compiled test class
exposes the same eleven label test methods. This review did not reproduce the
build command or test execution.

| Reviewed identity | SHA-256 |
| --- | --- |
| Current LevelTag source | `8c4a3be44290b4064e58610f843430ec9c23c688ff593a9e71913e0ff1299326` |
| Current LevelTag test source | `a39b4b2d333fbe82960517bc60640a4e19fa4c0627643698772cae91fc12da5e` |
| Current candidate jar | `0b7756ced8bc2e7889be63029d868e7e3e9ab4be8153745d51073c3eb98967ea` |
| HEAD reference source | `57b7f68d3de7d6da8f1002966e53107d07d346e4593c566dd33c37d492828203` |

The inspected artifact is
`weave-mods/atw-levelhead/build/libs/ATWLevelHead-0.1.0-atw-weave1.4.1.jar`.
Its `LevelTag.class` exactly matches the module's compiled main class and has
major version 61. Disassembly confirms the volatile byte, constructor without
classification/getter calls, explicit short circuit, subclass virtual dispatch
and exact-base memo branch/write. The sources jar matches current source after
line-ending normalization. Metadata retains Minecraft 1.8.9, `mcp-named`, the
existing initializer and both hooks. These checks establish correspondence of
the inspected build artifact, not installation or loaded-class identity.

## Report accuracy and remaining qualification

The round 2 report accurately separates current lazy behavior from preserved
round 1 history. The archive contains 29 files; all provenance-listed adapted
source hashes match in both snapshots. Reversing only the candidate package
adaptation recovers each reported original source hash, including round 1's
`e48021b8337a78a531d10f138a4d60d57de5997e3a74b6e95753ae80ef698152`.
Current capture equals reviewed source and both reference captures equal HEAD.
Harness/Java hashes match current files and archived provenance. Historical
copy-time verification of every original/archive file is reported by the owner;
this review cannot independently reconstruct that earlier filesystem state.

Read-only calculation from the retained CSVs confirms four forks, nine samples,
59 cases and 4,248 arm measurements. All 2,124 paired checksums agree and all
four stderr files report parity success. All 59 aggregate arm medians,
allocation summaries and paired ratio ranges agree with raw data. In particular:

- Mixed construct-only is 14.2 ns / 65.6 B for HEAD versus 20.9 ns / 120.1 B
  for lazy: the disclosed 6.7 ns / 54.5 B overhead remains.
- Mixed warm pairs include fork 2's candidate/reference medians of
  238.8 / 198.6 ns. The report correctly labels variable signs and discloses the
  loss rather than claiming a consistent warm win.
- Removed eager construction regex, zero measured warm allocation and historical
  eager/lazy comparisons support local helper conclusions. Separate runs do not
  isolate volatile-read cost or prove a game improvement.

The round 1 upfront-regex concern is addressed. Retained combined-string memory
and construction cost remain, including text-only disk-cache/level-mode paths.
New residuals are first-use classification on a potentially rendering caller,
duplicate cold work under contention, and volatile-load/branch cost on warm
ordinary reads. The single-thread screen does not quantify contention, isolated
first-use frame latency, retained heap or production creation/classification rates.

Parent qualification should verify the installed and loaded artifact after
restart, ordinary/textually nicked/explicit estimate labels, above-head colors
and both passes, tab output, fresh/warm tags, mode and world transitions, and
resource/font refresh under existing visibility and Hypixel gates. Include
creation-heavy text-only paths and matched quiet/crowded workloads. Runtime
behavior and frame/tick evidence must precede any performance promotion. No FPS,
frame-tail, transition, ping or hit-registration gain is established by this review.
