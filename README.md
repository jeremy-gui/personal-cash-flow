# Personal Cash Flow

A self-hosted household budget tracker. Import your bank statements, plan upcoming expenses and income, reconcile
the plan with what really happened, and follow your budgets by category. Multi-language and multi-currency support is
coming soon.

**Who it's for:** individuals or couples who want to track their own money on their own server, without connecting
a bank aggregator or sending their data to a third party. It started as the budget app of my own household.

The interface is in French and amounts are in euros.

## Features

- OFX and CSV bank statement import, with duplicate detection
- Planned transactions and manual reconciliation against imported bank lines
- Category and label suggestions learned from your past imports
- Recurring transactions, budgets, provisions and transfers between accounts
- Dashboard, yearly analysis, and a salary split calculator for couples
- Login with an admin who manages users; all users share the same data

## Built with

Spring Boot 4 (Java 21), PostgreSQL 16 with Flyway, Angular 22, nginx and Docker Compose.

## Run it

You need Docker with Docker Compose.

```sh
git clone <this repository's URL> personal-cash-flow
cd personal-cash-flow
cp .env.example .env
```

Fill in `POSTGRES_PASSWORD` and `APP_REMEMBER_ME_KEY` in `.env` (`openssl rand -hex 32` for each), then:

```sh
docker compose up -d --build
docker compose logs app | grep "Admin créé"
```

The second command prints the first admin's password, unless you set `APP_ADMIN_PASSWORD`. Open
<http://localhost:8080>, log in as `admin`, and change the password.

### Try it with sample data

1. In **Comptes**, create an account with the account number `12345678901` and make it the default.
2. In **Import bancaire**, import [`samples/sample.ofx`](samples/sample.ofx), which contains made-up transactions.
3. On the dashboard, select January 2026.

The salary split page starts with example lines (`Alex`, `Sam`) to replace with your own.

### Exposing it beyond your machine

The app listens on `127.0.0.1` only. It holds your financial data, so to reach it from elsewhere, use a VPN such as
Tailscale or an HTTPS reverse proxy. Don't publish the plain HTTP port on the internet.

### Back up and update

```sh
# Back up
docker compose exec -T db pg_dump -U leboncompte --clean leboncompte | gzip > backup.sql.gz

# Restore
docker compose stop app
gunzip -c backup.sql.gz | docker compose exec -T db psql -U leboncompte leboncompte
docker compose start app

# Update (database migrations run at startup)
git pull && docker compose up -d --build
```

Backups contain all your financial data: store them somewhere safe and keep them out of git.

## Good to know

- OFX imports find the account through its account number, so set it on the account first.
- CSV import supports one format, La Banque Postale's export (see `DefaultCsvBankFormat`).
- The salary split page relies on category names: `Salaire`, `Allocations` and `Renflouage` count as funding sources.
- The code still uses the app's former name, `LeBonCompte`, for the database and a few class names.

## Develop

```sh
docker compose -f docker-compose.dev.yml up -d    # PostgreSQL on localhost:5432
cd backend && ./gradlew bootRun                   # API on :8080; the admin password is in its log
cd frontend && npm ci && npx ng serve             # App on :4200, proxies /api to :8080
```

Tests: `./gradlew test` in `backend/` (needs the dev database running) and `npx ng test` in `frontend/`.

[`PRODUCT.md`](PRODUCT.md) and [`DESIGN.md`](DESIGN.md) describe the product and design decisions.

## Roadmap

See [`ROADMAP.md`](ROADMAP.md).

## License

The code is under the [MIT](LICENSE) license. The logo (`logo.png`, `logo-mark.png` and `favicon.png` in
`frontend/public/`) was generated with ChatGPT and is not covered by it.
