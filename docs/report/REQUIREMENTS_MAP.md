# Report and submission coverage

Authority: [original assignment specification](../requirements/assignment-specification.pdf),10 PDF pages, and [original analysis/requirements](../requirements/analysis-and-requirements.pdf),12 pages. Page numbers include covers. The former establishes the assessment; the latter derives the application scope and acceptance examples. Individual member contributions are not inferred from commit authorship. Unresolved administrative information is listed only on the report cover.

## Required artifacts

| Source | Requirement | Canonical location |
|---|---|---|
| Brief p6, section6.2 | Professional PDF, minimum15 pages, no maximum | [Report PDF](Clarity_Finance_Report.pdf),17 pages; corresponding [DOCX](Clarity_Finance_Report.docx) and [Markdown source](Clarity_Finance_Report.md). |
| Brief p5, section4.2; rubric pp9-10 | Scenario-derived functional/non-functional requirements and constraints; SRS and use-case diagram | Report sections1-2; original analysis PDF; [verification traceability](../VERIFICATION.md). |
| Brief p5, section4.2 | MVVM diagram showing screens, ViewModels, repositories and sources | Report section5; editable architecture SVG. |
| Brief p5, section4.2 | Firestore schema and indexing/multicurrency rationale; schema diagram in rubric | Report sections6-7; complete root rules/index files. |
| Brief p5, section4.2 | At least3 annotated wireframes/mockups | Report section3, W1/W2/W3; three individual editable SVG sources plus composite. |
| Brief p6, section6.2 | Implementation-specific decisions tied to Kavindu | Report sections1,3,7-12 and evaluation. |
| Brief p6, section6.1 | Private GitHub/GitLab repo access via LMS and iterative history; README overview/Firebase/build/architecture | Existing repository and authoritative [root README](../../README.md). Verify assessor access/LMS submission separately. |
| Brief p6, section6.3 | Release-signed or debug application APK connected to live Firebase; Android8+ physical/configured emulator validation | README build/output instructions and recorded API26 production/live evidence. Test APK/fictional host is not the production deliverable. |
| Brief p7, section6.4 | Scheduled15-minute live engineering demonstration with flows, architecture/schema/queries and Q&A | Prepare from report and README; confirm actual LMS slot. Not completed by generating documents. |
| Detailed rubric p10 | Supporting slide deck, live APK and Q&A | Slides remain a submission task; they do not replace the live engineering discussion. No mandated slide count. |
| Brief p7, integrity policy | Truthful assistance disclosure and understood team work | Report cover discloses actual Codex role; team must review and confirm its own contributions/policy compliance. |

The brief specifies **no font, margin, line-spacing, citation-style, word-count, filename or cover/reference exclusion rule**. Current A4 formatting is an editorial choice; any later official template takes precedence. There is no standalone References section or dangling numbered citation in the concise report. Original sources and figure provenance remain available here and in [ASSETS.md](ASSETS.md).

The group maximum is3. The printed deadline is30October2026; this preparation is dated5November2026, not backdated. The assignment is40% of the module grade. Absent Auth/rules, an uninstallable APK or non-attendance earns zero for the relevant criterion under brief p8. Administrative confirmations remain on the report cover, without invented identities, dates or percentages.

## Marking scheme

| Criterion (brief pp7-10) | Weight | Report/evidence |
|---|---:|---|
| Problem analysis and requirement extraction |15%| Sections1-2; supplied persona, objectives, specific FR/NFR derivation and use cases |
| Architecture and code quality |15%| Section5; actual Koin/ViewModel/repository boundaries, coroutines, readonly StateFlow and source assertions |
| Compose UI and UX |15%| Sections3-4; annotated mockups, selected final screenshots, Material3/system themes and reachable forms |
| Firebase integration |15%| Sections6,10-12; identity, schema/index, rules, realtime and durable recovery; local/live evidence separated |
| Feature completeness and innovation |10%| Sections7-9; exact financial separation, explicit goals, consumed recurring history and catalogue references; FR01-FR21 mapping |
| Technical documentation |10%| Report, SRS/source PDFs, diagrams/mockups, root README and concise verification record |
| Demo and presentation |20%| Future live APK/engineering discussion/supporting slides/Q&A; do not claim a completed demonstration |
| **Total** |**100%**| Coverage is not a predicted mark |

## Technical constraints and final handoff

- Kotlin source/Kotlin DSL; Compose Material3/no XML layouts; single production Activity/Navigation Compose. Standard JVM/Firebase APIs remain used: do not claim absence of all Java interoperability under the brief's broader wording.
- Koin satisfies the permitted DI choice. UI uses viewModelScope; process/session synchronization deliberately has an application supervisor scope. Explain this lifetime distinction against the brief's broad async wording.
- Firestore primary, real Firebase email/password Auth, owner rules and dashboard listeners remain production behaviour. Room is recommended, not mandatory. Actual minSdk26/target36 satisfies the API floor/target requirement; API35 limitations remain explicit.
- Review the cover's administrative register and contribution/assistance disclosure. Verify PDF after any edits; keep at least15 pages without padding or tiny figures.
- Submit private-repository access, complete security rules/index source, final application APK (identify hash/version/source) and PDF through the actual LMS instructions. Supply authorized Firebase configuration securely, never credentials in the report/repository.
- Attend the15-minute demonstration with supporting slides. Use an inspected designated account/device and separately authorized fixtures; inspect saved auth/recovery before startup. No deployment/live testing is implied by these instructions.
- Preserve approximate human timing, unexecuted TalkBack and API35/race/performance/deployed-equality limitations. Latest292-unit/final8 Android results and reused147 rules evidence must not be summed with older overlapping runs.
