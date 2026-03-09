# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Projet

API REST Spring Boot (Java 21) pour servir des quiz techniques au progiciel RH SAII. Fonctionne **sans IA** : lit une base PostgreSQL alimentée par un CLI TypeScript externe, assemble des quiz à la demande, génère des PDF (OpenPDF), et expose des sessions interactives pour les candidats.

## Commandes

```bash
# Compiler
mvn compile

# Lancer tous les tests unitaires (pas besoin de PostgreSQL)
mvn test

# Lancer un seul test
mvn test -Dtest=QuizMatcherServiceTest
mvn test -Dtest=QuizSessionServiceTest#should_create_session

# Démarrer le serveur (port 8080, nécessite PostgreSQL)
mvn spring-boot:run

# Packager en JAR
mvn package -DskipTests
```

> **Note** : Le Maven Wrapper (`mvnw`) n'est pas configuré dans ce projet — utiliser `mvn` directement. Java 21 requis — sur macOS, préfixer avec `JAVA_HOME=$(/usr/libexec/java_home -v 21)` si nécessaire.

## Architecture

### Stack technique
- **Java 21** / Spring Boot 3.4.3 / Maven
- **PostgreSQL** (schéma géré par Drizzle côté CLI TypeScript — `ddl-auto: validate` uniquement)
- **OpenPDF** pour la génération PDF
- **Bucket4j** pour le rate limiting applicatif (défense en profondeur)
- **Lombok** (`@Slf4j`, `@RequiredArgsConstructor`, etc.)

### Package `com.saii.quizapi`

Organisation en couches classiques, **sans architecture hexagonale** actuellement :

- **`controller/`** — REST controllers (`QuizController`, `SessionController`, `GlobalExceptionHandler`)
- **`service/`** — Logique métier (`QuizMatcherService`, `QuizSessionService`, `QuizPdfService`) + exceptions métier
- **`repository/`** — Spring Data JPA repositories (requête native `findByNameOrAlias` avec `unnest(aliases)` PostgreSQL)
- **`entity/`** — Entités JPA (`QuizTemplate`, `Question`, `Technology`, `QuizSession`, `SeniorityLevel`, etc.) — `Question.answerType` distingue les questions code (`code`) des questions textuelles (`text`)
- **`dto/`** — Records Java pour les requêtes/réponses JSON
- **`config/`** — Sécurité (`SecurityConfig`, `ApiKeyAuthFilter`, `RateLimitFilter`), CORS (`WebConfig`)

### Flux principal

1. Le progiciel RH appelle `POST /api/quiz/match` (ou `/match/session`) avec des prérequis techniques
2. `QuizMatcherService` résout les technologies (nom/alias), collecte les questions (avec fallback si aucune au niveau exact), et persiste un `QuizTemplate`
3. Le PDF est généré à la volée par `QuizPdfService` via `GET /api/quiz/{id}/pdf`

### Sécurité — Deux zones

- **Zone protégée (API key Bearer)** : `POST /api/sessions`, `/api/quiz/**` — pour le progiciel RH
- **Zone publique (token UUID)** : `GET/POST /api/sessions/{token}/*` — pour les candidats

### Sessions candidat — Machine à états

```
pending ──start──► in_progress ──complete──► completed
   │                     │                       │
   └──submit──► in_progress (auto-start)         └── (immuable)
```

L'expiration est vérifiée côté serveur à chaque accès (`startedAt + durationMinutes`). Les réponses attendues sont masquées tant que la session n'est pas `COMPLETED`.

## Conventions de test

- Tests unitaires purs avec **JUnit 5 + AssertJ + Mockito** (mode `STRICT_STUBS` via `@ExtendWith(MockitoExtension.class)`)
- **Pas de `@SpringBootTest`** pour les tests unitaires — pas besoin de PostgreSQL
- Fixtures centralisées dans `TestFixtures` (injection par réflexion via `setField` car les entités JPA n'ont pas de setters sur les IDs)
- Pattern de nommage : `should_<comportement>_when_<condition>`

## Configuration

Variables d'environnement (avec valeurs par défaut pour le dev) :

| Variable | Description | Défaut |
|---|---|---|
| `DB_URL` | URL JDBC PostgreSQL | `jdbc:postgresql://localhost:5433/saii` |
| `DB_USERNAME` / `DB_PASSWORD` | Credentials PostgreSQL | `saii` / `saii_dev` |
| `SAII_API_KEY` | Clé API pour les endpoints protégés | `dev-api-key-change-me` |
| `CORS_ALLOWED_ORIGINS` | Origines CORS autorisées | `http://localhost:4200` |
| `QUIZ_APP_BASE_URL` | URL de base pour les liens session | `http://localhost:4200` |
| `RATE_LIMIT_PER_MINUTE` | Rate limit applicatif | `60` |

## Points d'attention

- Le schéma PostgreSQL est géré **exclusivement** par Drizzle (CLI TypeScript) — ne jamais ajouter `ddl-auto: create/update`
- Les erreurs suivent le format **RFC 7807 ProblemDetail** (`GlobalExceptionHandler`)
- Port PostgreSQL par défaut : **5433** (pas 5432)
- La documentation d'architecture sécurité est dans `doc/ARCHI_SEC.md`
