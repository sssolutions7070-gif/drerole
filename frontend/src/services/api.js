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

  list: (applicationId) =>
    api.get(`/applications/${applicationId}/interview-rounds`),

  getJoinInfo: (roundId) =>
    api.get(`/interview-rounds/${roundId}/join`),

  updateStatus: (roundId, status) =>
    api.put(`/interview-rounds/${roundId}/status`, { status }),

  reschedule: (roundId, scheduledAt) =>
    api.put(`/interview-rounds/${roundId}/reschedule`, { scheduledAt }),
};

// Structured interview scorecards - recruiter-only
export const scorecardAPI = {
  submit: (roundId, scorecard) =>
    api.put(`/interview-rounds/${roundId}/scorecard`, scorecard),

  get: (roundId) =>
    api.get(`/interview-rounds/${roundId}/scorecard`),

  getForApplication: (applicationId) =>
    api.get(`/applications/${applicationId}/scorecards`),
};

// Talent pool API calls - every candidate a recruiter has ever received an application from,
// searchable, with private notes/tags independent of any one job posting
export const talentPoolAPI = {
  search: (recruiterId, search) =>
    api.get(`/recruiters/${recruiterId}/talent-pool`, { params: search ? { search } : {} }),

  upsertNote: (recruiterId, candidateUserId, notes, tags) =>
    api.put(`/recruiters/${recruiterId}/talent-pool/${candidateUserId}/note`, { notes, tags }),
};

// Job offer API calls - the terminal step of the hiring pipeline
export const offerAPI = {
  send: (applicationId, title, salaryOffered, startDate, additionalTerms) =>
    api.post(`/applications/${applicationId}/offer`, { title, salaryOffered, startDate, additionalTerms }),

  get: (applicationId) =>
    api.get(`/applications/${applicationId}/offer`),

  accept: (applicationId, signatureName) =>
    api.post(`/applications/${applicationId}/offer/accept`, { signatureName }),

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