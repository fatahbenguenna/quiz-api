# Guide de test local

Ce guide decrit comment demarrer l'ensemble de la stack SAII Quiz en developpement et tester le parcours complet : creation d'un quiz, generation d'un token de session et passage du quiz dans le navigateur.

## Prerequis

- **Java 21** (verifier : `/usr/libexec/java_home -v 21` sur macOS)
- **Maven 3.9+**
- **Node.js 18+** et **npm 10+**
- **PostgreSQL** en cours d'execution sur le port **5433** avec la base `saii` alimentee (schema gere par le CLI TypeScript Drizzle)

## Etape 1 — Demarrer le backend (quiz-api)

```bash
cd /chemin/vers/quiz-api
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn spring-boot:run
```

Le serveur demarre sur `http://localhost:8080`.

## Etape 2 — Demarrer le frontend (quiz-app)

```bash
cd /chemin/vers/quiz-app
npm start
```

L'application demarre sur `http://localhost:4200`. Le proxy redirige automatiquement les appels `/api` vers `localhost:8080`.

## Etape 3 — Creer un quiz et une session candidat

Un seul appel suffit grace a l'endpoint `POST /api/quiz/match/session` qui assemble le quiz ET cree la session :

```bash
curl -X POST http://localhost:8080/api/quiz/match/session \
  -H 'Authorization: Bearer dev-api-key-change-me' \
  -H 'Content-Type: application/json' \
  -d '{
    "jobTitle": "Developpeur Java/Spring Senior",
    "prerequisites": [
      { "technology": "Java", "seniority": "senior" },
      { "technology": "Spring Boot", "seniority": "confirme" }
    ],
    "maxQuestions": 5,
    "candidateName": "Alice Dupont",
    "candidateEmail": "alice.dupont@example.com"
  }'
```

### Parametres

| Champ | Obligatoire | Description |
|---|---|---|
| `jobTitle` | oui | Intitule du poste (devient le titre du quiz) |
| `prerequisites` | oui | Liste de prerequis `{ technology, seniority }` |
| `maxQuestions` | non | Nombre max de questions (defaut : 20) |
| `candidateName` | oui | Nom du candidat |
| `candidateEmail` | oui | Email du candidat |

> **Note** : les valeurs de `technology` doivent correspondre a des technologies presentes en base (nom ou alias). Les valeurs de `seniority` correspondent aux codes de la table `seniority_levels` (ex : `junior`, `confirme`, `senior`).

### Reponse attendue (201 Created)

```json
{
  "id": 1,
  "token": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "sessionUrl": "http://localhost:4200/session/a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "quizId": 42,
  "quizTitle": "Developpeur Java/Spring Senior",
  "candidateName": "Alice Dupont",
  "status": "pending",
  "createdAt": "2026-03-09T10:30:00+01:00"
}
```

## Etape 4 — Ouvrir le quiz dans le navigateur

Copier la valeur de `sessionUrl` dans la reponse et l'ouvrir dans le navigateur :

```
http://localhost:4200/session/a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

Le parcours candidat se deroule ensuite dans l'interface :

1. **Ecran d'accueil** — titre du quiz, duree, instructions → cliquer "Commencer le quiz"
2. **Quiz interactif** — naviguer entre les questions, ecrire du code dans l'editeur Monaco, le timer decompte
3. **Fin du quiz** — cliquer "Terminer" (ou attendre l'expiration du timer) → vue de revue avec les reponses attendues

## Alternative : tester via curl uniquement (sans frontend)

Si le frontend n'est pas demarre, il est possible de simuler le parcours candidat en ligne de commande. Remplacer `TOKEN` par le token obtenu a l'etape 3.

```bash
# Consulter le detail de la session
curl http://localhost:8080/api/sessions/TOKEN

# Demarrer la session (pending → in_progress)
curl -X POST http://localhost:8080/api/sessions/TOKEN/start

# Soumettre une reponse (repeter pour chaque question)
curl -X POST http://localhost:8080/api/sessions/TOKEN/answers \
  -H 'Content-Type: application/json' \
  -d '{ "questionId": 1, "candidateAnswer": "Ma reponse ici" }'

# Terminer la session (in_progress → completed)
curl -X POST http://localhost:8080/api/sessions/TOKEN/complete
```

> **Note** : soumettre une reponse sur une session `pending` la demarre automatiquement (`auto-start`).

## Depannage

| Probleme | Solution |
|---|---|
| `Connection refused` sur PostgreSQL | Verifier que PostgreSQL est lance sur le port **5433** (pas 5432) |
| `mvnw: no such file` | Utiliser `mvn` directement (le wrapper n'est pas configure) |
| `401 Unauthorized` sur `/api/quiz/**` | Ajouter le header `Authorization: Bearer dev-api-key-change-me` |
| `Technology not found` | Verifier que les technologies existent en base (`SELECT * FROM technologies`) |
| `No questions found` | Verifier que des questions existent pour la technologie et le niveau demandes |
