# Two Compose traps this app walked into

> **Rule:** a live region's description must change only as often as you want TalkBack to speak — never once per degree; and never add a sibling, even a zero-size one, to a `SpaceBetween` parent.

## 1. `liveRegion` on a continuously-changing value is a denial of service

A live region re-announces whenever its node's semantics **change**. The heading
readout carried:

```kotlin
val description = remember(rounded) { "Heading $rounded degrees, $label" }
Modifier.semantics(mergeDescendants = true) {
    contentDescription = description
    liveRegion = LiveRegionMode.Polite      // fires on every whole degree
}
```

`rounded` is the heading in whole degrees, so turning the phone through one
rotation queued roughly **360 announcements**. Polite only means they do not
interrupt — they still all get spoken, so TalkBack narrates continuously for as
long as the phone is moving and the user cannot hear anything else.

A comment above it said the keying had already been fixed once, from raw azimuth
to whole degrees. That was a 20× improvement on something that needed 360×.

### One node cannot carry both a precise description and a coarse announcement

Which is the actual trap — the obvious fix (coarsen the description) throws away
the exact heading on focus, and the other obvious fix (keep it precise) keeps the
narration. The split needs two nodes:

```kotlin
// Announced automatically: sector only — 8 changes per rotation.
Box(
    modifier = Modifier.size(0.dp).clearAndSetSemantics {
        contentDescription = sector
        liveRegion = LiveRegionMode.Polite
    },
)

// Read on focus: the exact heading, whenever the user asks for it.
Column(modifier = Modifier.semantics(mergeDescendants = true) {
    contentDescription = "Heading $rounded degrees, $spokenLabel"
}) { /* … */ }
```

Also: spell the cardinal out for the reader. Handed `"NE"` a screen reader says
two letters, and `"SW"` comes out as "southwest" only by luck of the
pronunciation dictionary — hence `bearingToSpokenLabel` alongside the on-screen
`bearingToFullLabel`, which stays abbreviated because "NORTH-EAST" is three times
as wide on a 52 sp readout.

## 2. A zero-size sibling is not free inside `SpaceBetween`

The live-region node above was first emitted as a sibling of the readout, whose
parent is:

```kotlin
Column(verticalArrangement = Arrangement.SpaceBetween) { … }
```

`SpaceBetween` distributes the free space into the gaps **between children**, so
the child count is an input to the layout. Adding a third child — even one
measuring 0 × 0 dp — adds another gap and moves everything on the compass screen.
Caught by reading the layout, not by the compiler, and it would not have thrown
or logged anything.

The node belongs *inside* whichever child it describes, not beside it. Same
applies to `SpaceAround` and `SpaceEvenly`; `Arrangement.Top` and friends are
indifferent to it.

## 3. A zero-size node does not exist for a screen reader

The fix for trap 1 above was first written as a `Modifier.size(0.dp)` `Box`
carrying `clearAndSetSemantics { contentDescription = …; liveRegion = … }`,
sitting invisibly beside the visible readout. It announces **nothing**.

Compose builds its accessibility node map in
`SemanticsUtils_androidKt.…findAllSemanticNodesRecursive`, which does
`region.set(node.touchBoundsInRoot)` then `region.op(unaccountedSpace, INTERSECT)`
and **only adds the node when that returns true**. A 0×0 rect makes an empty
Region, the intersection is empty, `op` returns false, and the node is dropped
from the tree. `sendSemanticsPropertyChangeEvents` iterates that same map, so no
live-region event ever fires. Minimum-touch-target expansion does not rescue it:
that applies only to nodes carrying an `OnClick` action, and a
`clearAndSetSemantics` node has none.

**Put the live region on a real, visible node whose own content already changes
at the cadence you want to announce at.** Here that is the cardinal label —
`NORTH`/`NE`/`EAST` changes eight times per rotation, which is exactly the
announce rate wanted, while the degree text changes ~360 times.

And do not solve it by merging the two into one node with a sector-keyed
description: a merged node's semantics include the degree `Text`, Compose emits
a content-changed event for a `Text` change as well as a description change, and
merging also replaces the exact degree on focus with the parent's description.
Two unmerged siblings — degree carries precision, cardinal carries the live
region — is the shape that works.

## 4. A state tag sized to its current text reflows everything around it

- The Settings Calibrate row showed accuracy as a trailing word. `NOT USABLE`
  is wider than `POOR`, so each flip rewrapped the `weight(1f)` title to two
  lines, grew the row, and made the whole dialog jump. Sensor accuracy flips
  often, so the dialog kept jumping.
- Fix: the tag reserves the width of its **longest** label with an invisible
  (`alpha(0f)`, `clearAndSetSemantics {}`) copy in a `Box`, with the live label
  drawn on top, aligned to the end. Then shorten the title until the reserved
  width fits on one line.
- Any trailing slot whose text changes at runtime needs the same treatment.
