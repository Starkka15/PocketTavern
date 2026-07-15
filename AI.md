# How AI Is Used in This Project

I'm open about this: this project is developed with heavy AI assistance, primarily
[Claude Code](https://claude.com/claude-code). I'd rather say so plainly than pretend
otherwise.

## What that actually means

- **A human directs the work.** I decide what gets built, make the architecture and
  product calls, and set the priorities. The AI is a tool I drive — not an autopilot I
  turn on and walk away from.
- **Nothing ships until it works on real hardware.** Code isn't "done" because it
  compiles or an AI says it's finished — it's done when the feature actually runs on the
  target device (a handheld, a phone, a retro device, whatever the project targets). I
  test it myself. A green build is not the finish line.
- **I read and own every change.** AI-written code that I don't understand or can't
  verify doesn't get committed. Bugs, design decisions, and mistakes here are mine.
- **Full implementations, not stubs.** Features are built as the complete real thing, not
  demo-ware that looks done in a screenshot.

## AI ethical obligations

I want to be honest about both what I can and can't stand behind here.

- **What I can't confirm:** I don't have visibility into what data the underlying AI model
  was trained on — that's controlled by the model provider, and I won't claim certainty I
  don't have. If you have concerns about how large language models are trained, those are
  fair, and they're not concerns I can resolve for you.
- **What I can control, and do:** everything *I* build *from* is open source. I direct the
  AI to build on open-source projects, respect their licenses, and credit the upstream
  authors. I don't use it to launder proprietary or closed-source code, and I don't use it
  to strip attribution from anyone's work.
- **The line I hold:** the human in the loop is responsible for the ethics of the output.
  I keep that responsibility rather than hiding behind the tool — the sourcing, the
  licensing, and the credit are my job to get right, whatever wrote the code.

## Credit and licensing

- Where this project builds on someone else's work (a fork, a port, a reused library),
  the original authors are credited and their license is respected. AI assistance doesn't
  change that — upstream gets credit regardless of how my own changes were written.
- If this repo is a **fork**, the upstream project and its authors deserve the credit for
  the foundation; this notice covers only my modifications on top.

## Why disclose it

Some communities and licenses have strong feelings about AI-generated code. I'd rather be
upfront so you can make an informed decision about using this, than hide it and have you
find out later. If AI-assisted code is a dealbreaker for you, that's a legitimate call and
you now have what you need to make it.

Questions or concerns about any of this? Open an issue.
