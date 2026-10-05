# Handoff — contexte projet pour un agent produit / analyse financière

## Ce document

Écrit pour un agent qui va reprendre le projet côté **fonctions produit et choix
d'évolution en analyse financière** (pas le design visuel, pas l'infra). Il
donne le contexte que la lecture seule du code ne donne pas : pourquoi les
choses sont faites comme ça, ce qui a été écarté et pourquoi, où sont les
limites actuelles. Ne duplique pas `PRODUCT.md`/`DESIGN.md` — il les complète
et y renvoie.

## 1. Le projet en une phrase

**LeBonCompte** : suivi budgétaire pour un foyer (un couple, deux
utilisateurs réels, l'un des deux est aussi le développeur). App web privée,
pas un produit commercial, pas d'Open Banking, pas d'IA, pas
d'authentification. Le "pourquoi" complet est dans `PRODUCT.md` — à lire en
premier, ne pas le reformuler ici.

## 2. Stack

- **Backend** : Spring Boot 4.1.1 / Java 21, package-by-feature
  (`account`, `category`, `transaction`, `budget`, `dashboard`,
  `imports`, `reconciliation`, `recurring`, `common`). PostgreSQL + Flyway
  (migrations `V1` à `V14`, séquentielles, jamais modifiées rétroactivement).
- **Tests backend** : Mockito pur uniquement (`@ExtendWith(MockitoExtension.class)`,
  `@Mock`/`@InjectMocks`, AssertJ). Jamais de `@SpringBootTest` ni
  Testcontainers — choix assumé de simplicité (cf. `PRODUCT.md`, principe
  "pas d'abstraction sans besoin réel avéré"). Les entités n'ont pas de
  setter d'id public : les fixtures de test passent par réflexion
  (`Field.setAccessible(true)`) — voir n'importe quel `*ServiceTest.java`.
- **Frontend** : Angular standalone components, signals partout (`signal`,
  `computed`), syntaxe `@if`/`@for`. Pas de librairie de state management,
  pas de librairie de graphiques (voir § 7).
- **Repo** : `LeBonCompte/` = backend, `frontend/` = Angular, racine =
  `docker-compose.yml` (Postgres local).

## 3. Lancer le projet

```
docker-compose up -d                    # Postgres sur :5432 (db/user/pwd: leboncompte)
cd LeBonCompte && ./gradlew bootRun      # backend sur :8080
cd frontend && npx ng serve              # frontend sur :4200 (proxy /api -> :8080, proxy.conf.json)
```

Connexion : session Spring Security (cookie) + CSRF (`XSRF-TOKEN`, renvoyé
tout seul par Angular). Tous les utilisateurs partagent les mêmes données ;
le rôle `ADMIN` ne sert qu'à gérer les utilisateurs (page `/users`). Au
premier démarrage sans utilisateur, un admin est créé depuis
`APP_ADMIN_USERNAME` (défaut `admin`) / `APP_ADMIN_PASSWORD` ; sans mot de
passe, un aléatoire est affiché une fois dans les logs. En prod, définir
aussi `APP_REMEMBER_ME_KEY` (signe le cookie « Rester connecté », 30 jours).
Le front doit être servi sur la même origine que `/api` (reverse proxy).

Tests : `./gradlew test` (backend, 130 tests actuellement) et
`npx ng build` (frontend — pas de vraie suite de tests au-delà d'un
`app.spec.ts` trivial via Vitest).

## 4. Modèle de données (l'essentiel)

- **Account** : compte bancaire, `initialBalance`, `isDefault`. **Un seul
  compte par défaut**, utilisé comme scope implicite partout (Dashboard,
  Transactions) — pas de vue multi-comptes consolidée aujourd'hui.
- **Category** : hiérarchie libre parent/enfant. Pas de distinction
  revenu/dépense au niveau catégorie (ça vient de `Transaction.type`), et pas
  de flag épargne/type non plus — si un futur besoin d'analyse financière en
  a besoin, il faudra l'ajouter (voir § 6).
- **Transaction** : le cœur du modèle.
  - `type` : `DEPENSE` | `REVENU`.
  - `status` : `PREVISIONNELLE` | `POINTEE` — **règle métier stricte** :
    `POINTEE` ne peut jamais être saisi à la main, uniquement produit par un
    import bancaire (CSV/OFX) ou un rapprochement.
  - `matched` : vrai une fois qu'une pointée a absorbé une prévisionnelle via
    rapprochement (empêche de la re-proposer comme candidate).
  - `categoryConfirmed` / `labelConfirmed` : catégorie/libellé auto-suggérés
    depuis l'historique d'import (matching par préfixe bidirectionnel sur le
    libellé nettoyé des dates) restent "non confirmés" tant que l'utilisateur
    ne valide pas — badge "suggéré" dans l'UI.
  - `originalDate`/`originalLabel` : la donnée brute de la banque, jamais
    modifiée après import même si le libellé affiché change.
- **Budget** : **une seule ligne par catégorie** (contrainte unique en DB),
  avec `startMonth` + `amount`. S'applique à tous les mois à partir de
  `startMonth`, indéfiniment — **pas d'historique mensuel, pas de montant
  qui varie dans le temps**. `spent`/`remaining`/`percentageConsumed`
  recalculés à la volée à chaque requête, jamais stockés.
- **RecurringTransaction** : génère des transactions `PREVISIONNELLE` chaque
  mois.
- **ImportBatch** : trace chaque import CSV/OFX.

## 5. Fonctionnalités actuelles (page par page)

- **Dashboard** : KPIs du mois, donut "Dépenses par catégorie", mini-liste
  "Budgets par catégorie" (couleurs synchronisées avec le donut, montant
  restant affiché), 5 dernières transactions, tableau des prévisionnelles
  non pointées.
- **Transactions** : deux tableaux (Prévisionnelles / Importées), import
  CSV+OFX, rapprochement manuel (recherche + association prévisionnelle↔pointée),
  suggestion auto de catégorie/libellé, en-têtes et titres de section
  "sticky" au scroll.
- **Comptes** : CRUD simple, un compte marqué par défaut.
- **Catégories** : hiérarchie parent/enfant.
- **Budgets** : CRUD par catégorie, barre de progression + reste.
- **Import bancaire** : upload CSV (un format banque générique supporté) et
  OFX.

## 6. Piste explorée et abandonnée pour l'instant : page "Analyse annuelle"

Une page d'analyse annuelle façon coach financier a été **entièrement conçue,
implémentée, testée puis abandonnée** dans cette session, à la demande de
l'utilisateur ("on abandonne la page analyse pour l'instant") — le code a été
retiré (`git restore` + suppression des fichiers non trackés) et la migration
DB correspondante annulée (colonne `category.is_savings` supprimée, ligne
retirée de `flyway_schema_history`). Le dépôt est revenu exactement à l'état
du dernier commit (`4ac850d`). **Rien de ce qui suit n'existe dans le code
aujourd'hui** — c'est gardé ici pour ne pas relancer la même réflexion à
zéro si le sujet revient.

**Demande d'origine** : un "fil conducteur" à respecter — total annuel
positif/négatif et de combien, évolution mensuelle par catégorie (courbe),
proposition de budget par catégorie qui respecte un objectif d'épargne.

**Ce qui avait été construit** : endpoint `GET /api/analysis/yearly?year=`,
scopé au compte par défaut ; un flag manuel `Category.isSavings` (case à
cocher sur la page Catégories) pour marquer quelles catégories comptent comme
épargne ; un algorithme de suggestion de budget par catégorie = moyenne
historique mensuelle, réduite proportionnellement (jamais amplifiée) pour
que le total hors épargne tienne dans (revenu moyen − épargne moyenne), les
catégories d'épargne restant toujours à leur propre moyenne ; un bouton
"Appliquer" qui créait/mettait à jour le vrai `Budget` ; un graphique en
courbes SVG fait main pour l'évolution mensuelle par catégorie.

**Pourquoi ça a été mis de côté** : l'utilisateur n'a pas donné de raison
explicite au-delà de "pour l'instant" — probablement une question de
priorité, pas un rejet du principe. À vérifier avec lui avant de relancer.

**Décisions déjà prises la dernière fois, à reconsidérer plutôt que refaire
depuis zéro** :
- Épargne = flag manuel par catégorie (choisi explicitement plutôt qu'une
  détection auto par nom, ou un simple objectif en % du revenu sans lien
  catégoriel).
- Algorithme volontairement simple : pas de saisonnalité, pas de détection
  de tendance, pas de pondération par catégorie (tout réduit au même
  prorata — pas de "protéger le logement avant de couper les loisirs").
- Scope = compte par défaut uniquement, une seule année à la fois (pas de
  "vs année précédente").
- Le modèle `Budget` actuel (un seul montant courant par catégorie, pas
  d'historique par mois) rendrait toute évolution vers un vrai suivi
  temporel du budget non triviale — ça demanderait de repenser ce modèle,
  pas juste d'ajouter un champ.

## 7. Conventions et pièges à connaître

- `spring.jpa.open-in-view=false` : ne jamais renvoyer une entité (ou
  construire un DTO à partir d'une entité) après la fin de la méthode
  `@Transactional` — ça lève `LazyInitializationException` en silence côté
  frontend si personne ne gère l'erreur. Construire le DTO **à l'intérieur**
  du service transactionnel (voir `DashboardService`/`ReconciliationService`
  comme modèle).
- `border-collapse: collapse` casse `position: sticky` sur un `<thead>` dans
  Chrome (le contenu du tableau passe devant l'en-tête pendant le scroll).
  Utiliser `border-collapse: separate; border-spacing: 0;` à la place (voir
  `transactions.component.css`).
- Empiler plusieurs éléments `position: sticky` à des `top` différents
  fonctionne nativement en CSS (pas besoin de JS), **mais chaque élément
  empilé doit garder une hauteur constante** — sinon l'offset des éléments
  suivants devient faux dès que le contenu varie (ex. un bouton qui
  apparaît/disparaît selon l'état).
- Hibernate 6 / HQL supporte `month(t.date)` directement dans une `@Query`
  (pas besoin de `FUNCTION('extract', ...)`) — utile si des requêtes
  groupées par mois refont surface un jour.
- Pas de librairie de graphiques : le donut du Dashboard est en SVG/CSS fait
  main (`conic-gradient`), avec une couleur déterministe par nom de
  catégorie (fonction `hashColor` locale à `dashboard.component.ts`) — pas
  encore extraite dans un utilitaire partagé (ça l'a été temporairement pour
  la page Analyse, mais l'extraction est repartie avec elle ; à refaire si
  une deuxième vue a besoin des mêmes couleurs).
- Convention de commit : titre en anglais à l'impératif décrivant le "quoi"
  pour l'utilisateur (pas le "comment" technique).

## 8. État actuel du dépôt (2026-09-22)

- Branche `master`, 46 commits, **arbre de travail propre** (à part ce
  fichier `HANDOFF.md`, non commité).
- Dernier commit : `4ac850d` "Show remaining budget and match its color to
  the spending donut on the dashboard".
- La page Analyse (§ 6) a été entièrement retirée : fichiers supprimés,
  migration `V15` annulée en base (colonne `category.is_savings` droppée,
  ligne retirée de `flyway_schema_history`). Le backend démarre proprement
  au schéma `V14`.
- Backend : 130 tests passent (`./gradlew test`).
- Frontend : `npx ng build` passe sans erreur.

## 9. Autres docs à lire

- `PRODUCT.md` — positionnement produit, utilisateurs, principes (schéma
  "impeccable", ne pas dupliquer ici, le mettre à jour à la source si le
  produit évolue).
- `DESIGN.md` — système de design (même outil "impeccable").
- `.claude/rules/workflow.md` — règles de workflow imposées à tout agent
  (lire avant de coder, tester après chaque changement, ne jamais déclarer
  terminé sans avoir exécuté le code).
- `.claude/reference/patterns.md` — anti-patterns / bons patterns courts.
