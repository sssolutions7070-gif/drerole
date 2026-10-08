// src/pages/RecruiterDashboard.jsx
import React, { useState, useEffect } from 'react';
import { 
  Plus, Users, Eye, Edit, Trash2, Briefcase, Building, 
  Mail, Phone, Calendar, BookOpen, BarChart3, X, Upload,
  MapPin, CheckCircle, XCircle, Clock, TrendingUp, Award,
  Target, Send, Filter, Download, ChevronDown, ChevronUp,
  Sparkles, UserCheck, Key, FileText, Loader, Star, CreditCard, Users2, MessageSquare, BadgeCheck, Copy
} from 'lucide-react';
import api from '../services/api';
import JobPostForm from '../components/JobPostForm';
import ApplicationsList from '../components/ApplicationsList';
import TestManagement from '../components/TestManagement';
import ExternalJobManagement from '../components/ExternalJobManagement';
import ExternalJobResults from '../components/ExternalJobResults';
import SubscriptionPanel from '../components/SubscriptionPanel';
import TalentPoolPanel from '../components/TalentPoolPanel';
import TeamPanel from '../components/TeamPanel';
import CandidateDiscoveryPanel from '../components/CandidateDiscoveryPanel';
import IntegrationsPanel from '../components/IntegrationsPanel';
import DirectMessagesInbox from '../components/DirectMessagesInbox';
import VerificationPanel from '../components/VerificationPanel';

const RecruiterDashboard = () => {
  const [jobs, setJobs] = useState([]);
  const [showJobForm, setShowJobForm] = useState(false);
  const [editingJob, setEditingJob] = useState(null);
  const [selectedJob, setSelectedJob] = useState(null);
  const [showApplications, setShowApplications] = useState(false);
  const [showTestManagement, setShowTestManagement] = useState(false);
  const [showTestResults, setShowTestResults] = useState(false);
  const [testResults, setTestResults] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [recruiterProfile, setRecruiterProfile] = useState(null);
  const [activeTab, setActiveTab] = useState('jobs');
  const [analyticsData, setAnalyticsData] = useState(null);
  const [analyticsLoading, setAnalyticsLoading] = useState(false);
  const [analyticsError, setAnalyticsError] = useState('');

  // External results state
  const [externalJobsWithStats, setExternalJobsWithStats] = useState([]);
  const [showExternalResults, setShowExternalResults] = useState(false);
  const [selectedExternalJobForResults, setSelectedExternalJobForResults] = useState(null);
  const [loadingExternalStats, setLoadingExternalStats] = useState(false);

  const currentUser = JSON.parse(localStorage.getItem('user'));

  useEffect(() => {
    fetchRecruiterJobs();
    fetchRecruiterProfile();
  }, []);

  const fetchRecruiterJobs = async () => {
    try {
      setLoading(true);
      
      const profileResponse = await api.get(`/recruiters/user/${currentUser.id}`);
      
      if (profileResponse.data.recruiter) {
        const recruiterId = profileResponse.data.recruiter.id;
        
        const jobsResponse = await api.get(`/recruiters/jobs/recruiter/${recruiterId}`);
        setJobs(jobsResponse.data || []);
      } else {
        setError('Recruiter profile not found. Please complete your recruiter profile first.');
      }
    } catch (error) {
      console.error('Error fetching recruiter jobs:', error);
      setError('Failed to load jobs: ' + (error.response?.data?.error || error.message));
      setJobs([]);
    } finally {
      setLoading(false);
    }
  };

  const fetchRecruiterProfile = async () => {
    try {
      const response = await api.get(`/recruiters/user/${currentUser.id}`);
      if (response.data.recruiter) {
        setRecruiterProfile(response.data.recruiter);
      }
    } catch (error) {
      console.error('Error fetching recruiter profile:', error);
    }
  };

  const fetchTestResults = async () => {
    try {
      setLoading(true);
      const response = await api.get(`/aptitude-tests/recruiter/${recruiterProfile?.id}/results`);
      setTestResults(response.data.assignments || []);
      setShowTestResults(true);
    } catch (error) {
      console.error('Error fetching test results:', error);
    } finally {
      setLoading(false);
    }
  };

  const fetchExternalJobsWithStats = async () => {
    if (!recruiterProfile?.id) return;
    
    try {
      setLoadingExternalStats(true);
      const response = await api.get(`/external-jobs/recruiter/${recruiterProfile.id}/jobs-with-stats`);
      setExternalJobsWithStats(response.data || []);
    } catch (error) {
      console.error('Error fetching external jobs with stats:', error);
      setExternalJobsWithStats([]);
    } finally {
      setLoadingExternalStats(false);
    }
  };

  const fetchAnalytics = async () => {
    if (!recruiterProfile?.id) return;

    try {
      setAnalyticsLoading(true);
      setAnalyticsError('');
      const response = await api.get(`/applications/recruiter/${recruiterProfile.id}/analytics`);
      setAnalyticsData(response.data);
    } catch (error) {
      console.error('Error fetching analytics:', error);
      setAnalyticsError('Failed to load analytics');
    } finally {
      setAnalyticsLoading(false);
    }
  };

  const handleCreateJob = async (jobData) => {
    try {
      const currentUser = JSON.parse(localStorage.getItem('user'));
      
      if (!recruiterProfile) {
        alert('Please complete your recruiter profile first');
        return;
      }

      const requestData = {
        ...jobData,
        recruiterId: recruiterProfile.id
      };

      
      const response = await api.post('/recruiters/jobs', requestData);
      
      setShowJobForm(false);
      fetchRecruiterJobs();
      
      alert('Job posted successfully!');
    } catch (error) {
      console.error('Error creating job:', error);
      alert('Failed to create job: ' + (error.response?.data?.error || error.message));
    }
  };

  const handleUpdateJob = async (jobData) => {
    try {
      const requestData = {
        ...jobData,
        recruiterId: recruiterProfile?.id
      };

      const response = await api.put(`/recruiters/jobs/${editingJob.id}`, requestData);
      
      setEditingJob(null);
      setShowJobForm(false);
      fetchRecruiterJobs();
    } catch (error) {
      console.error('Error updating job:', error);
      alert('Failed to update job: ' + (error.response?.data?.error || error.message));
    }
  };

  const handleDeleteJob = async (jobId) => {
    if (!window.confirm('Are you sure you want to delete this job posting?')) {
      return;
    }

    try {
      await api.delete(`/recruiters/jobs/${jobId}?recruiterId=${recruiterProfile?.id}`);
      fetchRecruiterJobs();
    } catch (error) {
      console.error('Error deleting job:', error);
      alert('Failed to delete job: ' + (error.response?.data?.error || error.message));
    }
  };

  const viewApplications = (job) => {
    setSelectedJob(job);
    setShowApplications(true);
  };

  const [duplicatingJobId, setDuplicatingJobId] = useState(null);
  const [featuringJobId, setFeaturingJobId] = useState(null);

  const handleToggleFeatured = async (job) => {
    setFeaturingJobId(job.id);
    try {
      await api.put(`/recruiters/jobs/${job.id}/featured`, {
        recruiterId: recruiterProfile?.id,
        featured: !job.featured,
      });
      await fetchRecruiterJobs();
    } catch (error) {
      console.error('Error updating featured status:', error);
      alert(error.response?.data?.error || 'Failed to update featured status');
    } finally {
      setFeaturingJobId(null);
    }
  };

  const handleDuplicateJob = async (job) => {
    if (!window.confirm(`Duplicate "${job.title}" as a new draft you can edit and re-publish?`)) {
      return;
    }
    setDuplicatingJobId(job.id);
    try {
      await api.post(`/recruiters/jobs/${job.id}/duplicate`, { recruiterId: recruiterProfile?.id });
      await fetchRecruiterJobs();
    } catch (error) {
      console.error('Error duplicating job:', error);
      alert('Failed to duplicate job: ' + (error.response?.data?.error || error.message));
    } finally {
      setDuplicatingJobId(null);
    }
  };

  const navigateToInterviewPage = () => {
    window.location.href = '/recruiter/interviews';
  };

  const parseSkills = (skillsJson) => {
    try {
      return skillsJson ? JSON.parse(skillsJson) : [];
    } catch {
      return [];
    }
  };

  const getStatusColor = (status) => {
    switch(status) {
      case 'PENDING':
        return 'bg-yellow-100 text-yellow-800';
      case 'COMPLETED':
        return 'bg-green-100 text-green-800';
      case 'PASSED':
        return 'bg-emerald-100 text-emerald-800';
      case 'FAILED':
        return 'bg-red-100 text-red-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  const getMatchColor = (percentage) => {
    if (percentage >= 80) return 'bg-green-100 text-green-800';
    if (percentage >= 60) return 'bg-yellow-100 text-yellow-800';
    if (percentage >= 40) return 'bg-orange-100 text-orange-800';
    return 'bg-red-100 text-red-800';
  };

  const formatDate = (dateString) => {
    if (!dateString) return 'N/A';
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  };

  if (loading && activeTab !== 'tests' && activeTab !== 'external' && activeTab !== 'external-results' && activeTab !== 'analytics') {
    return (
      <div className="max-w-7xl mx-auto py-8 px-4">
        <div className="animate-pulse">
          <div className="h-8 bg-gray-200 rounded w-1/4 mb-8"></div>
          <div className="space-y-4">
            {[...Array(3)].map((_, i) => (
              <div key={i} className="h-32 bg-gray-200 rounded"></div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto py-8 px-4">
      {/* Header */}
      <div className="mb-8">
        <div className="flex justify-between items-center mb-6">
          <div>
            <h1 className="text-3xl font-bold text-gray-800">Recruiter Dashboard</h1>
            <p className="text-gray-600 mt-2">
              Manage your job postings, view applications, and conduct aptitude tests
            </p>
          </div>
          <div className="flex space-x-3">
            <button
              onClick={navigateToInterviewPage}
              className="bg-purple-600 text-white px-6 py-3 rounded-lg font-semibold hover:bg-purple-700 transition-colors flex items-center"
            >
              <Calendar className="h-5 w-5 mr-2" />
              Interview Management
            </button>
            <button
              onClick={() => {
                setActiveTab('tests');
                setShowTestManagement(true);
              }}
              className="bg-indigo-600 text-white px-6 py-3 rounded-lg font-semibold hover:bg-indigo-700 transition-colors flex items-center"
            >
              <BookOpen className="h-5 w-5 mr-2" />
              Test Management
            </button>
            <button
              onClick={() => setShowJobForm(true)}
              className="bg-blue-600 text-white px-6 py-3 rounded-lg font-semibold hover:bg-blue-700 transition-colors flex items-center"
            >
              <Plus className="h-5 w-5 mr-2" />
              Post New Job
            </button>
          </div>
        </div>

        {/* Tab Navigation */}
        <div className="border-b border-gray-200 mb-6">
          <nav className="-mb-px flex space-x-8 overflow-x-auto">
            <button
              onClick={() => setActiveTab('jobs')}
              className={`py-4 px-1 border-b-2 font-medium text-sm flex items-center whitespace-nowrap ${
                activeTab === 'jobs'
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              }`}
            >
              <Briefcase className="h-5 w-5 mr-2" />
              My Jobs
            </button>
            <button
              onClick={() => {
                setActiveTab('tests');
                setShowTestManagement(true);
              }}
              className={`py-4 px-1 border-b-2 font-medium text-sm flex items-center whitespace-nowrap ${
                activeTab === 'tests'
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              }`}
            >
              <BookOpen className="h-5 w-5 mr-2" />
              Aptitude Tests
            </button>
            <button
              onClick={() => {
                setActiveTab('results');
                fetchTestResults();
              }}
              className={`py-4 px-1 border-b-2 font-medium text-sm flex items-center whitespace-nowrap ${
                activeTab === 'results'
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              }`}
            >
              <BarChart3 className="h-5 w-5 mr-2" />
              Test Results
            </button>
            <button
              onClick={() => setActiveTab('external')}
              className={`py-4 px-1 border-b-2 font-medium text-sm flex items-center whitespace-nowrap ${
                activeTab === 'external'
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              }`}
            >
              <Upload className="h-5 w-5 mr-2" />
              External Jobs
            </button>
            <button
              onClick={() => {
                setActiveTab('external-results');
                fetchExternalJobsWithStats();
              }}
              className={`py-4 px-1 border-b-2 font-medium text-sm flex items-center whitespace-nowrap ${
                activeTab === 'external-results'
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              }`}
            >
              <BarChart3 className="h-5 w-5 mr-2" />
              External Results
            </button>
            <button
              onClick={() => {
                setActiveTab('analytics');
                fetchAnalytics();
              }}
              className={`py-4 px-1 border-b-2 font-medium text-sm flex items-center whitespace-nowrap ${
                activeTab === 'analytics'
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              }`}
            >
              <TrendingUp className="h-5 w-5 mr-2" />
              Analytics
            </button>
            <button
              onClick={() => setActiveTab('billing')}
              className={`py-4 px-1 border-b-2 font-medium text-sm flex items-center whitespace-nowrap ${
                activeTab === 'billing'
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              }`}
            >
              <CreditCard className="h-5 w-5 mr-2" />
              Billing
            </button>
            <button
              onClick={() => setActiveTab('talent-pool')}
              className={`py-4 px-1 border-b-2 font-medium text-sm flex items-center whitespace-nowrap ${
                activeTab === 'talent-pool'
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              }`}
            >
              <Users className="h-5 w-5 mr-2" />
              Talent Pool
            </button>
            <button
              onClick={() => setActiveTab('team')}
              className={`py-4 px-1 border-b-2 font-medium text-sm flex items-center whitespace-nowrap ${
                activeTab === 'team'
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              }`}
            >
              <Users2 className="h-5 w-5 mr-2" />
              Team
            </button>
            <button
              onClick={() => setActiveTab('discover')}
              className={`py-4 px-1 border-b-2 font-medium text-sm flex items-center whitespace-nowrap ${
                activeTab === 'discover'
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              }`}
            >
              <Sparkles className="h-5 w-5 mr-2" />
              Discover Candidates
            </button>
            <button
              onClick={() => setActiveTab('integrations')}
              className={`py-4 px-1 border-b-2 font-medium text-sm flex items-center whitespace-nowrap ${
                activeTab === 'integrations'
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              }`}
            >
              <Key className="h-5 w-5 mr-2" />
              Integrations
            </button>
            <button
              onClick={() => setActiveTab('messages')}
              className={`py-4 px-1 border-b-2 font-medium text-sm flex items-center whitespace-nowrap ${
                activeTab === 'messages'
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              }`}
            >
              <MessageSquare className="h-5 w-5 mr-2" />
              Messages
            </button>
            <button
              onClick={() => setActiveTab('verification')}
              className={`py-4 px-1 border-b-2 font-medium text-sm flex items-center whitespace-nowrap ${
                activeTab === 'verification'
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              }`}
            >
              <BadgeCheck className="h-5 w-5 mr-2" />
              Verification
            </button>
          </nav>
        </div>

        {/* Recruiter Profile Info */}
        {recruiterProfile && (
          <div className="bg-white rounded-lg shadow-lg p-6 mb-6 border border-gray-200">
            <div className="flex items-center justify-between">
              <div className="flex items-center space-x-4">
                <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center">
                  <Building className="h-8 w-8 text-green-600" />
                </div>
                <div>
                  <h2 className="text-xl font-semibold text-gray-800">{recruiterProfile.companyName}</h2>
                  <p className="text-gray-600">{recruiterProfile.industry}</p>
                  <div className="flex items-center space-x-4 mt-2 text-sm text-gray-500">
                    {recruiterProfile.contactEmail && (
                      <div className="flex items-center">
                        <Mail className="h-4 w-4 mr-1" />
                        {recruiterProfile.contactEmail}
                      </div>
                    )}
                    {recruiterProfile.contactPhone && (
                      <div className="flex items-center">
                        <Phone className="h-4 w-4 mr-1" />
                        {recruiterProfile.contactPhone}
                      </div>
                    )}
                  </div>
                </div>
              </div>
              <div className="text-right">
                <p className="text-2xl font-bold text-blue-600">{jobs.length}</p>
                <p className="text-gray-600">Active Jobs</p>
              </div>
            </div>
          </div>
        )}
      </div>

      {error && (
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-6">
          {error}
        </div>
      )}

      {/* Jobs Tab Content */}
      {activeTab === 'jobs' && (
        <div className="space-y-6">
          {jobs.map((job) => (
            <div key={job.id} className="bg-white rounded-lg shadow-lg border border-gray-200 p-6">
              <div className="flex justify-between items-start mb-4">
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-2 flex-wrap">
                    <h3 className="text-xl font-semibold text-gray-800">{job.title}</h3>
                    {job.featured && (
                      <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-semibold bg-amber-100 text-amber-800">
                        <Star className="h-3 w-3 mr-1 fill-amber-600" />
                        Featured
                      </span>
                    )}
                    {job.scheduledFor && new Date(job.scheduledFor) > new Date() && (
                      <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-semibold bg-sky-100 text-sky-800">
                        <Calendar className="h-3 w-3 mr-1" />
                        Scheduled for {new Date(job.scheduledFor).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' })}
                      </span>
                    )}
                    <span className={`px-2 py-1 rounded-full text-xs font-semibold ${
                      job.moderationStatus === 'APPROVED' || !job.moderationStatus ? 'bg-green-100 text-green-800' :
                      job.moderationStatus === 'REJECTED' ? 'bg-red-100 text-red-800' :
                      'bg-yellow-100 text-yellow-800'
                    }`}>
                      {job.moderationStatus === 'PENDING' ? 'Pending review' :
                       job.moderationStatus === 'REJECTED' ? 'Not approved' : 'Live'}
                    </span>
                  </div>
                  {job.moderationStatus === 'PENDING' && (
                    <p className="text-sm text-yellow-700 bg-yellow-50 rounded px-3 py-1.5 mb-2 inline-block">
                      Awaiting admin review - not visible to candidates yet.
                    </p>
                  )}
                  {job.moderationStatus === 'REJECTED' && (
                    <p className="text-sm text-red-700 bg-red-50 rounded px-3 py-1.5 mb-2">
                      Not approved{job.rejectionReason ? `: ${job.rejectionReason}` : '.'} Edit and it will be re-reviewed.
                    </p>
                  )}
                  <p className="text-lg text-gray-600 mb-2">{job.company}</p>

                  <div className="flex flex-wrap gap-4 mb-3 text-sm text-gray-600">
                    <div className="flex items-center">
                      <MapPin className="h-4 w-4 mr-1" />
                      {job.location}
                    </div>
                    <div className="flex items-center">
                      <Users className="h-4 w-4 mr-1" />
                      {job.experienceLevel}
                    </div>
                    <div className="flex items-center">
                      <span className="w-2 h-2 bg-green-500 rounded-full mr-2"></span>
                      {job.jobType}
                    </div>
                    <div className="flex items-center">
                      <Clock className="h-4 w-4 mr-1" />
                      Posted: {formatDate(job.postedDate)}
                    </div>
                  </div>

                  <p className="text-gray-700 mb-4 line-clamp-2">
                    {job.description?.substring(0, 200)}...
                  </p>

                  <div className="flex flex-wrap gap-2 mb-4">
                    {parseSkills(job.requiredSkills).slice(0, 5).map((skill, index) => (
                      <span
                        key={index}
                        className="bg-blue-100 text-blue-800 px-3 py-1 rounded-full text-sm"
                      >
                        {skill}
                      </span>
                    ))}
                    {parseSkills(job.requiredSkills).length > 5 && (
                      <span className="bg-gray-100 text-gray-600 px-3 py-1 rounded-full text-sm">
                        +{parseSkills(job.requiredSkills).length - 5} more
                      </span>
                    )}
                  </div>
                </div>
                
                <div className="ml-4 flex flex-col space-y-2">
                  <button
                    onClick={() => viewApplications(job)}
                    className="inline-flex items-center px-4 py-2 bg-green-600 text-white rounded-md text-sm font-medium hover:bg-green-700 transition-colors"
                  >
                    <Eye className="h-4 w-4 mr-1" />
                    View Applications
                  </button>
                  
                  <div className="flex space-x-2">
                    <button
                      onClick={() => setEditingJob(job)}
                      className="flex-1 inline-flex items-center px-3 py-1 border border-gray-300 rounded-md text-sm font-medium text-gray-700 bg-white hover:bg-gray-50"
                    >
                      <Edit className="h-4 w-4 mr-1" />
                      Edit
                    </button>
                    <button
                      onClick={() => handleDeleteJob(job.id)}
                      className="flex-1 inline-flex items-center px-3 py-1 border border-red-300 rounded-md text-sm font-medium text-red-700 bg-white hover:bg-red-50"
                    >
                      <Trash2 className="h-4 w-4 mr-1" />
                      Delete
                    </button>
                  </div>
                  <div className="flex space-x-2">
                    <button
                      onClick={() => handleDuplicateJob(job)}
                      disabled={duplicatingJobId === job.id}
                      className="flex-1 inline-flex items-center justify-center px-3 py-1 border border-gray-300 rounded-md text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50"
                    >
                      <Copy className="h-4 w-4 mr-1" />
                      {duplicatingJobId === job.id ? 'Duplicating…' : 'Duplicate'}
                    </button>
                    <button
                      onClick={() => handleToggleFeatured(job)}
                      disabled={featuringJobId === job.id}
                      title={job.featured ? 'Remove from featured' : 'Feature this job (Pro)'}
                      className={`flex-1 inline-flex items-center justify-center px-3 py-1 border rounded-md text-sm font-medium disabled:opacity-50 ${
                        job.featured
                          ? 'border-amber-300 bg-amber-50 text-amber-700 hover:bg-amber-100'
                          : 'border-gray-300 bg-white text-gray-700 hover:bg-gray-50'
                      }`}
                    >
                      <Star className={`h-4 w-4 mr-1 ${job.featured ? 'fill-amber-500' : ''}`} />
                      {featuringJobId === job.id ? 'Updating…' : job.featured ? 'Featured' : 'Feature'}
                    </button>
                  </div>
                </div>
              </div>

              <div className="flex justify-between items-center pt-4 border-t border-gray-200">
                <div className="text-sm text-gray-500">
                  {job.isActive ? 'Active' : 'Inactive'}
                </div>
                {job.averageRating > 0 && (
                  <div className="flex items-center text-sm">
                    <Star className="h-4 w-4 text-yellow-400 mr-1" />
                    <span>{job.averageRating} ({job.totalRatings} ratings)</span>
                  </div>
                )}
              </div>
            </div>
          ))}

          {jobs.length === 0 && (
            <div className="text-center py-12">
              <Briefcase className="mx-auto h-16 w-16 text-gray-400 mb-4" />
              <h3 className="text-lg font-medium text-gray-900 mb-2">No jobs posted yet</h3>
              <p className="text-gray-500 mb-6">Get started by posting your first job opening.</p>
              <button
                onClick={() => setShowJobForm(true)}
                className="bg-blue-600 text-white px-6 py-3 rounded-lg font-semibold hover:bg-blue-700 transition-colors"
              >
                Post Your First Job
              </button>
            </div>
          )}
        </div>
      )}

      {/* Tests Tab Content */}
      {activeTab === 'tests' && showTestManagement && recruiterProfile && (
        <div className="bg-white rounded-lg shadow-lg p-6">
          <TestManagement recruiterId={recruiterProfile.id} />
        </div>
      )}

      {/* Test Results Tab Content */}
      {activeTab === 'results' && (
        <div className="bg-white rounded-lg shadow-lg p-6">
          <h3 className="text-xl font-semibold mb-4">Test Results</h3>
          
          {testResults.length === 0 ? (
            <div className="text-center py-8">
              <BarChart3 className="mx-auto h-12 w-12 text-gray-400 mb-4" />
              <p className="text-gray-500">No test results available yet</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-200">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Candidate</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Test</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Score</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Completed</th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {testResults.map((result) => (
                    <tr key={result.assignmentId}>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="text-sm font-medium text-gray-900">{result.candidateName}</div>
                        <div className="text-sm text-gray-500">{result.candidateEmail}</div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="text-sm text-gray-900">{result.testName}</div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="text-sm text-gray-900">{result.score}/{result.totalQuestions} ({result.percentage}%)</div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${getStatusColor(result.status)}`}>
                          {result.status}
                        </span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        {result.completedAt ? new Date(result.completedAt).toLocaleDateString() : '-'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* External Jobs Tab Content */}
      {activeTab === 'external' && recruiterProfile && (
        <div className="bg-white rounded-lg shadow-lg p-6">
          <ExternalJobManagement recruiterId={recruiterProfile.id} />
        </div>
      )}

      {/* External Results Tab Content */}
      {activeTab === 'external-results' && (
        <div className="bg-white rounded-lg shadow-lg p-6">
          <div className="flex justify-between items-center mb-6">
            <div>
              <h3 className="text-xl font-semibold text-gray-800">External Jobs - Results Overview</h3>
              <p className="text-gray-500 text-sm mt-1">Track shortlisted candidates and schedule interviews</p>
            </div>
            <button
              onClick={() => {
                setActiveTab('external');
              }}
              className="text-blue-600 hover:text-blue-800 text-sm flex items-center"
            >
              <Plus className="h-4 w-4 mr-1" />
              Create New External Job
            </button>
          </div>
          
          {loadingExternalStats ? (
            <div className="text-center py-12">
              <Loader className="h-8 w-8 animate-spin text-blue-600 mx-auto" />
              <p className="mt-2 text-gray-600">Loading external jobs...</p>
            </div>
          ) : externalJobsWithStats.length === 0 ? (
            <div className="text-center py-12">
              <Briefcase className="mx-auto h-16 w-16 text-gray-400 mb-4" />
              <h4 className="text-lg font-medium text-gray-900 mb-2">No External Jobs Created</h4>
              <p className="text-gray-500 mb-4">Create an external job posting to start receiving applications</p>
              <button
                onClick={() => setActiveTab('external')}
                className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700"
              >
                Create External Job
              </button>
            </div>
          ) : (
            <div className="space-y-6">
              {externalJobsWithStats.map(job => (
                <div key={job.id} className="border border-gray-200 rounded-lg p-5 hover:shadow-md transition-shadow">
                  <div className="flex justify-between items-start">
                    <div>
                      <h4 className="text-xl font-semibold text-gray-800">{job.title}</h4>
                      <p className="text-gray-600">{job.company}</p>
                      <div className="flex flex-wrap gap-3 mt-2 text-sm text-gray-500">
                        <span className="flex items-center"><MapPin className="h-3 w-3 mr-1" />{job.location || 'Location N/A'}</span>
                        <span className="flex items-center"><Briefcase className="h-3 w-3 mr-1" />{job.jobType || 'Full-time'}</span>
                        <span className="flex items-center"><Users className="h-3 w-3 mr-1" />Need: {job.requiredCandidates} candidates</span>
                        <span className="flex items-center"><Calendar className="h-3 w-3 mr-1" />Created: {formatDate(job.createdAt)}</span>
                      </div>
                    </div>
                    <button
                      onClick={() => {
                        setSelectedExternalJobForResults(job);
                        setShowExternalResults(true);
                      }}
                      className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 flex items-center"
                    >
                      <Eye className="h-4 w-4 mr-2" />
                      View Results
                    </button>
                  </div>
                  
                  {/* Stats for this job */}
                  <div className="mt-4 pt-4 border-t border-gray-100">
                    <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
                      <div className="text-center">
                        <div className="text-2xl font-bold text-blue-600">{job.totalCandidates || 0}</div>
                        <div className="text-xs text-gray-500">Total</div>
                      </div>
                      <div className="text-center">
                        <div className="text-2xl font-bold text-yellow-600">{job.pendingCount || 0}</div>
                        <div className="text-xs text-gray-500">Pending</div>
                      </div>
                      <div className="text-center">
                        <div className="text-2xl font-bold text-green-600">{job.shortlistedCount || 0}</div>
                        <div className="text-xs text-gray-500">Shortlisted</div>
                      </div>
                      <div className="text-center">
                        <div className="text-2xl font-bold text-purple-600">{job.averageMatchScore || 0}%</div>
                        <div className="text-xs text-gray-500">Avg Match</div>
                      </div>
                      <div className="text-center">
                        <div className="text-2xl font-bold text-orange-600">{job.requiredCandidates || 0}</div>
                        <div className="text-xs text-gray-500">Required</div>
                      </div>
                    </div>
                    
                    {/* Required Skills Preview */}
                    {job.requiredSkills && job.requiredSkills.length > 0 && (
                      <div className="mt-3 pt-2">
                        <p className="text-xs text-gray-500 mb-1">Required Skills:</p>
                        <div className="flex flex-wrap gap-1">
                          {job.requiredSkills.slice(0, 8).map((skill, idx) => (
                            <span key={idx} className="bg-blue-100 text-blue-800 px-2 py-0.5 rounded text-xs">
                              {skill}
                            </span>
                          ))}
                          {job.requiredSkills.length > 8 && (
                            <span className="text-gray-500 text-xs">+{job.requiredSkills.length - 8} more</span>
                          )}
                        </div>
                      </div>
                    )}
                    
                    {/* Progress Bar for Shortlisting */}
                    <div className="mt-3">
                      <div className="flex justify-between text-xs text-gray-500 mb-1">
                        <span>Shortlisting Progress</span>
                        <span>{job.shortlistedCount}/{job.requiredCandidates} candidates</span>
                      </div>
                      <div className="w-full bg-gray-200 rounded-full h-2">
                        <div 
                          className="h-2 rounded-full bg-green-500 transition-all duration-500"
                          style={{ width: `${Math.min((job.shortlistedCount / job.requiredCandidates) * 100, 100)}%` }}
                        />
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {activeTab === 'analytics' && (
        <div className="bg-white rounded-lg shadow-lg p-6">
          <h3 className="text-xl font-semibold text-gray-800 mb-1">Hiring Analytics</h3>
          <p className="text-gray-500 text-sm mb-6">
            Based on current application status across all of your jobs. Timing figures only
            cover applications with the relevant timestamps recorded.
          </p>

          {analyticsLoading ? (
            <div className="text-center py-12">
              <Loader className="h-8 w-8 animate-spin text-blue-600 mx-auto" />
              <p className="mt-2 text-gray-600">Loading analytics...</p>
            </div>
          ) : analyticsError ? (
            <div className="text-center py-12 text-red-600">{analyticsError}</div>
          ) : !analyticsData || analyticsData.totalApplications === 0 ? (
            <div className="text-center py-12">
              <TrendingUp className="mx-auto h-16 w-16 text-gray-400 mb-4" />
              <p className="text-gray-500">No applications yet to show analytics for.</p>
            </div>
          ) : (
            <>
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4 mb-8">
                <div className="bg-blue-50 rounded-lg p-4 text-center">
                  <p className="text-2xl font-bold text-blue-700">{analyticsData.totalJobs}</p>
                  <p className="text-xs text-gray-600 mt-1">Active Jobs</p>
                </div>
                <div className="bg-gray-50 rounded-lg p-4 text-center">
                  <p className="text-2xl font-bold text-gray-800">{analyticsData.totalApplications}</p>
                  <p className="text-xs text-gray-600 mt-1">Total Applications</p>
                </div>
                <div className="bg-purple-50 rounded-lg p-4 text-center">
                  <p className="text-2xl font-bold text-purple-700">{analyticsData.totalViewed}</p>
                  <p className="text-xs text-gray-600 mt-1">Viewed</p>
                </div>
                <div className="bg-green-50 rounded-lg p-4 text-center">
                  <p className="text-2xl font-bold text-green-700">{analyticsData.totalOffers}</p>
                  <p className="text-xs text-gray-600 mt-1">Offers Sent</p>
                </div>
                <div className="bg-yellow-50 rounded-lg p-4 text-center">
                  <p className="text-2xl font-bold text-yellow-700">
                    {analyticsData.averageMatchPercentage != null ? `${analyticsData.averageMatchPercentage}%` : '—'}
                  </p>
                  <p className="text-xs text-gray-600 mt-1">Avg. Match Score</p>
                </div>
                <div className="bg-indigo-50 rounded-lg p-4 text-center">
                  <p className="text-2xl font-bold text-indigo-700">
                    {analyticsData.averageDaysToOffer != null ? `${analyticsData.averageDaysToOffer}d` : '—'}
                  </p>
                  <p className="text-xs text-gray-600 mt-1">Avg. Time to Offer</p>
                </div>
              </div>

              {/* Visual Hiring Funnel Drop-off Bar */}
              <div className="bg-gradient-to-r from-slate-50 to-blue-50/40 border border-blue-100 rounded-xl p-5 mb-8">
                <div className="flex items-center justify-between mb-4">
                  <div>
                    <h4 className="font-semibold text-gray-800 flex items-center gap-2">
                      <TrendingUp className="h-5 w-5 text-blue-600" /> Hiring Funnel & Candidate Conversion
                    </h4>
                    <p className="text-xs text-gray-500 mt-0.5">
                      Conversion progression from candidate application to formal job offer.
                    </p>
                  </div>
                  <span className="text-xs bg-blue-100 text-blue-800 font-medium px-2.5 py-1 rounded-full">
                    {analyticsData.averageDaysToOffer != null ? `⚡ Time-to-Offer: ${analyticsData.averageDaysToOffer} Days` : '⚡ High Velocity'}
                  </span>
                </div>

                {(() => {
                  const totalApps = Math.max(1, analyticsData.totalApplications || 1);
                  const viewed = analyticsData.totalViewed || 0;
                  const shortlisted = analyticsData.jobs?.reduce((acc, j) => acc + (j.shortlisted || 0), 0) || 0;
                  const offers = analyticsData.totalOffers || 0;

                  const funnelSteps = [
                    { label: 'Applied', count: analyticsData.totalApplications, color: 'from-blue-500 to-blue-600' },
                    { label: 'Viewed', count: viewed, color: 'from-purple-500 to-purple-600' },
                    { label: 'Shortlisted', count: shortlisted, color: 'from-amber-500 to-amber-600' },
                    { label: 'Offer Sent', count: offers, color: 'from-emerald-500 to-emerald-600' },
                  ];

                  return (
                    <div className="space-y-3">
                      {funnelSteps.map((step, idx) => {
                        const pctOverall = Math.round((step.count / totalApps) * 100);
                        const prevCount = idx > 0 ? funnelSteps[idx - 1].count : null;
                        const stepConversion = prevCount && prevCount > 0 ? Math.round((step.count / prevCount) * 100) : null;

                        return (
                          <div key={step.label} className="space-y-1">
                            <div className="flex items-center justify-between text-xs">
                              <div className="flex items-center gap-2">
                                <span className="font-semibold text-gray-700 w-24">{step.label}</span>
                                {stepConversion !== null && (
                                  <span className="text-[11px] text-gray-500 font-mono">
                                    ({stepConversion}% of {funnelSteps[idx - 1].label})
                                  </span>
                                )}
                              </div>
                              <div className="flex items-center gap-2">
                                <span className="text-gray-500 font-mono text-[11px]">{pctOverall}% of all apps</span>
                                <span className="font-bold text-gray-900 w-8 text-right">{step.count}</span>
                              </div>
                            </div>
                            <div className="w-full bg-gray-200/80 rounded-full h-2.5 overflow-hidden">
                              <div
                                className={`bg-gradient-to-r ${step.color} h-2.5 rounded-full transition-all duration-500`}
                                style={{ width: `${Math.min(100, Math.max(2, pctOverall))}%` }}
                              />
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  );
                })()}

                {analyticsData.averageHoursToFirstView != null && (
                  <p className="text-xs text-gray-600 mt-4 flex items-center gap-1.5 pt-3 border-t border-blue-100/60">
                    <Clock className="h-4 w-4 text-blue-500 flex-shrink-0" />
                    <span>On average, your team first reviews candidate profiles within <strong>{analyticsData.averageHoursToFirstView} hours</strong> of submission.</span>
                  </p>
                )}
              </div>

              <h4 className="font-semibold text-gray-800 mb-3">By Job</h4>
              <div className="overflow-x-auto">
                <table className="min-w-full text-sm">
                  <thead>
                    <tr className="text-left text-gray-500 border-b border-gray-200">
                      <th className="py-2 pr-4">Job</th>
                      <th className="py-2 pr-4">Applications</th>
                      <th className="py-2 pr-4">Shortlisted</th>
                      <th className="py-2 pr-4">Rejected</th>
                      <th className="py-2 pr-4">Offers</th>
                      <th className="py-2 pr-4">Avg. Match</th>
                    </tr>
                  </thead>
                  <tbody>
                    {analyticsData.jobs.map((job) => (
                      <tr key={job.jobId} className="border-b border-gray-100">
                        <td className="py-2 pr-4 font-medium text-gray-800">{job.jobTitle}</td>
                        <td className="py-2 pr-4">{job.totalApplications}</td>
                        <td className="py-2 pr-4">{job.shortlisted}</td>
                        <td className="py-2 pr-4">{job.rejected}</td>
                        <td className="py-2 pr-4">{job.offers}</td>
                        <td className="py-2 pr-4">{job.averageMatchPercentage}%</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </div>
      )}

      {activeTab === 'billing' && recruiterProfile && (
        <SubscriptionPanel recruiterId={recruiterProfile.id} />
      )}

      {activeTab === 'talent-pool' && recruiterProfile && (
        <TalentPoolPanel recruiterId={recruiterProfile.id} />
      )}

      {activeTab === 'team' && recruiterProfile && (
        <TeamPanel recruiterId={recruiterProfile.id} />
      )}

      {activeTab === 'discover' && recruiterProfile && (
        <CandidateDiscoveryPanel recruiterId={recruiterProfile.id} />
      )}

      {activeTab === 'integrations' && recruiterProfile && (
        <IntegrationsPanel recruiterId={recruiterProfile.id} />
      )}

      {activeTab === 'messages' && recruiterProfile && (
        <DirectMessagesInbox role="recruiter" id={recruiterProfile.id} />
      )}

      {activeTab === 'verification' && recruiterProfile && (
        <VerificationPanel recruiterId={recruiterProfile.id} />
      )}

      {/* Job Form Modal */}
      {showJobForm && (
        <JobPostForm
          job={editingJob}
          onSubmit={editingJob ? handleUpdateJob : handleCreateJob}
          onCancel={() => {
            setShowJobForm(false);
            setEditingJob(null);
          }}
        />
      )}

      {/* Applications Modal */}
      {showApplications && selectedJob && (
        <ApplicationsList
          job={selectedJob}
          onClose={() => {
            setShowApplications(false);
            setSelectedJob(null);
          }}
        />
      )}

      {/* External Results Modal */}
      {showExternalResults && selectedExternalJobForResults && (
        <ExternalJobResults
          job={selectedExternalJobForResults}
          onClose={() => {
            setShowExternalResults(false);
            setSelectedExternalJobForResults(null);
          }}
        />
      )}
    </div>
  );
};

export default RecruiterDashboard;