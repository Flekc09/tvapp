---
name: sdlc
description: Invoke for any software engineering task that touches code or its lifecycle — intake of a feature/bug request, writing a design, implementing, reviewing a PR, planning tests, cutting a release, hotfixing production, or retiring a component.
version: 2.0
last-updated: 2026-07-03
authority: ISO/IEC/IEEE 12207:2017 (Systems and software engineering — Software life cycle processes), simplified for a small-business team
related: [agile-scrum-kanban, babok, itil4, nist-csf, cmmc-800-171, sop-knowledge-management, okr-kpi, pmbok-pmi, togaf-ea]
---

# SDLC — Software Development Lifecycle Agent

## 1. Mission & Triggers

This agent owns the engineering backbone: how a request becomes a requirement, a requirement becomes a design, a design becomes reviewed and tested code, code becomes a release, and a release is maintained and eventually retired. The outcome it protects is **traceability and reversibility**: at any moment you can answer "why does this code exist, how do we know it works, and how do we undo it?" Process cadence (sprints, boards, standups) lives in [[agile-scrum-kanban]]; this file defines the work that flows through that cadence.

**Invoke this agent when:**
- A stakeholder asks for a new feature, change, or bugfix and no written requirement exists yet.
- You are about to write or modify code and need to know what branch, tests, and review steps apply.
- You are asked to review a pull request (PR) or respond to review feedback.
- You need a test plan, or must decide unit vs. integration vs. end-to-end (E2E) coverage for a change.
- You are cutting, deploying, or rolling back a release, or handling a production incident fix.
- A component, dependency, endpoint, or feature must be deprecated or removed.
- Someone asks "is this done?" about engineering work — the Definition of Done in §5 is the answer.

**Do NOT invoke when:**
- Eliciting business needs from stakeholders or analyzing a fuzzy problem space with no solution direction yet → [[babok]].
- Planning sprint capacity, backlog ordering, ceremonies, or estimating → [[agile-scrum-kanban]].
- Operating a live service (incident severity classification, user support requests, service catalogs) → [[itil4]] — though the *code change* for an incident fix comes back here (Playbook G).
- Security control selection, risk assessments, or compliance evidence → [[nist-csf]] or [[cmmc-800-171]].
- Writing runbooks or internal how-to documentation not tied to a code change → [[sop-knowledge-management]].
- Project scheduling, budgeting, chartering, or WBS/stakeholder management for the surrounding project → [[pmbok-pmi]] — the authorized work packages come back here as Playbook A inputs.

## 2. Core Concepts (minimal glossary)

| Term | Definition |
|---|---|
| Requirement record | The single written statement of what must be built, with acceptance criteria. One record per independently shippable behavior. |
| Acceptance criterion (AC) | A binary, testable condition ("Given/When/Then" or checklist form) that decides whether a requirement is met. |
| RTM (Requirements Traceability Matrix) | Table mapping each requirement → design section → test ID(s). Lives with the project docs. |
| Design doc | A short written proposal (template in §4) reviewed *before* significant implementation. Required above the triviality threshold in Playbook B. |
| Trunk / main | The default branch. Always releasable: builds green, tests pass. |
| Feature branch | Short-lived branch (target: merged within 3 working days) named `feat/<ticket>-<slug>`, `fix/<ticket>-<slug>`, or `chore/<slug>`. |
| PR (pull request) | The unit of review and merge. One logical change per PR; target ≤ 400 changed lines excluding generated files. |
| CI (continuous integration) | Automated pipeline that runs build + tests + linters on every PR and on main. A red CI blocks merge, no exceptions. |
| Test pyramid | Coverage strategy: many unit tests (fast, isolated), fewer integration tests (real dependencies), few E2E tests (whole system, critical paths only). |
| Regression test | A test added specifically to reproduce a fixed bug so it cannot silently return. Mandatory for every bugfix. |
| Release | A tagged, versioned, deployable artifact plus its notes. Versioned `MAJOR.MINOR.PATCH` (semantic versioning). |
| Rollback plan | The pre-verified way to restore the previous release. Written *before* deploying, not during an outage. |
| Hotfix | An expedited fix branched from the released tag, not from unmerged work on main. |
| Environment | A named place code runs: at minimum `dev` (developer machines), `staging` (production-like, pre-release), `prod` (live). |
| Deprecation | Announced, dated removal of a feature/API with a migration path. Removal without announcement is an incident, not a deprecation. |
| DoD (Definition of Done) | The binary checklist in §5. Work not passing it is "in progress" regardless of what the board says. |

> Deviation: ISO/IEC/IEEE 12207:2017 defines 30 processes across the agreement, organizational project-enabling, technical management, and technical process groups. This file implements only the technical processes a small product team executes (requirements, architecture/design, implementation, integration, verification/validation, transition, maintenance, disposal) and folds the rest into escalation (§8) or sibling frameworks (§9). Where 12207:2017 says "Stakeholder Needs and Requirements Definition process," we say "Playbook A."

## 3. Operating Procedures

### Playbook A — Requirements Intake

**Input:** a request (email, chat message, ticket, verbal). **Output:** a requirement record (template §4.1) and an RTM row.

> TAILOR: Name the ticket system (e.g., GitHub Issues on repo X, Jira project key) — decider: business owner. IF none is recorded THEN the requirement record IS the ticket: use REQ-<nnn> wherever this file says `<ticket>` (branch names, commit footers, PR links).

1. Capture the request verbatim in the ticket. Do not paraphrase away the requester's words — you will need them when acceptance is disputed.
2. Classify: `feature` | `bug` | `chore` (refactor, dependency bump, tooling). IF `bug` and not yet severity-triaged THEN go to Playbook G step 1. IF you arrived here FROM Playbook G step 1 (an S3 sent to backlog) THEN record the severity in the record and continue at step 3 — do not return to G.
3. Write the requirement record using template §4.1. Every AC must be binary — a reviewer must be able to answer pass/fail without judgment calls.
4. IF you cannot write at least one binary AC THEN the request is not yet a requirement — send the open questions back to the requester, or hand to [[babok]] if the problem itself is unclear. Do not start coding.
5. Check for conflicts: search existing requirement records and open tickets for overlapping scope. IF conflict found THEN link both records and escalate per §8 if they demand contradictory behavior.
6. Size it. IF the change plausibly exceeds 3 person-days, touches authentication/authorization, payments, data deletion, or a public API contract THEN a design doc is required (Playbook B). Otherwise mark "design: not required" in the record and proceed to Playbook C.
7. Add an RTM row: requirement ID, empty design column, empty test column. **Checkable condition:** the record has an ID, at least one binary AC, a classification, and an RTM row.

### Playbook B — Design

**Input:** requirement record flagged "design required." **Output:** an approved design doc (template §4.2).

1. Write the design doc. Time-box the first draft to half a day; a design doc is 1–3 pages, not a thesis. IF the half-day expires with sections incomplete THEN send the draft to review anyway with the gaps listed under Open Questions — an incomplete doc under review beats a complete doc nobody has seen.
2. List at least two options considered (one may be "do nothing"), with the rejection reason for each non-chosen option. A design with one option is a decision announcement, not a design.
3. State the rollback story: how is this change turned off or reverted after release? IF the honest answer is "it can't be" (e.g., irreversible data migration) THEN add a migration-rehearsal step to the test plan and flag it in the doc header.
4. Request review from one other engineer — or, if none is available, record a self-review in the doc answering: (1) §6 item 1 — does the doc answer the verbatim request; (2) does every AC in REQ-<nnn> map to a section of the chosen design; (3) is the rollback story an executable command/flag, not an intention; (4) §6 item 4 — what would a skeptical senior reject first. §6 items 2, 3, and 5 are PR-only; skip them for docs.
   > TAILOR: Name the design reviewer of record for this business (a specific person or "any engineer who did not write it") — decider: business owner. If the team is a single engineer plus AI sessions, decide whether AI self-review per §6 is acceptable sign-off for designs touching money or user data — get the business owner to answer this once, in writing.
5. IF review raises an unresolved objection on data loss, security, or cost THEN stop and escalate per §8. Otherwise mark the doc `Approved`, date it, and fill the RTM design column.
6. **Checkable condition:** doc contains options table, rollback story, approval line with date and reviewer name.

### Playbook C — Implementation & Branching

**Input:** approved requirement (and design, if required). **Output:** a PR that passes CI.

> TAILOR: This playbook assumes trunk-based development with short-lived feature branches, which fits teams ≤ ~8 engineers. If the business has adopted GitFlow, release branches, or a monorepo tool, record that decision in `AGENTS.md` and adjust steps 1 and 6 accordingly — do not mix models — decider: business owner.

1. Branch from the current tip of `main`: `git switch -c feat/<ticket>-<slug> main`. Never branch from another feature branch.
2. Write or update tests alongside the code — for each AC in the requirement record, identify which test will prove it before writing the production code for it.
3. Commit in small, logical units. Each commit message: imperative summary ≤ 72 chars, body explaining *why* if non-obvious, ticket ID in the footer.
4. Keep the branch fresh: rebase or merge from `main` at least daily. IF the branch is older than 3 working days THEN either split the work and merge the finished part, or note the reason in the PR description.
5. Run the full local test suite and linter before opening the PR. IF you cannot run tests locally THEN say so explicitly in the PR description — never open a PR you have silently not tested.
6. Open the PR using template §4.3. Link the requirement record. Fill the RTM test column with the test IDs/names added.
7. **Checkable condition:** CI is green, PR description links the requirement, every AC maps to a named test (or an explicit, justified "not testable because…" line).

### Playbook D — Code Review

**Input:** an open PR. **Output:** merge or documented rejection. Review is a gate, not a courtesy.

1. Read the linked requirement first. IF the PR has no linked requirement THEN request one and stop reviewing — you cannot review code against unstated intent.
2. Check scope: one logical change? IF the PR mixes a refactor with a behavior change THEN request a split (exception: the refactor is ≤ ~20 lines and mechanically necessary for the change).
3. Review all five categories in order (a)–(e); complete the full pass and label all findings: (a) correctness against ACs, (b) tests actually assert the ACs (a test that cannot fail is a blocking finding), (c) error handling and edge cases (empty input, concurrent access, external-call failure), (d) security (injection, authorization checks on new endpoints, secrets in code), (e) readability and naming. Exception: IF a finding in (a) or (b) invalidates the whole approach (wrong behavior, or tests that cannot fail) THEN return the PR after finishing that category, listing which categories you did not review.
4. Label every comment `blocking:` or `nit:`. Nits alone never block a merge. IF the completed pass yields more than 10 blocking comments THEN stop, and discuss the approach with the author instead of continuing line-by-line — the design likely failed, not the typing.
5. Author responds to every blocking comment with a code change or a written reason; "done" with no pushed commit is not a response.
6. Merge requires: CI green + one approval from someone (or some session) other than the author + zero unresolved `blocking:` comments.
   > TAILOR: Decide and record whether an AI session may approve a human's PR and vice-versa, and whether any path (e.g., payments code) requires a specifically named human approver — decider: business owner.
7. Merge with the strategy recorded in `AGENTS.md`; IF none is recorded THEN use squash-merge and record that choice in `AGENTS.md` now. Delete the branch. **Checkable condition:** merged commit on `main` references the ticket; branch deleted.

### Playbook E — Testing Strategy

**Input:** a change of any kind. **Output:** the right tests at the right level, recorded in the RTM.

1. Default placement by rule, not taste:
   - Pure logic, parsing, calculation → **unit test**. Target: runs in milliseconds, no network/disk/clock.
   - Code whose main job is talking to a database, queue, filesystem, or another service → **integration test** against a real or containerized instance of that dependency. Mocking the dependency here tests the mock.
   - A revenue- or safety-critical user journey (sign-up, checkout, login, data export) → **one E2E test** for the happy path. Do not E2E-test edge cases; push them down the pyramid.
2. IF a behavior is asserted by both an E2E and a lower-level test THEN keep the lower-level one and justify the E2E by naming the integration risk it uniquely covers, or delete it.
3. Every bugfix adds a regression test that fails on the pre-fix code. IF you cannot make the test fail before the fix THEN you have not reproduced the bug — return to diagnosis.
4. Flaky test protocol: IF a test fails without explanation and the CI history for that test name shows another unexplained failure within the last 20 runs of `main` or any PR THEN quarantine it (skip with a linked ticket) the same day. A suite people rerun until green is a suite nobody trusts. Quarantined tests older than 10 working days escalate per §8.
5. Coverage: do not chase a percentage. The binary gate is the RTM — every requirement row has ≥ 1 test ID, every bugfix has a regression test. Use coverage reports only to find untested *branches* in code you are already changing.
6. **Checkable condition:** RTM test column complete; new tests fail when the feature is reverted (spot-check one per PR).

### Playbook F — Release Management

**Input:** `main` at a commit intended for release. **Output:** a tagged, deployed, verified release.

> TAILOR: Record the concrete deploy mechanism (CI job name, script path, or platform command), the release cadence (on-demand vs. weekly), who may authorize a production deploy, AND where errors are observed during the bake window (dashboard URL or exact log command). This playbook cannot be executed until those four facts are written into `AGENTS.md` or this section — decider: business owner.

1. Verify readiness: CI green on the release commit; no quarantined test covering a feature in this release; all merged PRs since last release have their tickets closed or explicitly carried over. Enumerate them with `git log <last-tag>..HEAD --oneline --merges` (or the PR list filtered by merge date since the last tag).
2. Choose the version: PATCH for fixes only, MINOR for backward-compatible features, MAJOR for any breaking change to a public contract. IF MAJOR THEN a deprecation cycle (Playbook H) must have preceded it — check before tagging.
3. Write release notes from the merged PR titles (template §4.4). Every user-visible change gets a line; internal chores are collapsed to one line.
4. Write/confirm the rollback plan: the previous tag, the exact command to redeploy it, and any data-migration reversal steps. IF the release includes an irreversible migration THEN deploy the migration and the code that requires it in **two separate releases** (expand-then-contract), so the code release stays rollback-safe.
5. Deploy to `staging`. Execute the smoke checklist: application starts, health endpoint returns OK, the release's headline feature works once, one pre-existing critical path works once. IF the release has no user-visible feature (PATCH/chores only) THEN substitute the fixed behavior (exercise one fixed bug path) or a second pre-existing critical path, and note the substitution on the smoke record.
6. Tag (`vX.Y.Z`), deploy to `prod`, re-run the smoke checklist against prod.
7. Watch error rates/logs for a defined bake window (default 30 minutes; overnight for MAJOR). IF new errors attributable to the release appear THEN execute the rollback plan first and diagnose second.
8. **Checkable condition:** tag exists, notes published, smoke checklist recorded with pass marks, rollback plan attached to the release.

### Playbook G — Maintenance & Bugfix

**Input:** a defect report. **Output:** a verified fix released, with a regression test.

1. Triage severity: **S1** prod down or data corruption → hotfix path (step 6); **S2** major function broken, workaround exists → next release; **S3** minor/cosmetic → backlog via Playbook A.
   > TAILOR: Confirm severity definitions and the S1 response-time expectation with the business owner; if a customer SLA exists, its clock overrides these defaults.
2. Reproduce the bug and write the failing regression test *before* changing production code (Playbook E step 3).
3. Diagnose to root cause. "It works after restart" is a symptom, not a cause; IF root cause is unknown after time-boxed investigation (default: 4 hours for S2/S3) THEN escalate per §8 rather than shipping a guess.
4. Fix via the normal path (Playbooks C–D). The regression test rides in the same PR.
5. Update any runbook or doc the bug proved wrong → hand doc changes to [[sop-knowledge-management]].
6. **Hotfix path (S1 only):** branch from the deployed tag (`git switch -c fix/<ticket> vX.Y.Z`), apply the minimal fix, get one expedited review (a second session or engineer reads the diff — never merge fully unreviewed), release as PATCH via Playbook F with a shortened bake window (default: 10 minutes of active log watching, then normal monitoring), then cherry-pick/merge the fix back to `main` the same day. An S1 fix that never lands on `main` will regress at the next release. For S1, the minimal fix may ship before the regression test exists, but the same-day merge-back PR to `main` MUST include the regression test (this satisfies the checkable condition). S1 diagnosis time-box: IF root cause is unknown after 1 hour THEN execute the current release's rollback plan (restore previous tag) instead of fixing forward, and escalate per §8 item 5.
7. **Checkable condition:** regression test merged; for S1, fix present on both the release tag and `main`.

### Playbook H — Deprecation & Decommission

**Input:** a decision to retire a feature, endpoint, dependency, or system. **Output:** clean removal with zero surprised users.

1. Confirm authority to deprecate: anything customer-visible or contractually referenced requires business-owner sign-off (§8 tripwire).
2. Measure current usage (logs, metrics, grep of client code). IF usage cannot be measured THEN add instrumentation and wait one representative period (default: 30 calendar days, or one full billing/usage cycle if longer; record the chosen period and its rationale in the DEP record) before proceeding.
3. Publish a deprecation notice (template §4.5) with: what, why, the removal date, the migration path, and the removal threshold (default: zero requests in the final 14 days before the removal date). Minimum notice: one full release cycle for internal consumers; for external consumers —
   > TAILOR: set the external deprecation window (commonly 90 days) per customer contracts; check with the business owner.
4. Add runtime warnings where feasible (log warning, HTTP `Deprecation`/`Sunset` headers, compiler deprecation attribute).
5. At the removal date: compare measured usage against the removal threshold recorded in the DEP notice (step 3). IF usage exceeds that threshold THEN do not remove — escalate with the list of remaining consumers. Otherwise remove code, tests, docs, config, scheduled jobs, and infrastructure in one MAJOR (or MINOR if never public) release.
6. Archive, don't orphan: note the removing release and the notice link in the requirement record; hand final knowledge-base updates to [[sop-knowledge-management]].
7. **Checkable condition:** grep for the removed identifiers returns only historical references (changelog, notice); no dead config or cron jobs remain.

## 4. Artifacts & Templates

Store artifacts in the product repo under `docs/` unless stated otherwise. Naming: `docs/requirements/REQ-<nnn>-<slug>.md`, `docs/design/DES-<nnn>-<slug>.md`, `docs/rtm.md`, `docs/deprecations/DEP-<nnn>-<slug>.md`. Assign `<nnn>` as the highest existing number in the relevant `docs/` subfolder plus one, zero-padded to three digits (e.g., `ls docs/requirements/ | sort | tail -1`). IF two parallel branches mint the same number THEN the later-merged PR renumbers before merge.

### 4.1 Requirement record

```markdown
# REQ-<nnn>: <one-line behavior>
- Requested by / date: <name>, <YYYY-MM-DD>
- Type: feature | bug | chore     Design doc: required | not required
- Estimate: <n person-days>
- Original request (verbatim): "<paste>"

## Acceptance criteria
1. Given <context>, when <action>, then <observable result>.   [binary]
2. ...

## Out of scope
- <explicitly excluded behavior, to prevent scope disputes>
```

### 4.2 Design doc

```markdown
# DES-<nnn>: <title>            Status: Draft | Approved (<reviewer>, <date>)
Requirement: REQ-<nnn>          Irreversible steps: yes/no

## Problem (3 sentences max)
## Options considered
| Option | Summary | Rejected because |
|---|---|---|
| A (chosen) | ... | — |
| B | ... | ... |
| Do nothing | ... | ... |
## Chosen design (diagram or bullets; data model + API changes explicit)
## Rollback story
## Test plan deltas (anything beyond Playbook E defaults)
## Open questions
```

### 4.3 PR description

```markdown
Requirement: REQ-<nnn> (link)   Design: DES-<nnn> | n/a
## What changed
## AC → test mapping
| AC # | Test name/ID | Level (unit/int/e2e) |
|---|---|---|
## Reviewer notes (risky spots, what I couldn't test locally and why)
## Checklist
- [ ] CI green  - [ ] Regression test (bugfix only)  - [ ] Docs updated or n/a
```

### 4.4 Release notes + smoke record

```markdown
# vX.Y.Z — <YYYY-MM-DD>
## Changes
- <user-visible change> (REQ-<nnn>)
## Internal
- <one collapsed line>
## Rollback
Previous tag: vX.Y.(Z-1). Command: <exact command>. Data reversal: <steps | none>.
## Smoke checklist                staging | prod
- App starts / health OK:           [ ]   |  [ ]
- Headline feature once:            [ ]   |  [ ]
- Critical path (login/checkout):   [ ]   |  [ ]
Bake window result (<duration>): pass | rolled back (<reason>)
```

### 4.5 Deprecation notice

```markdown
# DEP-<nnn>: <what is being removed>
Announced: <date>   Removal: <date>   Owner sign-off: <name/date>
Why: <1–2 sentences>
Migration: <exact replacement + example>
Current consumers: <list or metric snapshot>
Removal threshold: <value, e.g., zero requests in the final 14 days>
Measurement period (if instrumentation was added): <period + rationale>
```

### 4.6 RTM (one file, `docs/rtm.md`)

```markdown
| Req ID | Design | Test IDs | Status |
|---|---|---|---|
| REQ-014 | DES-006 | test_export_csv_unit, itest_export_s3 | Released v1.4.0 |
```

Status values (exactly one of): `Draft` | `Ready` (Gate R passed) | `In progress` | `Merged` | `Released vX.Y.Z` | `Deprecated (DEP-<nnn>)` | `Removed vX.Y.Z`.

## 5. Quality Gates & Definition of Done

All items binary. A single "no" fails the gate.

**Gate R (requirement ready → may implement):** record exists with ID; ≥ 1 binary AC; classification set; size estimate in person-days recorded; RTM row present; design doc approved if the Playbook A step 6 threshold was crossed.

**Gate M (PR may merge):** CI green; one non-author approval; zero unresolved `blocking:` comments; every AC has a named test or a written exemption; PR ≤ 400 changed lines or a written justification; requirement linked.

**Gate D — Definition of Done (ticket may close):** merged to `main`; RTM row shows test IDs; docs/runbooks updated or "n/a" recorded; bugfixes have a regression test that failed pre-fix; no quarantined test was added by this work.

**Gate F (release may ship):** version chosen by the Playbook F step 2 rule; notes written; rollback plan has an exact command; staging smoke checklist fully checked; irreversible migrations split into a separate prior release.

**Handoff gate:** work items leaving this framework for [[agile-scrum-kanban]] planning must have passed Gate R; incidents arriving from [[itil4]] must receive a severity (Playbook G step 1) before any code is written.

## 6. Validation Protocol (self-review for cheap sessions)

Run before claiming any deliverable complete. Record answers in the PR/doc.

1. Re-read the original request verbatim. Does the artifact answer *that*, or a more convenient nearby problem? Name the difference if any.
2. For each AC: point to the exact test (file + name) that proves it. IF you point to the same test twice for different ACs, verify it truly asserts both.
3. Revert-check: actually revert the change (`git stash`, or `git revert --no-commit` on the branch), run the suite, and record in the PR which test fails first. IF none fails THEN testing is decorative — add the missing test before claiming done.
4. Adversarial question: **what would a skeptical senior reject first?** Write the answer down; if the answer is "nothing," you haven't looked — the most common true answers are missing error-path handling and untested concurrent access.
5. Grep for leftovers: `TODO`, `FIXME`, debug prints, commented-out code, hardcoded secrets/URLs you added.
6. Confirm every `> TAILOR:` decision your work depended on was actually resolved and recorded — IF you assumed one silently THEN stop and escalate per §8.

## 7. Failure Modes & Anti-Patterns

| Anti-pattern | Why it happens | What to do instead |
|---|---|---|
| Coding straight from a chat message | Intake feels like bureaucracy under time pressure | Playbook A takes 10 minutes; disputed acceptance later takes days. Write the record first. |
| The 2,000-line "big bang" PR | Author kept "just finishing" before opening review | Split by Playbook C step 4; merge behind a feature flag if the feature isn't user-ready. |
| Rubber-stamp reviews ("LGTM", 90 seconds) | Reviewer trusts author or fears blocking | Playbook D step 3 order is mandatory; an approval with zero comments on a >100-line PR is itself a review smell — say what you checked. |
| Mock-everything integration tests | Mocks make tests fast and green | If the code's job is the dependency, test against the real/containerized dependency (Playbook E step 1). |
| Fix without reproduction | Pressure to "just make it stop" | Regression-test-first (Playbook G step 2); a fix you can't demonstrate is a bet. |
| Hotfix that never returns to main | Adrenaline ends when prod is green | Playbook G step 6 same-day merge-back; add it to the incident's closing checklist. |
| Irreversible migration + code in one release | Seems efficient | Expand-then-contract (Playbook F step 4); two boring releases beat one unrollbackable one. |
| Silent removal ("nobody uses this") | Usage was guessed, not measured | Playbook H steps 2–3: measure, announce, date, then remove. |
| Rerunning flaky tests until green | Deadline pressure; "it's just flaky" | Quarantine-with-ticket on second failure (Playbook E step 4); flakiness tolerated is signal destroyed. |

## 8. Escalation Criteria

Stop work and ask a human (the business owner unless a more specific owner is recorded) when ANY of the following is true. State what you were doing, the tripwire hit, and 1–3 options with your recommendation.

1. Two requirements or two stakeholders demand contradictory behavior and no recorded priority resolves it.
2. The change touches payment processing, pricing shown to customers, authentication/authorization logic, or bulk deletion/modification of user data — and no approved design doc covers exactly this change.
3. An irreversible action is imminent (dropping a column/table, deleting stored data, force-pushing shared history, removing a customer-visible feature) — always confirm before executing, even if a plan mentions it.
4. A design review objection about data loss, security, or cost remains unresolved (Playbook B step 5).
5. Root cause of an S1/S2 defect is unknown after the time-boxed investigation, or the same S1 recurs after a fix shipped.
6. A release needs to ship with a red or quarantined test "just this once."
7. A deprecation removal date arrives with measurable remaining usage (Playbook H step 5).
8. Any work would violate a customer contract, SLA, license term (e.g., copyleft dependency added to proprietary code), or handles regulated data (PII/CUI) in a new way — also loop in [[nist-csf]] / [[cmmc-800-171]].
9. A required `> TAILOR:` decision in this file is unresolved and the work cannot proceed without assuming an answer.
10. Estimated effort has grown to > 2× the size recorded at Gate R.

## 9. Interfaces to Other Frameworks

| Event | Direction | Framework | What crosses the boundary |
|---|---|---|---|
| Fuzzy business need, unclear problem | emit → | [[babok]] | Raw request + open questions |
| Business analysis completed | consume ← | [[babok]] | Analyzed needs / business requirements feeding Playbook A |
| Requirement passes Gate R | emit → | [[agile-scrum-kanban]] | Sized requirement record for backlog ordering and sprint planning |
| Sprint commits work | consume ← | [[agile-scrum-kanban]] | Ordered tickets; this file governs how each is executed |
| Release deployed (Gate F passed) | emit → | [[itil4]] | Release notes, rollback plan, monitoring expectations for operations |
| Production incident raised | consume ← | [[itil4]] | Incident report → severity triage in Playbook G step 1 |
| Security-relevant change or new data flow | emit → | [[nist-csf]] | Design doc section on data/auth changes for control review |
| Security control requirements or findings | consume ← | [[nist-csf]] | Control requirements/remediation findings that become requirement records (Playbook A) |
| Compliance-scoped system touched (CUI) | emit → | [[cmmc-800-171]] | Change description before implementation |
| Docs/runbooks changed by a release or bugfix | emit → | [[sop-knowledge-management]] | Updated/obsolete doc list from Playbook G step 5 and H step 6 |
| Bug triage or maintenance needs context | consume ← | [[sop-knowledge-management]] | Existing runbooks/SOPs consulted during Playbook G triage |
| Process metrics wanted (lead time, escape rate) | emit → | [[okr-kpi]] | Release dates, defect counts by severity from tickets |
| Project plan / WBS work package authorized | consume ← | [[pmbok-pmi]] | Baselined scope + schedule constraints for Playbook A |
| Architecture Contract / conformance checks govern a build | consume ← | [[togaf-ea]] | ADD excerpt + requirement IDs governing the build; conformance checks become review-gate items |
| Validated concept ready to build | consume ← | [[design-thinking]] / [[jtbd]] | Tested prototype decisions / job stories that become requirement records |
| System requires authorization (federal/ATO) | emit → | [[nist-rmf]] | Design docs + data flows for control implementation evidence |

## 10. Worked Micro-Example

Request (chat, verbatim): *"Customers keep asking for a CSV export of their invoices. Can we add a download button?"*

**Playbook A:** REQ-014 created. Type: feature. AC1: Given a logged-in customer with ≥ 1 invoice, when they click "Export CSV" on the invoices page, then a CSV downloads containing exactly their invoices with columns `id,date,amount,status`. AC2: Given a customer with 0 invoices, when they export, then they receive a CSV with only the header row. Out of scope: PDF export, scheduled emails. Size: ~2 days, but it exposes customer financial data via a new endpoint → **design required** (auth-touching per step 6).

**Playbook B:** DES-006, options: (A, chosen) synchronous endpoint streaming CSV, auth via existing session, per-customer scoping in the query; (B) async job + email link — rejected: overkill at current invoice volumes (< 5k rows); (do nothing) rejected: recurring customer request. Rollback: endpoint behind config flag `EXPORT_CSV_ENABLED`, default on; flip to off to disable. Reviewer approves same day after one blocking comment ("scope query by customer_id from session, not from request param") is fixed in the doc.

**Playbook C/E:** branch `feat/REQ-014-invoice-csv`. Tests written against ACs: `test_csv_serializer_columns` (unit), `test_csv_empty_invoices_header_only` (unit), `itest_export_endpoint_scopes_to_session_customer` (integration, real DB, asserts customer A cannot receive customer B's rows). No E2E: export is not a revenue-critical journey; the integration test covers the risky seam. RTM row filled.

**Playbook D:** PR #83, 240 lines. Reviewer finds one `blocking:` — endpoint missing the auth middleware decorator (security, step 3d) — and two nits. Author pushes fix; the integration test is extended to assert 401 for anonymous requests. CI green, approved, squash-merged.

**Playbook F precondition check** — AGENTS.md records: deploy = CI job `deploy-prod`, cadence = on-demand, prod authorizer = business owner, bake-window error observation = the `prod-errors` dashboard (URL in AGENTS.md). (Had any of these been missing, work stops at §8 item 9.)

**Playbook F:** shipped in v1.4.0 (MINOR: backward-compatible feature). Notes list the feature with REQ-014; rollback = redeploy v1.3.2 or set `EXPORT_CSV_ENABLED=false`. Staging smoke: health OK, exported a CSV once, login path once. Prod deploy, smoke repeated, 30-minute bake clean.

**RTM final row:** `REQ-014 | DES-006 | test_csv_serializer_columns, test_csv_empty_invoices_header_only, itest_export_endpoint_scopes_to_session_customer | Released v1.4.0`.

Validation (§6) spot-check: reverting the endpoint makes `itest_export_endpoint_scopes_to_session_customer` fail first — tests are load-bearing. Skeptic's first rejection: "what about invoices in other currencies?" — recorded as out of scope, new REQ opened.

## 11. Authoritative References

- ISO/IEC/IEEE 12207:2017 — *Systems and software engineering — Software life cycle processes* (paywalled at iso.org; vocabulary source for this file).
- ISO/IEC/IEEE 29148:2018 — *Requirements engineering* (basis for §4.1 acceptance-criteria discipline).
- IEEE 1012-2016 — *System, Software, and Hardware Verification and Validation* (governs V&V planning rigor; Playbook E's unit/integration/E2E placement is industry test-pyramid practice, not drawn from this standard).
- Semantic Versioning 2.0.0 — https://semver.org (versioning rule in Playbook F).
- Conventional branch/commit hygiene in Playbook C is team convention, not a formal standard — treat 12207 as authoritative where they conflict.
