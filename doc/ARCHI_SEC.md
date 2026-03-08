# Architecture de securite — quiz-api

Ce document decrit les decisions d'architecture liees a la securite de quiz-api,
les mecanismes en place et les responsabilites partagees avec l'infrastructure.

---

## Vue d'ensemble

```
Internet
   │
   ▼
┌──────────────────────────────────────────────┐
│  Infra (responsabilite DevOps)               │
│                                              │
│  ┌──────────────┐    ┌───────────────────┐   │
│  │  WAF / CDN   │───►│ Reverse Proxy     │   │
│  │  (Cloudflare,│    │ (Nginx, Traefik,  │   │
│  │   AWS Shield)│    │  HAProxy)         │   │
│  └──────────────┘    └───────┬───────────┘   │
│                              │               │
│  Couche infra :              │               │
│  - Rate limiting global      │               │
│  - Protection DDoS           │               │
│  - TLS termination           │               │
│  - IP allowlist/blocklist    │               │
│  - Geo-blocking              │               │
└──────────────────────────────┼───────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────┐
│  quiz-api (responsabilite applicative)       │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │  Spring Security                       │  │
│  │  - API key Bearer (endpoints admin)    │  │
│  │  - Endpoints candidat publics          │  │
│  │  - Headers de securite (HSTS, etc.)    │  │
│  │  - CSRF desactive (API stateless)      │  │
│  └────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────┐  │
│  │  Rate limiting applicatif (Bucket4j)   │  │
│  │  - Defense en profondeur               │  │
│  │  - Voir section dediee ci-dessous      │  │
│  └────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────┐  │
│  │  Validation metier                     │  │
│  │  - Bean Validation (Jakarta)           │  │
│  │  - Masquage des reponses attendues     │  │
│  │  - Expiration de session cote serveur  │  │
│  │  - Machine a etats stricte             │  │
│  └────────────────────────────────────────┘  │
└──────────────────────────────────────────────┘
```

---

## Separation des responsabilites

### Ce que l'infra doit gerer

| Mecanisme | Pourquoi c'est une responsabilite infra |
|---|---|
| **Rate limiting global** | Centralise, partage entre instances, configurable sans redeploiement |
| **Protection DDoS** | Necessite une capacite reseau que l'application ne peut pas fournir |
| **TLS / HTTPS** | Terminaison TLS au niveau du reverse proxy ou du load balancer |
| **IP allowlist / blocklist** | Gestion operationnelle, pas du code applicatif |
| **Geo-blocking** | Politique de securite globale, pas specifique a une app |
| **Logs d'acces centralises** | Correlation entre services, retention, alerting |

Un reverse proxy (Nginx, Traefik) ou un API Gateway (Kong, AWS API Gateway)
est **la bonne couche** pour le rate limiting en production :
- Les requetes abusives sont bloquees **avant** d'atteindre la JVM
- Les compteurs sont partageables entre instances (Redis, etc.)
- La config est modifiable a chaud sans redeploiement

### Ce que quiz-api gere

| Mecanisme | Implementation | Details |
|---|---|---|
| **Authentification API key** | `ApiKeyAuthFilter` + Spring Security | Header `Authorization: Bearer {key}` |
| **Autorisation par endpoint** | `SecurityConfig` | Endpoints candidat publics, admin proteges |
| **Validation des entrees** | Jakarta Bean Validation | `@NotBlank`, `@Email`, `@Size`, `@Positive` |
| **Masquage des reponses** | `QuizSessionService.toSessionDetail()` | `expectedAnswer` et `explanation` masques tant que status != COMPLETED |
| **Machine a etats** | `SessionStatus` enum | Transitions strictes : PENDING → IN_PROGRESS → COMPLETED |
| **Expiration de session** | `autoCompleteIfExpired()` | Verification serveur a chaque acces (startedAt + durationMinutes) |
| **Headers de securite** | Spring Security (defaut) | X-Content-Type-Options, X-Frame-Options, Cache-Control |
| **Gestion des erreurs** | `GlobalExceptionHandler` | RFC 7807 ProblemDetail, pas de stack traces exposees |
| **Rate limiting applicatif** | `RateLimitFilter` (Bucket4j) | Defense en profondeur (voir ci-dessous) |

---

## Rate limiting — Defense en profondeur

### Pourquoi un rate limiter applicatif en plus de l'infra ?

quiz-api embarque un `RateLimitFilter` (Bucket4j) comme **filet de securite** :

```
Requete ──► [Infra rate limit] ──► [App rate limit] ──► Controller
              (1ere ligne)         (2e ligne)
```

| | Rate limit infra | Rate limit applicatif |
|---|---|---|
| **Quand l'utiliser** | Toujours en production | Fallback / dev / tests |
| **Partage entre instances** | Oui (Redis, etc.) | Non (en memoire JVM) |
| **Survit au redemarrage** | Oui | Non |
| **Bloque avant la JVM** | Oui | Non |
| **Configuration** | Ops / infra | Variable d'env `RATE_LIMIT_PER_MINUTE` |

**En production dockerisee avec un devops confirme**, le rate limiting
de l'infra (Nginx `limit_req`, Traefik middleware, Kong plugin) est suffisant.
Le rate limiter applicatif agit en defense en profondeur :

- Protection en **environnement de dev** (pas de reverse proxy)
- **Fallback** si la config infra est mal deployee ou contournee
- **Specifique a l'API** — l'infra rate-limite par IP globalement,
  l'app peut appliquer des limites par endpoint si necessaire

### Desactiver le rate limit applicatif

Si l'infra gere le rate limiting, on peut rendre le filtre applicatif
tres permissif sans le supprimer :

```bash
RATE_LIMIT_PER_MINUTE=10000
```

### Configuration recommandee par environnement

| Environnement | Rate limit infra | Rate limit app | Valeur recommandee |
|---|---|---|---|
| Dev local | Aucun | Actif | `60` req/min (defaut) |
| Staging Docker | Nginx/Traefik | Actif (permissif) | `300` req/min |
| Production | Nginx/Traefik + WAF | Permissif ou desactive | `10000` req/min |

---

## Authentification — Deux zones

```
                    ┌─────────────────────────────────────────────┐
                    │              quiz-api                        │
                    │                                             │
  Progiciel RH ────►│  Zone protegee (API key requise)            │
  (Bearer token)    │  - POST /api/sessions                       │
                    │  - GET/POST /api/quiz/**                    │
                    │  - POST /api/quiz/match/session              │
                    │                                             │
  Candidat ────────►│  Zone publique (token UUID = auth implicite)│
  (lien direct)     │  - GET  /api/sessions/{token}               │
                    │  - POST /api/sessions/{token}/start          │
                    │  - POST /api/sessions/{token}/answers        │
                    │  - POST /api/sessions/{token}/complete       │
                    └─────────────────────────────────────────────┘
```

Le token UUID v4 (122 bits d'entropie) rend le brute-force impraticable
(~5.3 × 10^36 combinaisons). Le rate limiting infra ajoute une couche
supplementaire en limitant les tentatives par IP.

---

## Securite des donnees

### Masquage des reponses attendues

Les champs `expectedAnswer` et `explanation` sont **nulls** dans la reponse JSON
tant que la session n'est pas en statut `COMPLETED`. Cela empeche un candidat
d'inspecter les reponses du reseau pour tricher.

```
Session IN_PROGRESS → expectedAnswer: null, explanation: null
Session COMPLETED   → expectedAnswer: "...", explanation: "..."
```

### Credentials et secrets

Aucun secret n'est hardcode dans le code source. Tout est externalisable :

| Secret | Variable d'environnement | Defaut (dev uniquement) |
|---|---|---|
| URL PostgreSQL | `DB_URL` | `jdbc:postgresql://localhost:5433/saii` |
| User PostgreSQL | `DB_USERNAME` | `saii` |
| Mot de passe PostgreSQL | `DB_PASSWORD` | `saii_dev` |
| Cle API | `SAII_API_KEY` | `dev-api-key-change-me` |

> **Important** : les valeurs par defaut ne sont destinees qu'au developpement local.
> En production, toutes ces variables doivent etre definies via
> Docker secrets, Kubernetes secrets, ou un vault.

### Logging

Les tokens de session ne sont **jamais** logges. Seul l'ID numerique
de la session apparait dans les logs pour la tracabilite.

---

## Recommandations pour la mise en production

1. **Configurer TLS** au niveau du reverse proxy (Let's Encrypt, cert-manager)
2. **Definir `SAII_API_KEY`** avec une valeur forte (>= 32 caracteres aleatoires)
3. **Activer le rate limiting infra** (Nginx `limit_req_zone`, Traefik `rateLimit` middleware)
4. **Restreindre les origines CORS** via `CORS_ALLOWED_ORIGINS` aux domaines reels
5. **Configurer les secrets** via Docker secrets ou Kubernetes secrets (pas de `.env` en production)
6. **Monitorer les 429** — un pic de rate limiting peut indiquer une attaque ou un client mal configure
