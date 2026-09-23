import { describe, it, expect } from 'vitest';
import { isSkillMatch } from './skillMatching';

describe('isSkillMatch', () => {
  it('matches identical skills regardless of case and whitespace', () => {
    expect(isSkillMatch('Python', 'python')).toBe(true);
    expect(isSkillMatch(' Docker ', 'docker')).toBe(true);
  });

  it('matches known synonyms', () => {
    expect(isSkillMatch('JavaScript', 'JS')).toBe(true);
    expect(isSkillMatch('React', 'ReactJS')).toBe(true);
    expect(isSkillMatch('Kubernetes', 'K8s')).toBe(true);
    expect(isSkillMatch('Spring Boot', 'springboot')).toBe(true);
  });

  it('does not let Java falsely match JavaScript', () => {
    // Regression test: naive substring matching ("javascript".includes("java")) used to
    // treat these as the same skill, which is wrong - they are different languages.
    expect(isSkillMatch('Java', 'JavaScript')).toBe(false);
    expect(isSkillMatch('JavaScript', 'Java')).toBe(false);
  });

  it('does not let a single-letter skill match unrelated skills containing that letter', () => {
    // Regression test: "R" used to match almost every skill via substring containment,
    // since nearly every English word contains the letter "r".
    expect(isSkillMatch('R', 'React')).toBe(false);
    expect(isSkillMatch('R', 'Docker')).toBe(false);
    expect(isSkillMatch('R', 'JavaScript')).toBe(false);
  });

  it('matches multi-word skills on a shared word even outside the synonym list', () => {
    expect(isSkillMatch('Node.js Development', 'Node')).toBe(true);
    expect(isSkillMatch('Senior Python Developer', 'Python')).toBe(true);
  });

  it('tolerates a small typo in a long enough skill', () => {
    expect(isSkillMatch('Kubernetes', 'Kubernets')).toBe(true);
    expect(isSkillMatch('PostgreSQL', 'Postgresql')).toBe(true);
  });

  it('gives short skills no fuzzy leniency', () => {
    expect(isSkillMatch('Go', 'Do')).toBe(false);
    expect(isSkillMatch('C', 'R')).toBe(false);
  });

  it('still matches through punctuation', () => {
    expect(isSkillMatch('Go', 'Go!')).toBe(true);
  });

  it('does not match unrelated long skills', () => {
    expect(isSkillMatch('Kubernetes', 'JavaScript')).toBe(false);
    expect(isSkillMatch('MongoDB', 'PostgreSQL')).toBe(false);
  });

  it('never matches blank or missing skills', () => {
    expect(isSkillMatch('', 'Java')).toBe(false);
    expect(isSkillMatch('Java', '')).toBe(false);
    expect(isSkillMatch(null, 'Java')).toBe(false);
    expect(isSkillMatch('Java', undefined)).toBe(false);
  });
});
