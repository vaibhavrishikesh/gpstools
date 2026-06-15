# Ralph — Autonomous Agent Loop (setup in this project)

Ralph runs an AI coding CLI repeatedly until every user story in `prd.json` is done.
Each iteration = a **fresh agent** that picks the highest-priority unfinished story,
implements it, runs quality checks, commits, marks it `passes: true`, and logs learnings.
Source: https://github.com/snarktank/ralph

## What's installed here
```
scripts/ralph/
  ralph.sh            # the loop runner (executable)
  CLAUDE.md           # prompt template used when --tool claude
  prompt.md           # prompt template used when --tool amp
  prd.json.example    # example PRD format (copy to prd.json)
.claude/skills/
  prd/SKILL.md        # /prd  — generate a PRD from a feature idea
  ralph/SKILL.md      # /ralph — convert that PRD into prd.json
```
Project is a git repo (`git init` done). Ralph works on a branch named in `prd.json` → `branchName`.

## ⚠️ Prerequisite (NOT yet satisfied)
`ralph.sh` spawns the **Claude Code CLI** each iteration (`claude --dangerously-skip-permissions --print`).
On this machine the `claude` CLI is **not on PATH** (you're using the desktop/web app).
Install it before running the loop:
```bash
npm install -g @anthropic-ai/claude-code   # then verify: claude --version
```
(Or install `amp` and use `--tool amp`.)

## Workflow
1. **Write a PRD** — in Claude Code run `/prd` (project skill is installed) → saves to `tasks/prd-*.md`.
2. **Convert to JSON** — run `/ralph` → produces `scripts/ralph/prd.json` with user stories
   (each story: `acceptanceCriteria`, `priority`, `passes:false`). Set a `branchName` like `ralph/gpstools-mvp`.
3. **Run the loop:**
   ```bash
   ./scripts/ralph/ralph.sh --tool claude 10
   ```
   `10` = max iterations. Loop stops early when the agent emits `<promise>COMPLETE</promise>`.

## Key rules (so it behaves)
- **Right-size stories** — each must fit one context window. Too big = it stalls.
- **Quality checks are the feedback loop** — give each story acceptance criteria like
  "typecheck passes", "tests pass", "verify in browser". No checks = no safety net.
- Frontend stories should include a browser-verification criterion.
- Learnings accumulate in `scripts/ralph/progress.txt` (auto-created); reusable patterns
  go in a `## Codebase Patterns` section at the top, and in nearby `CLAUDE.md` files.
- Previous runs auto-archive to `scripts/ralph/archive/` when `branchName` changes.

## Also available globally (optional)
To get `/prd` and `/ralph` in *every* project (not just this one), in Claude Code run:
```
/plugin marketplace add snarktank/ralph
/plugin install ralph-skills@ralph-marketplace
```
Here they're installed project-local under `.claude/skills/`, so they already work in gpstools.
