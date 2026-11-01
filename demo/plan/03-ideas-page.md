# 03 — The ideas page: SSR list and an island form

## Goal
The home page lists the ideas (server-rendered) and lets a contributor add one. It works without
JavaScript (an HTML form) and better with it (an island that posts to the API and shows errors inline).

## User stories
- As a visitor, I open `/` and see the newest ideas, readable without JavaScript.
- As a contributor, I fill the form and my idea appears at the top.
- As a contributor, a too-short title shows the reason next to the field, and what I typed is kept.

## Spec
**Inputs.** `GET /`; `POST /` with form fields `title`, `description`, `tags` (comma-separated).

**Outputs.**

| Request | Response |
| --- | --- |
| `GET /` | `200` HTML: an `<h1>Idea Board</h1>`, a list of ideas (title, description, tags, votes, date), or "No ideas yet." |
| `POST /` valid | `303` redirect to `/` (post/redirect/get) |
| `POST /` invalid | `400` HTML: the page again, the form keeps the values, the error is shown next to the field |

**Invariants.**
1. The page and the API share one domain module: the form and `POST /api/ideas` refuse the same inputs with the same reason codes.
2. User text is escaped: a title `<script>alert(1)</script>` is shown as text.
3. The island (`app/islands/NewIdeaForm.tsx`) only enhances the HTML form; with JavaScript off the form still works.
4. Every form control has a label; errors are linked with `aria-describedby`.

## Acceptance checks
```bash
cd app && deno test -A                                   # page handler tests
curl -s localhost:8000/ | grep -c '<h1>Idea Board</h1>'  # 1
curl -s -o /dev/null -w '%{http_code}' -X POST localhost:8000/ \
  -d 'title=Offline%20mode&tags=ux'                      # 303
curl -s -X POST localhost:8000/ -d 'title=x' | grep -c 'too_short\|at least 3'   # >= 1
```

## Tests required
- `app/routes/index_test.ts` (or a handler test): empty state, list rendering, redirect on success,
  400 with kept values, escaping of a script title.
- `app/domain/form_test.ts`: comma-separated tags are split, trimmed, lowercased, empties dropped.

## Out of scope
Votes and filters (next rounds), styling beyond a clean readable layout, client-side routing.

## Expected result
A usable page at `/`. Spec `knowledge/components/idea-board-page.md` written before the code.
