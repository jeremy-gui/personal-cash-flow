# Roadmap

Planned work, roughly in priority order.

## Easier for other people to use

- [ ] Make the salary split independent of hardcoded category names (`Salaire`, `Allocations`, `Renflouage`)
- [ ] Support more CSV bank formats, with a column mapping chosen at import time
- [ ] Add a first-run guide: create an account, import a statement, set budgets
- [ ] Translate the interface (English first) and stop assuming the euro
- [ ] Publish prebuilt Docker images with each release

## Features

- [ ] Consolidated view across accounts (most pages follow the default account today)
- [ ] Year-over-year comparison in the yearly analysis
- [ ] Budget suggestions based on past spending and a savings goal
- [ ] CSV export of transactions

## Code health

- [ ] Add CI running the tests and the build on every push
- [ ] Add frontend tests: only a few exist today, against 182 on the backend
- [ ] Run the database-backed backend tests on Testcontainers so they need no running database
- [ ] Remove what's left of the one-off spreadsheet backfill (`/api/imports/reconcile-originals`)
- [ ] Rename the `leboncompte` database and the remaining `LeBonCompte` classes to match the app's name
