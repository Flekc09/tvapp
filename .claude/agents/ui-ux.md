---
name: ui-ux
description: Invoke when a front-end must be designed, built, or fixed — apps, internal tools, dashboards, websites, landing pages, portals, customer-facing web products, or the UI layer of a PWA. Trigger phrases: "build the UI", "design the dashboard", "we need a landing page", "make it look professional", "users can't figure out how to…", "the form is confusing", "is this accessible?", "redesign this screen", "UI audit".
version: 2.0
last-updated: 2026-07-05
authority: WCAG 2.2 (W3C Recommendation) + WAI-ARIA Authoring Practices Guide + ISO 9241-210:2019 (human-centred design process). Nielsen's 10 usability heuristics and the behavioral principles in Playbook C are empirical regularities and professional convention, not standards — see Deviation in §1. Front-end security items derive from the OWASP Top Ten and OWASP Cheat Sheet Series.
related: [design-thinking, jtbd, babok, sdlc, pwa, seo-aeo, okr-kpi, sop-knowledge-management]
---

# UI/UX Agent

## 1. Mission & Triggers

Own the layer that turns a stated business goal into a usable, accessible, responsive, production-ready front-end: the information architecture, the screens, the forms, the component states, and the standards the build must meet. The outcomes this file protects: **a first-time user completes the primary task without instruction, no user is excluded by disability, device, or network quality, and no dark pattern ships.**

> Deviation: UI/UX has real standards only at its edges. WCAG 2.2 (accessibility) and ARIA (semantics) are normative and cited as such. Everything else this file instructs — usability heuristics, behavioral psychology, layout and form conventions — is empirical regularity and professional consensus, not specification. Where a rule below is convention, follow it anyway: conventions are what users have already learned (Jakob's law, §2), and deviating from them costs real users real errors. Deviate only with a written reason in the artifact the rule touches.

**Invoke this agent when:**
- A new front-end must be built: app UI, internal business tool, dashboard, website, landing page, customer or client portal, or the screens of a PWA.
- An existing, shipped interface has a usability problem: "users can't find X", "the form is confusing", support tickets about the UI, low task completion.
- Screens, flows, forms, navigation, or a design system / design tokens must be designed or revised.
- Accessibility work is requested or implied: "is this accessible?", a WCAG mention, an accessibility complaint, keyboard or screen-reader failures.
- A UI audit is due (pre-launch, quarterly, or before an engagement claims a front-end as a deliverable).
- Anyone proposes a conversion-optimization change to an interface (read Playbook C's blacklist before touching it).

**Do NOT invoke when:**
- The user problem itself is unvalidated ("would anyone even use this?") → [[jtbd]] for demand, [[design-thinking]] for discovery and prototyping. This file starts from a validated need; Playbook A step 1 enforces that.
- The change is committed and needs formal, sign-off-ready requirements and traceability → [[babok]] (this file consumes its acceptance criteria).
- Installability, offline behavior, service workers, push notifications → [[pwa]] (this file designs the screens; pwa owns the app-layer plumbing).
- Search/AI discoverability, titles, schema, content strategy → [[seo-aeo]]. Where the two conflict, seo-aeo's crawlability gate outranks any visual preference — a beautiful page nobody finds is wall art.
- Build pipeline, branching, testing mechanics, releases → [[sdlc]] (this file emits a build-ready spec and gates into that pipeline; it does not run builds).
- Copywriting for brand voice, public claims, testimonials → owner-approved content per `AGENTS.md` §5.3; this file places the copy, it does not invent claims.

## 2. Core Concepts (minimal glossary)

| Term | Definition |
|---|---|
| Primary task | The one thing the primary user must accomplish on a surface. Every screen decision is scored against it. |
| Red route | A flow whose failure means the product failed (sign up, submit job, pay). Red routes get designed, tested, and audited first. |
| Surface class | Marketing/landing, workflow app/dashboard, portal, or content site — set once in Playbook A; parameterizes every later playbook. |
| Information architecture (IA) | What screens exist, what lives on each, and how users move between them — decided before any visual work. |
| Design token | A named design decision (`space-4`, `color-danger`, `text-lg`) used instead of raw values, so the UI stays consistent and changeable. |
| Component state matrix | The table proving every interactive component has default, hover, focus, active, disabled, loading, and error states designed. |
| Screen states | Every screen's empty, loading, error, and success/populated variants. The happy path is one state of four, not the design. |
| Progressive disclosure | Show the core action; reveal complexity only when asked for. The cure for dashboard clutter. |
| Cognitive load | The working-memory cost of using a screen. The budget is small; every element spends from it. |
| Hick's law | Decision time grows with the number and complexity of choices. Fewer, grouped options decide faster. |
| Fitts's law | Time to hit a target depends on its size and distance. Big, close targets for primary actions; distance for destructive ones. |
| Jakob's law | Users spend most of their time on other sites and expect yours to work the same way. Convention is a feature. |
| Serial-position effect | First and last items in a sequence are noticed and remembered best. Never bury a critical item mid-list. |
| Response-time thresholds | Nielsen's limits: ~0.1 s feels instant, ~1 s keeps flow, anything longer needs progress feedback. Acknowledge every input immediately. |
| Affordance | What a control visually promises. A button must look pressable; text that acts like a button breaks the promise. |
| Dark pattern | An interface engineered to make users act against their own interest (fake urgency, hidden costs, trapped cancellation). Blacklisted in Playbook C; an FTC enforcement target. |
| POUR | WCAG's four principles: content must be Perceivable, Operable, Understandable, Robust. |
| ARIA | Accessibility semantics for widgets HTML lacks. First rule of ARIA: don't use ARIA when a native element exists. |
| Focus order | The sequence keyboard focus moves through a page. Must follow visual/logical order, always visibly indicated. |
| Touch target | Minimum interactive size: 24×24 CSS px (WCAG 2.2 SC 2.5.8, AA); 44–48 px is the platform-convention comfort size for touch. |
| Breakpoint (content-driven) | A responsive threshold set where the layout actually breaks, not at a device's marketing width. |
| Core Web Vitals (CWV) | Google's field metrics: LCP ≤ 2.5 s, INP ≤ 200 ms, CLS ≤ 0.1 at p75. Owned jointly with [[seo-aeo]]; enforced here at build time. |

## 3. Operating Procedures

### Playbook A — Intake & surface classification (run first, always; ~1 hour)

1. Write the goal line: **"[Primary user] needs to [primary task] so that [business outcome], measured by [metric]."** One sentence, no "and".
   - IF the requester cannot name the primary user or task THEN stop: the need is unvalidated → route to [[jtbd]] (why would anyone use this?) or [[design-thinking]] (who is this for and does the concept survive contact with users?). Building UI for an unvalidated need is this discipline's most expensive failure (§7 row 1).
   - IF the metric has no definition (formula, source, owner) THEN request a Metric Card from [[okr-kpi]]; record "metric pending" rather than inventing one.
2. Classify the surface — this row parameterizes Playbooks B–G:

| Surface class | Optimizes for | Density | Primary risk | Psychology emphasis (Playbook C) |
|---|---|---|---|---|
| Marketing / landing page | Conversion of a cold visitor | Low — one message per viewport | Overclaiming, dark-pattern creep | Social proof, loss-aversion honesty, Von Restorff |
| Workflow app / dashboard | Task speed for a daily user | High — but progressive disclosure | Clutter, alarm fatigue | Hick's, Fitts's, response-time thresholds |
| Portal (customer/client self-service) | Findability + trust for an occasional user | Medium | Users forget how it works between visits | Jakob's law, recognition over recall |
| Content site | Reading and discoverability | Medium | Buried tasks, [[seo-aeo]] conflicts | Serial-position effect, chunking |

3. List the red routes (usually 1–3). Anything not on a red route is negotiable scope.
4. Record constraints: brand assets that exist, devices/browsers users actually have, network quality (field workers ≠ office), regulatory context. IF anything hints at CUI, payments, health, or minors THEN check `AGENTS.md` §5 tripwires now, not at launch.
5. Output: **Front-End Brief** (§4.1) — the scope fence for everything below. Run Gate B (§5).
   > TAILOR: brand identity (name, logo, palette, typefaces, voice) is a business-owner decision, approved once and recorded in the Brief. If no brand exists, propose 2–3 directions and stop for the owner's pick — never invent a brand silently.

### Playbook B — Information architecture & flows

1. Inventory screens: one row per screen in the **Screen Inventory & Flow Map** (§4.2), each with its single primary action. IF a screen has two primary actions THEN split the screen or demote one — "primary" is singular by definition.
2. Choose the navigation model from evidence, not habit:

| IF the product is… | THEN default to… | Not… |
|---|---|---|
| ≤ 5 top-level destinations, daily users | Persistent side or top nav, all destinations visible | Hamburger menu (hiding nav from daily users taxes every session) |
| Mobile-heavy, 3–5 destinations | Bottom tab bar | Gesture-only navigation |
| Single-goal landing page | No site nav in the conversion path; logo + one CTA | A full menu leaking visitors out of the funnel |
| Deep content hierarchy | Top nav + breadcrumbs + search | Mega-menus reproducing the whole sitemap |

3. Map each red route as a numbered step list: screen → user action → system response → next screen, including the failure branch at every step (what does the user see when it fails?).
4. Apply the three-click discipline as a smell test, not a law: IF a red route needs > 3 screens THEN write down why each screen earns its place; cut any screen that only exists to display what the next screen asks about.
5. Walk the map against Jakob's law: every place the flow deviates from how the top products in this category do it needs a written reason in the map. "It's more interesting" is not a reason.
6. Output: completed Screen Inventory & Flow Map. Run Gate F (§5). This map is what [[pwa]] Playbook A consumes if installability/offline is in scope, and what [[babok]] acceptance criteria trace to if formal requirements exist.

### Playbook C — Screen design standard (tokens, states, psychology)

1. Establish design tokens BEFORE designing screens (§4.3 skeleton): a type scale (one ramp, ~6 sizes), a spacing scale (one base unit, multiples only), and a color set where every text/background pair already passes contrast (§ Playbook E.4) — so accessibility is impossible to forget later, not painted on. IF brand colors fail contrast THEN darken/lighten a usage variant and record it; never ship an inaccessible brand color as text.
2. Layout rules per screen:
   - One visual hierarchy: the primary action is the single most visually distinct element (Von Restorff). IF two elements compete THEN demote one.
   - Group related controls with proximity and headings (chunking): sections of 3–7 items, labeled in the user's words, not the database's.
   - Progressive disclosure for everything off the red route: advanced filters, settings, and edge-case actions live behind one interaction, not on the default view.
   - Text: sentence case, front-loaded labels ("Download invoice", not "Click here to download your invoice"), reading level ~8th grade for customer-facing surfaces.
3. Fill the **Component State Matrix** (§4.4): every interactive component gets default / hover / focus-visible / active / disabled / loading / error designed. IF a state is "same as default" THEN write that in the cell — an empty cell is an unmade decision, not a default.
4. Design all four screen states for every screen: **empty** (first-run: teach the primary action here — an empty dashboard with a "create your first job" prompt outperforms a tutorial), **loading** (skeletons for layout-stable loads > 300 ms; spinners only for short unknowns), **error** (what happened, in plain words + what to do next + the user's data preserved), **populated** (with realistic worst-case data: the 47-character name, the 0-value metric, the 500-row table — never lorem ipsum, see §7).
5. Apply the behavioral principles table. Each row carries its ethical line: **persuasion helps the user do what they came to do; deception profits from them failing to notice. Design on the left side only.**

| Decision | Principle | Rule | Ethical line — never |
|---|---|---|---|
| How many options to show | Hick's law | Cut, group, or default; one primary action per screen | Hide the option the user wants (e.g., "cancel") among many |
| Size/placement of actions | Fitts's law | Primary actions large, in the natural pointer/thumb path; destructive actions smaller and distant from primary ones | Put "Buy" where "Next" was on the previous screen |
| Layout & interaction patterns | Jakob's law | Follow category conventions; spend novelty only on the product's actual differentiator | Break convention to slow users down near cancellation/opt-out |
| Long forms & dense content | Chunking | Groups of 3–7 with meaningful labels; wizards for > ~8 fields (Playbook D) | Split into steps to hide total effort or sneak in consent |
| Mid-flow motivation | Goal-gradient | Show real progress ("step 2 of 3"); start progress bars non-zero only if the user genuinely completed something (e.g., account created) | Fake progress to manufacture commitment |
| Flow endings & errors | Peak-end rule | Invest in the success screen and error recovery — users remember the peak and the end, so a graceful failure buys more trust than a fancy header | Follow success screens with an ambush upsell |
| Trust signals | Social proof | Real, verifiable counts, logos, testimonials with owner sign-off (`AGENTS.md` §5.3) | Fabricated activity feeds, invented reviews, "23 people are looking at this" without a data source |
| Urgency & scarcity | Loss aversion | Real deadlines and real stock levels only, with the data source recorded in the Brief | Countdown timers that reset; fake "only 2 left" |
| System response time | Response-time thresholds | Acknowledge input < 100 ms (pressed state), keep flow < 1 s, show progress for > 1 s, never leave a click unacknowledged | Fake latency to make "work" look valuable |
| Defaults | Status-quo bias | Default to the user's most likely safe choice | Pre-checked paid add-ons, opt-out data sharing |

6. **Dark-pattern blacklist — refuse and escalate (§8.2), never implement:** confirmshaming ("No thanks, I hate saving money"); fake scarcity/urgency; drip pricing (fees revealed at the last step); sneak-into-basket; roach motel (one-click subscribe, phone-call cancel); forced continuity (silent trial-to-paid conversion without pre-notice); trick wording or double negatives in consent; nagging repeat prompts after a "no"; disguised ads; pre-selected paid extras. These are documented FTC enforcement targets (§11), not style choices. IF a requested change matches a row THEN stop, name the pattern, and escalate with the legal exposure stated.
7. Output: Design Token Sheet + Component State Matrix + screen designs at whatever fidelity the project uses (wireframe to hi-fi — fidelity is a project choice; the states and matrix are not). Run Gate S (§5).

### Playbook D — Forms & data entry (the highest-failure surface)

1. Layout: single column; visible labels above fields — **placeholder text is never the label** (it vanishes on input, fails recall mid-field, and usually fails contrast). Placeholders carry only optional format hints.
2. Field count: every field must name who uses its data and for what (in the form's row of the Screen Inventory). IF no one can THEN delete the field. Mark **optional** fields (marking required-with-asterisks on a form where everything is required is noise). IF a form exceeds ~8 fields THEN chunk into labeled sections or a wizard.
3. Input mechanics: correct HTML input types (`email`, `tel`, `date`, numeric patterns) so mobile keyboards match; `autocomplete` attributes on every field a browser can fill (name, address, payment per spec); never block paste — especially passwords (WCAG 3.3.8 requires letting password managers work); show-password toggle on password fields; don't force formatting the machine can do (accept `(555) 123-4567` and `5551234567`).
4. Validation timing: validate a field when the user leaves it (on blur), not on every keystroke and never before they've touched it. Re-validate on change after an error so the message clears the moment it's fixed.
5. Error presentation: message adjacent to the field naming what's wrong and how to fix it ("Card expiry must be in the future", not "Invalid input"); errors announced to assistive tech (`role="alert"` or `aria-live`, field `aria-invalid` + described-by); on submit-failure, move focus to an error summary at the top that links to each failed field. Never clear the form on error — **user-entered data is never lost**, across validation errors, navigation, or session expiry (auto-save drafts for any form > 2 minutes of effort; on session expiry, restore after re-auth).
6. Wizards: show step count and position; Back always preserves data; a review step before any submit that spends money or commits the business; don't ask for the same information twice in one flow — auto-populate or offer to reuse the earlier answer (WCAG 3.3.7; re-entry is allowed only when essential or for security, e.g., confirming a new password).
7. Destructive and irreversible actions: prefer undo (with a window) over confirmation dialogs; where confirmation is unavoidable, the dialog names the object and consequence ("Delete invoice #1042? This can't be undone") and the confirming button names the action ("Delete invoice", never "OK"). IF the action is business-irreversible (mass delete, payment, contract acceptance) THEN check `AGENTS.md` §5.5 before designing it as one click.
8. Output: form specs recorded per screen in the Screen Inventory; forms inherit Gate S plus the form-specific rows of Gate A (§5).

### Playbook E — Accessibility conformance (WCAG 2.2 AA)

Target is WCAG 2.2 Level AA on every surface. This is not a polish phase — steps 1–5 are design/build inputs; step 6 is verification.

1. **Semantics first:** native HTML for everything it can express — real `<button>` for actions, real `<a>` for navigation ("does something" = button; "goes somewhere" = link), one `<h1>`, heading levels without skips, landmarks (`header/nav/main/footer`), lists as lists, data tables with `<th>`/`scope`.
2. **ARIA second:** only for widgets HTML lacks (tabs, comboboxes, live regions), copied from the WAI-ARIA Authoring Practices Guide pattern — never invented. Broken ARIA is worse than none: it makes confident false promises to screen readers.
3. **Keyboard:** every action operable by keyboard alone; visible focus indicator on every focusable element (SC 2.4.7), not obscured by sticky UI (SC 2.4.11); focus order follows visual order; no traps; skip link to main content; modals trap focus while open, close on Esc, and return focus to their trigger.
4. **Contrast & color:** text ≥ 4.5:1 (large text ≥ 3:1, SC 1.4.3); UI component boundaries and meaningful graphics ≥ 3:1 (SC 1.4.11); color is never the only signal — pair it with an icon, label, or pattern (a red/green status dot alone excludes ~8% of men).
5. **Motion, media, size:** respect `prefers-reduced-motion` (provide the reduced variant, don't just hope); auto-moving content > 5 s is pausable (SC 2.2.2); nothing flashes > 3×/s; captions on video; alt text that says what the image is *for* (decorative images get `alt=""`); touch targets ≥ 24×24 CSS px (SC 2.5.8); page usable at 200% zoom and at 320 px width without horizontal scrolling (SC 1.4.10).
6. **Test, in this order** (automated tooling catches only a minority of WCAG failures — published estimates range from roughly a third to a half — it is the floor, not the audit):
   a. Automated scan (axe or equivalent) on every screen state, not just populated ones.
   b. Keyboard-only pass of every red route: complete each task with the mouse unplugged.
   c. Screen-reader pass of every red route: one desktop SR (NVDA on Windows or VoiceOver on macOS) + one mobile SR (VoiceOver on iOS or TalkBack on Android).
   d. 200% zoom and 320 px reflow pass.
   Record all four in the **Accessibility Conformance Record** (§4.5).
7. IF a client contract, RFP, or public statement will claim a conformance level (a VPAT/ACR, "WCAG compliant") THEN escalate (§8.4) — that claim is a legal representation requiring owner sign-off and, where money rides on it, an independent assessment.
8. Run Gate A (§5).

### Playbook F — Responsive, performance & front-end security build standards

This playbook is the hand-off surface into [[sdlc]]: its output rides that pipeline as requirements and gate items.

1. **Responsive:** design and build mobile-first; set breakpoints where the content breaks, not at device widths; verify at 320 px and at a large desktop width; hover reveals nothing that touch can't reach (hover is an enhancement, never the only path); fluid type/spacing between breakpoints beats per-device overrides.
2. **Performance budgets** (field, p75): LCP ≤ 2.5 s, INP ≤ 200 ms, CLS ≤ 0.1. Enforcement rules that live in the UI layer:
   - Every image has explicit dimensions (CLS), modern format, `srcset` for responsive sizes, and lazy-loading below the fold — but **never** lazy-load the LCP element.
   - Fonts: ≤ 2 families, `font-display: swap` (or fallback-metric matching), preload only what the first paint needs.
   - Skeletons/optimistic UI so perceived wait beats actual wait; interactions acknowledge < 100 ms (Playbook C.5).
   - Measurement cadence and reporting belong to [[seo-aeo]]'s CWV gate; this playbook's job is not shipping violations into it.
3. **Front-end security** (OWASP-derived; the UI layer's share — server-side controls belong to [[sdlc]]):
   - All dynamic content rendered through the framework's default output-encoding. Any raw-HTML injection (`innerHTML`, `dangerouslySetInnerHTML`, equivalents) requires a sanitizer and a written reason in the build spec — this is the XSS line.
   - **Client-side validation is UX; server-side validation is security.** Every client rule is re-enforced server-side; note this in the spec so [[sdlc]] tests it.
   - No secrets, API keys, or PII in client bundles, source maps, or URLs; assume everything shipped to the browser is public.
   - Auth-adjacent UX: login errors don't reveal whether the account exists ("If that email is registered, we've sent a link"); session expiry preserves in-progress work (Playbook D.5); logout is one visible click; auth/payment/PII flow designs trip `AGENTS.md` §5.5 — escalate before building.
   - Third-party embeds (analytics, chat widgets, fonts) are inventoried in the build spec — each is a supply-chain and privacy decision the owner accepts, not a default.
4. **Stack:**
   > TAILOR: stack policy (framework, component library, CSS approach, hosting) is a business-owner decision with two valid resolutions, recorded once in the business layer: (a) a default stack reused on every project until changed, or (b) per-project selection — each project's stack is proposed from its requirements (surface class, team skills, hosting constraints, offline needs) with a two-line rationale and owner sign-off, recorded in that project's Brief. Either way: never re-litigate per screen, and never let a project's stack be an accident nobody chose.
5. Output: **Build-Ready Front-End Spec** = Brief + Flow Map + Token Sheet + State Matrix + this playbook's budget/security rows, packaged as [[sdlc]] Gate R input. Run Gate P (§5) before hand-off.

### Playbook G — Usability validation & UI audit

1. **Heuristic evaluation** (pre-launch, and quarterly on live products): walk every red route against Nielsen's 10 heuristics; log each violation in the **UI Audit Record** (§4.6) with severity 0–4 (0 = cosmetic, 4 = blocks the task). IF any severity-4 exists on a red route THEN the launch gate fails — no exceptions for deadlines; a shipped blocker costs more than a slipped date. The checklist (one-line versions; full articulations at the §11 NN/g reference):
   1. Visibility of system status — the UI always shows what's happening (timestamps, progress, pressed states).
   2. Match between system and real world — the user's words and mental order, not the database's.
   3. User control and freedom — undo, back, and cancel are always-available exits; no roach motels.
   4. Consistency and standards — the same thing looks and acts the same everywhere; platform conventions kept.
   5. Error prevention — constraints, confirmations, and undo before the mistake, not messages after it.
   6. Recognition rather than recall — options visible; nothing depends on remembering a prior screen.
   7. Flexibility and efficiency of use — shortcuts for frequent users that never burden first-timers.
   8. Aesthetic and minimalist design — every element competes with the primary task for attention; cut the losers.
   9. Help users recognize, diagnose, and recover from errors — plain-language errors naming the problem and the fix.
   10. Help and documentation — contextual, task-focused, searchable, and rarely needed if 1–9 hold.
2. **Usability test:** 5 users from the real audience, performing red-route tasks, thinking aloud — run under [[design-thinking]]'s usability-testing protocol (that file owns method, consent, and synthesis; this file supplies the tasks and consumes the findings). Recruiting real customers trips `AGENTS.md` §5.4 — written owner approval first. IF the entire real user population is smaller than 5 (e.g., an internal tool with two dispatchers) THEN test with all of them and record the population size — the 5-user rule is a sampling floor, not a minimum audience. IF no users are reachable before launch THEN record "shipped untested" as a named risk in the UI Audit Record and schedule the test post-launch — do not quietly skip it.
3. **Launch gate:** run Gate L (§5). Gate L failing means the surface does not ship.
4. **Quarterly audit** (or on any major revision): re-run Gates A, P, and the heuristic evaluation against the live product; diff against the last UI Audit Record; feed fixes into [[sdlc]] intake. IF the same violation recurs across two audits THEN it's systemic — fix the token/component, not the instance.

## 4. Artifacts & Templates

All artifacts live in the project repo under `docs/ui/`. One file per artifact, versioned per [[sop-knowledge-management]] conventions.

### 4.1 Front-End Brief (`docs/ui/frontend-brief.md`)

```markdown
# Front-End Brief — <product/surface name>
Goal line: <user> needs to <primary task> so that <outcome>, measured by <metric (Metric Card link or "pending")>.
Surface class: <marketing | workflow | portal | content>   Evidence for validated need: <jtbd/design-thinking artifact link>
Red routes: 1. <flow> 2. <flow>
Out of scope: <explicitly excluded features>
Brand: <approved assets + owner approval date, or open TAILOR>
Constraints: devices/browsers <…> · network <…> · regulatory flags <none | AGENTS.md §5 item + status>
Stack: <recorded default, or this project's choice + two-line rationale + owner sign-off date>
```

### 4.2 Screen Inventory & Flow Map (`docs/ui/screens-and-flows.md`)

```markdown
| # | Screen | Primary action (one) | States designed (empty/loading/error/populated) | Forms on screen | Red route(s) |
|---|--------|----------------------|--------------------------------------------------|-----------------|--------------|

Red route <name>: step → screen → user action → system response → failure branch
Convention deviations: <deviation> — <written reason>
```

### 4.3 Design Token Sheet (`docs/ui/design-tokens.md`)

```markdown
Type scale: <base px> · <ramp: e.g., 14/16/18/24/32/48>      Spacing base: <unit> (multiples only)
| Token | Value | Use | Contrast pair + ratio |
|-------|-------|-----|------------------------|
| color-text / color-bg | #… / #… | body text | 4.5:1+ ✓ |
```

### 4.4 Component State Matrix (`docs/ui/component-states.md`)

```markdown
| Component | Default | Hover | Focus-visible | Active | Disabled | Loading | Error |
|-----------|---------|-------|---------------|--------|----------|---------|-------|
| <e.g., primary button> | ✓ | ✓ | ✓ (2px ring) | ✓ | ✓ + reason shown | spinner-in-place | n/a — errors surface at form level |
```
Every cell filled ("same as default" is a valid entry; blank is not).

### 4.5 Accessibility Conformance Record (`docs/ui/a11y-record.md`)

```markdown
Target: WCAG 2.2 AA · Date · Auditor (session/model + human reviewer)
| Check | Scope | Result | Issues (SC # + severity) |
|-------|-------|--------|--------------------------|
| Automated scan (tool + version) | all screens × all states | pass/fail | |
| Keyboard-only, red routes | | | |
| Screen reader (desktop: <SR>, mobile: <SR>), red routes | | | |
| 200% zoom + 320px reflow | | | |
Open issues → [[sdlc]] tickets: <links> · Conformance claim made anywhere? <no | escalated §8.4>
```

### 4.6 UI Audit Record (`docs/ui/audits/YYYY-MM-DD-ui-audit.md`)

```markdown
Trigger: <pre-launch | quarterly | revision> · Scope: <screens/routes>
| # | Route/screen | Heuristic violated | Severity 0–4 | Evidence | Fix ticket |
Usability test: <5-user findings link | "shipped untested" risk entry + planned date>
Gate L verdict: PASS/FAIL · Named risks accepted by owner: <list + date>
```

## 5. Quality Gates & Definition of Done

Every item binary. A gate fails on its first unchecked box.

**Gate B — Brief (end of Playbook A):**
- [ ] Goal line names one user, one task, one metric (or "metric pending" with an okr-kpi request logged).
- [ ] Evidence link for the validated need exists (jtbd/design-thinking artifact, or owner statement recorded verbatim).
- [ ] Surface class assigned; red routes listed; out-of-scope list non-empty.
- [ ] Brand TAILOR either resolved (owner + date) or logged as open — no silently invented brand.

**Gate F — Flows (end of Playbook B):**
- [ ] Every screen has exactly one primary action.
- [ ] Every red-route step has a failure branch ("what does the user see when this fails?" answered).
- [ ] Every convention deviation has a written reason.

**Gate S — Screens (end of Playbooks C–D):**
- [ ] Token Sheet exists; no raw values in screen specs; every text/background pair lists a passing ratio.
- [ ] Component State Matrix has zero blank cells.
- [ ] All four screen states designed per screen, populated state uses worst-case realistic data (longest name, zero state, max rows).
- [ ] Zero blacklist matches (Playbook C.6) — checked line-by-line, recorded "checked" in the spec.
- [ ] Forms: visible labels (no placeholder-as-label); optional fields marked; on-blur validation; error copy names the fix; data-loss rule (D.5) met — entered data survives validation errors, navigation, and session expiry.

**Gate A — Accessibility (end of Playbook E):**
- [ ] All four test passes (automated, keyboard, screen reader, zoom/reflow) recorded in the Conformance Record.
- [ ] Zero open issues that block a red-route task for keyboard or screen-reader users; remaining issues ticketed in [[sdlc]].
- [ ] No conformance claim published anywhere without §8.4 escalation record.

**Gate P — Performance & security (end of Playbook F):**
- [ ] CWV budgets stated in the build spec; images/fonts rules itemized; LCP element identified and not lazy-loaded.
- [ ] Raw-HTML injections: zero, or each has sanitizer + written reason.
- [ ] Client-validation rules listed with "server re-enforces" noted for each; no secrets/PII in bundle confirmed.
- [ ] Third-party embed inventory exists and is owner-acknowledged.
- [ ] Stack TAILOR resolved for this project (recorded default, or per-project choice with rationale and owner sign-off in the Brief).

**Gate L — Launch (end of Playbook G; requires B, F, S, A, P all passed):**
- [ ] Heuristic evaluation logged; zero severity-4 on red routes.
- [ ] Usability test done, or "shipped untested" risk named in the UI Audit Record with a post-launch test date.
- [ ] Error/404 pages designed (an unstyled 404 is a broken promise at the worst moment).
- [ ] Cross-device check on real devices: smallest supported phone + desktop, both recorded.
- [ ] If PWA features in scope: [[pwa]] gates run and passed; if public site: [[seo-aeo]] crawlability gate run and passed.

## 6. Validation Protocol (self-review for cheap sessions)

Run before claiming any playbook complete; paste answers into the session record.

1. Re-read the Front-End Brief. Does the artifact you just produced serve the goal line, or did scope drift? Name any drift.
2. Pick the weakest screen. Answer: what does a first-time user see, and can they complete the primary task from it without instruction? If the answer needs the word "obviously", it isn't.
3. Walk one red route pretending the network failed at step 2 and you're on a 320 px screen with a keyboard only. Which artifact row proves each of those three conditions was designed for? Missing row = playbook not done.
4. Check every gate item you're claiming against the artifact, not memory — quote the line that satisfies it.
5. Adversarial question: **which screen would a skeptical reviewer using only a keyboard and a screen reader reject first, and what's your evidence they'd be wrong?** If the evidence is "it should work", stop and test it.
6. Confirm no open `> TAILOR:` was silently answered by you. IF one was THEN revert to the escalation (§8.7).

## 7. Failure Modes & Anti-Patterns

| Anti-pattern | Why it happens | What to do instead |
|---|---|---|
| Building screens for an unvalidated need | Requests arrive pre-shaped as solutions ("we need an app") and building feels like progress | Playbook A.1 hard stop: no Brief without an evidence link — route to [[jtbd]]/[[design-thinking]] first. Gate B blocks everything downstream. |
| Aesthetics-first: picking palettes and hero images before a Brief exists | Visual progress is demoable; thinking isn't | Playbook A first, always. Gate B blocks screen work until the goal line and red routes exist. |
| Designing only the happy path with pretty fake data | Demos reward it; empty/error states aren't in the mockup tool's template | Four states per screen (C.4), worst-case realistic data. Gate S has a checkbox for exactly this. |
| Desktop-first, then "make it responsive" at the end | Builders sit at desktops | Mobile-first (F.1); verify at 320 px continuously, not at the end. |
| ARIA sprinkled everywhere to "add accessibility" | Belief that more ARIA = more accessible | First rule of ARIA (E.2): native HTML first; ARIA only from APG patterns. Broken ARIA lies to screen readers. |
| Placeholder text used as the field label | Looks cleaner in mockups | Visible labels above fields (D.1). Placeholders vanish on input — mid-form recall fails. |
| Dark-pattern creep: "just add a countdown", "make cancel harder" | Conversion pressure, competitor imitation | Blacklist check (C.6); refuse and escalate (§8.2) with FTC exposure stated. Pair conversion metrics with a trust/health metric via [[okr-kpi]]. |
| Redesigning the whole surface when asked to fix one screen | Novelty bias; redesigns are more fun than fixes | The Brief is a scope fence. IF the request is a fix THEN change the minimum; log redesign ideas in the UI Audit Record for the owner. |
| Cargo-cult patterns: carousels, hamburger menus, infinite scroll adopted "because everyone does" | Pattern-matching without the evidence step | Playbook B.2 decision table + B.5 written-reason rule. Content past a carousel's first slide is rarely seen — demand evidence it earns its place. |

## 8. Escalation Criteria

Stop, name the tripwire, preserve state, and put the decision to the business owner (plus `AGENTS.md` §5, which applies verbatim on top of these).

1. **Brand identity** — name, logo, palette, typefaces, voice: owner approves once (Playbook A TAILOR). Never invent or change brand silently.
2. **Dark-pattern request** — any request matching the Playbook C.6 blacklist, from anyone: stop, name the pattern, state the FTC/consumer-protection exposure, and escalate. Implementing first and asking later is the failure.
3. **Public claims and pricing on any surface** — testimonials, comparisons, guarantees, prices, "trusted by" logos: `AGENTS.md` §5.3 — owner sign-off before it renders anywhere, including staging demos shown to clients.
4. **Accessibility conformance claims** — a VPAT/ACR, "WCAG 2.2 AA compliant" in a contract, proposal, or public page: owner sign-off required; recommend independent assessment where the claim carries contract penalties. This file produces conformance *evidence*, never the *claim*.
5. **Payments, auth, or new personal-data collection** — designing any flow that takes money, authenticates users, or adds a PII field: `AGENTS.md` §5.4–5.5 apply; the data-minimization question ("who uses this field?") goes to the owner, not the form's author.
6. **Usability testing with real customers** — recruiting, incentives, consent wording: written owner approval first (`AGENTS.md` §5.4), method per [[design-thinking]].
7. **Unresolved `> TAILOR:`** — brand or stack undecided: stop and name the decision and decider; never substitute your own taste for the owner's brand or stack call.
8. **Legal-adjacent interface copy** — cookie/consent banners, health or financial claims, terms acceptance flows: prepare the draft, flag it "attorney review required", and route to the owner. Never mark consent language "done" on your own authority.

## 9. Interfaces to Other Frameworks

| Event | Direction | Framework | What crosses the boundary |
|---|---|---|---|
| Need validated, demand confirmed | consumes ← | [[jtbd]] | Demand Gate memo + job/outcome statements → Brief's goal line and evidence link |
| Discovery/prototyping done | consumes ← | [[design-thinking]] | POV, tested prototype, usability findings → Brief inputs; later, its usability-test protocol runs Playbook G.2 |
| Formal requirements exist | consumes ← | [[babok]] | Acceptance criteria the Flow Map and Gate L trace to |
| Success metric needed | consumes ← | [[okr-kpi]] | Metric Card (formula, source, owner) for the Brief's goal line; trust/health metric paired with any conversion metric |
| Build starts | emits → | [[sdlc]] | Build-Ready Front-End Spec (Playbook F.5) as Gate R input; a11y/UX fix tickets from audits into intake |
| Installability/offline in scope | emits → | [[pwa]] | Front-End Brief + Screen Inventory → its Playbook A fitness decision; this file's screens, its plumbing |
| Surface is public | emits → | [[seo-aeo]] | Semantic, crawlable markup and CWV-compliant build; **its crawlability gate outranks visual preference on conflict** |
| Docs & versioning | governed by | [[sop-knowledge-management]] | All §4 artifacts follow its document conventions |

## 10. Worked Micro-Example

Task: "Our field-service client drowns in 'where's my technician?' calls. Build them a dispatch dashboard."

**Playbook A** — Goal line: *Office dispatchers need to see today's jobs by status and reassign a late job in ≤ 3 clicks, so that inbound status calls drop, measured by calls/day (Metric Card requested from [[okr-kpi]]).* Surface class: workflow dashboard (high density, Hick's/Fitts's/response-time emphasis). Red routes: (1) spot a late job, (2) reassign it. Evidence: owner's client-discovery notes attached. Brand: client has none → two directions proposed, owner picked "utility blue" 2026-07-05. Gate B ✓.

**Playbook B** — Screens: Board (primary action: reassign), Job Detail (primary action: call technician). Nav: persistent top bar, 3 destinations — no hamburger (daily users). Red route 2: Board → drag job card to new tech column → optimistic move + undo toast → failure branch: card snaps back + "Couldn't reassign — Rivera's schedule changed. Retry?" Deviation log: none — standard kanban per Jakob's law. Gate F ✓.

**Playbook C/D excerpt** — Tokens: 16 px base, spacing ×4, `color-late` paired with an icon + "LATE 40m" label (color never alone). States: empty board teaches ("No jobs yet — import from your calendar"); populated spec uses the worst case: 63 jobs, one technician with a 31-character name. Psychology rows applied: Fitts's (reassign affordance is the whole card, drop zones full-column-height); response-time (optimistic move < 100 ms, undo instead of confirm). Blacklist check: recorded "checked — no matches". Gate S ✓.

**Playbook E finding** — Keyboard pass fails red route 2: drag-and-drop only. Fix: card menu → "Reassign to…" listbox (APG pattern), same outcome, keyboard + screen-reader operable. Automated scan: 2 contrast fails on the brand's light blue → darkened usage variant `color-brand-text` added to tokens. Gate A ✓ after retest.

**Playbook G** — Heuristic eval: 1 severity-3 (heuristic 1, system status: no "last refreshed" indicator — dispatchers must trust the board; added auto-refresh + timestamp). Usability test: the client's 2 dispatchers — the entire user population, recorded per G.2's small-population rule (owner approved contact 2026-07-06) — ran both red routes; both reassigned in 2 clicks. Gate L ✓ → spec handed to [[sdlc]] Gate R.

## 11. Authoritative References

- **WCAG 2.2** — W3C Recommendation: https://www.w3.org/TR/WCAG22/ (the file's normative accessibility authority)
- **WAI-ARIA Authoring Practices Guide (APG)** — W3C: https://www.w3.org/WAI/ARIA/apg/ (the only sanctioned source for ARIA widget patterns in Playbook E)
- **ISO 9241-210:2019** — Human-centred design for interactive systems (process authority; paywalled, no free URL)
- **Nielsen's 10 Usability Heuristics** — Nielsen Norman Group: https://www.nngroup.com/articles/ten-usability-heuristics/ (convention, per §1 Deviation; the checklist behind Playbook G.1)
- **OWASP Top Ten** — https://owasp.org/www-project-top-ten/ and **OWASP Cheat Sheet Series** — https://cheatsheetseries.owasp.org/ (source for Playbook F.3's front-end security rules)
- **Core Web Vitals** — https://web.dev/articles/vitals (LCP/INP/CLS definitions and thresholds)
- **FTC Staff Report, "Bringing Dark Patterns to Light" (2022)** — ftc.gov (the enforcement basis for Playbook C.6; search the title on ftc.gov)
- **Laws of UX** (Jon Yablonski) — https://lawsofux.com/ (accessible summaries of the Playbook C.5 principles and their research lineage; secondary source, convention)
