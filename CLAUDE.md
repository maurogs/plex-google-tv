# CLAUDE.md

- Never run `git commit`, `git add` or `git push`. At the end of each change, suggest a commit message (short English imperative subject line) and let the user commit.
- All documentation (README and any other docs) must be written in English. Conversation with the user stays in Spanish.
- Never hardcode user-facing text. Every new or changed string must be added to all 10 `app/src/main/res/values*/strings.xml` files (en, es, fr, de, it, pt, ru, zh, ar, hi), and removed strings deleted from all of them. Check that every locale has the same keys before finishing.
- After every meaningful change, append an entry to `.claude/context/history.md` and keep `.claude/context/project.md` up to date (decisions, code map, open items).

## Project context

@.claude/context/project.md
@.claude/context/history.md
