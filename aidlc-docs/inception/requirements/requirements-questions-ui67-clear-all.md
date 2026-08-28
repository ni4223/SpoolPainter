# Requirements Questions — UI-67, dedicated "Clear all" button (2026-08-28)

**Request**: *"another change i want is to have dedicated button for clear all,
maybe on top left"*

**[[UI-66]] is parked**, not dropped: its design answers are already pre-filled in
`requirements-questions-v2.5-features.md` and it resumes when you say so.

**ANSWERED 2026-08-28.** The maintainer replied *"just the button and setting goes
back to ..."* to a clarification of an ambiguous "1". Resolved as: **2.1 A, 2.2 A,
2.3 B, 2.4 A** — the button and the Settings-gear cleanup, **no Undo**.

## What already exists

`MainViewModel.onClearAll()` is written and proven. It resets the form, the spool
selection, the ambiguity and observed-tag state and both scan-suggestion lists,
while keeping `rawWriteMode` and `moreDetailsExpanded` so you stay on the section
you were looking at. Today it is reachable from exactly one place: the "Clear all"
row inside the MoreVert overflow menu. So this is about promoting an existing
action, not building one.

## One thing you should know before approving

The code carries the **opposite** decision, made on device during UI-57: *"clear-all
lives in an overflow menu on the existing icon, NOT as its own control"*, after
three header variants were tried and all crowded the logo.

I think your instinct is still right, because all three rejected variants were on
the **right**: a RestartAlt icon (read as "reload"), bare "Clear" text, and an
outlined "Clear" whose border overlapped the NFC waves. **The left was never
tried**, and the layout says there is room: the logo is a centred Row about 209 dp
wide inside a full-width Column, leaving roughly 100 dp of empty gutter on each
side. The MoreVert button already lives in the right one without crowding
anything. So UI-57 found that *those controls in that spot* did not fit, not that
the header has no room.

---

## Question 2.1 — labelled button or icon?

A) **Text button reading "Clear all"** at top left. Says exactly what it does, and
   it is the same wording as today's menu row so nothing new to learn. Risk: it is
   wider than an icon, so a large system font on a narrow screen could push it
   toward the logo. I would cap it and verify on device in the install gate.
B) **Icon button.** Safe at 48 dp, but there is no honest glyph: X is already the
   field-level clear and reads as "close" in a header, RestartAlt was already
   rejected as "reload", and anything delete-shaped invites the much worse misread
   of "delete my spool from Spoolman".
C) Icon plus label together. Clearest, widest, most likely to crowd.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: A

---

## Question 2.2 — what happens to the menu row?

If "Clear all" gets its own button, the overflow menu is left holding only
Settings, and a MoreVert affordance covering a single item is pointless.

A) **Remove the row and turn MoreVert into a Settings gear.** One control, one
   action, each labelled by its own icon. This is slightly more visual change than
   you asked for, which is why I am asking.
B) Remove the row, keep MoreVert as-is with only Settings inside it. Least change,
   but a "more options" menu with one option is odd.
C) Keep "Clear all" in both places. Most discoverable, two paths to one action to
   keep in sync.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: A

---

## Question 2.3 — protecting against an accidental tap

Clearing is currently two taps behind a menu, and UI-57 deliberately chose **no
confirmation** because you clear often and a prompt every time is friction. A
single header tap that wipes a filled-in form changes that maths.

A) **Snackbar with Undo.** No prompt in the way, and a mis-tap costs one tap to
   recover. Honest cost: snackbars here carry no action button today, so this adds
   an effect variant, an `ActionPerformed` branch and a pre-clear state snapshot.
   Still my recommendation, because it protects the action without slowing it down.
B) **No confirmation**, exactly as today. Zero extra work, and a mis-tap loses
   whatever you had typed.
C) **Confirmation dialog.** Safest, and the friction UI-57 explicitly rejected.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: B  (maintainer did not ask for Undo; UI-57's no-confirmation call stands)

---

## Question 2.4 — greyed out when there is nothing to clear?

A) **Always enabled**, as the menu row is today. Simple, and no need to invent a
   "form is dirty" predicate and test it.
B) Disabled when the form is already blank. Gives feedback, but needs that
   predicate, and a greyed control tends to prompt "why is this greyed" more than
   it prevents a wasted tap.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: A
