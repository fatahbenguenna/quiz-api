# SAII Quiz API — Module Java (mode deconnecte)

API REST Spring Boot pour servir des quiz techniques au progiciel RH.
Ce module fonctionne **sans IA** : il lit la base PostgreSQL alimentee par le CLI TypeScript,
assemble des quiz a la demande, genere des PDF, et expose des sessions interactives
via une application standalone (quiz-app) avec editeur Monaco.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Progiciel RH (Angular)                         │
│                                                                         │
│  Fiche candidat ──► Bouton "Generer quiz" ──► POST /api/quiz/match     │
│                                                       │                │
│                                            ┌──────────┴──────────┐     │
│                                            │    quiz-api (Java)  │     │
│                                            │                     │     │
│                                            │  1. Resoudre technos │     │
│                                            │     (nom + aliases)  │     │
│                                            │  2. Collecter Q/R    │     │
│                                            │  3. Assembler quiz   │     │
│                                            │  4. Generer PDF      │     │
│                                            └──────────┬──────────┘     │
│                                                       │                │
│  Popin PDF ◄── GET /api/quiz/{id}/pdf ◄───────────────┘                │
└─────────────────────────────────────────────────────────────────────────┘
                               │
                    ┌──────────┴──────────┐
                    │   PostgreSQL (SAII)  │
                    │                     │
                    │  technologies       │
                    │  questions          │
                    │  quiz_templates     │
                    │  seniority_levels   │
                    └─────────────────────┘
```

### Structure du module

```
quiz-api/
├── pom.xml                                    # Spring Boot 3.4.3, Java 21, OpenPDF
├── .mvn/jvm.config
├── src/main/resources/
│   └── application.yml                        # Connexion PostgreSQL, port 8080
├── src/main/java/com/saii/quizapi/
│   ├── QuizApiApplication.java                # Point d'entree Spring Boot
│   ├── entity/
│   │   ├── SeniorityLevel.java                # Table de reference (code=PK, rank)
│   │   ├── Technology.java                    # Technologies (name, category)
│   │   ├── Question.java                      # Questions/reponses (FK technology)
│   │   ├── QuizTemplate.java                  # Quiz templates (created_by, OneToMany)
│   │   ├── QuizTemplateQuestion.java          # Table de liaison N:N avec position
│   │   ├── QuizTemplateQuestionId.java        # Cle composite (Embeddable)
│   │   ├── QuizSession.java                   # Session candidat (token UUID, status)
│   │   ├── QuizSessionAnswer.java             # Reponse candidat par question
│   │   ├── SessionStatus.java                 # Enum : PENDING, IN_PROGRESS, COMPLETED
│   │   └── SessionStatusConverter.java        # Convertisseur JPA enum <-> varchar
│   ├── repository/
│   │   ├── SeniorityLevelRepository.java
│   │   ├── TechnologyRepository.java          # findByNameOrAlias (requete native text[])
│   │   ├── QuestionRepository.java            # findByTechnologyIdAndSeniorityLevel
│   │   ├── QuizTemplateRepository.java
│   │   ├── QuizSessionRepository.java         # findByToken, findAllByOrderByCreatedAtDesc
│   │   └── QuizSessionAnswerRepository.java   # findBySessionIdAndQuestionId
│   ├── dto/
│   │   ├── TechPrerequisite.java              # record(technology, seniority)
│   │   ├── MatchRequest.java                  # record(jobTitle, prerequisites, maxQuestions)
│   │   ├── MatchAndStartRequest.java          # Requete combinee match + session
│   │   ├── QuizQuestionDto.java               # Vue question pour JSON/PDF
│   │   ├── QuizResponse.java                  # Quiz complet avec questions
│   │   ├── CreateSessionRequest.java          # record(quizId, candidateName, candidateEmail)
│   │   ├── SessionResponse.java               # Reponse creation session (token, URL)
│   │   ├── SessionDetailResponse.java         # Detail complet session + questions + reponses
│   │   └── SubmitAnswerRequest.java           # record(questionId, candidateAnswer)
│   ├── service/
│   │   ├── QuizMatcherService.java            # Algorithme de matching offline
│   │   ├── QuizPdfService.java                # Generation PDF (OpenPDF)
│   │   ├── QuizSessionService.java            # Cycle de vie des sessions
│   │   ├── QuizNotFoundException.java         # Exception metier quiz
│   │   ├── SessionNotFoundException.java      # Exception metier session
│   │   └── SessionStateException.java         # Transition d'etat invalide
│   ├── config/
│   │   ├── WebConfig.java                     # CORS configurable + SPA forwarding
│   │   ├── SecurityConfig.java                # Spring Security (API key + headers)
│   │   └── ApiKeyAuthFilter.java              # Filtre Bearer API key
│   └── controller/
│       ├── QuizController.java                # Endpoints quiz + match + match/session
│       ├── SessionController.java             # Endpoints sessions (admin listing + candidat)
│       └── GlobalExceptionHandler.java        # Gestion des erreurs (RFC 7807)
└── src/test/java/com/saii/quizapi/
    ├── TestFixtures.java                      # Helpers partages (setField, create*)
    └── service/
        ├── QuizMatcherServiceTest.java        # 4 tests unitaires (Mockito strict)
        ├── QuizPdfServiceTest.java            # 2 tests (validation magic number PDF)
        └── QuizSessionServiceTest.java        # 16 tests (cycle de vie, expiration, masquage)
```

---

## Pre-requis

- **Java 21** (Temurin, Corretto ou equivalent — pas GraalVM 24 qui casse Lombok)
- **Maven 3.9+**
- **PostgreSQL** en cours d'execution (base `saii` alimentee par le CLI TypeScript)
- La base doit contenir au minimum les tables `seniority_levels`, `technologies`, `questions`

---

## Installation et lancement

```bash
# 1. Se placer dans le module
cd saii/quiz-api

# 2. Compiler (Java 21 obligatoire)
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn compile

# 3. Lancer les tests
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test

# 4. Demarrer le serveur (port 8080)
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn spring-boot:run

# 5. Verifier (necessite la cle API)
curl http://localhost:8080/api/quiz/1 \
  -H 'Authorization: Bearer dev-api-key-change-me'
```

### Configuration (`application.yml`)

Toutes les valeurs sensibles sont externalisables via variables d'environnement :

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5433/saii}
    username: ${DB_USERNAME:saii}
    password: ${DB_PASSWORD:saii_dev}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false

server:
  port: ${SERVER_PORT:8080}

saii:
  quiz-app:
    base-url: ${QUIZ_APP_BASE_URL:http://localhost:4200}
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:4200}
  security:
    api-key: ${SAII_API_KEY:dev-api-key-change-me}
```

> **Important** : `ddl-auto: validate` verifie que les entites JPA correspondent au schema existant
> sans jamais creer ni modifier de tables. Le schema est gere exclusivement par Drizzle (CLI TypeScript).

### Securite — Authentification API key

Les endpoints d'administration (`GET /api/sessions`, `POST /api/sessions`, `/api/quiz/*`) sont proteges par une **cle API Bearer**.
Les endpoints candidat (`GET/POST /api/sessions/{token}/*`) sont publics (le token UUID fait office d'auth).

```bash
# Appel protege — necessite la cle API
curl -X POST http://localhost:8080/api/quiz/match \
  -H 'Authorization: Bearer dev-api-key-change-me' \
  -H 'Content-Type: application/json' \
  -d '{"jobTitle": "Dev Java", "prerequisites": [{"technology": "Java", "seniority": "confirme"}]}'

# Appel public — endpoint candidat
curl http://localhost:8080/api/sessions/a1b2c3d4-e5f6-...
```

Spring Security ajoute automatiquement les headers de securite (X-Content-Type-Options, X-Frame-Options, etc.).

> Pour les decisions d'architecture securite (separation infra/app, rate limiting, defense en profondeur),
> voir [doc/ARCHI_SEC.md](doc/ARCHI_SEC.md).

---

## API REST

### `GET /api/quiz/{id}` — Recuperer un quiz en JSON

Retourne un quiz existant avec toutes ses questions.

```bash
curl http://localhost:8080/api/quiz/1 \
  -H 'Authorization: Bearer dev-api-key-change-me'
```

Reponse :
```json
{
  "id": 1,
  "title": "Quiz Dev Java/Spring Senior",
  "description": "Quiz genere par IA pour l'offre Developpeur Java Senior",
  "targetSeniority": "senior",
  "durationMinutes": 30,
  "createdBy": "ai",
  "createdAt": "2026-03-07T06:30:00+01:00",
  "questions": [
    {
      "position": 1,
      "technology": "Java",
      "seniorityLevel": "senior",
      "question": "Expliquez le fonctionnement du garbage collector G1 en Java 21.",
      "answer": "G1 divise le heap en regions de taille egale...",
      "explanation": "Evalue la comprehension de la gestion memoire avancee.",
      "difficultyScore": 7
    },
    {
      "position": 2,
      "technology": "Spring Boot",
      "seniorityLevel": "confirme",
      "question": "Quel est le role de @SpringBootApplication ?",
      "answer": "Combine @Configuration, @EnableAutoConfiguration et @ComponentScan.",
      "explanation": null,
      "difficultyScore": 3
    }
  ]
}
```

### `GET /api/quiz/{id}/pdf` — Telecharger le PDF d'un quiz

Retourne un PDF (`Content-Type: application/pdf`) avec `Content-Disposition: inline`.
Le frontend Angular peut l'afficher directement dans un `<iframe>` ou un viewer PDF.

```bash
# Telecharger le PDF (necessite la cle API)
curl -o quiz-1.pdf http://localhost:8080/api/quiz/1/pdf \
  -H 'Authorization: Bearer dev-api-key-change-me'
```

Le PDF contient :
- Titre du quiz, niveau cible, duree estimee
- Chaque question numerotee avec sa technologie et son niveau
- La reponse attendue et l'explication (si disponible)
- Pied de page avec l'identifiant du quiz et le createur

### `POST /api/quiz/match` — Matcher et assembler un quiz (JSON)

Le progiciel RH envoie les prerequis techniques d'un poste.
L'API assemble un nouveau quiz a partir des questions disponibles en base.

```bash
curl -X POST http://localhost:8080/api/quiz/match \
  -H 'Authorization: Bearer dev-api-key-change-me' \
  -H 'Content-Type: application/json' \
  -d '{
    "jobTitle": "Developpeur Java/Spring Senior",
    "prerequisites": [
      {"technology": "Java", "seniority": "senior"},
      {"technology": "Spring Boot", "seniority": "confirme"},
      {"technology": "Docker", "seniority": "junior"}
    ],
    "maxQuestions": 15
  }'
```

Parametres :
| Champ | Type | Obligatoire | Description |
|-------|------|-------------|-------------|
| `jobTitle` | string | oui | Intitule du poste (devient le titre du quiz) |
| `prerequisites` | array | oui | Liste des prerequis techniques |
| `prerequisites[].technology` | string | oui | Nom de la technologie (ex: "Java", "Spring Boot") |
| `prerequisites[].seniority` | string | oui | Niveau attendu (debutant, junior, confirme, senior, expert, architecte) |
| `maxQuestions` | integer | non | Nombre max de questions (defaut : 20) |

Algorithme de matching :
1. Pour chaque prerequis, recherche la technologie par nom **ou alias** (requete native `unnest(aliases)`)
2. Recupere les questions correspondant a la technologie + seniorite exacte
3. **Fallback** : si aucune question au niveau exact, prend toutes les questions de la technologie
4. Limite au `maxQuestions` total
5. Determine la seniorite cible du quiz = la plus elevee parmi les prerequis
6. Persiste le quiz en base avec `created_by = 'java-matcher'`

Reponse : meme format JSON que `GET /api/quiz/{id}`.

### `POST /api/quiz/match/pdf` — Matcher et retourner directement le PDF

Meme logique que `/match` mais retourne le PDF au lieu du JSON.

```bash
curl -X POST http://localhost:8080/api/quiz/match/pdf \
  -H 'Authorization: Bearer dev-api-key-change-me' \
  -H 'Content-Type: application/json' \
  -d '{
    "jobTitle": "DevOps Senior",
    "prerequisites": [
      {"technology": "Docker", "seniority": "senior"},
      {"technology": "Kubernetes", "seniority": "confirme"}
    ]
  }' \
  -o quiz-devops.pdf
```

### `POST /api/quiz/match/session` — Matcher + creer une session candidat (endpoint cle)

Le progiciel RH envoie les prerequis techniques + les infos du candidat **en un seul appel**.
L'API assemble le quiz, cree une session avec un token UUID, et retourne l'URL que le candidat peut ouvrir.

```bash
curl -X POST http://localhost:8080/api/quiz/match/session \
  -H 'Authorization: Bearer dev-api-key-change-me' \
  -H 'Content-Type: application/json' \
  -d '{
    "jobTitle": "Dev Java/Spring Senior",
    "prerequisites": [
      {"technology": "Java", "seniority": "senior"},
      {"technology": "Spring Boot", "seniority": "confirme"}
    ],
    "maxQuestions": 10,
    "candidateName": "Alice Dupont",
    "candidateEmail": "alice.dupont@example.com"
  }'
```

Reponse :
```json
{
  "id": 1,
  "token": "a1b2c3d4-e5f6-...",
  "sessionUrl": "http://localhost:4200/session/a1b2c3d4-e5f6-...",
  "quizId": 42,
  "quizTitle": "Dev Java/Spring Senior",
  "candidateName": "Alice Dupont",
  "status": "pending",
  "createdAt": "2026-03-07T08:30:00+01:00"
}
```

Le `sessionUrl` est directement invocable : le progiciel RH l'affiche comme lien ou l'envoie par email.

---

### API Sessions — Cycle de vie du quiz candidat

#### `GET /api/sessions` — Lister toutes les sessions (admin)

Retourne la liste de toutes les sessions, triees par date de creation decroissante.
Utilise par le tableau de bord admin de la quiz-app.

```bash
curl http://localhost:8080/api/sessions \
  -H 'Authorization: Bearer dev-api-key-change-me'
```

Reponse :
```json
[
  {
    "id": 1,
    "token": "a1b2c3d4-e5f6-...",
    "sessionUrl": "http://localhost:4200/session/a1b2c3d4-e5f6-...",
    "quizId": 42,
    "quizTitle": "Dev Java Senior",
    "candidateName": "Alice Dupont",
    "status": "pending",
    "createdAt": "2026-03-07T08:30:00+01:00"
  }
]
```

#### `POST /api/sessions` — Creer une session pour un quiz existant

```bash
curl -X POST http://localhost:8080/api/sessions \
  -H 'Content-Type: application/json' \
  -d '{"quizId": 42, "candidateName": "Alice Dupont", "candidateEmail": "alice@example.com"}'
```

#### `GET /api/sessions/{token}` — Detail complet de la session

Retourne le quiz, les questions, et les reponses du candidat.
Utilise par la quiz-app pour les vues candidat et interviewer.

```bash
curl http://localhost:8080/api/sessions/a1b2c3d4-e5f6-...
```

#### `POST /api/sessions/{token}/start` — Demarrer la session

Passe de `pending` a `in_progress`. Le timer commence.

```bash
curl -X POST http://localhost:8080/api/sessions/a1b2c3d4-e5f6-.../start
```

#### `POST /api/sessions/{token}/answers` — Soumettre une reponse

Idempotent : met a jour la reponse si elle existe deja.
Auto-demarre la session si encore en `pending`.

```bash
curl -X POST http://localhost:8080/api/sessions/a1b2c3d4-e5f6-.../answers \
  -H 'Content-Type: application/json' \
  -d '{"questionId": 10, "candidateAnswer": "public sealed class Shape permits Circle, Rectangle {}"}'
```

#### `POST /api/sessions/{token}/complete` — Terminer la session

Passe de `in_progress` a `completed`. Les reponses ne sont plus modifiables.

```bash
curl -X POST http://localhost:8080/api/sessions/a1b2c3d4-e5f6-.../complete
```

#### Machine a etats de la session

```
  pending ──start──► in_progress ──complete──► completed
     │                     │                       │
     │                     └──expiration──────────►│
     │                       (auto-complete)        │
     └──submit──► in_progress (auto-start)         └── (immuable)
```

L'API verifie automatiquement l'expiration (startedAt + durationMinutes) a chaque acces.
Si le temps est depasse, la session est auto-completee cote serveur.

### Gestion des erreurs

Les erreurs suivent le format **RFC 7807 Problem Detail** :

```json
{
  "type": "about:blank",
  "title": "Quiz non trouve",
  "status": 404,
  "detail": "Quiz introuvable : id=999"
}
```

| Code HTTP | Cas |
|-----------|-----|
| `200` | Succes |
| `201` | Session creee avec succes |
| `204` | Reponse soumise (pas de body) |
| `400` | Requete invalide (JSON malformate, champs manquants) |
| `404` | Quiz ou session non trouve |
| `409` | Transition d'etat invalide (ex: completer une session deja terminee) |

---

## Integration Angular

### Scenario typique dans le progiciel RH

```
┌──────────────────────────────────────────────────────────────────┐
│  Angular — Fiche candidat                                        │
│                                                                   │
│  ┌─────────────────────────────────┐                             │
│  │ Poste : Dev Java Senior         │                             │
│  │ Prerequis :                      │                             │
│  │   - Java (senior)               │                             │
│  │   - Spring Boot (confirme)      │                             │
│  │   - Docker (junior)             │                             │
│  │                                  │                             │
│  │  [Generer le quiz technique]  ◄─── Bouton declencheur         │
│  └─────────────────────────────────┘                             │
│              │                                                    │
│              │ click                                              │
│              ▼                                                    │
│  POST /api/quiz/match ──► { id: 42, title: "...", ... }          │
│              │                                                    │
│              │ quizId = response.id                               │
│              ▼                                                    │
│  ┌──────────────────────────────────────────┐                    │
│  │         Popin / Dialog                    │                    │
│  │  ┌────────────────────────────────────┐  │                    │
│  │  │  <iframe>                          │  │                    │
│  │  │  src="/api/quiz/42/pdf"            │  │                    │
│  │  │                                    │  │                    │
│  │  │  ┌────────────────────────────┐   │  │                    │
│  │  │  │  Quiz Dev Java Senior     │   │  │                    │
│  │  │  │  Niveau: senior | 30 min  │   │  │                    │
│  │  │  │                            │   │  │                    │
│  │  │  │  Q1 — Java (senior)       │   │  │                    │
│  │  │  │  Expliquez le GC G1...    │   │  │                    │
│  │  │  │  Reponse: G1 divise...    │   │  │                    │
│  │  │  │  ...                       │   │  │                    │
│  │  │  └────────────────────────────┘   │  │                    │
│  │  └────────────────────────────────────┘  │                    │
│  │                                           │                    │
│  │  [Telecharger PDF]  [Imprimer]  [Fermer] │                    │
│  └──────────────────────────────────────────┘                    │
└──────────────────────────────────────────────────────────────────┘
```

### Service Angular — `QuizService`

```typescript
// quiz.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface TechPrerequisite {
  technology: string;
  seniority: string;
}

export interface MatchRequest {
  jobTitle: string;
  prerequisites: TechPrerequisite[];
  maxQuestions?: number;
}

export interface QuizQuestion {
  position: number;
  technology: string;
  seniorityLevel: string;
  question: string;
  answer: string;
  explanation: string | null;
  difficultyScore: number;
}

export interface QuizResponse {
  id: number;
  title: string;
  description: string;
  targetSeniority: string;
  durationMinutes: number;
  createdBy: string;
  createdAt: string;
  questions: QuizQuestion[];
}

@Injectable({ providedIn: 'root' })
export class QuizService {

  private readonly baseUrl = '/api/quiz';

  constructor(private readonly http: HttpClient) {}

  /** Recuperer un quiz existant en JSON */
  getQuiz(id: number): Observable<QuizResponse> {
    return this.http.get<QuizResponse>(`${this.baseUrl}/${id}`);
  }

  /** Matcher les prerequis et assembler un nouveau quiz */
  matchQuiz(request: MatchRequest): Observable<QuizResponse> {
    return this.http.post<QuizResponse>(`${this.baseUrl}/match`, request);
  }

  /** URL du PDF pour un quiz donne (pour iframe/download) */
  getPdfUrl(quizId: number): string {
    return `${this.baseUrl}/${quizId}/pdf`;
  }

  /** Telecharger le PDF en tant que Blob (pour sauvegarde) */
  downloadPdf(quizId: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${quizId}/pdf`, {
      responseType: 'blob',
    });
  }
}
```

### Composant Angular — Popin avec viewer PDF

```typescript
// quiz-dialog.component.ts
import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { QuizService } from './quiz.service';

export interface QuizDialogData {
  quizId: number;
  quizTitle: string;
}

@Component({
  selector: 'app-quiz-dialog',
  template: `
    <h2 mat-dialog-title>{{ data.quizTitle }}</h2>

    <mat-dialog-content class="pdf-container">
      <iframe
        [src]="pdfUrl"
        width="100%"
        height="600px"
        type="application/pdf">
      </iframe>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="download()">Telecharger PDF</button>
      <button mat-button (click)="print()">Imprimer</button>
      <button mat-raised-button mat-dialog-close>Fermer</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .pdf-container {
      min-width: 700px;
      min-height: 620px;
    }
    iframe {
      border: 1px solid #e0e0e0;
      border-radius: 4px;
    }
  `],
})
export class QuizDialogComponent {

  readonly pdfUrl: SafeResourceUrl;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: QuizDialogData,
    private readonly dialogRef: MatDialogRef<QuizDialogComponent>,
    private readonly quizService: QuizService,
    private readonly sanitizer: DomSanitizer,
  ) {
    const url = this.quizService.getPdfUrl(data.quizId);
    this.pdfUrl = this.sanitizer.bypassSecurityTrustResourceUrl(url);
  }

  download(): void {
    this.quizService.downloadPdf(this.data.quizId).subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `quiz-${this.data.quizId}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  print(): void {
    const iframe = document.querySelector('iframe') as HTMLIFrameElement;
    iframe?.contentWindow?.print();
  }
}
```

### Utilisation depuis la fiche candidat

```typescript
// candidate-detail.component.ts (extrait)
import { MatDialog } from '@angular/material/dialog';
import { QuizService, MatchRequest } from './quiz.service';
import { QuizDialogComponent } from './quiz-dialog.component';

@Component({ /* ... */ })
export class CandidateDetailComponent {

  constructor(
    private readonly dialog: MatDialog,
    private readonly quizService: QuizService,
  ) {}

  /** Appele au clic sur "Generer le quiz technique" */
  generateQuiz(): void {
    // Construire la requete a partir des prerequis du poste
    const request: MatchRequest = {
      jobTitle: this.candidate.jobTitle,
      prerequisites: this.candidate.prerequisites.map(p => ({
        technology: p.technology,
        seniority: p.seniorityLevel,
      })),
      maxQuestions: 15,
    };

    // 1. Matcher/assembler le quiz
    this.quizService.matchQuiz(request).subscribe(quiz => {
      // 2. Ouvrir la popin avec le PDF
      this.dialog.open(QuizDialogComponent, {
        width: '800px',
        data: {
          quizId: quiz.id,
          quizTitle: quiz.title,
        },
      });
    });
  }
}
```

### Configuration du proxy Angular (developpement)

Pour que les appels `/api/*` soient rediriges vers le serveur Java en dev :

```json
// proxy.conf.json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true
  }
}
```

```bash
ng serve --proxy-config proxy.conf.json
```

### CORS (si frontend et API sur des domaines differents)

Les origines CORS sont configurables via la variable d'environnement `CORS_ALLOWED_ORIGINS`
(par defaut `http://localhost:4200`). Plusieurs origines separees par virgule sont supportees.

---

## Algorithme de matching — Detail

```
Entree : MatchRequest { jobTitle, prerequisites[], maxQuestions }

Pour chaque prerequis (technology, seniority) :
  │
  ├─ Recherche technologie (TechnologyRepository.findByNameOrAlias)
  │   SQL : WHERE LOWER(name) = LOWER(:name)
  │            OR LOWER(:name) = ANY(SELECT LOWER(unnest(aliases)))
  │   │
  │   ├─ Non trouvee → log.debug + ignorer ce prerequis
  │   │
  │   └─ Trouvee → recuperer les questions
  │       │
  │       ├─ Chercher par (technology_id, seniority_level) exact
  │       │   │
  │       │   ├─ Questions trouvees → ajouter (dans la limite de maxQuestions)
  │       │   │
  │       │   └─ Aucune question → FALLBACK
  │       │       └─ Prendre toutes les questions de cette technologie
  │       │           (tous niveaux confondus)
  │       │
  │       └─ Ajouter au resultat (dans la limite de maxQuestions)
  │
  └─ Si result.size >= maxQuestions → arreter

Seniorite cible = MAX(prerequis[].seniority) via seniority_levels.rank

Creer QuizTemplate :
  - title = jobTitle
  - target_seniority = seniorite cible calculee
  - created_by = "java-matcher"
  - duration_minutes = 30

Persister en base → retourner QuizResponse
```

---

## Tests

```bash
# Tests unitaires uniquement (pas besoin de PostgreSQL)
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test

# Verifier que le code compile
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn compile

# Packager en JAR executable
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn package -DskipTests
```

Tests existants (22 au total) :
| Classe | Tests | Description |
|--------|-------|-------------|
| `QuizMatcherServiceTest` | 4 | Matching, fallback, limite, erreur si vide |
| `QuizPdfServiceTest` | 2 | Generation PDF valide, PDF vide |
| `QuizSessionServiceTest` | 16 | Cycle de vie, masquage reponses, expiration, erreurs |

---

## Build et deploiement

```bash
# Packager en JAR executable
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn package -DskipTests

# Lancer le JAR
java -jar target/quiz-api-0.1.0.jar

# Ou avec un profil specifique
java -jar target/quiz-api-0.1.0.jar --spring.profiles.active=production
```

### Variables d'environnement en production

```bash
# Connexion PostgreSQL
DB_URL=jdbc:postgresql://prod-db:5432/saii
DB_USERNAME=saii_prod
DB_PASSWORD=secret

# Port du serveur
SERVER_PORT=9090

# URL de base de la quiz-app (pour les liens dans les reponses)
QUIZ_APP_BASE_URL=https://quiz.example.com

# Origines CORS autorisees (separer par virgule)
CORS_ALLOWED_ORIGINS=https://rh.example.com,https://quiz.example.com

# Cle API pour les endpoints proteges
SAII_API_KEY=ma-cle-api-secrete
```

---

## Quiz App — Application standalone

L'application `quiz-app` (dossier `saii/quiz-app/`) est une **SPA Angular 21 autonome** avec Monaco Editor.
Elle offre deux interfaces :

- **Tableau de bord admin** (`/`) — connexion par cle API, liste de toutes les sessions avec statut, lien direct vers chaque session
- **Interface candidat** (`/session/:token`) — editeur de code Monaco, timer, sauvegarde automatique, vue review

### Flux d'utilisation

```
Progiciel RH                    quiz-api                     quiz-app
     │                              │                            │
     │ POST /api/quiz/match/session │                            │
     │─────────────────────────────►│                            │
     │    { prerequisites,          │                            │
     │      candidateName }         │                            │
     │                              │                            │
     │◄─────────────────────────────│                            │
     │  { sessionUrl: ".../session/abc123" }                     │
     │                              │                            │
     │  Envoyer l'URL au candidat   │                            │
     │  (email, lien, iframe)       │                            │
     │                              │                            │
     │                              │   GET /api/sessions/abc123 │
     │                              │◄───────────────────────────│
     │                              │───────────────────────────►│
     │                              │    { quiz, questions }     │
     │                              │                            │
     │                              │  POST .../answers          │
     │                              │◄───────────────────────────│
     │                              │                            │
     │                              │  POST .../complete         │
     │                              │◄───────────────────────────│
     │                              │───────────────────────────►│
     │                              │    Vue review (readonly)   │
```

### Lancement en developpement

```bash
# Terminal 1 — API Java
cd saii/quiz-api
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn spring-boot:run

# Terminal 2 — App Angular
cd saii/quiz-app
npm start   # ou : ng serve (port 4200, proxy vers localhost:8080)
```

### Build production

```bash
cd saii/quiz-app
ng build --configuration=production

# Le build genere dans dist/quiz-app/browser/
# Copier dans src/main/resources/static/ du quiz-api pour servir via Spring Boot
```

### Structure

```
quiz-app/
├── angular.json                           # Config build + assets Monaco Editor
├── proxy.conf.json                        # Proxy API pour le dev
├── src/
│   ├── index.html
│   ├── main.ts
│   ├── styles.scss
│   └── app/
│       ├── app.ts                         # Composant racine (router-outlet)
│       ├── app.config.ts                  # Providers (HttpClient, Router)
│       ├── app.routes.ts                  # Routes : / (admin), /session/:token (candidat)
│       ├── services/
│       │   └── session.service.ts         # Client HTTP (listing admin + candidat)
│       ├── components/
│       │   ├── code-editor/
│       │   │   └── code-editor.component.ts  # Wrapper Monaco Editor
│       │   └── text-answer/
│       │       └── text-answer.component.ts  # Textarea pour questions texte
│       └── pages/
│           ├── admin/
│           │   ├── admin.component.ts     # Tableau de bord admin
│           │   ├── admin.component.html
│           │   └── admin.component.scss
│           └── session/
│               ├── session.component.ts   # Orchestrateur (landing/quiz/review)
│               ├── session.component.html # Template avec 3 vues
│               └── session.component.scss # Styles complets
```
