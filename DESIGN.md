---
name: LeBonCompte
description: A category-standard fintech dashboard — rounded cards, one confident blue, soft elevation — built to a Revolut-grade finish.
colors:
  bg: "#f5f6f8"
  surface: "#ffffff"
  ink: "#0f172a"
  ink-soft: "#64748b"
  ink-faint: "#94a3b8"
  border: "#e6e9ee"
  border-soft: "#f0f2f5"
  blue: "#2563eb"
  blue-dark: "#1d4ed8"
  blue-soft: "#eff6ff"
  blue-100: "#dbeafe"
  red: "#dc2626"
  red-soft: "#fef2f2"
  green: "#16a34a"
  green-soft: "#f0fdf4"
typography:
  display:
    fontFamily: "Inter, system-ui, -apple-system, sans-serif"
    fontSize: "2.5rem"
    fontWeight: 800
    lineHeight: 1.1
    letterSpacing: "-0.02em"
  headline:
    fontFamily: "Inter, system-ui, -apple-system, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 700
    letterSpacing: "-0.02em"
  title:
    fontFamily: "Inter, system-ui, -apple-system, sans-serif"
    fontSize: "0.95rem"
    fontWeight: 700
  body:
    fontFamily: "Inter, system-ui, -apple-system, sans-serif"
    fontSize: "15px"
    fontWeight: 400
    lineHeight: 1.5
    fontFeature: "tnum"
  label:
    fontFamily: "Inter, system-ui, -apple-system, sans-serif"
    fontSize: "0.72rem"
    fontWeight: 600
    letterSpacing: "0.04em"
    fontFeature: "uppercase"
rounded:
  sm: "10px"
  md: "14px"
  lg: "20px"
  pill: "999px"
spacing:
  1: "0.25rem"
  2: "0.5rem"
  3: "0.75rem"
  4: "1rem"
  6: "1.5rem"
  8: "2rem"
components:
  button-primary:
    backgroundColor: "{colors.blue}"
    textColor: "#ffffff"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "10px 16px"
  button-primary-hover:
    backgroundColor: "{colors.blue-dark}"
    textColor: "#ffffff"
    rounded: "{rounded.sm}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "10px 16px"
  button-secondary-hover:
    backgroundColor: "{colors.bg}"
    textColor: "{colors.ink}"
    rounded: "{rounded.sm}"
  input-field:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    padding: "10px 12px"
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.lg}"
    padding: "{spacing.6}"
---

# Design System: LeBonCompte

## Overview

**Creative North Star: "The Standard Ledger"**

LeBonCompte is a category-standard fintech dashboard: white rounded cards floating on a cool light-gray canvas, one confident blue as the sole accent, soft ambient shadows instead of rules or borders-as-structure, and Inter carrying every weight from body text to the hero balance. This is a deliberate, non-ironic execution of "an app that looks like a serious modern bank" — chosen explicitly over a hand-ruled paper-ledger notebook world, a Minitel/Télétexte pastiche, a receipt-ticket concept, and a "grounded economy page" direction — to the quality bar of Revolut. Nothing here winks at its genre; it plays it straight.

The system's only overtly designed object is the hero balance card: a blue gradient panel that breaks from the white-card default to carry the month's two headline figures. Everywhere else, structure comes from the same repeating unit — a white, rounded, softly-shadowed card — whether it holds a table, a form, a donut chart, or a list of recent transactions. Color is disciplined: blue means primary action and current navigation state, green/red mean income/expense, and nothing else borrows either.

**Key Characteristics:**
- One repeating container — the rounded white card with a hairline border and a soft shadow — used for every content block on every page
- Exactly one accent color (blue) for actions and active state; red/green reserved strictly for expense/income polarity
- Inter end-to-end (400–800), with tabular figures (`font-feature-settings: 'tnum' 1`) applied globally rather than a separate monospace face
- Generous corner radii (10–20px) and pill shapes for chips, nav links, and progress bars; no sharp corners anywhere
- Depth via layered soft shadows, not borders; the hero card is the one place shadow does real color work (a blue-tinted shadow, not a neutral one)

## Colors

Cool neutral base, one blue accent, and a strict red/expense green/income pair — nothing else in the system carries semantic color.

### Primary
- **Action Blue** (`#2563eb`): primary buttons, submit-button fill, focus rings, hero-card gradient start, active nav link (text + pill background), donut segment 1, progress-bar fill, brand mark, scrollbar accessibility outline. Deepens to **Action Blue Dark** (`#1d4ed8`) on primary-button hover.

### Neutral
- **Canvas** (`#f5f6f8`): page background (`body`), hover fill for table rows, buttons, and nav links.
- **Surface** (`#ffffff`): every card, input, and button's resting background.
- **Ink** (`#0f172a`): headings, body text, primary button label.
- **Ink Soft** (`#64748b`): secondary text — labels, table headers, KPI labels, muted amounts, form labels.
- **Ink Faint** (`#94a3b8`): disabled text, inactive icon toggles, "no candidate" placeholder text.
- **Border** (`#e6e9ee`): default card/input/button border.
- **Border Soft** (`#f0f2f5`): table-row dividers, progress-bar tracks, disabled-button border.

### Secondary
- **Expense Red** (`#dc2626`): negative amounts, expense KPI icon, error banners (on `#fef2f2` tint), over-budget progress fill, danger icon buttons, the two-step delete-confirm button.
- **Income Green** (`#16a34a`): positive amounts, income KPI icon, success/result banners (on `#f0fdf4` tint), reconciled-status accents.

### Named Rules
**The Two-Ink Extension Rule.** Red and green are reserved for the expense/income (negative/positive) polarity and its direct error/success extensions. Blue is reserved for primary action and navigation/focus state. No fourth accent color exists anywhere in the shipped build; a category-spending donut and the recent-transactions avatars are the one deliberate exception — they draw from a shared 8-color categorical palette (`#2563eb #7c3aed #db2777 #ea580c #0891b2 #65a30d #ca8a04 #64748b`, assigned by a deterministic hash of category/label) precisely because they need to distinguish many categories, not signal polarity.

## Typography

**Display/Body/Label Font:** Inter (with system-ui, -apple-system, sans-serif), self-hosted via `@fontsource/inter` at weights 400/500/600/700/800.

**Character:** A single grotesque family carries the whole hierarchy — weight and size do the differentiating work a font pairing would otherwise do. Numerals use Inter's tabular-figure OpenType feature (`font-feature-settings: 'tnum' 1`, applied globally in `body` and reasserted on numeric inputs/table cells) rather than switching to a monospace face, so ledger-style column alignment is achieved without a second font.

### Hierarchy
- **Display** (800, 2.5rem, line-height 1.1, letter-spacing -0.02em): the two hero-card balance figures (solde actuel, prévisionnel fin de mois). Reserved for the hero card only.
- **Headline** (700, 1.5rem, letter-spacing -0.02em): page `h1`.
- **Title** (700, 0.95rem): card/section titles (`.card-title`, budget-card category name); the global `h2` role runs slightly lighter (600, 0.95rem, `--ink`) for looser subsection headers.
- **Body** (400, 15px, line-height 1.5): running text, table cell content, form hints.
- **Label** (600, 0.72–0.85rem, uppercase where noted, letter-spacing 0.04em on table headers): table column headers (uppercase), KPI labels, form field labels (500 weight, not uppercase), donut/legend captions.

### Named Rules
**The Tabular-Everywhere Rule.** Any numeral rendered in the app — money, dates, percentages — inherits `tnum` tabular figures from the global `body` rule or a local reassertion on the element; no numeral is left to Inter's proportional default in a data context.

## Layout

A single centered column (`main`, `max-width: 1120px`) below a sticky full-width topbar (`--surface` background, `1px solid var(--border)` bottom edge, `z-index: 10`). Page content follows a `.page-header` pattern: `h1` left, page-level control (month picker, primary action) right, wrapping on narrow viewports.

Spacing runs on a single rem scale reused everywhere: `--space-1` (0.25rem) through `--space-8` (2rem), with `--space-4`/`--space-6` as the workhorse gaps between cards and form fields, and `--space-8` between `main`'s outer padding and major vertical rhythm. Forms lay out as wrapped flex rows of label+field pairs (`gap: var(--space-6)`), never a rigid grid — the one grid in the system is the Budgets page's card list (`repeat(auto-fill, minmax(280px, 1fr))`) and the Dashboard's `split-row` (`1.1fr 1fr`, collapsing to one column under 780px).

Responsive behavior is consistent across all data tables: each sits inside a `.table-scroll` (`overflow-x: auto`) and several set an explicit `min-width` (transactions: 900px fixed-layout with named column widths; categories: 420px) so the table scrolls horizontally on narrow viewports instead of clipping or reflowing. Below 640px the topbar switches from a wrapping nav to a single-line, horizontally-scrollable pill strip (`overflow-x: auto`, `scrollbar-width: none`) rather than stacking links onto multiple lines.

## Elevation & Depth

Layered soft shadows on a flat canvas — the opposite of the flat, rule-only incumbent this build replaced. Every card carries `--shadow-sm` (`0 1px 2px rgba(15,23,42,.05)`) at rest; KPI cards and the split-row cards use the same token. The hero balance card is the one place shadow carries color: `--shadow-lg` (`0 20px 40px rgba(37,99,235,.18)`), a blue-tinted glow that makes the gradient panel feel lit from within rather than merely stacked. Submit buttons pick up a matching small blue shadow (`0 4px 12px rgba(37,99,235,.25)`) to read as "raised and primary" even before hover.

### Shadow Vocabulary
- **Ambient card** (`--shadow-sm`, `0 1px 2px rgba(15,23,42,.05)`): resting elevation for every card, KPI card, and icon button.
- **Panel** (`--shadow-md`, `0 8px 24px rgba(15,23,42,.06), 0 2px 6px rgba(15,23,42,.04)`): reserved token for a heavier panel; not yet exercised beyond the shared scale.
- **Hero glow** (`--shadow-lg`, `0 20px 40px rgba(37,99,235,.18)`): the hero balance card only — the one shadow that's colored, not neutral.

### Named Rules
**The Card-Is-Elevated Rule.** Every content grouping in the system is a `--surface` card with a border and a shadow at rest; nothing structural relies on a bare rule line the way the previous notebook world did. Depth communicates hierarchy, not just borders.

## Shapes

Rounded, consistently and generously: `--radius-sm` (10px) for inputs, buttons, and small icon buttons; `--radius-md` (14px) for KPI cards and the brand mark's inner corner treatment; `--radius-lg` (20px) for the general `.card` container and the hero card; `--radius-pill` (999px) for nav links, progress-bar tracks/fills, and round icon toggles. Round shapes (`border-radius: 50%`) are reserved for genuinely circular elements — the donut chart, its center hole, KPI/recent-transaction avatar icons.

### Named Rules
**The No-Sharp-Corner Rule.** No element in the shipped build has `border-radius: 0`; every container, control, and chip uses the sm/md/lg/pill scale or a true circle. A square-cornered box anywhere in this system is a regression toward the rejected notebook world.

## Components

### Buttons
- **Shape:** rounded rectangle (`--radius-sm`, 10px), `1px solid var(--border)`, `padding: 10px 16px`.
- **Default/Secondary:** background `--surface`, text `--ink`; hover fills `--bg` and darkens the border slightly (`#d7dce3`); active presses down 1px (`transform: translateY(1px)`).
- **Primary (`button[type=submit]`):** background `--blue`, white text, blue border, `0 4px 12px rgba(37,99,235,.25)` shadow at rest; hover deepens to `--blue-dark`.
- **Destructive confirm (`.confirm-delete`):** the armed second step of a delete flow — red text/border on `--red-soft`, inverts to solid red with white text on hover.
- **Disabled:** faint text/border (`--ink-faint` / `--border-soft`), no shadow, `cursor: not-allowed`.
- **Icon buttons:** square variants (28px / 26px) at `--radius-sm` or `--radius-pill` for compact table-row actions (edit, delete, type/status toggles); same hover-to-`--bg` convention, danger variant hover-fills `--red-soft`.

### Cards / Containers
- **Corner Style:** `--radius-lg` (20px) for the standard `.card`; KPI cards use `--radius-md` (14px).
- **Background:** `--surface`, always.
- **Shadow Strategy:** `--shadow-sm` at rest (see Elevation & Depth); the hero card alone uses `--shadow-lg`.
- **Border:** `1px solid var(--border)` (KPI cards and standard cards); the hero card has no border, relying on its gradient fill and shadow instead.
- **Internal Padding:** `--space-6` (standard card), `--space-4` (KPI card, budget-mini card), `--space-8` (hero card).

### Inputs / Fields
- **Style:** boxed and rounded — `1px solid var(--border)`, `--radius-sm`, `padding: 10px 12px`, `--surface` background. Selects get a custom double-chevron via background-image gradients, no native appearance.
- **Focus:** border turns `--blue`, plus a `0 0 0 3px var(--blue-soft)` halo — a soft focus ring, not a color-only or underline change.
- **File input:** the `::file-selector-button` matches the secondary-button treatment (bordered rounded rectangle, hover fills `--bg`).
- **Error state:** surfaced via an adjacent `.error` banner (see below), not a per-field red border.

### Navigation
Sticky white topbar (`1px solid var(--border)` bottom edge), wordmark in Inter 800 with a small blue-gradient square brand mark (`€`, `--radius-md`-ish 9px). Nav links are pill-shaped (`--radius-pill`), `--ink-soft` at rest, `--ink` on `--bg` hover, and `--blue` on `--blue-soft` when active (`routerLinkActive`). Below 640px the nav collapses from a wrapping row into a single-line horizontally-scrollable pill strip (scrollbar hidden) rather than stacking.

### Tables
- **Header:** uppercase Label-role `th` (`--ink-soft`, 0.72rem, letter-spacing 0.04em), `1px solid var(--border)` bottom border, `white-space: nowrap`.
- **Rows:** `1px solid var(--border-soft)` bottom border only, last row borderless; hover fills the row `--bg`.
- **Numeric cells:** tabular-figure Inter, right-aligned, `--red`/`--green` via `.negative`/`.positive` on negative/positive amounts.
- **Overflow:** every table sits in `.table-scroll` (`overflow-x: auto`); the Transactions table is additionally `table-layout: fixed` with named column-width classes, and the Catégories table carries a `min-width: 420px` floor specifically so it scrolls rather than clips on narrow viewports.

### The Hero Balance Card (signature component)
The Dashboard's top panel breaks from the white-card default: a `135deg` blue gradient (`#2563eb → #3b82f6 → #60a5fa`), white text, `--radius-lg` corners, `--shadow-lg`'s colored glow, holding two figures (current balance at Display scale, end-of-month projection at a secondary Display size) separated by a vertical `rgba(255,255,255,.28)` divider. It is the one place Display-scale type and a colored shadow both appear, and it is not reused as a generic panel style elsewhere.

### The Category Donut + Avatar Palette (signature pattern)
A `conic-gradient`-driven ring (140px, with a `--surface` center hole showing the period total) paired with a legend list of colored dots and amounts. The same 8-color categorical palette, assigned by a deterministic hash of category name (donut) or transaction label/category (recent-transaction round initial-letter avatars), is reused between the Dashboard's spending-by-category chart and its "Dernières transactions" list, so a category's color stays consistent between the two views within a session.

### KPI Cards
Three-up row (`Revenus` / `Dépenses` / `Solde net`), each a `--radius-md` white card with a 38px circular icon badge (`--green-soft`/`--red-soft` tint, `--radius-md` on the badge square) holding a stroked SVG line-icon (up/down arrow), a Label-role caption, and a bold value. The `Solde net` card's arrow direction is data-driven (up for ≥0, down for negative), matching the fixed-direction icons on the other two.

## Do's and Don'ts

### Do:
- **Do** build every content block as a `--surface` card at `--radius-lg` (20px) with `1px solid var(--border)` and `--shadow-sm`; it is the system's one repeating structural unit.
- **Do** keep blue as the only accent for primary actions, focus, and active nav state; reserve red/green strictly for the expense/income polarity and its error/success extensions.
- **Do** wrap every data table in `.table-scroll` and give it an explicit `min-width` when its columns would otherwise clip on narrow viewports (as Transactions and Catégories do).
- **Do** collapse the topbar nav to a horizontally-scrollable single-line pill strip below 640px rather than letting it wrap to multiple lines.
- **Do** apply tabular figures (`font-feature-settings: 'tnum' 1`) to any numeral in a data context; Inter stays a single family end-to-end.

### Don't:
- **Don't** introduce a square-cornered (`border-radius: 0`) element anywhere; every container/control in the shipped build is rounded or circular — a sharp corner is a regression toward the previous, rejected notebook world.
- **Don't** add a fourth accent color; blue (action/nav) and red/green (expense/income) are the confirmed full set.
- **Don't** reuse the hero card's blue gradient and colored shadow as a general "important card" treatment — it is reserved for the Dashboard's balance summary.
- **Don't** style KPI or recent-transaction icons as line-only "eyebrow" chips or add invented label kickers above card titles; none exist in the shipped build, and the finish-review process specifically treats fabricated micro-labels as a defect, not a pattern to extend.

**Not canonized:** the reserved `--shadow-md` token and the Budgets/Dashboard-only `over-budget` progress-fill red are real tokens in the build but exercised in only one or two narrow spots each; they're recorded above as component-local behavior rather than promoted to system-wide named rules, since a value used once or twice isn't yet a system.
