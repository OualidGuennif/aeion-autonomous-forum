# FB-003: ship a serverless, single-file gallery for diagrams

- **Severity:** medium (a run's whole round-07 output is only usable from `file://` with a manual workaround; every harness will hit this)
- **Found in:** round 07, after the owner tried to open the gallery
- **Status:** applied (tools/architecture/gallery)
- **Requested by:** the owner, 2026-10-29 — "we want a serverless HTML thing like this, with all the details regarding the page and css"

## The problem
The plan's round 07 expects `render/index.html` to open offline, with a live thumbnail per diagram.
The straightforward build — one HTML per diagram plus a gallery page that frames them — fails in
the common case:

1. A browser under macOS cannot read `~/Desktop` without the "Files and Folders" permission, so
   even a self-contained `file://…/diagrams/system-overview.html` shows
   "Access to the file was denied".
2. `file://` pages are treated as opaque origins: a gallery opened from disk may render its iframes
   blank even when the files are readable.
3. A local server works, but is a second process, binds IPv4 only by default (`localhost` resolves
   to `::1` in Firefox, so `python3 -m http.server 8137` fails there until `--bind ::` is added),
   and contradicts the "opens offline" acceptance check.

None of this is about the diagrams: Archify's HTML is fully self-contained (inline `<svg>`, inline
scripts, no `fetch`, no external stylesheet). The gallery's own design is what forces the server.

## What worked, with the details
`demo/pi/render/build_gallery.py` builds one 11.7 MB page that needs nothing but itself:

- **Embed, do not link.** Each diagram HTML is read as text and written into
  `<script type="application/json" data-diagram="<name>">`, JSON-escaped. `</` must be escaped to
  `<\/` (valid JSON, and it keeps the literal `</script>` out of the raw-text element):
  `json.dumps(html).replace("</", "<\\/")`. On load, `JSON.parse(node.textContent)` returns the
  document; base64 would work too but costs 33% and an extra decode.
- **Draw through `srcdoc`.** `<iframe data-diagram="…">` starts with no `src`; assigning
  `frame.srcdoc = data[name]` renders the full document, scripts included, with no file or network
  access. `frame.srcdoc.length` is a cheap integrity check (≈810 KB per diagram).
- **Load lazily.** An `IntersectionObserver` with `rootMargin: '600px'` sets `srcdoc` when a card
  approaches the viewport and unobserves it; fourteen 800 KB documents in the DOM at once is heavy.
- **Clear the placeholder on load, not on assignment.** The "Loading preview…" overlay stays on top
  of the frame until the frame's own `load` event: keep a per-card `data-ready` attribute and one
  CSS rule, `.preview[data-ready="true"] .empty { display: none }`. Hiding it when `srcdoc` is set
  leaves the overlay visible over a still-blank frame, which is exactly the "stuck" bug the owner
  saw.
- **A viewer, not a page load.** One `<div id="viewer">` overlay holds a second iframe; "Open full
  size" assigns the same `srcdoc`, so the full diagram is interactive (Archify's toolbar, hover,
  theme) with no further file reads. `‹ Prev` / `Next ›` page through the same `order` array, and
  `keydown` handles `Escape`, `ArrowLeft`, `ArrowRight`; `body` scroll is locked while open and
  restored on close.
- **Page and CSS that earned the owner's praise** (kept verbatim in the builder): a warm paper
  `#f7f4ec`, ink `#1d2f2a`, muted `#5a6a63`, hairline `#d9ded4`, accent `#8a3f1d`;
  `Georgia` for headings (`clamp(38px,6vw,68px)/1.06`, `-.035em`), `system-ui` 16/1.6 for body;
  uppercase 12px letter-spaced `.kind` labels; two-column grid of bordered cards with 14px radius;
  a 245 px preview window where the iframe is `1200×500` scaled `0.49` from `top left` with
  `pointer-events: none`; a dark `#0d1512` viewer bar with a serif title; one `@media
  (max-width:750px)` rule that drops to one column and scales previews to `0.31`.
- **Verification without a browser on screen.** Chrome headless over the DevTools protocol from
  Node (`--headless=new --remote-debugging-port=… --window-size=1500,26000` with the big window so
  every lazily-observed card intersects): assert 14 frames, each with `srcdoc.length > 100000`, a
  real `<svg>` in `contentDocument`, `data-ready` set and the placeholder computed `display: none`;
  then click a card, click `Next`, dispatch `Escape` and assert the viewer's state. Result in this
  run: `14/14 previews, placeholders cleared, viewer pages and closes on file://`.

## Proposed fix in the base factory
1. Add a small, documented builder as a shared tool — e.g. `tools/render/gallery` — that takes a
   folder of Archify HTML and a metadata list (name, kind, title, question, group) and writes the
   single-file page; keep the JSON escaping, lazy `srcdoc`, placeholder rule, viewer and the CSS
   above as the template. It is ~120 lines of Python, no dependency.
2. Say it in `demo/plan/07-architecture.md` and in the `graph` skill: **rendered HTML is generated,
   not tracked** (`.gitignore` it) and **the gallery is one file**, so `render/index.html` opens
   from any `file://` path, no server, no permission on sibling files.
3. Add the acceptance check the plan is missing: open the gallery from `file://` in a browser (or
   the headless check above) and confirm every preview rendered — `archify browser-check` proves
   each diagram, not the page that embeds them.
4. Optional: note the two server facts in the same place, since both bit this run —
   browsers deny `~/Desktop` without permission (a server or another folder avoids it), and
   `python3 -m http.server` binds IPv4 only until `--bind ::`.

The full working implementation is in this run at `demo/pi/render/build_gallery.py`; `index.html`
is its output and is intentionally untracked.
