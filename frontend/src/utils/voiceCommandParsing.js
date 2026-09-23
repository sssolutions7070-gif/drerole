// Pure parsing helpers for the voice assistant's "apply to job N" and "rate job N X stars"
// commands. Kept separate from VoiceAssistant.jsx so they can be unit tested without any
// speech/DOM setup.

const NUMBER_WORDS = {
  one: 1, two: 2, three: 3, four: 4, five: 5,
  first: 1, second: 2, third: 3, fourth: 4, fifth: 5,
};

/** 1-5 star rating mentioned in the command (e.g. "5 stars", "five star rating"), or null. */
export const extractStarRating = (command) => {
  const lower = command.toLowerCase();

  const digitMatch = lower.match(/([1-5])\s*star/);
  if (digitMatch) {
    return parseInt(digitMatch[1], 10);
  }

  const wordMatch = lower.match(/\b(one|two|three|four|five)\b\s*star/);
  if (wordMatch) {
    return NUMBER_WORDS[wordMatch[1]];
  }

  return null;
};

/** 0-based index of the job referred to (e.g. "job 2", "job number 2", "the second job"), or null. */
export const extractJobOrdinal = (command) => {
  const lower = command.toLowerCase();

  const digitMatch = lower.match(/job\s*(?:number|#)?\s*(\d+)/);
  if (digitMatch) {
    const n = parseInt(digitMatch[1], 10);
    return n >= 1 ? n - 1 : null;
  }

  const wordAfterJobMatch = lower.match(/job\s+(one|two|three|four|five)\b/);
  if (wordAfterJobMatch) {
    return NUMBER_WORDS[wordAfterJobMatch[1]] - 1;
  }

  const ordinalMatch = lower.match(/\b(first|second|third|fourth|fifth)\b/);
  if (ordinalMatch) {
    return NUMBER_WORDS[ordinalMatch[1]] - 1;
  }

  return null;
};

/**
 * Resolves which job a command refers to from the list last shown to the user: by ordinal
 * ("job 2", "the first one") first, then by a title word appearing in the command.
 */
export const resolveJobFromCommand = (command, jobs, extractTitle) => {
  if (!jobs || jobs.length === 0) {
    return null;
  }

  const ordinal = extractJobOrdinal(command);
  if (ordinal !== null) {
    return jobs[ordinal] || null;
  }

  const title = typeof extractTitle === 'function' ? extractTitle(command) : null;
  if (title) {
    const lowerTitle = title.toLowerCase();
    return jobs.find((job) => (job.title || '').toLowerCase().includes(lowerTitle)) || null;
  }

  return null;
};
