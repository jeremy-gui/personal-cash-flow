# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Un couple qui gère ensemble les finances de son foyer : deux utilisateurs réels et directs, pas un persona générique. L'un des deux est aussi le développeur de l'application.

## Product Purpose

Suivre les finances du foyer : saisir/importer les dépenses et revenus, suivre les opérations bancaires réellement passées, gérer des opérations prévisionnelles, rapprocher le prévisionnel avec le réel, catégoriser les dépenses, définir des budgets par catégorie, visualiser les dépenses du mois et leur répartition. Succès = une V0 réellement utilisée au quotidien par les deux utilisateurs, pas une démonstration de faisabilité technique.

## Positioning

Un outil personnel et privé, pas un produit commercial concurrent des apps bancaires grand public (Bankin, Linxo, etc.). Différence assumée : pas d'intégration bancaire automatique (Open Banking), pas d'IA, architecture volontairement minimale — conçu pour être compris et maintenu par son unique développeur, pas pour scaler à d'autres utilisateurs.

## Operating Context

Usage mensuel récurrent : import du relevé bancaire OFX, saisie manuelle de dépenses/revenus anticipés (prévisionnel), rapprochement des prévisionnels avec les opérations bancaires réelles importées, consultation du dashboard pour suivre budgets par catégorie et solde prévisionnel de fin de mois.

## Capabilities and Constraints

Monolithe modulaire Spring Boot (backend) + Angular (frontend) + PostgreSQL, déjà en place. Explicitement exclus pour l'instant : microservices, Kafka, Redis, Kubernetes, event sourcing, CQRS complexe, authentification/OAuth, IA/LLM, Open Banking, cloud spécifique. Devise unique : EUR. Règle métier stricte : le statut POINTEE d'une transaction ne peut jamais être saisi manuellement — il ne provient que d'un import bancaire ou d'un rapprochement avec une opération bancaire réelle.

## Brand Commitments

Nom : "LeBonCompte" (jeu de mots sur "avoir le bon compte"). Aucune autre identité de marque établie — pas de logo, pas de charte graphique, pas de ton de voix fixé.

Direction visuelle (standing preference, choisie explicitement le 2026-09-14 en remplacement du monde "Cahier de comptes familial") : dashboard fintech standard du genre — cartes arrondies, accent bleu, ombres douces, typographie sans-serif claire — exécuté avec le niveau de finition de Revolut comme référence de qualité. Choix assumé et non ironique : pas de pastiche, pas de clin d'œil, l'app doit ressembler à une app bancaire moderne sérieuse.

## Evidence on Hand

Aucune donnée réelle fournie à ce jour ; seules des données de test fictives existent (comptes et transactions créés pendant le développement). Pas de témoignages ni de contenu marketing à préserver.

## Product Principles

- Simplicité pragmatique avant sophistication architecturale — pas d'abstraction sans besoin réel avéré.
- Chaque fonctionnalité doit apporter une vraie valeur d'usage immédiate aux deux utilisateurs.
- Le modèle de données ne ment jamais sur l'origine d'une donnée (ex : POINTEE = rapproché avec la banque, jamais une saisie manuelle).
- Une V0 utilisable par deux personnes réelles vaut mieux qu'un produit démonstrateur complet mais inutilisé.
- Évolutif sans sur-ingénierie : la V0 laisse la porte ouverte (ex. hiérarchie de catégories, modèle de rapprochement) sans construire ce qui n'est pas encore nécessaire.

## Accessibility & Inclusion

Aucune exigence spécifique au-delà des bonnes pratiques standard (contraste, focus visible, labels de formulaire) — confirmé : application privée à deux utilisateurs, pas de norme (WCAG AA/AAA) imposée.
