# Requirements Questions — UI-66, auto-start the second pair (2026-08-27)

**Session scope: UI-66 only.** Set by the maintainer: *"i told you i just want to
work on this new feature for now."* No other feature, and [[UI-64]] is not in this
session.

Nothing is asked here that I could answer myself:
- **Extension opt-ins** — not asked. Already decided **No** for both Security
  Baseline and Property-Based Testing in an earlier session; carried forward.
- **Release scoping** — not asked. One feature, so it ships as its own version.
- **Which features** — answered by the request itself.

## The request

*"you know how we have pair another flow, there after 1st pair we get message
asking if we want to pair another, there lets just do flow like we start second
pair with clear message and then user get to cancel if they want"*

Opt-out instead of opt-in. Full code trace in `ui-followups.md` → **UI-66**.

## How to answer

**Every question already has my recommendation filled in.** Read down, change any
letter you disagree with, and say "go". If you just say "go", I build exactly what
is written below.

Two of these are worth actually reading: **1.1** is a correctness problem, and
**1.2** decides whether the flow feels broken on its most common path.

---

## Question 1.1 — the same-tag hazard (correctness, not polish)

`TwoTagUseCase` arms with `expectedUid = null`, so nothing stops the tag you just
wrote from being counted as the second tag. Today the deliberate "Pair another"
tap is the gap in which you lift the phone off. Auto-arming removes that gap while
the phone is still sitting on the tag. `MoveOnBind` returns `Proceed` for a UID
already on the spool, so it re-appends and reports a second pair that never
happened.

A) **Reject a re-tap of the same tag**, say "Same tag. Tap a different one.", keep
   listening. Threads the first UID into `TwoTagInput`. Small, and it keeps the
   tag count honest.
B) Reject it and stop, treating a re-tap as "done". A genuine fumble then looks
   identical to finishing.
C) Ignore it. Least work; the spool's tag count can be wrong.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: A

---

## Question 1.2 — what happens when you walk away (the common path)

Today's timeout copy is *"No second tag tapped. Tap Write to retry."* — written
for someone who tried and missed. Under auto-start it fires every time someone
only wanted one tag, which will be most of the time.

A) **Benign summary**, same as Done gives today: "Paired and written. Spool #12
   has 1 tag." Truthful, nothing framed as failure.
B) Silence. Cleanest, but someone who *was* reaching for a second tag gets no
   signal that the window closed.
C) Keep today's wording, and accept that it says "No second tag tapped" on the
   most common path.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: A

---

## Question 1.3 — how long it waits

Currently 15 s (`WRITE_TIMEOUT_MS_DEFAULT`), shared with every other write.

A) **Keep 15 s**, shared. It is the number every other write already uses; a
   second constant is a thing to maintain and get wrong.
B) About 8 s. You learn sooner that you are done.
C) About 30 s. More room to fetch a second tag; the Cancel row lingers.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: A

---

## Question 1.4 — after a second tag succeeds, offer a third?

Today it stops at two: a successful second pair goes straight to Idle.

A) **Stop at two**, as today. Matches the two-tags-on-a-spool case this flow was
   built for.
B) Keep going until you cancel or it times out. Consistent with opt-out, but every
   extra tag costs a timeout wait.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: A

---

## Question 1.5 — the wording on screen

The caption sits on the centered NFC overlay, where "Tap second tag to pair" is
today. With the sheet gone it has two jobs: confirm the first tag worked, and say
what is happening now.

A) Overlay reads **"Tap another tag for this spool"**; the first tag's success
   comes as its own snackbar, **"Paired and written"**. Removing the sheet removes
   the exact reason [[UI-03]] buried that text inside it, so the snackbar is
   readable again.
B) Overlay carries both: "Saved. Tap another tag, or Cancel". One surface, long
   caption on a small overlay.
C) Keep "Tap second tag to pair" plus the success snackbar. Least churn.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: A

---

## Question 1.6 — the vendor case

A vendor first tag gets UID-linked, not written (`isVendorPair`), and the sheet
says "Tag linked" rather than "Saved" today.

A) **Auto-start for vendor pairs too**, wording adjusted to "Tag linked. Tap
   another tag for this spool". One flow, one behaviour.
B) Vendor pairs go straight to Idle with just the summary. Rationale: a Bambu
   spool usually has the one tag it came with.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: A
