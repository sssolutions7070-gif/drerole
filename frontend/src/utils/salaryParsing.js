// Job postings store salary as a free-text string (recruiters type it themselves), so real data
// looks like "$80,000 - $120,000", "?6,00,000 - ?10,00,000" (rupees), a bare "50000", or plain
// garbage. This is deliberately forgiving: it pulls out a currency symbol (if any) and the
// largest number in the string - the upper bound for a range, or the value itself for a single
// figure - and returns null when nothing numeric can be found at all, so callers can decide how
// to treat "unparseable" (typically: exclude from a salary filter, but still show unfiltered).
const CURRENCY_SYMBOLS = ['$', '₹', '€', '£'];

export function parseSalary(salaryRange) {
  if (!salaryRange || typeof salaryRange !== 'string') return null;

  const currency = CURRENCY_SYMBOLS.find(sym => salaryRange.includes(sym)) || null;

  const numbers = (salaryRange.match(/\d[\d,.]*/g) || [])
    .map(n => parseFloat(n.replace(/,/g, '')))
    .filter(n => !isNaN(n) && n > 0);

  if (numbers.length === 0) return null;

  return { currency, value: Math.max(...numbers) };
}

// Distinct currency symbols actually present across a list of jobs, for populating a filter
// dropdown from real data rather than a hardcoded, possibly-wrong list of currencies.
export function getUniqueSalaryCurrencies(jobs) {
  const currencies = new Set();
  jobs.forEach(job => {
    const parsed = parseSalary(job.salaryRange);
    if (parsed && parsed.currency) currencies.add(parsed.currency);
  });
  return Array.from(currencies);
}
