# Catalog job

Nightly job that turns the iptv-org API into `catalog.json.gz`, `history.json` and `latest.json` for the TV app. Spec: `docs/superpowers/specs/2026-09-22-tv-app-design.md` section 4.

## One-time setup on GitHub

1. Push this repository to GitHub as a **public** repo (scheduled workflows and free Pages need it).
2. Settings → Pages → Source: **GitHub Actions**.
3. Settings → Secrets and variables → Actions → Variables → New: `PAGES_BASE` = `https://<owner>.github.io/<repo>`.
4. Actions → catalog → Run workflow (leave `force` off). First run takes 30 to 60 minutes. The deploy job's last step checks that `https://<owner>.github.io/<repo>/latest.json` returns the version it just built; if that step fails, `PAGES_BASE` is wrong.
5. Nothing else. The workflow creates the `catalog-failure` label itself the first time it needs it.

## Platform rules

- GitHub disables scheduled workflows after 60 days with no repository activity. The `keepalive` job re-enables it with one API call each run (no third-party action).
- Output is deployed as a Pages artifact, never committed. The repo does not grow.
- Pages soft bandwidth limit is 100 GB/month. This project uses a tiny fraction.

## Local run

    cd catalog
    npm install
    npm test
    PAGES_BASE=https://<owner>.github.io/<repo> npm run run     # writes ./out

Env: `PAGES_BASE` (required), `OUT_DIR` (out), `API_BASE`, `CONCURRENCY` (50), `PER_HOST` (2), `TIMEOUT_MS` (10000), `ALLOW_EMPTY_HISTORY` (`1` for local runs when the Pages site is unreachable; never set in CI), `FORCE_PUBLISH` (`1` to publish through the up-rate guard; the workflow sets it from its `force` input).

## Guards

The run refuses to publish, and opens an issue (or comments on the open one), when the up-rate falls more than 25 points below the previous run or the API is unreachable. The previous catalog stays live.

The guard compares against the last *published* rate, so after a real, permanent drop (the runner moved region, a large host started blocking Azure, iptv-org bulk-added dead feeds) every later night would fail too. When you have confirmed the drop is genuine, run the workflow by hand with `force` on: it publishes and the new rate becomes the baseline. Never use it for a drop you cannot explain.

After each deploy the workflow fetches `latest.json` from `PAGES_BASE` and fails if it does not carry the version just built. That catches a wrong `PAGES_BASE`, which would otherwise make every night a "first run" with the guard silently off.
