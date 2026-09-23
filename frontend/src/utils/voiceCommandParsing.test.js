import { describe, it, expect } from 'vitest';
import { extractStarRating, extractJobOrdinal, resolveJobFromCommand } from './voiceCommandParsing';

describe('extractStarRating', () => {
  it('parses a digit rating followed by "star(s)"', () => {
    expect(extractStarRating('rate this 5 stars')).toBe(5);
    expect(extractStarRating('give it 3 star')).toBe(3);
  });

  it('parses a spelled-out rating followed by "star(s)"', () => {
    expect(extractStarRating('rate job one five stars')).toBe(5);
    expect(extractStarRating('two star review')).toBe(2);
  });

  it('ignores digits that are not adjacent to the word "star"', () => {
    expect(extractStarRating('rate job 5')).toBeNull();
    expect(extractStarRating('apply to job 2')).toBeNull();
  });

  it('returns null when there is no rating at all', () => {
    expect(extractStarRating('apply to the first job')).toBeNull();
  });

  it('rejects an out-of-range digit rating', () => {
    expect(extractStarRating('9 star hotel')).toBeNull();
  });
});

describe('extractJobOrdinal', () => {
  it('parses "job N"', () => {
    expect(extractJobOrdinal('apply to job 2')).toBe(1);
    expect(extractJobOrdinal('rate job number 3')).toBe(2);
  });

  it('parses a spelled-out number directly after "job"', () => {
    expect(extractJobOrdinal('apply to job two')).toBe(1);
  });

  it('parses ordinal words', () => {
    expect(extractJobOrdinal('apply to the first job')).toBe(0);
    expect(extractJobOrdinal('rate the second one five stars')).toBe(1);
  });

  it('does not confuse a star rating for a job number', () => {
    // "job" is not adjacent to any of these numbers, so none should be misread as the job index.
    expect(extractJobOrdinal('rate this 5 stars')).toBeNull();
  });

  it('returns null when no job reference is present', () => {
    expect(extractJobOrdinal('show me all jobs')).toBeNull();
  });
});

describe('resolveJobFromCommand', () => {
  const jobs = [
    { id: 1, title: 'Backend Developer' },
    { id: 2, title: 'Frontend Developer' },
    { id: 3, title: 'Data Scientist' },
  ];
  const extractTitle = (command) => {
    if (command.includes('data scientist')) return 'data scientist';
    if (command.includes('frontend')) return 'frontend';
    return null;
  };

  it('resolves by ordinal first', () => {
    expect(resolveJobFromCommand('apply to job 2', jobs, extractTitle)).toEqual(jobs[1]);
    expect(resolveJobFromCommand('apply to the first job', jobs, extractTitle)).toEqual(jobs[0]);
  });

  it('falls back to a title match when there is no ordinal', () => {
    expect(resolveJobFromCommand('apply to the data scientist job', jobs, extractTitle)).toEqual(jobs[2]);
  });

  it('returns null for an out-of-range ordinal', () => {
    expect(resolveJobFromCommand('apply to job 9', jobs, extractTitle)).toBeNull();
  });

  it('returns null when there is nothing to match and no title extractor is given', () => {
    expect(resolveJobFromCommand('apply to that job', jobs)).toBeNull();
  });

  it('returns null when the job list is empty', () => {
    expect(resolveJobFromCommand('apply to job 1', [], extractTitle)).toBeNull();
  });
});
