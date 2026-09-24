# Whole-branch review — `catalog-job` (3d52ed9..ad8b9c0) — 2026-09-24 — reviewer: Fable 5.1 (fresh subagent)

> Status 2026-09-24: both Important findings fixed in a4fb5f8 and written back into plan Tasks 6 and 13. While pinning the keepalive action for finding 2, GitHub reported the `gautamkrishnar/keepalive-workflow` repository blocked (terms of service, since 2025-04-21), so the keepalive is now one `gh api` enable-workflow call with no third-party action. The seven minors were deferred at merge and fixed later on 2026-09-24 (branch `catalog-minors`), each with a test and written back into plan Tasks 2, 6, 7, 12 and 13; the suite went from 78 to 87 tests. The "Declined to judge" lines were each ruled on by the executor (see the commit message of a4fb5f8 and the summary the owner received); none became a finding.

Verified by the reviewer: `npm run typecheck` clean, `npx vitest run` 76/76 (77 after the fix pass). Read: every file under `catalog/src`, `catalog/test`, `catalog/scripts`, the workflow, the README, spec sections 3.1/4/7/8, the ledger, and the Opus review findings B1/M12–M14.

## Strengths

- Architecture matches plan and spec: every I/O module takes `fetch` as a parameter, the clock is injected in `runPipeline`, no test touches the network. Tests exercise real logic through fake transports.
- All five Review Focus items have their pinned tests and pass: redirect/final-host/login-page, relative media URI resolved against the final URL, same URL under two channels kept and probed once, stale history URLs dropped, first run on 404.
- Applied review fixes do what was asked: B1 `Math.round` with an integer assertion; M12 media-follow 401/403/429/451 → `unverified`; M13 generic-ccTLD and redirector deny-lists with tests; M14 `force` input, post-deploy `latest.json` verification and issue de-duplication, force path tested end to end.
- Safety rules hold: a network error loading history propagates rather than becoming "first run"; a failed guard returns before `writeOutputs`; `ALLOW_EMPTY_HISTORY` confined to local runs; force still refuses when nothing was probed.
- Contract with the app: every spec 4.4 field emitted with the right type; `DEFAULT_UA` byte-identical to the CLAUDE.md invariant; the app plan's parser reads `responseMs` via `nextDouble` then rounds; no `other` category id collision.
- Body-stall handling judges bytes received; the limiter does not deadlock on rejection; a CRLF in a user-supplied header lands as `down` rather than a crash (confirmed by experiment).

## Issues

### Critical — none.

### Important

1. **One malformed variant URI in any master playlist aborts the entire nightly run.** `catalog/src/probe.ts` — `new URL(parsed.mediaUri, got.finalUrl)` throws `TypeError: Invalid URL` for URIs such as `http://bad host:abc/x.m3u8`; nothing catches it, it rejects through `limiter.run` into the pipeline's `Promise.all`, and after ~20 minutes of probing the job exits non-zero, publishes nothing and opens an issue. Confirmed by running `probeStream` against such a master. **Fixed:** `probeStream` wraps the probe and reports the stream as `down` with the error text; test `never throws: a master whose media uri cannot be resolved is down`.
2. **Every job, including a third-party action on a floating tag, could deploy to Pages.** Workflow-level `pages: write`, `id-token: write`, `issues: write`, `actions: write` reached the `keepalive` job running `gautamkrishnar/keepalive-workflow@v2`, a mutable tag, with enough permission to mint an OIDC token and publish at the URL every TV downloads. **Fixed:** permissions are per job; the third-party action is gone (see status note).

### Minor (fixed later on 2026-09-24)

3. `pipeline.ts` URL de-dup keeps the last record for a URL; when `streams.json` lists the same URL twice with different `user_agent`/`referrer`, only one header set is probed.
4. `main.ts` `Number('')` is `0`; a blank `CONCURRENCY` or `PER_HOST` hangs the limiter until the 120-minute timeout. Use `Number(x) || default`.
5. The post-deploy `curl` has no cache-buster (Pages serves `max-age=600`), so a stale edge copy can fail the check and blame `PAGES_BASE`; append `?v=$EXPECTED`. A trailing slash in `PAGES_BASE` yields `//history.json`.
6. `fetch-source.ts` and `history.ts` set no request timeout; a hung API response holds the runner for the full job timeout. `signal: AbortSignal.timeout(60_000)`.
7. `probe.ts` picks the 1 MB playlist budget by Content-Type only; an `.m3u8` served as `application/octet-stream` is read to 64 KB and a late `#EXT-X-ENDLIST` is missed. Use the URL suffix too.
8. A variant playlist answering 200 with HTML is `down` ("media playlist invalid") while the same body at top level is `unverified`.
9. `history.ts` accepts `{"streams": null}` (`typeof null === 'object'`) and `mergeHistory` throws later.

## Declined to judge (each ruled on by the executor; none became a finding)

- M15 (channels hidden because the runner sees them as down): owner decision pending.
- Logo validation by HEAD only: spec 4.4 mandates HEAD.
- Per-host limiter keyed on the original host: plan Review Focus 1 accepts it.
- Full `ipinfo.io` JSON in public logs: spec 4.3 requires egress logging; it is a runner IP.
- Master → media follow-up gets its own 10 s budget: spec ambiguous, not consequential.
- Masters with only `#EXT-X-MEDIA` renditions or nested masters resolve to `down`: outside the format table, rare.
- `history.json` ~5 MB uncompressed: read once a night by the job.
- `pickLogo` is O(channels × logos): no observable effect.
- Working in place on `catalog-job` rather than a worktree: process only.

## Assessment

**Ready to merge?** With fixes (both applied).

**Reasoning:** Faithful to the plan and spec, well tested, applied review fixes are real; the two Important findings (a single malformed playlist cancelling the night, and Pages-deploy credentials reaching an unpinned third-party action) had to close before the first scheduled run, and now have.
