import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

/**
 * Prérequis technique envoyé à l'API de matching.
 */
export interface TechPrerequisite {
  technology: string;
  seniority: string;
}

/**
 * Requête de matching pour générer un quiz.
 */
export interface MatchRequest {
  jobTitle: string;
  prerequisites: TechPrerequisite[];
  maxQuestions: number;
}

/**
 * Mapping des niveaux du progiciel RH vers les codes séniorité de l'API.
 * Format attendu : "Niveau 1 (Junior)", "Niveau 2 (Confirmé)", "Niveau 3 (Senior)"
 */
const SENIORITY_MAP: Record<string, string> = {
  '1': 'junior',
  '2': 'confirme',
  '3': 'senior',
};

const DEFAULT_SENIORITY = 'confirme';

/**
 * Service client Angular pour générer des PDF de quiz via l'API quiz-api.
 *
 * Conçu pour être utilisé depuis une application Angular tierce
 * qui possède les données brutes du progiciel RH (chaîne de prérequis
 * séparés par des points-virgules et niveau de séniorité textuel).
 *
 * Exemple d'utilisation :
 * ```typescript
 * quizPdfService.generateAndOpen(
 *   'Dev Java Senior',
 *   'Java 11;Spring Boot 2.1;Angular 8',
 *   'Niveau 2 (Confirmé)',
 * );
 * ```
 */
@Injectable({ providedIn: 'root' })
export class QuizPdfService {
  private readonly http = inject(HttpClient);

  /**
   * Génère et ouvre le PDF d'un quiz à partir des données brutes du progiciel RH.
   *
   * @param jobTitle       intitulé du poste (ex: "Dev Java Senior")
   * @param prerequisites  chaîne brute séparée par des ; (ex: "Java 11;Spring Boot 2.1;Angular 8")
   * @param seniorityLabel niveau brut (ex: "Niveau 2 (Confirmé)")
   * @param maxQuestions   nombre max de questions (défaut : 20)
   */
  generateAndOpen(
    jobTitle: string,
    prerequisites: string,
    seniorityLabel: string,
    maxQuestions = 20,
  ): void {
    const seniority = parseSeniority(seniorityLabel);
    const prereqs = parsePrerequisites(prerequisites, seniority);

    const body: MatchRequest = { jobTitle, prerequisites: prereqs, maxQuestions };

    this.http
      .post('/api/quiz/match/pdf', body, { responseType: 'blob' })
      .subscribe((blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url);
      });
  }
}

/**
 * "Niveau 2 (Confirmé)" → "confirme"
 * Extrait le chiffre du niveau et le mappe vers le code séniorité API.
 */
export function parseSeniority(label: string): string {
  const match = label.match(/Niveau\s+(\d)/i);
  const level = match?.[1] ?? '2';
  return SENIORITY_MAP[level] ?? DEFAULT_SENIORITY;
}

/**
 * "Java 11;Spring Boot 2.1;GiT;Jira/Confluence10" →
 * [{ technology: "Java", seniority: "confirme" }, ...]
 *
 * Supprime les versions numériques en fin de nom (espaces optionnels + chiffres/points).
 */
export function parsePrerequisites(raw: string, seniority: string): TechPrerequisite[] {
  return raw
    .split(';')
    .map((s) => s.trim())
    .filter((s) => s.length > 0)
    .map((s) => ({
      technology: s.replace(/\s*[\d.]+$/, '').trim(),
      seniority,
    }));
}
