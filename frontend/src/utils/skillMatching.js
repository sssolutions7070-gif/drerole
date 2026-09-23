// Mirrors the backend's JobRecommendationService.isSkillMatch so the match percentage and
// badges shown on the Jobs page agree with what the backend actually recommends.

const SKILL_SYNONYM_GROUPS = [
  ['javascript', 'js', 'ecmascript'],
  ['typescript', 'ts'],
  ['node', 'nodejs', 'node.js'],
  ['react', 'reactjs', 'react.js'],
  ['vue', 'vuejs', 'vue.js'],
  ['angular', 'angularjs'],
  ['next', 'nextjs', 'next.js'],
  ['spring boot', 'springboot', 'spring-boot'],
  ['spring', 'springframework'],
  ['python', 'py'],
  ['html', 'html5'],
  ['css', 'css3'],
  ['sass', 'scss'],
  ['rest', 'restapi', 'restful', 'rest api'],
  ['graphql', 'graph ql'],
  ['mongodb', 'mongo'],
  ['postgresql', 'postgres', 'psql'],
  ['mysql', 'my sql'],
  ['docker', 'containerization'],
  ['kubernetes', 'k8s'],
  ['aws', 'amazon web services'],
  ['gcp', 'google cloud', 'google cloud platform'],
  ['azure', 'microsoft azure'],
  ['ci cd', 'cicd', 'ci/cd', 'continuous integration'],
  ['machine learning', 'ml'],
  ['artificial intelligence', 'ai'],
  ['data science', 'ds'],
  ['natural language processing', 'nlp'],
  ['deep learning', 'dl'],
  ['git', 'github', 'version control'],
  ['c sharp', 'c#', 'csharp'],
  ['c plus plus', 'c++', 'cpp'],
  ['golang', 'go'],
  ['dotnet', '.net', 'asp.net'],
  ['ui ux', 'ui/ux', 'user interface design'],
  ['qa', 'quality assurance', 'testing'],
  ['oop', 'object oriented programming'],
  ['api', 'apis'],
  ['sql', 'structured query language'],
  ['nosql', 'no sql'],
];

const SKILL_TO_GROUP = new Map();
for (const group of SKILL_SYNONYM_GROUPS) {
  const set = new Set(group);
  for (const member of group) {
    SKILL_TO_GROUP.set(member, set);
  }
}

const MIN_FUZZY_TOKEN_LENGTH = 5;

const normalizeSkill = (skill) => (skill == null ? '' : String(skill).toLowerCase().trim());

const tokenize = (normalized) =>
  new Set(normalized.split(/[^a-z0-9#+]+/).filter((token) => token.length > 0));

const shareASynonymGroup = (a, b) => {
  const group = SKILL_TO_GROUP.get(a);
  return !!group && group.has(b);
};

// Classic Levenshtein edit distance (insert/delete/substitute).
const levenshteinDistance = (a, b) => {
  let previous = Array.from({ length: b.length + 1 }, (_, j) => j);
  for (let i = 1; i <= a.length; i++) {
    const current = [i];
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      current[j] = Math.min(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost);
    }
    previous = current;
  }
  return previous[b.length];
};

const isTypoOfEachOther = (a, b) => {
  if (a.length < MIN_FUZZY_TOKEN_LENGTH || b.length < MIN_FUZZY_TOKEN_LENGTH) {
    return false;
  }
  const maxLen = Math.max(a.length, b.length);
  const threshold = Math.min(3, Math.floor(maxLen / 5));
  return levenshteinDistance(a, b) <= threshold;
};

/**
 * True when a job's required skill and a candidate's listed skill should count as the same
 * thing. Exact match, then a shared synonym group, then an exact shared word token (so
 * "Spring Boot" matches "spring-boot" without "Java" ever matching "JavaScript" the way naive
 * substring matching used to), then a bounded edit-distance check for typos in longer words.
 */
export const isSkillMatch = (jobSkill, userSkill) => {
  const js = normalizeSkill(jobSkill);
  const us = normalizeSkill(userSkill);
  if (!js || !us) return false;
  if (js === us) return true;
  if (shareASynonymGroup(js, us)) return true;

  const jsTokens = tokenize(js);
  const usTokens = tokenize(us);
  for (const token of jsTokens) {
    if (usTokens.has(token)) return true;
  }
  for (const jsToken of jsTokens) {
    for (const usToken of usTokens) {
      if (shareASynonymGroup(jsToken, usToken) || isTypoOfEachOther(jsToken, usToken)) {
        return true;
      }
    }
  }
  return false;
};

export default isSkillMatch;
