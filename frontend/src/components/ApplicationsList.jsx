// src/components/ApplicationsList.jsx
import React, { useState, useEffect } from 'react';
import {
  X, Mail, Phone, Calendar, Download, Star, BookOpen, User,
  Briefcase, RefreshCw, Eye, Clock, CheckCircle, XCircle,
  Calendar as CalendarIcon, MapPin, MessageCircle, TrendingUp,
  FileText, Percent, Target, Award, Sparkles, Video, FileCheck,
  Columns, GripVertical, List, Search, EyeOff, ShieldCheck, GitCompare
} from 'lucide-react';
import api from '../services/api';
import MessageThread from './MessageThread';
import InterviewPipelinePanel from './InterviewPipelinePanel';
import OfferPanel from './OfferPanel';

const ApplicationsList = ({ job, onClose }) => {
  const [applications, setApplications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedApplication, setSelectedApplication] = useState(null);
  const [messageThreadApplication, setMessageThreadApplication] = useState(null);
  const [pipelineApplicationId, setPipelineApplicationId] = useState(null);
  const [offerApplicationId, setOfferApplicationId] = useState(null);
  const [showStatusModal, setShowStatusModal] = useState(false);
  const [showInterviewModal, setShowInterviewModal] = useState(false);
  const [showTestModal, setShowTestModal] = useState(false);
  const [showTestResultsModal, setShowTestResultsModal] = useState(false);
  const [tests, setTests] = useState([]);
  const [dreamRoleTests, setDreamRoleTests] = useState([]);
  const [testType, setTestType] = useState('manual');
  const [selectedTestId, setSelectedTestId] = useState('');
  const [deadlineHours, setDeadlineHours] = useState(48);
  const [testResults, setTestResults] = useState(null);
  const [actionLoading, setActionLoading] = useState(null);
  const [recruiterId, setRecruiterId] = useState(null);
  const [selectedIds, setSelectedIds] = useState(new Set());
  const [bulkActionLoading, setBulkActionLoading] = useState(false);
  const [bulkResultMessage, setBulkResultMessage] = useState('');
  const [showBulkMessageModal, setShowBulkMessageModal] = useState(false);
  const [sendingBulkMessage, setSendingBulkMessage] = useState(false);
  const [showCompareModal, setShowCompareModal] = useState(false);
  const [exportingCsv, setExportingCsv] = useState(false);
  const [viewMode, setViewMode] = useState('list'); // 'list' | 'kanban'
  const [searchQuery, setSearchQuery] = useState('');
  const [draggedApp, setDraggedApp] = useState(null);
  const [activeDropColumn, setActiveDropColumn] = useState(null);
  const [kanbanToast, setKanbanToast] = useState(null);
  const [blindScreening, setBlindScreening] = useState(false);

  useEffect(() => {
    if (!kanbanToast) return;
    const timer = setTimeout(() => setKanbanToast(null), 4000);
    return () => clearTimeout(timer);
  }, [kanbanToast]);

  useEffect(() => {
    fetchApplications();
    fetchRecruiterId();
  }, [job]);

  useEffect(() => {
    if (recruiterId) {
      fetchTests();
      fetchDreamRoleTests();
    }
  }, [recruiterId]);

  const fetchRecruiterId = async () => {
    try {
      const currentUser = JSON.parse(localStorage.getItem('user'));
      let rId = job.recruiter?.id;

      if (!rId && currentUser?.recruiterId) {
        rId = currentUser.recruiterId;
      }

      if (!rId && currentUser?.id) {
        try {
          const profileResponse = await api.get(`/recruiters/user/${currentUser.id}`);
          if (profileResponse.data.recruiter) {
            rId = profileResponse.data.recruiter.id;
          }
        } catch (profileError) {
          console.error('Error fetching recruiter profile:', profileError);
        }
      }
      
      setRecruiterId(rId);
    } catch (error) {
      console.error('Error getting recruiter ID:', error);
    }
  };

  const fetchApplications = async () => {
    try {
      setLoading(true);
      setError('');
      

      const currentUser = JSON.parse(localStorage.getItem('user'));
      let rId = job.recruiter?.id;

      if (!rId && currentUser?.recruiterId) {
        rId = currentUser.recruiterId;
      }

      if (!rId && currentUser?.id) {
        try {
          const profileResponse = await api.get(`/recruiters/user/${currentUser.id}`);
          if (profileResponse.data.recruiter) {
            rId = profileResponse.data.recruiter.id;
          }
        } catch (profileError) {
          console.error('Error fetching recruiter profile:', profileError);
        }
      }

      if (!rId) {
        setError('Recruiter profile not found. Please complete your recruiter registration.');
        setApplications([]);
        return;
      }

      const response = await api.get(`/resume-matching/job/${job.id}/applications/sorted?recruiterId=${rId}`);
      
      if (response.data && response.data.applications) {
        setApplications(response.data.applications);
      } else {
        setApplications([]);
      }
    } catch (error) {
      console.error('Error fetching applications:', error);
      setError('Failed to load applications: ' + (error.response?.data?.error || error.message));
      setApplications([]);
    } finally {
      setLoading(false);
    }
  };

  const fetchTests = async () => {
    try {
      if (!recruiterId) {
        return;
      }
      
      const response = await api.get(`/aptitude-tests/recruiter/${recruiterId}`);
      setTests(response.data || []);
    } catch (error) {
      console.error('Error fetching tests:', error);
    }
  };

  const fetchDreamRoleTests = async () => {
    try {
      const response = await api.get(`/dream-role-tests/recruiter/${recruiterId}`);
      setDreamRoleTests(response.data || []);
    } catch (error) {
      console.error('Error fetching DreamRole tests:', error);
    }
  };

  const fetchTestResults = async (applicationId) => {
    try {
      const response = await api.get(`/aptitude-tests/application/${applicationId}`);
      if (response.data.hasTest) {
        setTestResults(response.data.assignment);
        setShowTestResultsModal(true);
      } else {
        alert('No test assigned to this candidate');
      }
    } catch (error) {
      console.error('Error fetching test results:', error);
      alert('Failed to fetch test results');
    }
  };

  const getStatusColor = (status) => {
    switch (status) {
      case 'APPLICATION_SUBMITTED':
        return 'bg-blue-100 text-blue-800';
      case 'VIEWED_BY_RECRUITER':
        return 'bg-purple-100 text-purple-800';
      case 'SHORTLISTED':
        return 'bg-green-100 text-green-800';
      case 'TEST_ASSIGNED':
        return 'bg-orange-100 text-orange-800';
      case 'TEST_IN_PROGRESS':
        return 'bg-yellow-100 text-yellow-800';
      case 'TEST_COMPLETED':
        return 'bg-indigo-100 text-indigo-800';
      case 'TEST_PASSED':
        return 'bg-emerald-100 text-emerald-800';
      case 'TEST_FAILED':
        return 'bg-red-100 text-red-800';
      case 'INTERVIEW_SCHEDULED':
        return 'bg-yellow-100 text-yellow-800';
      case 'REJECTED':
        return 'bg-red-100 text-red-800';
      case 'OFFER_SENT':
        return 'bg-emerald-100 text-emerald-800';
      case 'OFFER_ACCEPTED':
        return 'bg-green-100 text-green-800';
      case 'OFFER_DECLINED':
        return 'bg-gray-100 text-gray-800';
      case 'WITHDRAWN':
        return 'bg-gray-100 text-gray-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  const getMatchColor = (percentage) => {
    if (percentage >= 80) return 'bg-green-100 text-green-800 border-green-200';
    if (percentage >= 60) return 'bg-yellow-100 text-yellow-800 border-yellow-200';
    if (percentage >= 40) return 'bg-orange-100 text-orange-800 border-orange-200';
    return 'bg-red-100 text-red-800 border-red-200';
  };

  const getStatusIcon = (status) => {
    switch (status) {
      case 'APPLICATION_SUBMITTED':
        return <Clock className="h-4 w-4" />;
      case 'VIEWED_BY_RECRUITER':
        return <Eye className="h-4 w-4" />;
      case 'SHORTLISTED':
        return <CheckCircle className="h-4 w-4" />;
      case 'TEST_ASSIGNED':
        return <BookOpen className="h-4 w-4" />;
      case 'TEST_IN_PROGRESS':
        return <BookOpen className="h-4 w-4" />;
      case 'TEST_COMPLETED':
        return <Award className="h-4 w-4" />;
      case 'TEST_PASSED':
        return <Award className="h-4 w-4" />;
      case 'TEST_FAILED':
        return <XCircle className="h-4 w-4" />;
      case 'INTERVIEW_SCHEDULED':
        return <CalendarIcon className="h-4 w-4" />;
      case 'REJECTED':
        return <XCircle className="h-4 w-4" />;
      case 'OFFER_SENT':
        return <Star className="h-4 w-4" />;
      case 'OFFER_ACCEPTED':
        return <CheckCircle className="h-4 w-4" />;
      case 'OFFER_DECLINED':
        return <XCircle className="h-4 w-4" />;
      case 'WITHDRAWN':
        return <XCircle className="h-4 w-4" />;
      default:
        return <Clock className="h-4 w-4" />;
    }
  };

  const formatDate = (dateString) => {
    if (!dateString) return 'Unknown date';
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  const parseSkills = (skillsJson) => {
    try {
      if (!skillsJson) return [];
      if (typeof skillsJson === 'string') {
        return JSON.parse(skillsJson);
      }
      return skillsJson;
    } catch (error) {
      console.error('Error parsing skills:', error);
      return [];
    }
  };

  const handleDownloadResume = async (application) => {
    try {
      const response = await api.get(`/resumes/download/${application.id}`, {
        responseType: 'blob'
      });
      
      const blob = new Blob([response.data]);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      
      const applicantName = application.applicantName || application.applicant?.fullName || 'candidate';
      const sanitizedName = applicantName.replace(/[^a-zA-Z0-9]/g, '_');
      
      let fileExtension = '.pdf';
      const contentType = response.headers['content-type'];
      if (contentType) {
        if (contentType.includes('pdf')) fileExtension = '.pdf';
        else if (contentType.includes('word')) fileExtension = '.doc';
        else if (contentType.includes('plain')) fileExtension = '.txt';
      }
      
      link.download = `resume_${sanitizedName}${fileExtension}`;
      document.body.appendChild(link);
      link.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(link);
      
    } catch (error) {
      alert('Unable to download resume. Please try again.');
    }
  };

  const handleStatusUpdate = async (applicationId, action, data = {}, options = {}) => {
    const { silent = false } = options;
    if (!silent) setActionLoading(applicationId);

    try {
      const currentUser = JSON.parse(localStorage.getItem('user'));
      let rId = job.recruiter?.id;

      if (!rId && currentUser?.recruiterId) {
        rId = currentUser.recruiterId;
      }

      if (!rId && currentUser?.id) {
        const profileResponse = await api.get(`/recruiters/user/${currentUser.id}`);
        if (profileResponse.data.recruiter) {
          rId = profileResponse.data.recruiter.id;
        }
      }

      if (!rId) {
        alert('Recruiter information not found');
        return;
      }

      let response;
      
      switch (action) {
        case 'view':
          response = await api.put(`/applications/${applicationId}/view?recruiterId=${rId}`);
          break;
        case 'shortlist':
          response = await api.put(`/applications/${applicationId}/shortlist?recruiterId=${rId}`, {
            notes: data.notes || 'Application shortlisted'
          });
          break;
        case 'assign-test':
          if (data.testType === 'dreamrole') {
            response = await api.post('/dream-role-tests/assign', {
              testId: data.testId,
              applicationId,
              deadlineHours: data.deadlineHours || 48
            });
          } else {
            response = await api.post('/aptitude-tests/assign', {
              testId: data.testId,
              applicationId,
              deadlineHours: data.deadlineHours || 48
            });
          }
          break;
        case 'schedule-interview':
          response = await api.put(`/applications/${applicationId}/schedule-interview?recruiterId=${rId}`, {
            interviewDate: data.interviewDate,
            interviewLocation: data.interviewLocation,
            notes: data.notes || 'Interview scheduled'
          });
          break;
        case 'reject':
          response = await api.put(`/applications/${applicationId}/reject?recruiterId=${rId}`, {
            rejectionReason: data.rejectionReason || 'Application rejected'
          });
          break;
        case 'send-offer':
          response = await api.put(`/applications/${applicationId}/send-offer?recruiterId=${rId}`, {
            offerDetails: data.offerDetails || 'Offer sent'
          });
          break;
        default:
          break;
      }

      if (response && response.data) {
        if (!silent) {
          alert('Status updated successfully!');
          fetchApplications();
          setShowStatusModal(false);
          setShowInterviewModal(false);
          setShowTestModal(false);
        }
        return { success: true };
      }
      return { success: false, error: 'No response from server' };
    } catch (error) {
      console.error('Error updating status:', error);
      const message = error.response?.data?.error || error.message;
      if (!silent) {
        alert('Failed to update status: ' + message);
      }
      return { success: false, error: message };
    } finally {
      if (!silent) setActionLoading(null);
    }
  };

  const toggleSelected = (applicationId) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(applicationId)) {
        next.delete(applicationId);
      } else {
        next.add(applicationId);
      }
      return next;
    });
  };

  const toggleSelectAll = () => {
    setSelectedIds((prev) =>
      prev.size === filteredApplications.length ? new Set() : new Set(filteredApplications.map((a) => a.id))
    );
  };

  const handleBulkAction = async (action) => {
    const ids = Array.from(selectedIds);
    if (ids.length === 0) return;

    const label = action === 'shortlist' ? 'shortlist' : 'reject';
    if (!window.confirm(`${label === 'shortlist' ? 'Shortlist' : 'Reject'} ${ids.length} selected application${ids.length === 1 ? '' : 's'}?`)) {
      return;
    }

    setBulkActionLoading(true);
    setBulkResultMessage('');
    const data = action === 'shortlist'
      ? { notes: 'Bulk shortlisted' }
      : { rejectionReason: 'Application rejected' };

    const results = await Promise.all(ids.map((id) => handleStatusUpdate(id, action, data, { silent: true })));
    const succeeded = results.filter((r) => r.success).length;
    const failed = results.length - succeeded;

    setBulkResultMessage(
      failed === 0
        ? `${succeeded} application${succeeded === 1 ? '' : 's'} ${label}ed.`
        : `${succeeded} ${label}ed, ${failed} failed.`
    );
    setSelectedIds(new Set());
    setBulkActionLoading(false);
    fetchApplications();
  };

  // One message to every selected candidate's own thread with the recruiter - previously a
  // recruiter had to open each candidate's thread separately to send the same update/nudge to
  // everyone shortlisted for (or still pending on) this job. Takes the body as an argument
  // (from BulkMessageModal's own local state) rather than reading it from state up here: this
  // component is redefined fresh on every render of ApplicationsList, so if the typed text lived
  // in ApplicationsList's own state, every keystroke would re-render ApplicationsList and remount
  // the modal (losing focus) - exactly like InterviewModal below keeps its own fields local for
  // the same reason.
  const handleSendBulkMessage = async (body) => {
    const ids = Array.from(selectedIds);
    if (ids.length === 0 || !body.trim()) return;

    setSendingBulkMessage(true);
    setBulkResultMessage('');
    try {
      const response = await api.post(`/recruiters/jobs/${job.id}/applications/bulk-message`, {
        applicationIds: ids,
        body: body.trim(),
      });
      const { sentCount, failedCount } = response.data;
      setBulkResultMessage(
        failedCount === 0
          ? `Message sent to ${sentCount} candidate${sentCount === 1 ? '' : 's'}.`
          : `Sent to ${sentCount}, failed for ${failedCount}.`
      );
      setShowBulkMessageModal(false);
      setSelectedIds(new Set());
    } catch (error) {
      alert('Failed to send bulk message: ' + (error.response?.data?.error || error.message));
    } finally {
      setSendingBulkMessage(false);
    }
  };

  const handleExportCsv = async () => {
    let rId = recruiterId;
    if (!rId) {
      const currentUser = JSON.parse(localStorage.getItem('user') || '{}');
      rId = currentUser?.recruiterId;
    }
    if (!rId) return;

    setExportingCsv(true);
    try {
      const response = await api.get(`/recruiters/jobs/${job.id}/applications/export?recruiterId=${rId}`, {
        responseType: 'blob',
      });
      const url = window.URL.createObjectURL(new Blob([response.data], { type: 'text/csv' }));
      const link = document.createElement('a');
      link.href = url;
      link.download = `applicants_${(job.title || 'job').replace(/[^a-z0-9]+/gi, '_').toLowerCase()}.csv`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Error exporting applicants:', error);
      alert('Failed to export applicants: ' + (error.response?.data?.error || error.message));
    } finally {
      setExportingCsv(false);
    }
  };

  const KANBAN_COLUMNS = [
    {
      id: 'applied',
      label: 'Applied',
      icon: Clock,
      color: 'border-blue-200 bg-blue-50/20',
      badgeClass: 'bg-blue-100 text-blue-800 border-blue-200',
      statuses: ['APPLICATION_SUBMITTED', 'VIEWED_BY_RECRUITER'],
      description: 'New & unreviewed applicants',
    },
    {
      id: 'shortlisted',
      label: 'Shortlisted',
      icon: CheckCircle,
      color: 'border-emerald-200 bg-emerald-50/20',
      badgeClass: 'bg-emerald-100 text-emerald-800 border-emerald-200',
      statuses: ['SHORTLISTED'],
      description: 'Passed initial screening',
    },
    {
      id: 'assessment',
      label: 'Assessment',
      icon: BookOpen,
      color: 'border-purple-200 bg-purple-50/20',
      badgeClass: 'bg-purple-100 text-purple-800 border-purple-200',
      statuses: ['TEST_ASSIGNED', 'TEST_IN_PROGRESS', 'TEST_COMPLETED', 'TEST_PASSED', 'TEST_FAILED'],
      description: 'Tests assigned or completed',
    },
    {
      id: 'interview',
      label: 'Interview',
      icon: Video,
      color: 'border-amber-200 bg-amber-50/20',
      badgeClass: 'bg-amber-100 text-amber-800 border-amber-200',
      statuses: ['INTERVIEW_SCHEDULED', 'INTERVIEW_COMPLETED'],
      description: 'Live or scheduled rounds',
    },
    {
      id: 'offer',
      label: 'Offer / Hired',
      icon: Star,
      color: 'border-teal-200 bg-teal-50/20',
      badgeClass: 'bg-teal-100 text-teal-800 border-teal-200',
      statuses: ['OFFER_SENT', 'OFFER_ACCEPTED'],
      description: 'Offers extended or accepted',
    },
    {
      id: 'rejected',
      label: 'Rejected',
      icon: XCircle,
      color: 'border-rose-200 bg-rose-50/20',
      badgeClass: 'bg-rose-100 text-rose-800 border-rose-200',
      statuses: ['REJECTED', 'OFFER_DECLINED', 'WITHDRAWN'],
      description: 'Not moving forward',
    },
  ];

  const getColumnIdForStatus = (status) => {
    for (const col of KANBAN_COLUMNS) {
      if (col.statuses.includes(status)) return col.id;
    }
    return 'applied';
  };

  const handleDropOnColumn = async (targetColumnId) => {
    if (!draggedApp) return;
    const currentColumnId = getColumnIdForStatus(draggedApp.status);
    const candidateName = draggedApp.applicantName || draggedApp.applicant?.fullName || 'Candidate';
    const appId = draggedApp.id;
    const appToMove = draggedApp;

    if (currentColumnId === targetColumnId) {
      setDraggedApp(null);
      setActiveDropColumn(null);
      return;
    }

    if (targetColumnId === 'shortlisted') {
      setKanbanToast({ message: `Shortlisting ${candidateName}...`, type: 'info' });
      setApplications((prev) =>
        prev.map((a) => (a.id === appId ? { ...a, status: 'SHORTLISTED' } : a))
      );
      const res = await handleStatusUpdate(appId, 'shortlist', { notes: 'Shortlisted via Kanban' }, { silent: true });
      if (res?.success) {
        setKanbanToast({ message: `✓ ${candidateName} moved to Shortlisted!`, type: 'success' });
      } else {
        setKanbanToast({ message: `Failed to shortlist ${candidateName}`, type: 'error' });
        fetchApplications();
      }
    } else if (targetColumnId === 'rejected') {
      if (window.confirm(`Reject application for ${candidateName}?`)) {
        setKanbanToast({ message: `Rejecting ${candidateName}...`, type: 'info' });
        setApplications((prev) =>
          prev.map((a) => (a.id === appId ? { ...a, status: 'REJECTED' } : a))
        );
        const res = await handleStatusUpdate(appId, 'reject', { rejectionReason: 'Rejected via Kanban' }, { silent: true });
        if (res?.success) {
          setKanbanToast({ message: `✓ ${candidateName} moved to Rejected`, type: 'success' });
        } else {
          setKanbanToast({ message: `Failed to reject ${candidateName}`, type: 'error' });
          fetchApplications();
        }
      }
    } else if (targetColumnId === 'assessment') {
      handleAssignTest(appToMove);
    } else if (targetColumnId === 'interview') {
      setSelectedApplication(appToMove);
      setShowInterviewModal(true);
    } else if (targetColumnId === 'offer') {
      setOfferApplicationId(appId);
    } else if (targetColumnId === 'applied') {
      const res = await handleStatusUpdate(appId, 'view', {}, { silent: true });
      if (res?.success) {
        setApplications((prev) =>
          prev.map((a) => (a.id === appId ? { ...a, status: 'VIEWED_BY_RECRUITER' } : a))
        );
      }
    }

    setDraggedApp(null);
    setActiveDropColumn(null);
  };

  const getCandidateDisplayName = (app) => {
    if (blindScreening) {
      return `Candidate #${app.id}`;
    }
    return app.applicantName || app.applicant?.fullName || 'Candidate';
  };

  const getCandidateDisplayEmail = (app) => {
    if (blindScreening) {
      return 'Identity & contact info hidden for blind screening';
    }
    return app.applicantEmail || app.applicant?.email || 'No email provided';
  };

  const filteredApplications = applications.filter((app) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    const name = (app.applicantName || app.applicant?.fullName || '').toLowerCase();
    const email = (app.applicantEmail || app.applicant?.email || '').toLowerCase();
    const skills = (app.applicantSkills || '').toLowerCase();
    return name.includes(q) || email.includes(q) || skills.includes(q);
  });

  const handleAssignTest = (application) => {
    setSelectedApplication(application);
    setShowTestModal(true);
  };

  // Test Assignment Modal Component
  const TestAssignmentModal = () => {
    const [localTestId, setLocalTestId] = useState(selectedTestId);
    const [localDeadline, setLocalDeadline] = useState(deadlineHours);
    const [localTestType, setLocalTestType] = useState(testType);

    if (!selectedApplication) return null;

    return (
      <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50">
        <div className="relative top-20 mx-auto p-5 border w-full max-w-md shadow-lg rounded-md bg-white">
          <div className="flex justify-between items-center mb-4">
            <h3 className="text-lg font-semibold">Assign Test</h3>
            <button 
              onClick={() => setShowTestModal(false)} 
              className="text-gray-400 hover:text-gray-600"
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          <div className="space-y-4">
            {/* Test Type Selection */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Test Type
              </label>
              <div className="flex space-x-4">
                <label className="flex items-center">
                  <input
                    type="radio"
                    name="testType"
                    value="manual"
                    checked={localTestType === 'manual'}
                    onChange={() => {
                      setLocalTestType('manual');
                      setTestType('manual');
                      setLocalTestId('');
                      setSelectedTestId('');
                    }}
                    className="mr-2"
                  />
                  <span className="text-sm">Manual Test</span>
                </label>
                <label className="flex items-center">
                  <input
                    type="radio"
                    name="testType"
                    value="dreamrole"
                    checked={localTestType === 'dreamrole'}
                    onChange={() => {
                      setLocalTestType('dreamrole');
                      setTestType('dreamrole');
                      setLocalTestId('');
                      setSelectedTestId('');
                    }}
                    className="mr-2"
                  />
                  <span className="text-sm flex items-center">
                    <Sparkles className="h-4 w-4 text-purple-600 mr-1" />
                    DreamRole Test
                  </span>
                </label>
              </div>
            </div>

            {/* Test Selection Dropdown */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Select Test <span className="text-red-500">*</span>
              </label>
              
              {localTestType === 'manual' ? (
                <select
                  value={localTestId}
                  onChange={(e) => {
                    setLocalTestId(e.target.value);
                    setSelectedTestId(e.target.value);
                  }}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="">Choose a manual test</option>
                  {tests.map(test => (
                    <option key={test.id} value={test.id}>
                      {test.testName} ({test.totalQuestions || 0} questions • {test.durationMinutes} mins)
                    </option>
                  ))}
                </select>
              ) : (
                <select
                  value={localTestId}
                  onChange={(e) => {
                    setLocalTestId(e.target.value);
                    setSelectedTestId(e.target.value);
                  }}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500"
                >
                  <option value="">Choose a DreamRole test</option>
                  {dreamRoleTests.map(test => (
                    <option key={test.id} value={test.id}>
                      {test.testName} ({test.targetBranch}) - {test.technicalQuestionsAdded || 0}/10 tech
                    </option>
                  ))}
                </select>
              )}

              {localTestType === 'manual' && tests.length === 0 && (
                <div className="mt-2 p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
                  <p className="text-sm text-yellow-800 font-medium">No manual tests available</p>
                  <p className="text-xs text-yellow-600 mt-1">
                    Please create a manual test first.
                  </p>
                </div>
              )}

              {localTestType === 'dreamrole' && dreamRoleTests.length === 0 && (
                <div className="mt-2 p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
                  <p className="text-sm text-yellow-800 font-medium">No DreamRole tests available</p>
                  <p className="text-xs text-yellow-600 mt-1">
                    Please create a DreamRole test first.
                  </p>
                </div>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Deadline (hours) <span className="text-red-500">*</span>
              </label>
              <input
                type="number"
                min="1"
                max="168"
                value={localDeadline}
                onChange={(e) => {
                  setLocalDeadline(parseInt(e.target.value));
                  setDeadlineHours(parseInt(e.target.value));
                }}
                className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <p className="text-xs text-gray-500 mt-1">
                Candidate must complete test within {localDeadline} hours
              </p>
            </div>

            {localTestId && (
              <div className={`p-3 rounded-lg ${localTestType === 'dreamrole' ? 'bg-purple-50' : 'bg-blue-50'}`}>
                <h4 className={`font-medium mb-2 ${localTestType === 'dreamrole' ? 'text-purple-800' : 'text-blue-800'}`}>
                  Test Details:
                </h4>
                {localTestType === 'manual' ? (
                  <>
                    <p className="text-sm text-blue-700">
                      {tests.find(t => t.id === parseInt(localTestId))?.testName}
                    </p>
                    <p className="text-xs text-blue-600 mt-1">
                      {tests.find(t => t.id === parseInt(localTestId))?.totalQuestions} questions • 
                      {tests.find(t => t.id === parseInt(localTestId))?.durationMinutes} minutes
                    </p>
                  </>
                ) : (
                  <>
                    <p className="text-sm text-purple-700">
                      {dreamRoleTests.find(t => t.id === parseInt(localTestId))?.testName}
                    </p>
                    <p className="text-xs text-purple-600 mt-1">
                      Branch: {dreamRoleTests.find(t => t.id === parseInt(localTestId))?.targetBranch} • 
                      40 Aptitude + {dreamRoleTests.find(t => t.id === parseInt(localTestId))?.technicalQuestionsAdded || 0} Technical
                    </p>
                  </>
                )}
              </div>
            )}
          </div>

          <div className="flex justify-end space-x-3 mt-6">
            <button
              onClick={() => setShowTestModal(false)}
              className="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 bg-white hover:bg-gray-50"
            >
              Cancel
            </button>
            <button
              onClick={() => handleStatusUpdate(selectedApplication.id, 'assign-test', {
                testId: localTestId,
                deadlineHours: localDeadline,
                testType: localTestType
              })}
              disabled={!localTestId || actionLoading === selectedApplication.id}
              className={`px-4 py-2 text-white rounded-md text-sm font-medium disabled:opacity-50 ${
                localTestType === 'dreamrole' 
                  ? 'bg-purple-600 hover:bg-purple-700' 
                  : 'bg-blue-600 hover:bg-blue-700'
              }`}
            >
              {actionLoading === selectedApplication.id ? 'Assigning...' : 'Assign Test'}
            </button>
          </div>
        </div>
      </div>
    );
  };

  // Test Results Modal Component
  const CompareModal = () => {
    const compared = filteredApplications.filter((a) => selectedIds.has(a.id));
    if (compared.length === 0) return null;

    const rows = [
      { label: 'Candidate', render: (a) => a.applicantName || a.applicant?.fullName || 'Unknown' },
      { label: 'Match Score', render: (a) => a.matchPercentage > 0 ? `${a.matchPercentage}%` : '—' },
      {
        label: 'Status',
        render: (a) => (
          <span className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-medium ${getStatusColor(a.status)}`}>
            {a.status?.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (l) => l.toUpperCase()) || 'Unknown'}
          </span>
        ),
      },
      { label: 'Experience', render: (a) => a.applicantExperience || '—' },
      { label: 'Education', render: (a) => a.applicantEducation || '—' },
      {
        label: 'Skills',
        render: (a) => (
          <div className="flex flex-wrap gap-1">
            {parseSkills(a.applicantSkills).length > 0
              ? parseSkills(a.applicantSkills).map((skill, i) => (
                  <span key={i} className="px-2 py-0.5 bg-blue-50 text-blue-700 rounded text-xs">{skill}</span>
                ))
              : <span className="text-gray-400">—</span>}
          </div>
        ),
      },
      { label: 'Applied', render: (a) => formatDate(a.applicationDate) },
    ];

    return (
      <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50">
        <div className="relative top-10 mx-auto p-5 border w-full max-w-4xl shadow-lg rounded-md bg-white">
          <div className="flex justify-between items-center mb-4">
            <h3 className="text-lg font-semibold flex items-center">
              <GitCompare className="h-5 w-5 mr-2 text-blue-600" />
              Comparing {compared.length} candidate{compared.length === 1 ? '' : 's'}
            </h3>
            <button
              onClick={() => setShowCompareModal(false)}
              aria-label="Close comparison"
              className="text-gray-400 hover:text-gray-600"
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          <div className="overflow-x-auto">
            <table className="min-w-full border border-gray-200 rounded-lg">
              <tbody>
                {rows.map((row) => (
                  <tr key={row.label} className="border-b border-gray-100 last:border-0">
                    <th className="text-left align-top px-3 py-3 bg-gray-50 text-xs font-semibold text-gray-500 uppercase tracking-wide w-32">
                      {row.label}
                    </th>
                    {compared.map((application) => (
                      <td key={application.id} className="px-3 py-3 text-sm text-gray-800 align-top border-l border-gray-100">
                        {row.render(application)}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    );
  };

  const TestResultsModal = () => {
    if (!testResults) return null;

    return (
      <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50">
        <div className="relative top-20 mx-auto p-5 border w-full max-w-lg shadow-lg rounded-md bg-white">
          <div className="flex justify-between items-center mb-4">
            <h3 className="text-lg font-semibold">Test Results</h3>
            <button 
              onClick={() => setShowTestResultsModal(false)} 
              className="text-gray-400 hover:text-gray-600"
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          <div className="space-y-4">
            <div className="bg-gray-50 p-4 rounded-lg">
              <h4 className="font-medium text-gray-800 mb-2">{testResults.testName}</h4>
              
              <div className="grid grid-cols-2 gap-3 mb-3">
                <div className="text-center p-2 bg-blue-50 rounded">
                  <div className="text-2xl font-bold text-blue-600">{testResults.score || 0}</div>
                  <div className="text-xs text-gray-600">Score</div>
                </div>
                <div className="text-center p-2 bg-green-50 rounded">
                  <div className="text-2xl font-bold text-green-600">{testResults.totalQuestions || 0}</div>
                  <div className="text-xs text-gray-600">Total</div>
                </div>
              </div>

              <div className="mb-2">
                <div className="flex justify-between text-sm mb-1">
                  <span>Percentage</span>
                  <span className="font-bold">{testResults.percentage || 0}%</span>
                </div>
                <div className="w-full bg-gray-200 rounded-full h-2">
                  <div 
                    className={`h-2 rounded-full ${
                      testResults.passed ? 'bg-green-500' : 'bg-red-500'
                    }`}
                    style={{ width: `${testResults.percentage || 0}%` }}
                  ></div>
                </div>
              </div>

              <div className="mt-3 flex items-center justify-between">
                <span className="text-sm text-gray-600">Status</span>
                <span className={`px-2 py-1 rounded-full text-xs font-medium ${
                  testResults.passed 
                    ? 'bg-green-100 text-green-800'
                    : testResults.status === 'PENDING'
                    ? 'bg-yellow-100 text-yellow-800'
                    : 'bg-red-100 text-red-800'
                }`}>
                  {testResults.passed ? 'PASSED' : testResults.status}
                </span>
              </div>

              {testResults.completedAt && (
                <p className="text-xs text-gray-500 mt-2">
                  Completed: {formatDate(testResults.completedAt)}
                </p>
              )}
            </div>

            {testResults.passed && (
              <div className="bg-green-50 border border-green-200 rounded-lg p-4">
                <p className="text-green-800 text-sm font-medium">
                  ✅ Candidate has passed the test. You can now schedule an interview.
                </p>
              </div>
            )}

            {testResults.status === 'PENDING' && (
              <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4">
                <p className="text-yellow-800 text-sm">
                  ⏳ Test is pending. Candidate needs to complete the test.
                </p>
              </div>
            )}
          </div>

          <div className="flex justify-end mt-6">
            <button
              onClick={() => setShowTestResultsModal(false)}
              className="px-4 py-2 bg-blue-600 text-white rounded-md text-sm font-medium hover:bg-blue-700"
            >
              Close
            </button>
          </div>
        </div>
      </div>
    );
  };

  // Bulk Message Modal Component - one message to every selected candidate's own thread. The
  // typed text is local state (like InterviewModal's fields below), not lifted to
  // ApplicationsList - see the comment on handleSendBulkMessage for why that matters.
  const BulkMessageModal = () => {
    const [localBody, setLocalBody] = useState('');
    const count = selectedIds.size;
    return (
      <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50">
        <div className="relative top-20 mx-auto p-5 border w-full max-w-md shadow-lg rounded-md bg-white">
          <div className="flex justify-between items-center mb-4">
            <h3 className="text-lg font-semibold">
              Message {count} candidate{count === 1 ? '' : 's'}
            </h3>
            <button
              onClick={() => setShowBulkMessageModal(false)}
              aria-label="Close"
              className="text-gray-400 hover:text-gray-600"
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          <textarea
            value={localBody}
            onChange={(e) => setLocalBody(e.target.value)}
            rows={5}
            placeholder="Write one message to send to everyone selected..."
            className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <p className="text-xs text-gray-500 mt-1">
            Each candidate gets this in their own message thread with you - replies come back individually.
          </p>

          <div className="flex justify-end space-x-3 mt-4">
            <button
              onClick={() => setShowBulkMessageModal(false)}
              className="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 bg-white hover:bg-gray-50"
            >
              Cancel
            </button>
            <button
              onClick={() => handleSendBulkMessage(localBody)}
              disabled={!localBody.trim() || sendingBulkMessage}
              className="px-4 py-2 bg-blue-600 text-white rounded-md text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
            >
              {sendingBulkMessage ? 'Sending...' : `Send to ${count}`}
            </button>
          </div>
        </div>
      </div>
    );
  };

  // Interview Modal Component
  const InterviewModal = () => {
    const [interviewDate, setInterviewDate] = useState('');
    const [interviewTime, setInterviewTime] = useState('');
    const [interviewLocation, setInterviewLocation] = useState('');
    const [notes, setNotes] = useState('');
    const [errors, setErrors] = useState({});

    if (!selectedApplication) return null;

    const validateForm = () => {
      const newErrors = {};
      
      if (!interviewDate) {
        newErrors.interviewDate = 'Interview date is required';
      }
      
      if (!interviewTime) {
        newErrors.interviewTime = 'Interview time is required';
      }
      
      if (!interviewLocation.trim()) {
        newErrors.interviewLocation = 'Interview location is required';
      }
      
      setErrors(newErrors);
      return Object.keys(newErrors).length === 0;
    };

    const handleSubmit = () => {
      if (!validateForm()) return;

      const dateTimeString = `${interviewDate}T${interviewTime}:00`;
      
      const data = {
        interviewDate: dateTimeString,
        interviewLocation: interviewLocation.trim(),
        notes: notes || `Interview scheduled for ${new Date(dateTimeString).toLocaleString()} at ${interviewLocation}`
      };

      handleStatusUpdate(selectedApplication.id, 'schedule-interview', data);
    };

    const getMinDate = () => {
      const tomorrow = new Date();
      tomorrow.setDate(tomorrow.getDate() + 1);
      return tomorrow.toISOString().split('T')[0];
    };

    return (
      <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50">
        <div className="relative top-20 mx-auto p-5 border w-full max-w-md shadow-lg rounded-md bg-white">
          <div className="flex justify-between items-center mb-4">
            <h3 className="text-lg font-semibold">Schedule Interview</h3>
            <button 
              onClick={() => setShowInterviewModal(false)} 
              className="text-gray-400 hover:text-gray-600"
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Interview Date <span className="text-red-500">*</span>
              </label>
              <input
                type="date"
                min={getMinDate()}
                value={interviewDate}
                onChange={(e) => setInterviewDate(e.target.value)}
                className={`w-full border rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                  errors.interviewDate ? 'border-red-500' : 'border-gray-300'
                }`}
              />
              {errors.interviewDate && (
                <p className="text-red-500 text-xs mt-1">{errors.interviewDate}</p>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Interview Time <span className="text-red-500">*</span>
              </label>
              <input
                type="time"
                value={interviewTime}
                onChange={(e) => setInterviewTime(e.target.value)}
                className={`w-full border rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                  errors.interviewTime ? 'border-red-500' : 'border-gray-300'
                }`}
              />
              {errors.interviewTime && (
                <p className="text-red-500 text-xs mt-1">{errors.interviewTime}</p>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Interview Location <span className="text-red-500">*</span>
              </label>
              <div className="flex items-center">
                <MapPin className="h-4 w-4 text-gray-400 mr-2" />
                <input
                  type="text"
                  value={interviewLocation}
                  onChange={(e) => setInterviewLocation(e.target.value)}
                  placeholder="Enter interview location (e.g., Main Office, Zoom, etc.)"
                  className={`w-full border rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                    errors.interviewLocation ? 'border-red-500' : 'border-gray-300'
                  }`}
                />
              </div>
              {errors.interviewLocation && (
                <p className="text-red-500 text-xs mt-1">{errors.interviewLocation}</p>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Additional Notes</label>
              <textarea
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                rows="3"
                placeholder="Additional instructions for the candidate..."
              />
            </div>

            {(interviewDate && interviewTime && interviewLocation) && (
              <div className="p-3 bg-blue-50 border border-blue-200 rounded-md">
                <h4 className="font-medium text-blue-800 mb-2">Interview Preview:</h4>
                <p className="text-sm text-blue-700">
                  <strong>Date:</strong> {new Date(`${interviewDate}T${interviewTime}`).toLocaleDateString('en-US', { 
                    weekday: 'long', 
                    year: 'numeric', 
                    month: 'long', 
                    day: 'numeric' 
                  })}
                </p>
                <p className="text-sm text-blue-700">
                  <strong>Time:</strong> {new Date(`${interviewDate}T${interviewTime}`).toLocaleTimeString('en-US', {
                    hour: '2-digit',
                    minute: '2-digit'
                  })}
                </p>
                <p className="text-sm text-blue-700">
                  <strong>Location:</strong> {interviewLocation}
                </p>
              </div>
            )}
          </div>

          <div className="flex justify-end space-x-3 mt-6">
            <button
              onClick={() => setShowInterviewModal(false)}
              className="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 bg-white hover:bg-gray-50"
            >
              Cancel
            </button>
            <button
              onClick={handleSubmit}
              disabled={actionLoading === selectedApplication.id}
              className="px-4 py-2 bg-blue-600 text-white rounded-md text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
            >
              {actionLoading === selectedApplication.id ? 'Scheduling...' : 'Schedule Interview'}
            </button>
          </div>
        </div>
      </div>
    );
  };

  // Status Update Modal Component
  const StatusUpdateModal = () => {
    const [notes, setNotes] = useState('');
    const [rejectionReason, setRejectionReason] = useState('');
    const [offerDetails, setOfferDetails] = useState('');

    if (!selectedApplication) return null;

    const handleSubmit = () => {
      const action = showStatusModal === 'reject' ? 'reject' :
                    showStatusModal === 'offer' ? 'send-offer' : 'shortlist';
      
      const data = showStatusModal === 'reject' ? {
        rejectionReason: rejectionReason || 'Not selected'
      } : showStatusModal === 'offer' ? {
        offerDetails: offerDetails || 'Congratulations! We are pleased to offer you the position.'
      } : {
        notes: notes || 'Application shortlisted'
      };

      handleStatusUpdate(selectedApplication.id, action, data);
    };

    return (
      <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50">
        <div className="relative top-20 mx-auto p-5 border w-full max-w-md shadow-lg rounded-md bg-white">
          <div className="flex justify-between items-center mb-4">
            <h3 className="text-lg font-semibold">
              {showStatusModal === 'reject' && 'Reject Application'}
              {showStatusModal === 'offer' && 'Send Offer'}
              {showStatusModal === 'shortlist' && 'Shortlist Application'}
            </h3>
            <button onClick={() => setShowStatusModal(false)} className="text-gray-400 hover:text-gray-600">
              <X className="h-5 w-5" />
            </button>
          </div>

          {showStatusModal === 'reject' && (
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Rejection Reason</label>
                <textarea
                  value={rejectionReason}
                  onChange={(e) => setRejectionReason(e.target.value)}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  rows="3"
                  placeholder="Reason for rejection..."
                />
              </div>
            </div>
          )}

          {showStatusModal === 'offer' && (
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Offer Details</label>
                <textarea
                  value={offerDetails}
                  onChange={(e) => setOfferDetails(e.target.value)}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  rows="4"
                  placeholder="Offer details, salary, benefits, start date..."
                />
              </div>
            </div>
          )}

          {showStatusModal === 'shortlist' && (
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
                <textarea
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  rows="3"
                  placeholder="Notes about why this candidate was shortlisted..."
                />
              </div>
            </div>
          )}

          <div className="flex justify-end space-x-3 mt-6">
            <button
              onClick={() => setShowStatusModal(false)}
              className="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 bg-white hover:bg-gray-50"
            >
              Cancel
            </button>
            <button
              onClick={handleSubmit}
              disabled={actionLoading === selectedApplication.id}
              className="px-4 py-2 bg-blue-600 text-white rounded-md text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
            >
              {actionLoading === selectedApplication.id ? 'Updating...' : 'Update'}
            </button>
          </div>
        </div>
      </div>
    );
  };

  if (loading) {
    return (
      <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50">
        <div className="relative top-20 mx-auto p-5 border w-full max-w-4xl shadow-lg rounded-md bg-white">
          <div className="text-center py-8">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600 mx-auto"></div>
            <p className="mt-4 text-gray-600">Loading applications...</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50">
      <div className={`relative top-10 mx-auto p-6 border w-full shadow-2xl rounded-xl bg-white transition-all ${
        viewMode === 'kanban' ? 'max-w-[96vw] xl:max-w-[1550px]' : 'max-w-6xl'
      }`}>
        {/* Header */}
        <div className="flex flex-wrap items-center justify-between gap-4 mb-5 pb-4 border-b border-gray-200">
          <div>
            <div className="flex items-center space-x-3">
              <h2 className="text-2xl font-bold text-gray-900">Applications for {job.title}</h2>
              <span className="bg-blue-100 text-blue-800 text-xs font-semibold px-2.5 py-0.5 rounded-full">
                {applications.length} Total
              </span>
            </div>
            <p className="text-gray-500 text-sm mt-0.5">
              {job.company ? `${job.company} • ` : ''}Manage candidates across hiring pipeline stages
            </p>
          </div>

          <div className="flex items-center space-x-3">
            {/* Blind Screening Toggle */}
            <button
              type="button"
              onClick={() => setBlindScreening((prev) => !prev)}
              aria-pressed={blindScreening}
              aria-label={blindScreening ? 'Disable Blind Screening' : 'Enable Blind Screening'}
              className={`inline-flex items-center px-3 py-1.5 rounded-lg text-xs font-semibold transition-all border ${
                blindScreening
                  ? 'bg-purple-100 text-purple-800 border-purple-300 shadow-sm'
                  : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
              }`}
              title="Blind Screening masks candidate names, avatars, and contact information to promote bias-free evaluations"
            >
              {blindScreening ? (
                <>
                  <EyeOff className="h-3.5 w-3.5 mr-1.5 text-purple-600" />
                  <span>Blind Screening: ON</span>
                </>
              ) : (
                <>
                  <Eye className="h-3.5 w-3.5 mr-1.5 text-gray-500" />
                  <span>Blind Screening</span>
                </>
              )}
            </button>

            {/* View Mode Toggle */}
            <div className="inline-flex rounded-lg border border-gray-200 p-1 bg-gray-50" role="group" aria-label="View mode">
              <button
                type="button"
                onClick={() => setViewMode('list')}
                className={`inline-flex items-center px-3 py-1.5 rounded-md text-xs font-medium transition-all ${
                  viewMode === 'list'
                    ? 'bg-white text-blue-700 shadow-sm font-semibold'
                    : 'text-gray-600 hover:text-gray-900'
                }`}
                aria-pressed={viewMode === 'list'}
              >
                <List className="h-3.5 w-3.5 mr-1.5" />
                List View
              </button>
              <button
                type="button"
                onClick={() => setViewMode('kanban')}
                className={`inline-flex items-center px-3 py-1.5 rounded-md text-xs font-medium transition-all ${
                  viewMode === 'kanban'
                    ? 'bg-white text-blue-700 shadow-sm font-semibold'
                    : 'text-gray-600 hover:text-gray-900'
                }`}
                aria-pressed={viewMode === 'kanban'}
              >
                <Columns className="h-3.5 w-3.5 mr-1.5" />
                Kanban Board
              </button>
            </div>

            <button onClick={onClose} className="text-gray-400 hover:text-gray-600 p-2 rounded-lg hover:bg-gray-100 transition-colors">
              <X className="h-6 w-6" />
            </button>
          </div>
        </div>

        {/* Toolbar: Refresh, Search & Sort Info */}
        <div className="flex flex-wrap items-center justify-between gap-3 mb-5">
          <div className="flex items-center space-x-3 flex-1 min-w-[240px]">
            <button
              onClick={fetchApplications}
              className="flex items-center px-3.5 py-1.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors shadow-sm"
            >
              <RefreshCw className="h-4 w-4 mr-1.5" />
              Refresh
            </button>

            {/* Candidate Search */}
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute left-3 top-2.5 h-4 w-4 text-gray-400" />
              <input
                type="text"
                placeholder="Search candidates or skills..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full pl-9 pr-8 py-1.5 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              {searchQuery && (
                <button
                  onClick={() => setSearchQuery('')}
                  className="absolute right-2.5 top-2.5 text-gray-400 hover:text-gray-600"
                  aria-label="Clear search"
                >
                  <X className="h-3.5 w-3.5" />
                </button>
              )}
            </div>
          </div>

          <div className="flex items-center space-x-2 text-xs text-gray-500 bg-gray-50 px-3 py-1.5 rounded-lg border border-gray-200">
            <Target className="h-4 w-4 text-green-600" />
            <span>Ranked by AI Match Score</span>
          </div>
        </div>

        {/* Blind Screening Active Banner */}
        {blindScreening && (
          <div className="mb-4 p-3 bg-purple-50 border border-purple-200 rounded-lg flex items-center justify-between text-xs text-purple-900 shadow-sm animate-fade-in">
            <div className="flex items-center space-x-2">
              <ShieldCheck className="h-4 w-4 text-purple-600 flex-shrink-0" />
              <span>
                <strong>Blind Screening Active:</strong> Personal demographic identifiers, candidate names, and contact details are masked to evaluate candidates strictly on skills, assessment results, and match scores.
              </span>
            </div>
            <button
              onClick={() => setBlindScreening(false)}
              className="text-purple-700 hover:text-purple-900 underline font-medium ml-2 flex-shrink-0"
            >
              Turn off
            </button>
          </div>
        )}

        {/* Error message */}
        {error && (
          <div className="mb-5 p-4 bg-red-100 border border-red-400 text-red-700 rounded-lg">
            <div className="flex items-center">
              <Mail className="h-5 w-5 mr-2" />
              <span>{error}</span>
            </div>
          </div>
        )}

        {/* Kanban Toast notification */}
        {kanbanToast && (
          <div
            className={`mb-4 px-4 py-2.5 rounded-lg text-sm font-medium flex items-center justify-between shadow-sm animate-fade-in ${
              kanbanToast.type === 'success'
                ? 'bg-emerald-50 text-emerald-800 border border-emerald-200'
                : kanbanToast.type === 'error'
                ? 'bg-rose-50 text-rose-800 border border-rose-200'
                : 'bg-blue-50 text-blue-800 border border-blue-200'
            }`}
          >
            <span>{kanbanToast.message}</span>
            <button
              onClick={() => setKanbanToast(null)}
              className="ml-2 text-current opacity-60 hover:opacity-100"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        )}

        {viewMode === 'kanban' ? (
          <div className="overflow-x-auto pb-4 pt-1">
            <div className="flex gap-4 min-w-[1300px]">
              {KANBAN_COLUMNS.map((col) => {
                const ColIcon = col.icon;
                const colApps = filteredApplications.filter((app) =>
                  col.statuses.includes(app.status)
                );
                const isDropTarget = activeDropColumn === col.id;

                return (
                  <div
                    key={col.id}
                    data-testid={`kanban-column-${col.id}`}
                    onDragOver={(e) => {
                      e.preventDefault();
                      if (activeDropColumn !== col.id) {
                        setActiveDropColumn(col.id);
                      }
                    }}
                    onDragLeave={(e) => {
                      if (!e.currentTarget.contains(e.relatedTarget)) {
                        setActiveDropColumn(null);
                      }
                    }}
                    onDrop={(e) => {
                      e.preventDefault();
                      handleDropOnColumn(col.id);
                    }}
                    className={`flex flex-col rounded-xl border p-3 min-w-[220px] flex-1 transition-all ${
                      col.color
                    } ${
                      isDropTarget
                        ? 'ring-2 ring-blue-500 border-blue-400 bg-blue-50/70 shadow-lg scale-[1.01]'
                        : 'border-gray-200'
                    }`}
                  >
                    {/* Column Header */}
                    <div className="flex items-center justify-between mb-3 px-1">
                      <div className="flex items-center space-x-2">
                        <ColIcon className="h-4 w-4" />
                        <span className="font-semibold text-gray-800 text-sm">{col.label}</span>
                      </div>
                      <span className={`text-xs px-2 py-0.5 rounded-full font-bold border ${col.badgeClass}`}>
                        {colApps.length}
                      </span>
                    </div>

                    {/* Column Cards */}
                    <div className="space-y-2.5 flex-1 min-h-[160px] overflow-y-auto max-h-[65vh] pr-1">
                      {colApps.length === 0 ? (
                        <div
                          className={`h-full min-h-[140px] rounded-lg border-2 border-dashed flex flex-col items-center justify-center p-3 text-center transition-colors ${
                            isDropTarget ? 'border-blue-400 bg-blue-100/50' : 'border-gray-300/80 bg-white/40'
                          }`}
                        >
                          <p className="text-xs text-gray-500 font-medium">Drag candidates here</p>
                          <p className="text-[10px] text-gray-400 mt-0.5">{col.description}</p>
                        </div>
                      ) : (
                        colApps.map((app) => (
                          <div
                            key={app.id}
                            data-testid={`kanban-card-${app.id}`}
                            draggable
                            onDragStart={(e) => {
                              e.dataTransfer.setData('text/plain', String(app.id));
                              setDraggedApp(app);
                            }}
                            onDragEnd={() => {
                              setDraggedApp(null);
                              setActiveDropColumn(null);
                            }}
                            className={`bg-white rounded-lg border p-3 shadow-sm hover:shadow-md transition-all cursor-grab active:cursor-grabbing ${
                              draggedApp?.id === app.id
                                ? 'opacity-40 border-dashed border-blue-400'
                                : 'border-gray-200 hover:border-blue-300'
                            }`}
                          >
                            {/* Card Top: Drag handle + Name + Match Badge */}
                            <div className="flex items-start justify-between gap-1.5 mb-1.5">
                              <div className="flex items-center space-x-1.5 min-w-0">
                                <GripVertical className="h-4 w-4 text-gray-400 flex-shrink-0 cursor-grab" />
                                {blindScreening && (
                                  <ShieldCheck className="h-3.5 w-3.5 text-purple-600 flex-shrink-0" />
                                )}
                                <span className={`font-semibold text-sm truncate ${blindScreening ? 'text-purple-900 font-mono' : 'text-gray-900'}`}>
                                  {getCandidateDisplayName(app)}
                                </span>
                              </div>
                              {app.matchPercentage > 0 && (
                                <span className={`flex-shrink-0 text-xs font-bold px-1.5 py-0.5 rounded-full border ${getMatchColor(app.matchPercentage)}`}>
                                  {app.matchPercentage}%
                                </span>
                              )}
                            </div>

                            {/* Email */}
                            <p className="text-xs text-gray-500 truncate mb-2">
                              {getCandidateDisplayEmail(app)}
                            </p>

                            {/* Skills pills */}
                            {app.applicantSkills && (
                              <div className="flex flex-wrap gap-1 mb-2.5">
                                {parseSkills(app.applicantSkills).slice(0, 3).map((sk, idx) => (
                                  <span key={idx} className="bg-slate-100 text-slate-700 text-[10px] px-1.5 py-0.5 rounded font-medium">
                                    {sk}
                                  </span>
                                ))}
                                {parseSkills(app.applicantSkills).length > 3 && (
                                  <span className="text-[10px] text-gray-400">
                                    +{parseSkills(app.applicantSkills).length - 3}
                                  </span>
                                )}
                              </div>
                            )}

                            {/* Footer: status and quick action icons */}
                            <div className="pt-2 border-t border-gray-100 flex items-center justify-between text-xs">
                              <span className={`inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium ${getStatusColor(app.status)}`}>
                                {getStatusIcon(app.status)}
                                <span className="ml-1 capitalize">{app.status?.replace(/_/g, ' ').toLowerCase()}</span>
                              </span>

                              <div className="flex items-center space-x-1">
                                <button
                                  type="button"
                                  onClick={() => setMessageThreadApplication(app)}
                                  title="Message Candidate"
                                  className="p-1 text-gray-400 hover:text-blue-600 rounded hover:bg-gray-100 transition-colors"
                                >
                                  <MessageCircle className="h-3.5 w-3.5" />
                                </button>
                                {app.hasResume && (
                                  <button
                                    type="button"
                                    onClick={() => handleDownloadResume(app)}
                                    title="Download Resume"
                                    className="p-1 text-gray-400 hover:text-blue-600 rounded hover:bg-gray-100 transition-colors"
                                  >
                                    <Download className="h-3.5 w-3.5" />
                                  </button>
                                )}
                                <button
                                  type="button"
                                  onClick={() => setPipelineApplicationId(app.id)}
                                  title="Interview Pipeline"
                                  className="p-1 text-gray-400 hover:text-teal-600 rounded hover:bg-gray-100 transition-colors"
                                >
                                  <Video className="h-3.5 w-3.5" />
                                </button>
                                {col.id === 'applied' && (
                                  <button
                                    type="button"
                                    onClick={async () => {
                                      const res = await handleStatusUpdate(app.id, 'shortlist', { notes: 'Shortlisted from Kanban' });
                                      if (res?.success) fetchApplications();
                                    }}
                                    title="Quick Shortlist"
                                    className="p-1 text-gray-400 hover:text-emerald-600 rounded hover:bg-gray-100 transition-colors"
                                  >
                                    <CheckCircle className="h-3.5 w-3.5" />
                                  </button>
                                )}
                              </div>
                            </div>
                          </div>
                        ))
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        ) : (
          /* List View */
          <>
            {/* Bulk Action Bar */}
            {applications.length > 0 && (
              <div className="mb-4 p-4 bg-gray-50 border border-gray-200 rounded-lg flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center space-x-3">
                  <label className="flex items-center space-x-2 cursor-pointer text-sm font-medium text-gray-700">
                    <input
                      type="checkbox"
                      checked={filteredApplications.length > 0 && selectedIds.size === filteredApplications.length}
                      onChange={toggleSelectAll}
                      aria-label="Select all applications"
                      className="h-4 w-4 text-blue-600 rounded border-gray-300 focus:ring-blue-500 cursor-pointer"
                    />
                    <span>Select All ({filteredApplications.length})</span>
                  </label>
                  {selectedIds.size > 0 && (
                    <span className="text-sm text-gray-500">
                      | {selectedIds.size} selected
                    </span>
                  )}
                </div>

                <div className="flex items-center space-x-2">
                  {selectedIds.size >= 2 && selectedIds.size <= 4 && (
                    <button
                      type="button"
                      onClick={() => setShowCompareModal(true)}
                      className="inline-flex items-center px-3 py-1.5 bg-blue-600 text-white rounded-md text-sm font-medium hover:bg-blue-700 transition-colors shadow-sm"
                    >
                      <GitCompare className="h-4 w-4 mr-1.5" />
                      Compare ({selectedIds.size})
                    </button>
                  )}
                  {selectedIds.size > 0 && (
                    <>
                      <button
                        type="button"
                        onClick={() => setShowBulkMessageModal(true)}
                        disabled={bulkActionLoading}
                        className="inline-flex items-center px-3 py-1.5 bg-blue-600 text-white rounded-md text-sm font-medium hover:bg-blue-700 disabled:opacity-50 transition-colors shadow-sm"
                      >
                        <MessageCircle className="h-4 w-4 mr-1.5" />
                        Message ({selectedIds.size})
                      </button>
                      <button
                        type="button"
                        onClick={() => handleBulkAction('shortlist')}
                        disabled={bulkActionLoading}
                        className="inline-flex items-center px-3 py-1.5 bg-green-600 text-white rounded-md text-sm font-medium hover:bg-green-700 disabled:opacity-50 transition-colors shadow-sm"
                      >
                        <CheckCircle className="h-4 w-4 mr-1.5" />
                        Shortlist ({selectedIds.size})
                      </button>
                      <button
                        type="button"
                        onClick={() => handleBulkAction('reject')}
                        disabled={bulkActionLoading}
                        className="inline-flex items-center px-3 py-1.5 bg-red-600 text-white rounded-md text-sm font-medium hover:bg-red-700 disabled:opacity-50 transition-colors shadow-sm"
                      >
                        <XCircle className="h-4 w-4 mr-1.5" />
                        Reject ({selectedIds.size})
                      </button>
                      <button
                        type="button"
                        onClick={() => setSelectedIds(new Set())}
                        disabled={bulkActionLoading}
                        className="px-2.5 py-1.5 text-xs text-gray-500 hover:text-gray-700 underline"
                      >
                        Clear
                      </button>
                    </>
                  )}
                  <button
                    type="button"
                    onClick={handleExportCsv}
                    disabled={exportingCsv}
                    className="inline-flex items-center px-3 py-1.5 border border-gray-300 rounded-md text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50 transition-colors shadow-sm"
                  >
                    <Download className="h-4 w-4 mr-1.5" />
                    {exportingCsv ? 'Exporting…' : 'Export CSV'}
                  </button>
                </div>

                {bulkResultMessage && (
                  <div className="w-full text-sm font-medium text-blue-700 bg-blue-50 px-3 py-1.5 rounded border border-blue-200">
                    {bulkResultMessage}
                  </div>
                )}
              </div>
            )}

            {filteredApplications.length === 0 ? (
              <div className="text-center py-12">
                <Mail className="mx-auto h-16 w-16 text-gray-400 mb-4" />
                <h3 className="text-lg font-medium text-gray-900 mb-2">
                  {searchQuery ? 'No matching candidates' : 'No applications yet'}
                </h3>
                <p className="text-gray-500">
                  {searchQuery ? `No candidates found matching "${searchQuery}"` : 'Applications will appear here when candidates apply to this job.'}
                </p>
              </div>
            ) : (
              <div className="space-y-6">
                {filteredApplications.map((application, index) => (
              <div
                key={application.id}
                className={`border rounded-lg p-6 hover:shadow-md transition-shadow ${
                  selectedIds.has(application.id) ? 'border-blue-500 bg-blue-50/20' : 'border-gray-200'
                }`}
              >
                <div className="flex justify-between items-start mb-4">
                  <div className="flex items-start space-x-4 flex-1">
                    <div className="pt-2 flex-shrink-0">
                      <input
                        type="checkbox"
                        checked={selectedIds.has(application.id)}
                        onChange={() => toggleSelected(application.id)}
                        aria-label={`Select ${application.applicantName || 'applicant'}`}
                        className="h-4 w-4 text-blue-600 rounded border-gray-300 focus:ring-blue-500 cursor-pointer"
                      />
                    </div>
                    {index < 3 && (
                      <div className={`flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center text-white font-bold text-sm ${
                        index === 0 ? 'bg-yellow-500' : 
                        index === 1 ? 'bg-gray-400' : 
                        'bg-orange-500'
                      }`}>
                        {index + 1}
                      </div>
                    )}
                    
                    <div className={`w-12 h-12 rounded-full flex items-center justify-center flex-shrink-0 ${
                      blindScreening ? 'bg-purple-100 border border-purple-200' : 'bg-blue-100'
                    }`}>
                      {blindScreening ? (
                        <ShieldCheck className="h-6 w-6 text-purple-600" />
                      ) : (
                        <User className="h-6 w-6 text-blue-600" />
                      )}
                    </div>
                    <div className="flex-1">
                      <div className="flex items-center space-x-3 mb-2">
                        <h3 className={`text-xl font-semibold ${blindScreening ? 'text-purple-900 font-mono' : 'text-gray-900'}`}>
                          {getCandidateDisplayName(application)}
                        </h3>
                        
                        {application.matchPercentage > 0 && (
                          <span className={`inline-flex items-center px-3 py-1 rounded-full text-sm font-medium border ${getMatchColor(application.matchPercentage)}`}>
                            <TrendingUp className="h-4 w-4 mr-1" />
                            {application.matchPercentage}% Match
                          </span>
                        )}
                      </div>
                      
                      <p className="text-gray-600">
                        {getCandidateDisplayEmail(application)}
                      </p>
                      
                      <div className="mt-2">
                        <span className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-medium ${getStatusColor(application.status)}`}>
                          {getStatusIcon(application.status)}
                          <span className="ml-1">
                            {application.status?.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase()) || 'Unknown'}
                          </span>
                        </span>
                      </div>
                      
                      {application.applicantSkills && (
                        <div className="mt-2 flex flex-wrap gap-1">
                          {parseSkills(application.applicantSkills).slice(0, 4).map((skill, skillIndex) => (
                            <span
                              key={skillIndex}
                              className="bg-blue-100 text-blue-800 px-2 py-1 rounded text-xs font-medium"
                            >
                              {skill}
                            </span>
                          ))}
                          {parseSkills(application.applicantSkills).length > 4 && (
                            <span className="text-gray-500 text-xs">
                              +{parseSkills(application.applicantSkills).length - 4} more
                            </span>
                          )}
                        </div>
                      )}
                    </div>
                  </div>
                  
                  <div className="flex flex-col items-end space-y-2">
                    <div className="flex space-x-2">
                      {application.hasResume && (
                        <>
                          <button 
                            onClick={() => handleDownloadResume(application)}
                            className="inline-flex items-center px-3 py-1 border border-blue-300 rounded-md text-sm font-medium text-blue-700 bg-white hover:bg-blue-50"
                          >
                            <Download className="h-4 w-4 mr-1" />
                            Resume
                          </button>
                        </>
                      )}
                    </div>
                  </div>
                </div>

                {/* Application Timeline */}
                <div className="mb-4">
                  <h4 className="font-semibold text-gray-800 mb-2 flex items-center">
                    <Clock className="h-4 w-4 mr-2" />
                    Application Timeline
                  </h4>
                  <div className="flex items-center space-x-4 text-sm text-gray-600">
                    <div className="flex items-center">
                      <Calendar className="h-4 w-4 mr-1" />
                      Applied: {formatDate(application.applicationDate)}
                    </div>
                    {application.viewedAt && (
                      <div className="flex items-center">
                        <Eye className="h-4 w-4 mr-1" />
                        Viewed: {formatDate(application.viewedAt)}
                      </div>
                    )}
                  </div>
                </div>

                {/* Detailed Match Analysis */}
                {application.matchPercentage > 0 && (
                  <div className="mb-4 p-4 bg-gradient-to-r from-blue-50 to-indigo-50 border border-blue-200 rounded-lg">
                    <h4 className="font-semibold text-blue-800 mb-3 flex items-center">
                      <Target className="h-4 w-4 mr-2" />
                      AI Resume Match Analysis
                    </h4>
                    
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      <div className="flex items-center justify-between">
                        <span className="text-sm font-medium text-gray-700">Overall Match Score</span>
                        <div className="flex items-center space-x-2">
                          <div className="w-24 bg-gray-200 rounded-full h-2">
                            <div 
                              className={`h-2 rounded-full ${
                                application.matchPercentage >= 80 ? 'bg-green-500' :
                                application.matchPercentage >= 60 ? 'bg-yellow-500' :
                                application.matchPercentage >= 40 ? 'bg-orange-500' : 'bg-red-500'
                              }`}
                              style={{ width: `${application.matchPercentage}%` }}
                            ></div>
                          </div>
                          <span className={`text-sm font-bold ${
                            application.matchPercentage >= 80 ? 'text-green-600' :
                            application.matchPercentage >= 60 ? 'text-yellow-600' :
                            application.matchPercentage >= 40 ? 'text-orange-600' : 'text-red-600'
                          }`}>
                            {application.matchPercentage}%
                          </span>
                        </div>
                      </div>

                      <div className="flex items-center justify-between">
                        <span className="text-sm font-medium text-gray-700">Skills Alignment</span>
                        <span className="text-sm text-gray-600">
                          {application.matchedSkills ? parseSkills(application.matchedSkills).length : 0} skills matched
                        </span>
                      </div>
                    </div>

                    <div className="mt-3 grid grid-cols-1 md:grid-cols-2 gap-4">
                      <div>
                        <h5 className="text-sm font-medium text-green-700 mb-2">✅ Matched Skills</h5>
                        <div className="flex flex-wrap gap-1">
                          {application.matchedSkills && parseSkills(application.matchedSkills).slice(0, 6).map((skill, index) => (
                            <span key={index} className="bg-green-100 text-green-800 px-2 py-1 rounded text-xs">
                              {skill}
                            </span>
                          ))}
                          {application.matchedSkills && parseSkills(application.matchedSkills).length > 6 && (
                            <span className="text-green-600 text-xs">+{parseSkills(application.matchedSkills).length - 6} more</span>
                          )}
                        </div>
                      </div>

                      <div>
                        <h5 className="text-sm font-medium text-red-700 mb-2">❌ Missing Skills</h5>
                        <div className="flex flex-wrap gap-1">
                          {application.missingSkills && parseSkills(application.missingSkills).slice(0, 6).map((skill, index) => (
                            <span key={index} className="bg-red-100 text-red-800 px-2 py-1 rounded text-xs">
                              {skill}
                            </span>
                          ))}
                          {application.missingSkills && parseSkills(application.missingSkills).length > 6 && (
                            <span className="text-red-600 text-xs">+{parseSkills(application.missingSkills).length - 6} more</span>
                          )}
                        </div>
                      </div>
                    </div>
                  </div>
                )}

                {/* Qualifications */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-4">
                  <div className="space-y-3">
                    <h4 className="font-semibold text-gray-800 flex items-center">
                      <Briefcase className="h-4 w-4 mr-2" />
                      Contact Information
                    </h4>
                    {blindScreening ? (
                      <div className="p-3 bg-purple-50/80 border border-purple-200 rounded-md text-xs text-purple-800 flex items-center gap-2">
                        <ShieldCheck className="h-4 w-4 text-purple-600 flex-shrink-0" />
                        <span>Contact details and personal identifiers hidden in Blind Screening Mode.</span>
                      </div>
                    ) : (
                      <div className="space-y-2">
                        <div className="flex items-center text-sm text-gray-600">
                          <Mail className="h-4 w-4 mr-2" />
                          {application.applicantEmail || application.applicant?.email || 'No email'}
                        </div>
                        {(application.applicantPhone || application.applicant?.phone) && (
                          <div className="flex items-center text-sm text-gray-600">
                            <Phone className="h-4 w-4 mr-2" />
                            {application.applicantPhone || application.applicant?.phone}
                          </div>
                        )}
                      </div>
                    )}
                  </div>

                  <div className="space-y-3">
                    <h4 className="font-semibold text-gray-800 flex items-center">
                      <Star className="h-4 w-4 mr-2" />
                      Qualifications
                    </h4>
                    <div className="space-y-2">
                      {application.applicantExperience && (
                        <div className="flex items-center text-sm text-gray-600">
                          <div className="w-2 h-2 bg-yellow-500 rounded-full mr-2"></div>
                          {application.applicantExperience} years of experience
                        </div>
                      )}
                      {application.applicantEducation && (
                        <div className="flex items-center text-sm text-gray-600">
                          <BookOpen className="h-4 w-4 mr-2" />
                          {application.applicantEducation}
                        </div>
                      )}
                    </div>
                  </div>
                </div>

                {/* All Skills */}
                {application.applicantSkills && (
                  <div className="mb-4">
                    <h4 className="font-semibold text-gray-800 mb-2">All Skills & Technologies</h4>
                    <div className="flex flex-wrap gap-2">
                      {parseSkills(application.applicantSkills).map((skill, index) => (
                        <span
                          key={index}
                          className="bg-blue-100 text-blue-800 px-3 py-1 rounded-full text-sm font-medium"
                        >
                          {skill}
                        </span>
                      ))}
                    </div>
                  </div>
                )}

                {/* Recruiter Actions */}
                <div className="mt-6 pt-4 border-t border-gray-200">
                  <h4 className="font-semibold text-gray-800 mb-3">Application Actions</h4>
                  <div className="flex flex-wrap gap-2">
                    {!application.viewedByRecruiter && (
                      <button
                        onClick={() => {
                          setSelectedApplication(application);
                          handleStatusUpdate(application.id, 'view');
                        }}
                        disabled={actionLoading === application.id}
                        className="inline-flex items-center px-3 py-2 border border-purple-300 text-purple-700 rounded-md text-sm font-medium hover:bg-purple-50 disabled:opacity-50"
                      >
                        <Eye className="h-4 w-4 mr-1" />
                        Mark as Viewed
                      </button>
                    )}
                    
                    <button
                      onClick={() => {
                        setSelectedApplication(application);
                        setShowStatusModal('shortlist');
                      }}
                      className="inline-flex items-center px-3 py-2 border border-green-300 text-green-700 rounded-md text-sm font-medium hover:bg-green-50"
                    >
                      <CheckCircle className="h-4 w-4 mr-1" />
                      Shortlist
                    </button>

                    <button
                      onClick={() => setMessageThreadApplication(application)}
                      className="inline-flex items-center px-3 py-2 border border-gray-300 text-gray-700 rounded-md text-sm font-medium hover:bg-gray-50"
                    >
                      <MessageCircle className="h-4 w-4 mr-1" />
                      Message Candidate
                    </button>

                    {application.status !== 'REJECTED' && (
                      <button
                        onClick={() => setPipelineApplicationId(application.id)}
                        className="inline-flex items-center px-3 py-2 border border-teal-300 text-teal-700 rounded-md text-sm font-medium hover:bg-teal-50"
                      >
                        <Video className="h-4 w-4 mr-1" />
                        Interview Pipeline
                      </button>
                    )}

                    {application.status !== 'REJECTED' && (
                      <button
                        onClick={() => setOfferApplicationId(application.id)}
                        className="inline-flex items-center px-3 py-2 border border-green-300 text-green-700 rounded-md text-sm font-medium hover:bg-green-50"
                      >
                        <FileCheck className="h-4 w-4 mr-1" />
                        Offer
                      </button>
                    )}

                    {application.status === 'SHORTLISTED' && (
                      <button
                        onClick={() => handleAssignTest(application)}
                        className="inline-flex items-center px-3 py-2 border border-blue-300 text-blue-700 rounded-md text-sm font-medium hover:bg-blue-50"
                      >
                        <BookOpen className="h-4 w-4 mr-1" />
                        Assign Test
                      </button>
                    )}

                    {(application.status === 'TEST_PASSED') && (
                      <button
                        onClick={() => {
                          setSelectedApplication(application);
                          setShowInterviewModal(true);
                        }}
                        className="inline-flex items-center px-3 py-2 border border-yellow-300 text-yellow-700 rounded-md text-sm font-medium hover:bg-yellow-50"
                      >
                        <CalendarIcon className="h-4 w-4 mr-1" />
                        Schedule Interview
                      </button>
                    )}

                    {(application.status === 'TEST_COMPLETED' || application.status === 'TEST_PASSED' || application.status === 'TEST_FAILED') && (
                      <button
                        onClick={() => fetchTestResults(application.id)}
                        className="inline-flex items-center px-3 py-2 border border-indigo-300 text-indigo-700 rounded-md text-sm font-medium hover:bg-indigo-50"
                      >
                        <Award className="h-4 w-4 mr-1" />
                        View Test Results
                      </button>
                    )}

                    <button
                      onClick={() => {
                        setSelectedApplication(application);
                        setShowStatusModal('reject');
                      }}
                      className="inline-flex items-center px-3 py-2 border border-red-300 text-red-700 rounded-md text-sm font-medium hover:bg-red-50"
                    >
                      <XCircle className="h-4 w-4 mr-1" />
                      Reject
                    </button>
                    
                    {application.hasResume && (
                      <button
                        onClick={() => handleDownloadResume(application)}
                        className="inline-flex items-center px-3 py-2 border border-blue-300 text-blue-700 rounded-md text-sm font-medium hover:bg-blue-50"
                      >
                        <FileText className="h-4 w-4 mr-1" />
                        Download Resume
                      </button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </>
    )}

        {/* Modals */}
        {showInterviewModal && <InterviewModal />}
        {pipelineApplicationId && (
          <InterviewPipelinePanel
            applicationId={pipelineApplicationId}
            isRecruiter={true}
            onClose={() => setPipelineApplicationId(null)}
          />
        )}
        {offerApplicationId && (
          <OfferPanel
            applicationId={offerApplicationId}
            isRecruiter={true}
            onClose={() => setOfferApplicationId(null)}
          />
        )}
        {showStatusModal && <StatusUpdateModal />}
        {showTestModal && <TestAssignmentModal />}
        {showTestResultsModal && <TestResultsModal />}
        {showCompareModal && <CompareModal />}
        {showBulkMessageModal && <BulkMessageModal />}
        {messageThreadApplication && (
          <MessageThread
            applicationId={messageThreadApplication.id}
            title={`${messageThreadApplication.applicantName || messageThreadApplication.applicant?.fullName || 'Candidate'} - ${job?.title || ''}`}
            onClose={() => setMessageThreadApplication(null)}
          />
        )}
      </div>
    </div>
  );
};

export default ApplicationsList;