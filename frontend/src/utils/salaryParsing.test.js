import { describe, it, expect } from 'vitest';
import { parseSalary, getUniqueSalaryCurrencies } from './salaryParsing';

describe('parseSalary', () => {
  it('parses a USD range and takes the upper bound', () => {
    expect(parseSalary('$80,000 - $120,000')).toEqual({ currency: '$', value: 120000 });
  });

  it('parses a rupee range with Indian-style comma grouping', () => {
    expect(parseSalary('₹6,00,000 - ₹10,00,000')).toEqual({ currency: '₹', value: 1000000 });
  });

  it('parses a bare number with no currency symbol', () => {
    expect(parseSalary('50000')).toEqual({ currency: null, value: 50000 });
  });

  it('takes the larger number regardless of order in the string', () => {
    expect(parseSalary('up to $100,000 (min $60,000)')).toEqual({ currency: '$', value: 100000 });
  });

  it('returns null for text with no digits at all', () => {
    expect(parseSalary('asdfghjkl')).toBeNull();
    expect(parseSalary('Competitive')).toBeNull();
  });

  it('returns null for empty, null or non-string input', () => {
    expect(parseSalary('')).toBeNull();
    expect(parseSalary(null)).toBeNull();
    expect(parseSalary(undefined)).toBeNull();
    expect(parseSalary(50000)).toBeNull();
  });

  it('ignores a lone zero (not a meaningful salary figure)', () => {
    expect(parseSalary('$0')).toBeNull();
  });
});

describe('getUniqueSalaryCurrencies', () => {
  it('collects distinct currency symbols actually present in the job list', () => {
    const jobs = [
      { salaryRange: '$80,000 - $120,000' },
      { salaryRange: '$90,000 - $130,000' },
      { salaryRange: '₹6,00,000 - ₹10,00,000' },
      { salaryRange: '50000' },
      { salaryRange: 'asdfghjkl' },
    ];
    expect(getUniqueSalaryCurrencies(jobs)).toEqual(['$', '₹']);
  });

  it('returns an empty array when no job has a parseable currency', () => {
    expect(getUniqueSalaryCurrencies([{ salaryRange: '50000' }, { salaryRange: 'n/a' }])).toEqual([]);
  });
});
