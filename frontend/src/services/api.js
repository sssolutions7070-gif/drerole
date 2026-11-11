import axios from 'axios';

import { API_BASE_URL } from '../config';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add request interceptor to include auth token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Paths a logged-out visitor can be on; a 401 here must not force a redirect, since that would
// reload the very page it's already on (see handleResponseError).
export const PUBLIC_PATHS = ['/login', '/register', '/recruiter/register', '/forgot-password', '/reset-password'];

export const handleResponseError = (error) => {
  if (error.response?.status === 401 && !PUBLIC_PATHS.includes(window.location.pathname)) {
    // Token expired or invalid
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    window.location.href = '/login';
  }
  return Promise.reject(error);
};

// Add response interceptor for error handling
api.interceptors.response.use((response) => response, handleResponseError);

// Job Rating API calls
export const jobRatingAPI = {
  // Rate a job
  rateJob: (jobId, userId, ratingData) =>
    api.post(`/jobs/${jobId}/rate/${userId}`, ratingData),

  // Get job ratings
  getJobRatings: (jobId) =>
    api.get(`/jobs/${jobId}/ratings`),

  // Get user's rating for a job
  getUserRating: (jobId, userId) =>
    api.get(`/jobs/${jobId}/rating/user/${userId}`),

  // Get job rating stats
  getJobRatingStats: (jobId) =>
    api.get(`/jobs/${jobId}/rating/stats`),

  // Delete rating
  deleteRating: (ratingId) =>
    api.delete(`/jobs/ratings/${ratingId}`),
};

// Self-service account data export / deletion
export const accountAPI = {
  exportMyData: (userId) =>
    api.get(`/users/${userId}/data-export`),

  deleteMyAccount: (userId, password) =>
    api.delete(`/users/${userId}/account`, { data: { password } }),

  getSessions: (userId) =>
    api.get(`/users/${userId}/sessions`),

  revokeSession: (userId, sessionId) =>
    api.delete(`/users/${userId}/sessions/${sessionId}`),

  getProfileViews: (userId) =>
    api.get(`/users/${userId}/profile-views`),
};

// Google Calendar sync - connecting/checking status/disconnecting is always about the signed-in
// caller (resolved server-side from the auth token), so none of these take a userId.
export const calendarAPI = {
  getGoogleConnectUrl: () =>
    api.get('/calendar/google/connect'),

  getGoogleStatus: () =>
    api.get('/calendar/google/status'),

  disconnectGoogle: () =>
    api.delete('/calendar/google'),
};

// Saved Jobs API calls
export const savedJobsAPI = {
  // List a user's saved jobs
  getSavedJobs: (userId) =>
    api.get(`/users/${userId}/saved-jobs`),

  // Whether a specific job is already saved by this user
  isJobSaved: (userId, jobId) =>
    api.get(`/users/${userId}/saved-jobs/${jobId}/status`),

  // Bookmark a job
  saveJob: (userId, jobId) =>
    api.post(`/users/${userId}/saved-jobs/${jobId}`),

  // Remove a bookmark
  unsaveJob: (userId, jobId) =>
    api.delete(`/users/${userId}/saved-jobs/${jobId}`),
};

// Messaging API calls (chat between a candidate and the recruiter behind their application)
export const messagesAPI = {
  getThread: (applicationId) =>
    api.get(`/applications/${applicationId}/messages`),

  sendMessage: (applicationId, body) =>
    api.post(`/applications/${applicationId}/messages`, { body }),

  markRead: (applicationId) =>
    api.put(`/applications/${applicationId}/messages/read`),

  getUnreadCount: (userId) =>
    api.get(`/messages/unread-count/${userId}`),
};

// Interview pipeline API calls (a sequence of rounds per application, each with its own
// video/code-editor room - WebRTC signaling itself happens over a separate WebSocket)
export const interviewRoundsAPI = {
  schedule: (applicationId, title, roundType, scheduledAt) =>
    api.post(`/applications/${applicationId}/interview-rounds`, { title, roundType, scheduledAt }),

  propose: (applicationId, title, roundType, slots) =>
    api.post(`/applications/${applicationId}/interview-rounds/propose`, { title, roundType, slots }),

  confirmSlot: (roundId, scheduledAt) =>
    api.post(`/interview-rounds/${roundId}/confirm-slot`, { scheduledAt }),

  list: (applicationId) =>
    api.get(`/applications/${applicationId}/interview-rounds`),

  getJoinInfo: (roundId) =>
    api.get(`/interview-rounds/${roundId}/join`),

  updateStatus: (roundId, status) =>
    api.put(`/interview-rounds/${roundId}/status`, { status }),

  reschedule: (roundId, scheduledAt) =>
    api.put(`/interview-rounds/${roundId}/reschedule`, { scheduledAt }),

  downloadCalendarInvite: (roundId) =>
    api.get(`/interview-rounds/${roundId}/calendar.ics`, { responseType: 'blob' }),

  uploadRecording: (roundId, file) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post(`/interview-rounds/${roundId}/recording`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  getRecordingUrl: (roundId) =>
    `/api/interview-rounds/${roundId}/recording`,

  getCopilotProbes: (roundId, data) =>
    api.post(`/interview-rounds/${roundId}/copilot-probes`, data),

  getRecordingInsights: (roundId) =>
    api.get(`/interview-rounds/${roundId}/recording-insights`),

  // The candidate's own rating of this specific round (not the company overall - see
  // interviewExperienceAPI for that). Feeds the recruiter's public accountability stats.
  submitFeedback: (roundId, rating, comment) =>
    api.post(`/interview-rounds/${roundId}/feedback`, { rating, comment }),

  getFeedback: (roundId) =>
    api.get(`/interview-rounds/${roundId}/feedback`),
};

// Structured interview scorecards - recruiter-only
export const scorecardAPI = {
  submit: (roundId, scorecard) =>
    api.put(`/interview-rounds/${roundId}/scorecard`, scorecard),

  get: (roundId) =>
    api.get(`/interview-rounds/${roundId}/scorecard`),

  getForApplication: (applicationId) =>
    api.get(`/applications/${applicationId}/scorecards`),

  generateDraft: (roundId, payload) =>
    api.post(`/interview-rounds/${roundId}/scorecard/generate-draft`, payload),
};

// Talent pool API calls - every candidate a recruiter has ever received an application from,
// searchable, with private notes/tags independent of any one job posting
export const talentPoolAPI = {
  search: (recruiterId, search) =>
    api.get(`/recruiters/${recruiterId}/talent-pool`, { params: search ? { search } : {} }),

  upsertNote: (recruiterId, candidateUserId, notes, tags) =>
    api.put(`/recruiters/${recruiterId}/talent-pool/${candidateUserId}/note`, { notes, tags }),
};

// A company's hiring team - teammates get the same access as the account owner
export const recruiterTeamAPI = {
  list: (recruiterId) =>
    api.get(`/recruiters/${recruiterId}/team`),

  invite: (recruiterId, email, role) =>
    api.post(`/recruiters/${recruiterId}/team`, { email, role }),

  remove: (recruiterId, membershipId) =>
    api.delete(`/recruiters/${recruiterId}/team/${membershipId}`),
};

// Job offer API calls - the terminal step of the hiring pipeline
export const offerAPI = {
  send: (applicationId, title, salaryOffered, startDate, additionalTerms) =>
    api.post(`/applications/${applicationId}/offer`, { title, salaryOffered, startDate, additionalTerms }),

  get: (applicationId) =>
    api.get(`/applications/${applicationId}/offer`),

  getLetter: (applicationId) =>
    api.get(`/applications/${applicationId}/offer/letter`),

  accept: (applicationId, signatureName, signatureDataUrl) =>
    api.post(`/applications/${applicationId}/offer/accept`, { signatureName, signatureDataUrl }),

  decline: (applicationId, reason) =>
    api.post(`/applications/${applicationId}/offer/decline`, { reason }),

  withdraw: (applicationId) =>
    api.post(`/applications/${applicationId}/offer/withdraw`),
};

// AI mock-interview practice API calls
export const mockInterviewAPI = {
  start: (userId, jobId) =>
    api.post(`/users/${userId}/mock-interviews`, { jobId }),

  list: (userId) =>
    api.get(`/users/${userId}/mock-interviews`),

  get: (userId, sessionId) =>
    api.get(`/users/${userId}/mock-interviews/${sessionId}`),

  answer: (userId, sessionId, questionId, answer) =>
    api.put(`/users/${userId}/mock-interviews/${sessionId}/questions/${questionId}/answer`, { answer }),

  // Score trend across every past session plus the lowest-scoring answered questions overall.
  getHistory: (userId) =>
    api.get(`/users/${userId}/mock-interviews/history`),
};

// Interview experience reports - what interviewing at a company was actually like
export const interviewExperienceAPI = {
  submit: (userId, recruiterId, difficultyRating, outcome, processDescription) =>
    api.post(`/users/${userId}/interview-experiences/${recruiterId}`, { difficultyRating, outcome, processDescription }),

  getMine: (userId, recruiterId) =>
    api.get(`/users/${userId}/interview-experiences/${recruiterId}`),
};

// A candidate's verified skill badges - public/shareable
export const skillBadgeAPI = {
  list: (userId) =>
    api.get(`/users/${userId}/badges`),
};

// Salary benchmarking from posted job ranges - public, no auth required
export const salaryInsightsAPI = {
  get: (title, location, experienceLevel) =>
    api.get('/salary-insights', { params: { title, location, experienceLevel } }),

  // Personalized to the caller's own offer - unlike get() above, this needs a logged-in user.
  getNegotiationAdvice: (jobTitle, location, experienceLevel, offeredSalary) =>
    api.post('/salary-insights/negotiation-advice', { jobTitle, location, experienceLevel, offeredSalary }),
};

// Browsing the open-to-work candidate pool - not scoped to one recruiter/company
export const candidateDiscoveryAPI = {
  discover: (skill, location) =>
    api.get('/candidates/discover', { params: { skill, location } }),

  aiSearch: (query) =>
    api.get('/candidates/discover/ai-search', { params: { query } }),

  recordView: (candidateUserId) =>
    api.post(`/candidates/${candidateUserId}/views`),
};

// Saved candidate discovery searches and alert settings
export const savedCandidateSearchAPI = {
  list: (recruiterId) =>
    api.get(`/recruiters/${recruiterId}/saved-searches`),

  create: (recruiterId, searchData) =>
    api.post(`/recruiters/${recruiterId}/saved-searches`, searchData),

  delete: (recruiterId, searchId) =>
    api.delete(`/recruiters/${recruiterId}/saved-searches/${searchId}`),

  toggleAlert: (recruiterId, searchId, alertEnabled) =>
    api.put(`/recruiters/${recruiterId}/saved-searches/${searchId}/alert`, { alertEnabled }),

  checkMatches: (recruiterId, searchId) =>
    api.post(`/recruiters/${recruiterId}/saved-searches/${searchId}/check`),
};

// Multi-step automated outreach campaigns to a sourced candidate - step 0 sends immediately,
// later steps fire once their delay elapses, and the whole sequence stops once the candidate
// replies (see OutreachSequenceService)
export const outreachSequenceAPI = {
  list: (recruiterId) =>
    api.get(`/recruiters/${recruiterId}/outreach-sequences`),

  start: (recruiterId, candidateUserId, steps) =>
    api.post(`/recruiters/${recruiterId}/outreach-sequences`, { candidateUserId, steps }),

  stop: (recruiterId, sequenceId) =>
    api.delete(`/recruiters/${recruiterId}/outreach-sequences/${sequenceId}`),
};

// Platform-wide growth and funnel metrics for the admin dashboard
export const platformAnalyticsAPI = {
  getOverview: () =>
    api.get('/admin/analytics/overview'),
};

// API keys - let a recruiter's own integration (script, ATS, Zapier) call the API as themselves
export const apiKeyAPI = {
  list: (recruiterId) =>
    api.get(`/recruiters/${recruiterId}/api-keys`),

  generate: (recruiterId, label) =>
    api.post(`/recruiters/${recruiterId}/api-keys`, { label }),

  revoke: (recruiterId, keyId) =>
    api.delete(`/recruiters/${recruiterId}/api-keys/${keyId}`),
};

// Webhooks - an HMAC-signed POST to a recruiter's own URL when a chosen event happens
export const webhookAPI = {
  list: (recruiterId) =>
    api.get(`/recruiters/${recruiterId}/webhooks`),

  register: (recruiterId, url, eventType) =>
    api.post(`/recruiters/${recruiterId}/webhooks`, { url, eventType }),

  remove: (recruiterId, webhookId) =>
    api.delete(`/recruiters/${recruiterId}/webhooks/${webhookId}`),

  testPing: (recruiterId, webhookId) =>
    api.post(`/recruiters/${recruiterId}/webhooks/${webhookId}/test`),
};

// Chat between a recruiter and a candidate outside of any application - reaching out to someone
// found via candidateDiscoveryAPI, before any application exists
export const directMessageAPI = {
  getThread: (recruiterId, candidateUserId) =>
    api.get(`/recruiters/${recruiterId}/direct-messages/${candidateUserId}`),

  send: (recruiterId, candidateUserId, body) =>
    api.post(`/recruiters/${recruiterId}/direct-messages/${candidateUserId}`, { body }),

  listForRecruiter: (recruiterId) =>
    api.get(`/recruiters/${recruiterId}/direct-messages`),

  listForCandidate: (userId) =>
    api.get(`/users/${userId}/direct-messages`),
};

// "Verified Employer" - a recruiter submits proof of their business, an admin reviews it
export const recruiterVerificationAPI = {
  getStatus: (recruiterId) =>
    api.get(`/recruiters/${recruiterId}/verification`),

  submit: (recruiterId, file) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post(`/recruiters/${recruiterId}/verification`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
};

export const adminVerificationAPI = {
  listPending: () =>
    api.get('/admin/verifications/pending'),

  downloadDocument: (recruiterId) =>
    api.get(`/admin/verifications/${recruiterId}/document`, { responseType: 'blob' }),

  approve: (recruiterId) =>
    api.post(`/admin/verifications/${recruiterId}/approve`),

  reject: (recruiterId, reason) =>
    api.post(`/admin/verifications/${recruiterId}/reject`, { reason }),
};

// Persisted notification feed backing the bell icon (separate from the transient toast socket)
export const notificationAPI = {
  list: (userId) =>
    api.get(`/users/${userId}/notifications`),

  unreadCount: (userId) =>
    api.get(`/users/${userId}/notifications/unread-count`),

  markRead: (userId, notificationId) =>
    api.post(`/users/${userId}/notifications/${notificationId}/read`),

  markAllRead: (userId) =>
    api.post(`/users/${userId}/notifications/read-all`),
};

// Resume Matching API calls
export const resumeMatchingAPI = {
  // Get applications sorted by match percentage
  getApplicationsSortedByMatch: (jobId, recruiterId) =>
    api.get(`/resume-matching/job/${jobId}/applications/sorted?recruiterId=${recruiterId}`),

  // Get detailed resume analysis for an application
  getResumeAnalysis: (applicationId) =>
    api.get(`/resume-matching/application/${applicationId}/resume-analysis`),
};

// Enhanced Job Application API calls
export const jobApplicationAPI = {
  // Apply for job with resume
  applyWithResume: (jobId, userId, applicationData) =>
    api.post(`/jobs/${jobId}/apply-with-resume/${userId}`, applicationData),
};

// Enhanced Resume API calls
export const resumeAPI = {
  // Upload resume
  uploadResume: (userId, file) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post(`/resumes/upload/${userId}`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },

  // Get user resumes
  getUserResumes: (userId) =>
    api.get(`/resumes/user/${userId}`),

  // Get resume analysis
  getResumeAnalysis: (resumeId) =>
    api.get(`/resumes/${resumeId}/analysis`),

  // Get latest resume
  getLatestResume: (userId) =>
    api.get(`/resumes/user/${userId}/latest`),
};

// Enhanced Application Status API calls
export const applicationStatusAPI = {
  // Get user applications
  getUserApplications: (userId) =>
    api.get(`/applications/user/${userId}`),

  // "My Insights" - a candidate's own status breakdown, average match score, most commonly
  // missing skills across their applications, and profile view count.
  getUserInsights: (userId) =>
    api.get(`/applications/user/${userId}/insights`),

  // Get job applications
  getJobApplications: (jobId) =>
    api.get(`/applications/job/${jobId}`),

  // Update application status
  updateStatus: (applicationId, recruiterId, statusData) =>
    api.put(`/applications/${applicationId}/status?recruiterId=${recruiterId}`, statusData),

  // Mark as viewed
  markAsViewed: (applicationId, recruiterId) =>
    api.put(`/applications/${applicationId}/view?recruiterId=${recruiterId}`),

  // Shortlist application
  shortlist: (applicationId, recruiterId, notes) =>
    api.put(`/applications/${applicationId}/shortlist?recruiterId=${recruiterId}`, { notes }),

  // Schedule interview
  scheduleInterview: (applicationId, recruiterId, interviewData) =>
    api.put(`/applications/${applicationId}/schedule-interview?recruiterId=${recruiterId}`, interviewData),

  // Reject application
  reject: (applicationId, recruiterId, rejectionReason) =>
    api.put(`/applications/${applicationId}/reject?recruiterId=${recruiterId}`, { rejectionReason }),

  // Send offer
  sendOffer: (applicationId, recruiterId, offerDetails) =>
    api.put(`/applications/${applicationId}/send-offer?recruiterId=${recruiterId}`, { offerDetails }),

  // Candidate withdraws their own application - distinct from offerAPI.withdraw, which
  // withdraws an already-sent offer
  withdrawApplication: (applicationId) =>
    api.post(`/applications/${applicationId}/withdraw`),

  // AI-generated, constructive explanation grounded in this application's own match data -
  // only meaningful once its status is REJECTED.
  getRejectionExplanation: (applicationId) =>
    api.get(`/applications/${applicationId}/rejection-explanation`),
};

// Enhanced Job Management API calls
export const jobManagementAPI = {
  // Create job posting
  createJob: (recruiterId, jobData) =>
    api.post('/recruiters/jobs', { ...jobData, recruiterId }),

  // Update job posting
  updateJob: (jobId, recruiterId, jobData) =>
    api.put(`/recruiters/jobs/${jobId}`, { ...jobData, recruiterId }),

  // Delete job posting
  deleteJob: (jobId, recruiterId) =>
    api.delete(`/recruiters/jobs/${jobId}?recruiterId=${recruiterId}`),

  // Get recruiter jobs
  getRecruiterJobs: (recruiterId) =>
    api.get(`/recruiters/jobs/recruiter/${recruiterId}`),

  // Get job applications (recruiter)
  getJobApplications: (jobId, recruiterId) =>
    api.get(`/recruiters/jobs/${jobId}/applications?recruiterId=${recruiterId}`),

  // Clone a job posting's content as a fresh pending draft
  duplicateJob: (jobId, recruiterId) =>
    api.post(`/recruiters/jobs/${jobId}/duplicate`, { recruiterId }),

  // Download a job's applicants as a CSV file
  exportApplicationsCsv: (jobId, recruiterId) =>
    api.get(`/recruiters/jobs/${jobId}/applications/export?recruiterId=${recruiterId}`, {
      responseType: 'blob',
    }),
};

// Job Recommendation API calls
export const jobRecommendationAPI = {
  // Get job recommendations for user
  getRecommendations: (userId) =>
    api.get(`/jobs/recommendations/${userId}`),

  // Get detailed recommendations
  getDetailedRecommendations: (userId) =>
    api.get(`/jobs/recommendations/${userId}/detailed`),

  // Get recommendations by skills
  getRecommendationsBySkills: (skills) =>
    api.get('/jobs/recommendations/skills', { params: { skills } }),

  // Search jobs
  searchJobs: (keyword) =>
    api.get('/jobs/search', { params: { keyword } }),
};

// Authentication API calls
export const authAPI = {
  // Register user
  register: (userData) =>
    api.post('/auth/register', userData),

  // Login user
  login: (credentials) =>
    api.post('/auth/login', credentials),

  // Logout user
  logout: () =>
    api.post('/auth/logout'),

  // Get current user
  getCurrentUser: () =>
    api.get('/auth/me'),

  // Register recruiter
  registerRecruiter: (recruiterData) =>
    api.post('/recruiters/register', recruiterData),

  // Get recruiter profile
  getRecruiterProfile: (userId) =>
    api.get(`/recruiters/user/${userId}`),

  // Update recruiter profile
  updateRecruiterProfile: (userId, profileData) =>
    api.put(`/recruiters/user/${userId}/profile`, profileData),
};

// Utility API calls
export const utilityAPI = {
  // Health check
  healthCheck: () =>
    api.get('/health'),
};

// Default export for backward compatibility
export default api;

// Convenience function for applying to jobs with resume
export const applyForJobWithResume = (jobId, userId, applicationData) => {
  return jobApplicationAPI.applyWithResume(jobId, userId, applicationData);
};

// Convenience function for getting sorted applications
export const getSortedApplications = (jobId, recruiterId) => {
  return resumeMatchingAPI.getApplicationsSortedByMatch(jobId, recruiterId);
};

// Convenience function for uploading resume and applying
export const uploadResumeAndApply = async (userId, jobId, file, applicationData) => {
  try {
    // First upload the resume
    const uploadResponse = await resumeAPI.uploadResume(userId, file);
    
    // Then apply with the resume data
    const applicationResponse = await jobApplicationAPI.applyWithResume(jobId, userId, {
      ...applicationData,
      resumeFilePath: uploadResponse.data.filePath,
      resumeParsedText: uploadResponse.data.parsedText || 'Resume content extracted'
    });
    
    return applicationResponse;
  } catch (error) {
    throw error;
  }
};