import { parseSeniority, parsePrerequisites } from './quiz-pdf.service';

describe('parseSeniority', () => {
  it('should parse "Niveau 1 (Junior)" to "junior"', () => {
    expect(parseSeniority('Niveau 1 (Junior)')).toBe('junior');
  });

  it('should parse "Niveau 2 (Confirmé)" to "confirme"', () => {
    expect(parseSeniority('Niveau 2 (Confirmé)')).toBe('confirme');
  });

  it('should parse "Niveau 3 (Senior)" to "senior"', () => {
    expect(parseSeniority('Niveau 3 (Senior)')).toBe('senior');
  });

  it('should default to "confirme" for unknown level number', () => {
    expect(parseSeniority('Niveau 9 (Inconnu)')).toBe('confirme');
  });

  it('should default to "confirme" for unparseable input', () => {
    expect(parseSeniority('quelque chose')).toBe('confirme');
  });
});

describe('parsePrerequisites', () => {
  it('should split by semicolon and assign seniority', () => {
    const result = parsePrerequisites('Java;Angular', 'junior');
    expect(result).toEqual([
      { technology: 'Java', seniority: 'junior' },
      { technology: 'Angular', seniority: 'junior' },
    ]);
  });

  it('should strip trailing version numbers', () => {
    const result = parsePrerequisites('Java 11;Spring Boot 2.1;Angular 8', 'confirme');
    expect(result).toEqual([
      { technology: 'Java', seniority: 'confirme' },
      { technology: 'Spring Boot', seniority: 'confirme' },
      { technology: 'Angular', seniority: 'confirme' },
    ]);
  });

  it('should handle versions with dots', () => {
    const result = parsePrerequisites('Hibernate 5.3;PostgreSQL 10', 'senior');
    expect(result).toEqual([
      { technology: 'Hibernate', seniority: 'senior' },
      { technology: 'PostgreSQL', seniority: 'senior' },
    ]);
  });

  it('should preserve names without version numbers', () => {
    const result = parsePrerequisites('GiT;Gitlab;Gitlab-ci', 'confirme');
    expect(result).toEqual([
      { technology: 'GiT', seniority: 'confirme' },
      { technology: 'Gitlab', seniority: 'confirme' },
      { technology: 'Gitlab-ci', seniority: 'confirme' },
    ]);
  });

  it('should handle mixed names with slash and trailing digits', () => {
    const result = parsePrerequisites('Jira/Confluence10', 'junior');
    expect(result).toEqual([
      { technology: 'Jira/Confluence', seniority: 'junior' },
    ]);
  });

  it('should ignore empty segments from extra semicolons', () => {
    const result = parsePrerequisites('Java;;Angular; ;', 'confirme');
    expect(result).toEqual([
      { technology: 'Java', seniority: 'confirme' },
      { technology: 'Angular', seniority: 'confirme' },
    ]);
  });
});
