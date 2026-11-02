# Provenance and license

This experimental module is provided under **GPL-3.0-only** (see `LICENSE`).
ATW's implementation reads the original map supplied by injected bytecode and
uses Java's existing COW snapshot iteration. It does not copy or bundle Weave's
EventBus class, subscription/removal implementation, reflective consumer,
loader, API, Kotlin runtime, or ASM classes. Canonical parity cases were adapted
from ATW's private `upgrade-work/pvp-20261006/eventbus-screen/EventBusScreen.java`.

Behavior was verified against Weave 1.4.1 published/shaded binaries and the
pinned Weave source/tag `f5509fe9eb9c2004996ee65ed3de3ced20ca44bf`:

- Source project: <https://gitlab.com/weave-mc/weave>
- EventBus source: `api/src/main/kotlin/net/weavemc/api/event/EventBus.kt`
- Reference EventBus source SHA-256:
  `922d36d0e5431024b1b1653b729f645f401418be949f630d4980f143642cc0f2`
- The pinned root LICENSE is GNU GPL version 3, SHA-256:
  `230184f60bae2feaf244f10a8bac053c8ff33a183bcc365b4d8b876d2b7f4809`.
  The pinned README identifies GPLv3 as well. No applicable MIT grant was found.

The strict guard stores fingerprints of the original classes, not their
executable implementation. Distribution must preserve the module's source and
license notices. The Gradle wrapper is reused from this repository's canonical
modules and remains subject to its upstream Gradle license.
