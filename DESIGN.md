---
name: LeBonCompte
description: A household bookkeeping app styled as a hand-ruled family account notebook, not a fintech dashboard.
colors:
  paper: "#f5f0e1"
  surface: "#fffdf6"
  ink: "#2b2b2b"
  ink-soft: "#6b6250"
  paper-line: "rgba(43, 43, 43, 0.09)"
  rule-soft: "rgba(43, 43, 43, 0.22)"
  red: "#8b1e1e"
  red-tint: "#f3e2df"
  green: "#2f6b3a"
  green-tint: "#e3ebe0"
  kraft: "#c9a66b"
typography:
  display:
    fontFamily: "IBM Plex Mono, ui-monospace, 'Courier New', monospace"
    fontSize: "2.5rem"
    fontWeight: 600
    lineHeight: 1.1
  headline:
    fontFamily: "IBM Plex Sans, system-ui, sans-serif"
    fontSize: "1.75rem"
    fontWeight: 700
    letterSpacing: "-0.01em"
  title:
    fontFamily: "IBM Plex Sans, system-ui, sans-serif"
    fontSize: "1.05rem"
    fontWeight: 600
    letterSpacing: "0.06em"
    fontFeature: "uppercase"
  body:
    fontFamily: "IBM Plex Sans, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "IBM Plex Sans, system-ui, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 600
    letterSpacing: "0.07em"
    fontFeature: "uppercase"
  mono:
    fontFamily: "IBM Plex Mono, ui-monospace, 'Courier New', monospace"
    fontSize: "0.95rem"
    fontWeight: 500
    letterSpacing: "normal"
rounded:
  none: "0px"
spacing:
  1: "0.25rem"
  2: "0.5rem"
  3: "0.75rem"
  4: "1rem"
  6: "1.5rem"
  8: "2rem"
components:
  button-primary:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.paper}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
    padding: "8px 16px"
  button-primary-hover:
    backgroundColor: "{colors.red}"
    textColor: "{colors.paper}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
    padding: "8px 16px"
  button-secondary-hover:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.paper}"
  input-field:
    backgroundColor: "transparent"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.none}"
    padding: "8px 4px"
  table-header:
    backgroundColor: "transparent"
    textColor: "{colors.ink-soft}"
    typography: "{typography.label}"
---

# Design System: LeBonCompte

## Overview

**Creative North Star: "Cahier de comptes familial"**

LeBonCompte is styled as a hand-ruled household account notebook (cahier de comptes) shared by two people, not a commercial banking dashboard. The page is a ruled écru sheet: a faint 27px repeating horizontal-line texture runs behind every screen, a thin red "marge" rule runs down the left edge of the main content column, and section breaks are drawn with double rules (`3px double`) the way a ledger separates totals from detail, rather than with cards or shadows. Every number — balances, amounts, dates, percentages — is set in IBM Plex Mono with tabular figures and right-aligned in tables, exactly as a hand-kept ledger aligns its columns; every label and heading is set in IBM Plex Sans. The one deliberately theatrical flourish is the month picker, drawn as a small red-bordered stamp rotated `-2deg`, like a date cachet pressed onto the page.

The palette stays close to its paper: écru background (`#f5f0e1`), near-black ink for text and rules (`#2b2b2b`), and exactly two semantic accents — a muted brick red for expenses/negative values and a sober green for income/positive values — used nowhere else. A warm kraft tone marks navigation on the dark topbar, echoing a notebook's cover material. Nothing in the system is rounded, nothing casts a shadow, and no color outside the red/green pair carries semantic weight.

**Key Characteristics:**
- Ruled-paper background texture on every screen, never a plain fill
- Zero border-radius, zero box-shadow, anywhere
- Numbers are always IBM Plex Mono, tabular, right-aligned; everything else is IBM Plex Sans
- Structure comes from rule lines (single, double, dashed-soft) and a left marge rule, never from cards
- Red = expense/negative/error/focus; green = income/positive/success; both used sparingly and consistently

## Colors

The palette is paper-and-ink with two ledger accents; nothing else carries color.

### Primary
- **Écriture Rouge** (`#8b1e1e`): expense and negative amounts, error banners, focus rings, the month-picker stamp, submit-button hover, active nav underline. The book's "red ink."

### Secondary
- **Vert Sobre** (`#2f6b3a`): income and positive amounts, budget-under-target progress fill, success/result banners. The book's "black ink for what's owed to you," rendered as a quiet green rather than a literal black.

### Neutral
- **Paper** (`#f5f0e1`): page background, always carrying the ruled-line texture.
- **Surface** (`#fffdf6`): input focus background, table-row hover, button resting background — a slightly warmer, lifted-off-the-page white used for interactive surfaces.
- **Ink** (`#2b2b2b`): body text, headings, rule lines, submit-button fill.
- **Ink Soft** (`#6b6250`): secondary text — section labels (h2), table headers, field labels, muted amounts.
- **Paper Line** (`rgba(43, 43, 43, 0.09)`): the ruled-paper background texture and progress-bar tracks.
- **Rule Soft** (`rgba(43, 43, 43, 0.22)`): secondary dividers (table row borders, h2 underline, budget-card separators).
- **Kraft** (`#c9a66b`): topbar navigation links and the topbar's double-rule border, evoking a notebook cover.
- **Red Tint / Green Tint** (`#f3e2df` / `#e3ebe0`): background fill for error and success/result banners, always paired with a matching solid border.

### Named Rules
**The Two-Ink Rule.** Only red and green ever carry semantic color, and only for the expense/income (negative/positive) distinction and its direct extensions (error/success banners, focus). No other UI state borrows either color, and no third accent color exists.

## Typography

**Display/Body Font:** IBM Plex Sans (with system-ui, sans-serif), self-hosted via `@fontsource` at weights 400/500/600/700.
**Mono/Numeric Font:** IBM Plex Mono (with ui-monospace, Courier New, monospace), self-hosted via `@fontsource` at weights 400/500/600/700.

**Character:** A grotesque-plus-mono pairing chosen for its bureaucratic, form-like precision — Plex Sans reads as a clean administrative hand for labels and prose, Plex Mono gives every figure the fixed-width alignment of a ledger column.

### Hierarchy
- **Display** (600, 2.5rem, line-height 1.1, Plex Mono): the dashboard's headline balance figures (solde actuel, prévisionnel fin de mois). The only place numbers are allowed to dominate a screen.
- **Headline** (700, 1.75rem, Plex Sans, letter-spacing -0.01em): page `h1`, underlined with a `3px double` rule.
- **Title** (600, 1.05rem, uppercase, letter-spacing 0.06em, Plex Sans, color ink-soft): section headings (`h2`) such as "Dépenses par catégorie", underlined with a `1px solid` soft rule.
- **Body** (400, 1rem/16px, line-height 1.5, Plex Sans): running text, form hints.
- **Label** (600, 0.72–0.8rem, uppercase, letter-spacing 0.07em, Plex Sans, color ink-soft): table column headers, form field labels, KPI labels ("Revenus", "Dépenses", "Solde net").
- **Mono** (500, 0.95rem–1.4rem depending on context, Plex Mono, tabular-nums): every amount, date, and percentage outside the Display role — table cells, KPI values, budget figures, `input[type=number/date/month]`.

### Named Rules
**The Mono-for-Numbers Rule.** Any value that is a number — money, a date, a percentage, an ID — is set in IBM Plex Mono with tabular figures. Plex Sans never renders a numeral in a data context.

## Layout

The page is a single centered column, `max-width: 1000px`, offset from a `2px solid rgba(139, 30, 30, 0.28)` red rule running down its left edge — the copybook's marge line — with extra left padding (`space-6 + 12px`) to clear it. The dark topbar (`--ink` background, `4px double var(--kraft)` bottom border) spans full width above it and holds the wordmark and flat text navigation.

Spacing runs on a single rem scale reused everywhere: `--space-1` (0.25rem) through `--space-8` (2rem), with `--space-4` (1rem) and `--space-6` (1.5rem) as the workhorse gaps between fields, and `--space-8` between major page sections. Forms lay out as wrapped flex rows of label+field pairs (`gap: var(--space-6)`), never as a rigid grid.

Responsive behavior is minimal and consistent: every data table is wrapped in a `.table-scroll` (`overflow-x: auto`) so it scrolls horizontally on narrow viewports rather than reflowing; the dashboard's category-spending rows collapse from a 3-column grid to a 2-area stacked grid (name+amount on one line, bar below) under 480px. No breakpoint changes the topbar, which wraps its nav links instead of collapsing to a menu.

## Elevation & Depth

Flat. There is no `box-shadow` anywhere in the system and depth is never simulated with blur or layering. Hierarchy is conveyed entirely by rule weight: a `3px double` border marks a major structural break (page title, balance-row top), a `2px solid` border marks a table header's bottom edge, and a `1px solid` soft rule marks a minor divider (row separators, `h2` underlines). The one exception that reads as "raised" is the month-picker stamp, which achieves its lifted feel through a red double-border-plus-offset-outline and a slight rotation, not through shadow.

### Named Rules
**The No-Shadow Rule.** Nothing in this system casts a shadow or receives elevation via blur. If something needs to stand apart, it gets a heavier rule weight or a double rule, not a drop shadow.

## Shapes

Every corner in the system is square (`border-radius: 0`), including buttons, inputs, selects, file-picker buttons, error/success banners, and the scrollbar thumb. There is no exception for the signature month-picker stamp: its lifted look comes from a rotated rectangle with a doubled red border, still hard-cornered. Containers are defined by rule lines and background-tint blocks (error/success banners), never by rounded panels.

### Named Rules
**The Rectangle Rule.** `border-radius: 0` everywhere, no exceptions. A rounded corner anywhere in this system is a bug.

## Components

### Buttons
- **Shape:** hard rectangle (0 radius), `1px solid var(--rule)` border, `padding: 8px 16px` (`space-2 space-4`).
- **Default:** background `--surface`, text `--ink`.
- **Submit (`button[type=submit]`):** background `--ink`, text `--paper` at rest — pre-inverted to mark it as the primary action.
- **Hover:** default/secondary buttons invert to `background: var(--ink); color: var(--paper)`; submit buttons instead go to `background: var(--red)`.
- **Disabled:** text and border drop to `--ink-soft` / `--rule-soft` at 0.6 opacity, cursor `not-allowed`.

### Inputs / Fields
- **Style:** no box. Fields are written on the line: `background: transparent`, no side/top borders, only `border-bottom: 1px solid var(--rule)`. Numeric/date/month inputs switch to Plex Mono with tabular figures.
- **Focus:** `border-bottom: 2px solid var(--red)` plus a `--surface` background — the field visibly "lifts off the page" only while being written in.
- **Select:** native appearance removed, custom double-chevron drawn via background-image gradients; same underline treatment as text inputs.
- **File input:** the button portion (`::file-selector-button`) is a bordered rectangle that inverts to ink/paper on hover, matching the button component.

### Tables
- **Header:** `th` text is Label-role (uppercase, ink-soft), left-aligned by default, bottom border `2px solid var(--rule)`; numeric columns are overridden to right-align per table (e.g. amount/budget/% columns).
- **Rows:** `1px solid var(--rule-soft)` bottom border only — no vertical rules, no zebra striping; hover fills the row with `--surface`.
- **Numeric cells:** Plex Mono, tabular-nums, right-aligned, colored `--red`/`--green` for negative/positive amounts via `.negative`/`.positive` classes.
- **Overflow:** every table sits inside `.table-scroll` (`overflow-x: auto`) so it scrolls on mobile instead of reflowing.

### The Cachet Stamp (signature component)
The month picker — reused identically on Dashboard, Transactions, and Budgets — is a small `--surface` box with a `2px solid var(--red)` border plus a `1px solid var(--red)` outline offset 3px outward, rotated `-2deg`, label text uppercase red Plex Sans at 700 weight. It is the system's one intentionally "stamped" object, standing in for a date cachet on a physical ledger page; it is not a general card or button pattern and should not be reused for anything other than a month/period selector.

### Progress Bars
Flat `6px` tall bar, `--paper-line` track, solid fill color with no radius and no gradient: red for category-spending fill (dashboard), green for budgets under target and red (`.over-budget`) for budgets over target.

### Banners (error / result)
Flat block, tinted background (`--red-tint` / `--green-tint`) with a matching `1px solid` border in the solid color, `0.85rem` text. Result/success banners that carry a figure use Plex Mono.

### Navigation
Dark topbar (`--ink` background), wordmark in Plex Sans 700, links in kraft (`--kraft`) at rest, white (`--paper`) on hover, with the active route marked by a `2px solid var(--red)` underline. Links wrap on narrow viewports rather than collapsing into a menu.

## Do's and Don'ts

### Do:
- **Do** keep the ruled-paper background texture (27px repeating horizontal line) on every screen; it is the system's baseline, not a decoration for one page.
- **Do** set every number — money, date, percentage — in IBM Plex Mono, tabular-nums, right-aligned in tabular contexts.
- **Do** use double rules (`3px double`) only for the top-level structural breaks a ledger would use for a grand total, not for routine dividers.
- **Do** reserve the rotated red cachet-stamp treatment for period/month selectors specifically; it is a named, singular component, not a general "important field" style.

### Don't:
- **Don't** add `border-radius` or `box-shadow` anywhere in this system; both are confirmed absent by every stylesheet in the build.
- **Don't** introduce a card/panel container with its own background fill to group content — grouping is done with rule lines and section headings (`h2`), matching the incumbent build.
- **Don't** use red or green for anything other than the expense/income (negative/positive) distinction and its direct error/success extensions; a third accent color would break the Two-Ink Rule.
- **Don't** replace the underline-style text inputs with boxed/bordered fields — the "written on the line" input treatment is confirmed system-wide across all six feature pages.
