# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Des particuliers ou des couples qui gèrent eux-mêmes les finances de leur foyer et hébergent l'application chez eux. Le projet est né pour un couple, dont l'un des deux est le développeur, et reste utilisé par ce foyer.

## Product Purpose

Suivre les finances du foyer : saisir/importer les dépenses et revenus, suivre les opérations bancaires réellement passées, gérer des opérations prévisionnelles, rapprocher le prévisionnel avec le réel, catégoriser les dépenses, définir des budgets par catégorie, visualiser les dépenses du mois et leur répartition. Succès = une application réellement utilisée au quotidien, qu'un nouvel utilisateur peut installer seul, pas une démonstration de faisabilité technique.

## Positioning

Un outil open source et auto-hébergé, pas un produit commercial concurrent des apps bancaires grand public (Bankin, Linxo, etc.). Différence assumée : les données restent chez l'utilisateur, pas d'intégration bancaire automatique (Open Banking), pas d'IA, architecture volontairement minimale — simple à comprendre et à maintenir. Chaque installation sert un foyer ; ce n'est pas un service multi-clients.

## Operating Context

Usage mensuel récurrent : import du relevé bancaire OFX, saisie manuelle de dépenses/revenus anticipés (prévisionnel), rapprochement des prévisionnels avec les opérations bancaires réelles importées, consultation du dashboard pour suivre budgets par catégorie et solde prévisionnel de fin de mois.

## Capabilities and Constraints

Monolithe modulaire Spring Boot (backend) + Angular (frontend) + PostgreSQL, déjà en place. Explicitement exclus pour l'instant : microservices, Kafka, Redis, Kubernetes, event sourcing, CQRS complexe, OAuth, IA/LLM, Open Banking, cloud spécifique. Devise unique : EUR. Règle métier stricte : le statut POINTEE d'une transaction ne peut jamais être saisi manuellement — il ne provient que d'un import bancaire ou d'un rapprochement avec une opération bancaire réelle.

## Brand Commitments

Nom : "Personal Cash Flow" (anciennement "LeBonCompte", nom encore présent dans le package Java et la base). Logo : maison, famille et pièces (`frontend/public/logo.png`). Pas de charte graphique ni de ton de voix fixés au-delà de la direction visuelle ci-dessous.

Direction visuelle (standing preference, choisie explicitement le 2026-09-14 en remplacement du monde "Cahier de comptes familial") : dashboard fintech standard du genre — cartes arrondies, accent bleu, ombres douces, typographie sans-serif claire — exécuté avec le niveau de finition de Revolut comme référence de qualité. Choix assumé et non ironique : pas de pastiche, pas de clin d'œil, l'app doit ressembler à une app bancaire moderne sérieuse.

## Evidence on Hand

Le dépôt ne contient que des données fictives (`samples/`, tests, lignes d'exemple de la page répartition). Pas de témoignages ni de contenu marketing à préserver.

## Product Principles

- Simplicité pragmatique avant sophistication architecturale — pas d'abstraction sans besoin réel avéré.
- Chaque fonctionnalité doit apporter une vraie valeur d'usage immédiate au foyer qui l'utilise.
- Le modèle de données ne ment jamais sur l'origine d'une donnée (ex : POINTEE = rapproché avec la banque, jamais une saisie manuelle).
- Une application utilisable par de vraies personnes vaut mieux qu'un produit démonstrateur complet mais inutilisé.
- Évolutif sans sur-ingénierie : la V0 laisse la porte ouverte (ex. hiérarchie de catégories, modèle de rapprochement) sans construire ce qui n'est pas encore nécessaire.

## Accessibility & Inclusion

Aucune exigence spécifique au-delà des bonnes pratiques standard (contraste, focus visible, labels de formulaire) — pas de norme (WCAG AA/AAA) imposée à ce jour.
