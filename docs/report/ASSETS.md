# Report assets and production notes

The canonical report is `Clarity_Finance_Report.md`, its editable DOCX and rendered PDF in this directory. There is no alternate draft/final-v2. It contains17 pages: cover, summary/contents and15 substantive pages. The original brief mandates minimum15 pages, not a particular font/margin style.

## Figures

| Figure | Source and print rendering | Purpose |
|---|---|---|
|1| `figures/use-cases.svg` / `.png` | Authenticated-user use cases and application boundary; labels include literal ASCII `UC02-03`. |
|2a-c| `figures/wireframes.svg` / `.png` | Three separately annotated static mockups: Overview, Add Expense and History. |
|W1/W2/W3 individually| `figures/wireframe-1.svg`, `wireframe-2.svg`, `wireframe-3.svg` and PNGs | Editable/readable individual versions of the three panels, not competing report drafts. |
|3| `screenshots/overview.png` | Recorded fictional approved-palette Overview; not a fresh live screenshot. |
|4| `screenshots/history.png` | Recorded final fictional History metadata fixture, independent of human-capture totals. |
|5| `figures/architecture.svg` / `.png` | Actual MVVM/Koin/ViewModelStore and process/session ownership. |
|6| `figures/schema.svg` / `.png` | Owner-scoped collections, child ledger/milestones and protocol history; field detail is in complete rules/source. |
|7| `figures/protocol.svg` / `.png` | Durable acceptance, reconciliation, uncertain outcome and authoritative fencing distinction. |

All diagrams are original AI-assisted documentation illustrations derived from the original requirements and actual implementation; they are not runtime traces or copied product branding. The static mockups contain fictional amounts and are not a functioning duplicate app. Existing screenshots are unchanged byte copies. Their retired gallery origin paths and hashes are provenance in `screenshot-provenance.json`, not runtime dependencies or links to missing files.

## Encoding and layout

Figure labels and the concise report source use ASCII characters in explicitly UTF-8 files. Range labels use hyphens; currency uses LKR/USD; calculations use readable ASCII operators; diagram arrows are vector lines/triangles. This removes the malformed dash/quote sequences from the earlier embedded diagrams. PNGs are regenerated from the corrected SVG, not patched by changing document text alone.

The report uses A4,11-point Calibri body,10.5-point tables and9.5-point captions, ordinary margins and near-single line spacing. Diagrams have minimum20-pixel source labels and are displayed at readable report width; full editable sources remain available. No blank page, oversized text or artificial spacing is used to meet length. Page numbers and contents are updated by Word. Office-profile last-modified identity is stripped from DOCX metadata.

Report tooling is in `tools/report/` (see root README). It changes documentation only. Local third-party document tools are not app dependencies. `verify_report.py` checks17 pages/chapter placement, text bounds, preserved paragraphs/table cells, valid image paths, UTF-8/ASCII figure labels and no dangling numbered citations/References section, and renders every page into a private external inspection directory. Automated checks accompany visual inspection; they do not replace it or constitute application testing.

Application evidence remains in [consolidated verification](../VERIFICATION.md). No app/rules suite, live session, device change or Firebase publication is implied by generating the report. Source/setup/submission mapping is in [REQUIREMENTS_MAP.md](REQUIREMENTS_MAP.md).
