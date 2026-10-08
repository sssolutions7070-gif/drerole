import React, { useState, useEffect } from 'react';
import { Users, Building, Briefcase, FileText, Trash2, ToggleLeft, ToggleRight, BarChart3, ScrollText, Clock, CheckCircle, XCircle, Star, MessageSquare, TrendingUp, BadgeCheck } from 'lucide-react';
import api from '../services/api';
import PlatformAnalyticsPanel from '../components/PlatformAnalyticsPanel';
import PendingVerificationsPanel from '../components/PendingVerificationsPanel';

const AdminDashboard = () => {
  const [stats, setStats] = useState({});
  const [activeTab, setActiveTab] = useState('dashboard');
  const [users, setUsers] = useState([]);
  const [recruiters, setRecruiters] = useState([]);
  const [jobs, setJobs] = useState([]);
  const [pendingJobs, setPendingJobs] = useState([]);
  const [pendingReviews, setPendingReviews] = useState([]);
  const [pendingExperiences, setPendingExperiences] = useState([]);
  const [applications, setApplications] = useState([]);
  const [auditLog, setAuditLog] = useState([]);
  const [loading, setLoading] = useState(true);

  const [selectedJobIds, setSelectedJobIds] = useState(new Set());
  const [selectedReviewIds, setSelectedReviewIds] = useState(new Set());
  const [selectedExperienceIds, setSelectedExperienceIds] = useState(new Set());
  const [bulkLoading, setBulkLoading] = useState(false);
  const [bulkJobMessage, setBulkJobMessage] = useState('');
  const [bulkReviewMessage, setBulkReviewMessage] = useState('');
  const [bulkExperienceMessage, setBulkExperienceMessage] = useState('');

  const toggleSelected = (setIds, id) => {
    setIds((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  const toggleSelectAll = (setIds, items) => {
    setIds((prev) => (prev.size === items.length ? new Set() : new Set(items.map((i) => i.id))));
  };

  /** Fires `requestFn` for every selected id in parallel, tolerating individual failures, then
   *  reports how many succeeded and refetches the queue. Shared by every bulk approve/reject
   *  action below - the per-item endpoints are already admin-gated, so no new backend route was
   *  needed, same as the recruiter-side bulk actions this mirrors. */
  const runBulkModeration = async (ids, setIds, requestFn, refetch, setMessage, label) => {
    setBulkLoading(true);
    setMessage('');
    const results = await Promise.allSettled(Array.from(ids).map((id) => requestFn(id)));
    const succeeded = results.filter((r) => r.status === 'fulfilled').length;
    const failed = results.length - succeeded;
    setMessage(`${succeeded} ${label}${succeeded === 1 ? '' : 's'} processed${failed === 0 ? '' : `, ${failed} failed`}.`);
    setIds(new Set());
    setBulkLoading(false);
    refetch();
  };

  const bulkApproveJobs = () => {
    if (selectedJobIds.size === 0) return;
    if (!window.confirm(`Approve ${selectedJobIds.size} selected job posting(s)?`)) return;
    runBulkModeration(selectedJobIds, setSelectedJobIds, (id) => api.put(`/admin/jobs/${id}/approve`),
      fetchPendingJobs, setBulkJobMessage, 'job');
  };

  const bulkRejectJobs = () => {
    if (selectedJobIds.size === 0) return;
    const reason = window.prompt(`Reason for rejecting ${selectedJobIds.size} selected posting(s):`);
    if (reason === null) return;
    runBulkModeration(selectedJobIds, setSelectedJobIds, (id) => api.put(`/admin/jobs/${id}/reject`, { reason }),
      fetchPendingJobs, setBulkJobMessage, 'job');
  };

  const bulkApproveReviews = () => {
    if (selectedReviewIds.size === 0) return;
    if (!window.confirm(`Approve ${selectedReviewIds.size} selected review(s)?`)) return;
    runBulkModeration(selectedReviewIds, setSelectedReviewIds, (id) => api.put(`/admin/company-reviews/${id}/approve`),
      fetchPendingReviews, setBulkReviewMessage, 'review');
  };

  const bulkRejectReviews = () => {
    if (selectedReviewIds.size === 0) return;
    const reason = window.prompt(`Reason for rejecting ${selectedReviewIds.size} selected review(s):`);
    if (reason === null) return;
    runBulkModeration(selectedReviewIds, setSelectedReviewIds, (id) => api.put(`/admin/company-reviews/${id}/reject`, { reason }),
      fetchPendingReviews, setBulkReviewMessage, 'review');
  };

  const bulkApproveExperiences = () => {
    if (selectedExperienceIds.size === 0) return;
    if (!window.confirm(`Approve ${selectedExperienceIds.size} selected report(s)?`)) return;
    runBulkModeration(selectedExperienceIds, setSelectedExperienceIds, (id) => api.put(`/admin/interview-experiences/${id}/approve`),
      fetchPendingExperiences, setBulkExperienceMessage, 'report');
  };

  const bulkRejectExperiences = () => {
    if (selectedExperienceIds.size === 0) return;
    const reason = window.prompt(`Reason for rejecting ${selectedExperienceIds.size} selected report(s):`);
    if (reason === null) return;
    runBulkModeration(selectedExperienceIds, setSelectedExperienceIds, (id) => api.put(`/admin/interview-experiences/${id}/reject`, { reason }),
      fetchPendingExperiences, setBulkExperienceMessage, 'report');
  };

  useEffect(() => {
    fetchDashboardStats();
    fetchPendingJobs();
    fetchPendingReviews();
    fetchPendingExperiences();
  }, []);

  useEffect(() => {
    if (activeTab === 'users') fetchUsers();
    if (activeTab === 'recruiters') fetchRecruiters();
    if (activeTab === 'jobs') fetchJobs();
    if (activeTab === 'pending-jobs') fetchPendingJobs();
    if (activeTab === 'pending-reviews') fetchPendingReviews();
    if (activeTab === 'pending-experiences') fetchPendingExperiences();
    if (activeTab === 'applications') fetchApplications();
    if (activeTab === 'audit-log') fetchAuditLog();
  }, [activeTab]);

  const fetchDashboardStats = async () => {
    try {
      const response = await api.get('/admin/dashboard/stats');
      setStats(response.data);
    } catch (error) {
      console.error('Error fetching stats:', error);
    } finally {
      setLoading(false);
    }
  };

  const fetchUsers = async () => {
    try {
      const response = await api.get('/admin/users');
      setUsers(response.data);
    } catch (error) {
      console.error('Error fetching users:', error);
      console.error('Error details:', error.response);
    }
  };

  const fetchRecruiters = async () => {
    try {
      const response = await api.get('/admin/recruiters');
      setRecruiters(response.data);
    } catch (error) {
      console.error('Error fetching recruiters:', error);
    }
  };

  const fetchJobs = async () => {
    try {
      const response = await api.get('/admin/jobs');
      setJobs(response.data);
    } catch (error) {
      console.error('Error fetching jobs:', error);
    }
  };

  const fetchApplications = async () => {
    try {
      const response = await api.get('/admin/applications');
      setApplications(response.data);
    } catch (error) {
      console.error('Error fetching applications:', error);
    }
  };

  const fetchAuditLog = async () => {
    try {
      const response = await api.get('/admin/audit-log');
      setAuditLog(response.data);
    } catch (error) {
      console.error('Error fetching audit log:', error);
    }
  };

  const toggleUserActive = async (userId) => {
    try {
      await api.put(`/admin/users/${userId}/toggle-active`);
      fetchUsers();
    } catch (error) {
      alert('Failed to update user status: ' + (error.response?.data?.error || error.message));
    }
  };

  const deleteUser = async (userId) => {
    if (!window.confirm('Are you sure you want to delete this user?')) return;
    
    try {
      await api.delete(`/admin/users/${userId}`);
      fetchUsers();
      fetchDashboardStats();
      alert('User deleted successfully!');
    } catch (error) {
      alert('Failed to delete user: ' + (error.response?.data?.error || error.message));
    }
  };

  const deleteRecruiter = async (recruiterId) => {
    if (!window.confirm('Are you sure you want to delete this recruiter?')) return;
    
    try {
      await api.delete(`/admin/recruiters/${recruiterId}`);
      fetchRecruiters();
      fetchDashboardStats();
      alert('Recruiter deleted successfully!');
    } catch (error) {
      alert('Failed to delete recruiter: ' + (error.response?.data?.error || error.message));
    }
  };

  const deleteJob = async (jobId) => {
    if (!window.confirm('Are you sure you want to delete this job?')) return;
    
    try {
      await api.delete(`/admin/jobs/${jobId}`);
      fetchJobs();
      fetchDashboardStats();
      alert('Job deleted successfully!');
    } catch (error) {
      alert('Failed to delete job: ' + (error.response?.data?.error || error.message));
    }
  };

  const toggleJobActive = async (jobId) => {
    try {
      await api.put(`/admin/jobs/${jobId}/toggle-active`);
      fetchJobs();
    } catch (error) {
      alert('Failed to update job status: ' + (error.response?.data?.error || error.message));
    }
  };

  const fetchPendingJobs = async () => {
    try {
      const response = await api.get('/admin/jobs/pending');
      setPendingJobs(response.data);
    } catch (error) {
      console.error('Error fetching pending jobs:', error);
    }
  };

  const approveJob = async (jobId) => {
    try {
      await api.put(`/admin/jobs/${jobId}/approve`);
      fetchPendingJobs();
    } catch (error) {
      alert('Failed to approve job: ' + (error.response?.data?.error || error.message));
    }
  };

  const rejectJob = async (jobId) => {
    const reason = window.prompt('Reason for rejecting this posting (shown to the recruiter):');
    if (reason === null) return; // cancelled

    try {
      await api.put(`/admin/jobs/${jobId}/reject`, { reason });
      fetchPendingJobs();
    } catch (error) {
      alert('Failed to reject job: ' + (error.response?.data?.error || error.message));
    }
  };

  const fetchPendingReviews = async () => {
    try {
      const response = await api.get('/admin/company-reviews/pending');
      setPendingReviews(response.data);
    } catch (error) {
      console.error('Error fetching pending reviews:', error);
    }
  };

  const approveReview = async (reviewId) => {
    try {
      await api.put(`/admin/company-reviews/${reviewId}/approve`);
      fetchPendingReviews();
    } catch (error) {
      alert('Failed to approve review: ' + (error.response?.data?.error || error.message));
    }
  };

  const rejectReview = async (reviewId) => {
    const reason = window.prompt('Reason for rejecting this review (shown to the reviewer):');
    if (reason === null) return; // cancelled

    try {
      await api.put(`/admin/company-reviews/${reviewId}/reject`, { reason });
      fetchPendingReviews();
    } catch (error) {
      alert('Failed to reject review: ' + (error.response?.data?.error || error.message));
    }
  };

  const fetchPendingExperiences = async () => {
    try {
      const response = await api.get('/admin/interview-experiences/pending');
      setPendingExperiences(response.data);
    } catch (error) {
      console.error('Error fetching pending interview experiences:', error);
    }
  };

  const approveExperience = async (experienceId) => {
    try {
      await api.put(`/admin/interview-experiences/${experienceId}/approve`);
      fetchPendingExperiences();
    } catch (error) {
      alert('Failed to approve interview experience: ' + (error.response?.data?.error || error.message));
    }
  };

  const rejectExperience = async (experienceId) => {
    const reason = window.prompt('Reason for rejecting this report (shown to the candidate):');
    if (reason === null) return; // cancelled

    try {
      await api.put(`/admin/interview-experiences/${experienceId}/reject`, { reason });
      fetchPendingExperiences();
    } catch (error) {
      alert('Failed to reject interview experience: ' + (error.response?.data?.error || error.message));
    }
  };

  if (loading) {
    return (
      <div className="max-w-7xl mx-auto py-8 px-4">
        <div className="animate-pulse">
          <div className="h-8 bg-gray-200 rounded w-1/4 mb-8"></div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
            {[...Array(4)].map((_, i) => (
              <div key={i} className="h-32 bg-gray-200 rounded"></div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto py-8 px-4">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-800 mb-2">Admin Dashboard</h1>
        <p className="text-gray-600">Manage users, recruiters, jobs, and applications</p>
      </div>

      {/* Navigation Tabs */}
      <div className="mb-8 border-b border-gray-200">
        <nav className="-mb-px flex space-x-8">
          {[
            { id: 'dashboard', name: 'Dashboard', icon: BarChart3 },
            { id: 'analytics', name: 'Analytics', icon: TrendingUp },
            { id: 'users', name: 'Users', icon: Users },
            { id: 'recruiters', name: 'Recruiters', icon: Building },
            { id: 'jobs', name: 'Jobs', icon: Briefcase },
            { id: 'pending-jobs', name: `Pending Jobs${pendingJobs.length > 0 ? ` (${pendingJobs.length})` : ''}`, icon: Clock },
            { id: 'pending-reviews', name: `Pending Reviews${pendingReviews.length > 0 ? ` (${pendingReviews.length})` : ''}`, icon: Star },
            { id: 'pending-experiences', name: `Pending Interview Reports${pendingExperiences.length > 0 ? ` (${pendingExperiences.length})` : ''}`, icon: MessageSquare },
            { id: 'pending-verifications', name: 'Pending Verifications', icon: BadgeCheck },
            { id: 'applications', name: 'Applications', icon: FileText },
            { id: 'audit-log', name: 'Audit Log', icon: ScrollText }
          ].map((tab) => {
            const Icon = tab.icon;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`flex items-center space-x-2 py-4 px-1 border-b-2 font-medium text-sm ${
                  activeTab === tab.id
                    ? 'border-blue-500 text-blue-600'
                    : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                }`}
              >
                <Icon className="h-5 w-5" />
                <span>{tab.name}</span>
              </button>
            );
          })}
        </nav>
      </div>

      {/* Dashboard Stats */}
      {activeTab === 'dashboard' && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
          <div className="bg-white p-6 rounded-lg shadow-lg border border-gray-200">
            <div className="flex items-center">
              <Users className="h-8 w-8 text-blue-600 mr-3" />
              <div>
                <p className="text-sm font-medium text-gray-600">Total Users</p>
                <p className="text-2xl font-bold text-gray-900">{stats.totalUsers || 0}</p>
              </div>
            </div>
          </div>

          <div className="bg-white p-6 rounded-lg shadow-lg border border-gray-200">
            <div className="flex items-center">
              <Building className="h-8 w-8 text-green-600 mr-3" />
              <div>
                <p className="text-sm font-medium text-gray-600">Recruiters</p>
                <p className="text-2xl font-bold text-gray-900">{stats.totalRecruiters || 0}</p>
              </div>
            </div>
          </div>

          <div className="bg-white p-6 rounded-lg shadow-lg border border-gray-200">
            <div className="flex items-center">
              <Briefcase className="h-8 w-8 text-purple-600 mr-3" />
              <div>
                <p className="text-sm font-medium text-gray-600">Job Postings</p>
                <p className="text-2xl font-bold text-gray-900">{stats.totalJobs || 0}</p>
              </div>
            </div>
          </div>

          <div className="bg-white p-6 rounded-lg shadow-lg border border-gray-200">
            <div className="flex items-center">
              <FileText className="h-8 w-8 text-orange-600 mr-3" />
              <div>
                <p className="text-sm font-medium text-gray-600">Applications</p>
                <p className="text-2xl font-bold text-gray-900">{stats.totalApplications || 0}</p>
              </div>
            </div>
          </div>
        </div>
      )}

      {activeTab === 'analytics' && (
        <PlatformAnalyticsPanel />
      )}

      {/* Users Tab */}
      {activeTab === 'users' && (
        <div className="bg-white rounded-lg shadow-lg border border-gray-200">
          <div className="px-6 py-4 border-b border-gray-200">
            <h3 className="text-lg font-semibold text-gray-800">All Users ({users.length})</h3>
          </div>
          <div className="overflow-x-auto">
            {users.length === 0 ? (
              <div className="text-center py-8">
                <Users className="mx-auto h-12 w-12 text-gray-400 mb-4" />
                <p className="text-gray-500">No users found in the system.</p>
              </div>
            ) : (
              <table className="min-w-full divide-y divide-gray-200">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">User</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Email</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Phone</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Role</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {users.map((user) => (
                    <tr key={user.id}>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="text-sm font-medium text-gray-900">{user.fullName}</div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{user.email}</td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{user.phone || 'N/A'}</td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
                          user.roleId === 2 ? 'bg-red-100 text-red-800' :
                          user.roleId === 3 ? 'bg-green-100 text-green-800' :
                          'bg-blue-100 text-blue-800'
                        }`}>
                          {user.role}
                        </span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
                          user.isActive ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-600'
                        }`}>
                          {user.isActive ? 'Active' : 'Deactivated'}
                        </span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                        {user.roleId !== 2 && (
                          <div className="flex items-center space-x-4">
                            <button
                              onClick={() => toggleUserActive(user.id)}
                              className="text-gray-600 hover:text-gray-900 flex items-center"
                              title={user.isActive ? 'Deactivate account' : 'Reactivate account'}
                            >
                              {user.isActive ? (
                                <ToggleRight className="h-4 w-4 mr-1 text-green-600" />
                              ) : (
                                <ToggleLeft className="h-4 w-4 mr-1" />
                              )}
                              {user.isActive ? 'Deactivate' : 'Reactivate'}
                            </button>
                            <button
                              onClick={() => deleteUser(user.id)}
                              className="text-red-600 hover:text-red-900 flex items-center"
                            >
                              <Trash2 className="h-4 w-4 mr-1" />
                              Delete
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>
      )}

      {/* Audit Log Tab */}
      {activeTab === 'audit-log' && (
        <div className="bg-white rounded-lg shadow-lg border border-gray-200">
          <div className="px-6 py-4 border-b border-gray-200">
            <h3 className="text-lg font-semibold text-gray-800">Admin Audit Log</h3>
            <p className="text-sm text-gray-500 mt-1">The last 200 admin actions (deletes, activations/deactivations), most recent first.</p>
          </div>
          <div className="overflow-x-auto">
            {auditLog.length === 0 ? (
              <div className="text-center py-8">
                <ScrollText className="mx-auto h-12 w-12 text-gray-400 mb-4" />
                <p className="text-gray-500">No admin actions recorded yet.</p>
              </div>
            ) : (
              <table className="min-w-full divide-y divide-gray-200">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">When</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Admin</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Action</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Target</th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {auditLog.map((entry) => (
                    <tr key={entry.id}>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        {entry.createdAt ? new Date(entry.createdAt).toLocaleString() : 'N/A'}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{entry.adminEmail}</td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                          {entry.action}
                        </span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        {entry.targetType} #{entry.targetId} - {entry.targetDescription}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>
      )}

      {/* Recruiters Tab */}
      {activeTab === 'recruiters' && (
        <div className="bg-white rounded-lg shadow-lg border border-gray-200">
          <div className="px-6 py-4 border-b border-gray-200">
            <h3 className="text-lg font-semibold text-gray-800">All Recruiters ({recruiters.length})</h3>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Company</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Contact</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Industry</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Jobs Posted</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {recruiters.map((recruiter) => (
                  <tr key={recruiter.id}>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm font-medium text-gray-900">{recruiter.companyName}</div>
                      <div className="text-sm text-gray-500">{recruiter.companySize}</div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm text-gray-900">{recruiter.user?.email}</div>
                      <div className="text-sm text-gray-500">{recruiter.contactEmail}</div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{recruiter.industry}</td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{recruiter.jobCount}</td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                      <button
                        onClick={() => deleteRecruiter(recruiter.id)}
                        className="text-red-600 hover:text-red-900 flex items-center"
                      >
                        <Trash2 className="h-4 w-4 mr-1" />
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Jobs Tab */}
      {activeTab === 'jobs' && (
        <div className="bg-white rounded-lg shadow-lg border border-gray-200">
          <div className="px-6 py-4 border-b border-gray-200">
            <h3 className="text-lg font-semibold text-gray-800">All Jobs ({jobs.length})</h3>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Job Title</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Company</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Location</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Applications</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Moderation</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {jobs.map((job) => (
                  <tr key={job.id}>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm font-medium text-gray-900">{job.title}</div>
                      <div className="text-sm text-gray-500">{job.jobType} • {job.experienceLevel}</div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">{job.company}</td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{job.location}</td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{job.applicationCount}</td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <button
                        onClick={() => toggleJobActive(job.id)}
                        className={`flex items-center space-x-2 text-sm font-medium ${
                          job.isActive ? 'text-green-600' : 'text-gray-400'
                        }`}
                      >
                        {job.isActive ? <ToggleRight className="h-5 w-5" /> : <ToggleLeft className="h-5 w-5" />}
                        <span>{job.isActive ? 'Active' : 'Inactive'}</span>
                      </button>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className={`px-2 py-1 rounded-full text-xs font-semibold ${
                        job.moderationStatus === 'APPROVED' ? 'bg-green-100 text-green-800' :
                        job.moderationStatus === 'REJECTED' ? 'bg-red-100 text-red-800' :
                        'bg-yellow-100 text-yellow-800'
                      }`}>
                        {job.moderationStatus || 'APPROVED'}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-medium space-x-2">
                      <button
                        onClick={() => deleteJob(job.id)}
                        className="text-red-600 hover:text-red-900 flex items-center"
                      >
                        <Trash2 className="h-4 w-4 mr-1" />
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Pending Jobs Tab */}
      {activeTab === 'pending-jobs' && (
        <div className="bg-white rounded-lg shadow-lg border border-gray-200">
          <div className="px-6 py-4 border-b border-gray-200">
            <h3 className="text-lg font-semibold text-gray-800">Pending Job Postings ({pendingJobs.length})</h3>
            <p className="text-sm text-gray-500 mt-1">New postings wait here until approved, so nothing unreviewed reaches public job listings.</p>
          </div>
          {pendingJobs.length > 0 && (
            <div className="px-6 py-3 bg-gray-50 border-b border-gray-200 flex flex-wrap items-center gap-3">
              <label className="flex items-center space-x-2 text-sm font-medium text-gray-700 cursor-pointer">
                <input
                  type="checkbox"
                  checked={selectedJobIds.size === pendingJobs.length}
                  onChange={() => toggleSelectAll(setSelectedJobIds, pendingJobs)}
                  aria-label="Select all pending jobs"
                  className="h-4 w-4 text-blue-600 rounded border-gray-300"
                />
                <span>Select All</span>
              </label>
              {selectedJobIds.size > 0 && (
                <>
                  <span className="text-sm text-gray-500">{selectedJobIds.size} selected</span>
                  <button
                    onClick={bulkApproveJobs}
                    disabled={bulkLoading}
                    className="inline-flex items-center px-3 py-1.5 bg-green-600 text-white rounded-md text-sm font-medium hover:bg-green-700 disabled:opacity-50"
                  >
                    <CheckCircle className="h-4 w-4 mr-1.5" /> Approve Selected
                  </button>
                  <button
                    onClick={bulkRejectJobs}
                    disabled={bulkLoading}
                    className="inline-flex items-center px-3 py-1.5 bg-red-600 text-white rounded-md text-sm font-medium hover:bg-red-700 disabled:opacity-50"
                  >
                    <XCircle className="h-4 w-4 mr-1.5" /> Reject Selected
                  </button>
                </>
              )}
              {bulkJobMessage && <span className="text-sm text-blue-700">{bulkJobMessage}</span>}
            </div>
          )}
          {pendingJobs.length === 0 ? (
            <div className="text-center py-12 text-gray-500">
              <Clock className="mx-auto h-12 w-12 text-gray-300 mb-3" />
              Nothing waiting for review.
            </div>
          ) : (
            <div className="divide-y divide-gray-200">
              {pendingJobs.map((job) => (
                <div key={job.id} className="px-6 py-4 flex items-start justify-between">
                  <div className="flex items-start">
                    <input
                      type="checkbox"
                      checked={selectedJobIds.has(job.id)}
                      onChange={() => toggleSelected(setSelectedJobIds, job.id)}
                      aria-label={`Select ${job.title}`}
                      className="h-4 w-4 mt-1 mr-3 text-blue-600 rounded border-gray-300"
                    />
                    <div>
                    <div className="text-sm font-medium text-gray-900">{job.title}</div>
                    <div className="text-sm text-gray-500">
                      {job.company} • {job.location || 'No location specified'} • {job.jobType}
                    </div>
                    <p className="text-sm text-gray-600 mt-2 max-w-xl line-clamp-2">{job.description}</p>
                    </div>
                  </div>
                  <div className="flex items-center space-x-3 flex-shrink-0 ml-4">
                    <button
                      onClick={() => approveJob(job.id)}
                      className="inline-flex items-center px-3 py-2 border border-green-300 text-green-700 rounded-md text-sm font-medium hover:bg-green-50"
                    >
                      <CheckCircle className="h-4 w-4 mr-1" />
                      Approve
                    </button>
                    <button
                      onClick={() => rejectJob(job.id)}
                      className="inline-flex items-center px-3 py-2 border border-red-300 text-red-700 rounded-md text-sm font-medium hover:bg-red-50"
                    >
                      <XCircle className="h-4 w-4 mr-1" />
                      Reject
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {activeTab === 'pending-reviews' && (
        <div className="bg-white rounded-lg shadow-lg border border-gray-200">
          <div className="px-6 py-4 border-b border-gray-200">
            <h3 className="text-lg font-semibold text-gray-800">Pending Company Reviews ({pendingReviews.length})</h3>
            <p className="text-sm text-gray-500 mt-1">A new or edited review waits here until approved, so nothing unreviewed reaches a company's public profile.</p>
          </div>
          {pendingReviews.length > 0 && (
            <div className="px-6 py-3 bg-gray-50 border-b border-gray-200 flex flex-wrap items-center gap-3">
              <label className="flex items-center space-x-2 text-sm font-medium text-gray-700 cursor-pointer">
                <input
                  type="checkbox"
                  checked={selectedReviewIds.size === pendingReviews.length}
                  onChange={() => toggleSelectAll(setSelectedReviewIds, pendingReviews)}
                  aria-label="Select all pending reviews"
                  className="h-4 w-4 text-blue-600 rounded border-gray-300"
                />
                <span>Select All</span>
              </label>
              {selectedReviewIds.size > 0 && (
                <>
                  <span className="text-sm text-gray-500">{selectedReviewIds.size} selected</span>
                  <button
                    onClick={bulkApproveReviews}
                    disabled={bulkLoading}
                    className="inline-flex items-center px-3 py-1.5 bg-green-600 text-white rounded-md text-sm font-medium hover:bg-green-700 disabled:opacity-50"
                  >
                    <CheckCircle className="h-4 w-4 mr-1.5" /> Approve Selected
                  </button>
                  <button
                    onClick={bulkRejectReviews}
                    disabled={bulkLoading}
                    className="inline-flex items-center px-3 py-1.5 bg-red-600 text-white rounded-md text-sm font-medium hover:bg-red-700 disabled:opacity-50"
                  >
                    <XCircle className="h-4 w-4 mr-1.5" /> Reject Selected
                  </button>
                </>
              )}
              {bulkReviewMessage && <span className="text-sm text-blue-700">{bulkReviewMessage}</span>}
            </div>
          )}
          {pendingReviews.length === 0 ? (
            <div className="text-center py-12 text-gray-500">
              <Star className="mx-auto h-12 w-12 text-gray-300 mb-3" />
              Nothing waiting for review.
            </div>
          ) : (
            <div className="divide-y divide-gray-200">
              {pendingReviews.map((review) => (
                <div key={review.id} className="px-6 py-4 flex items-start justify-between">
                  <div className="flex items-start">
                    <input
                      type="checkbox"
                      checked={selectedReviewIds.has(review.id)}
                      onChange={() => toggleSelected(setSelectedReviewIds, review.id)}
                      aria-label={`Select review by ${review.user?.email || 'unknown reviewer'}`}
                      className="h-4 w-4 mt-1 mr-3 text-blue-600 rounded border-gray-300"
                    />
                    <div>
                    <div className="text-sm font-medium text-gray-900">
                      {review.recruiter?.companyName || 'Unknown company'} - {review.rating}/5
                    </div>
                    <div className="text-sm text-gray-500">
                      by {review.user?.email || 'unknown reviewer'}
                    </div>
                    <p className="text-sm text-gray-600 mt-2 max-w-xl">{review.comment}</p>
                    </div>
                  </div>
                  <div className="flex items-center space-x-3 flex-shrink-0 ml-4">
                    <button
                      onClick={() => approveReview(review.id)}
                      className="inline-flex items-center px-3 py-2 border border-green-300 text-green-700 rounded-md text-sm font-medium hover:bg-green-50"
                    >
                      <CheckCircle className="h-4 w-4 mr-1" />
                      Approve
                    </button>
                    <button
                      onClick={() => rejectReview(review.id)}
                      className="inline-flex items-center px-3 py-2 border border-red-300 text-red-700 rounded-md text-sm font-medium hover:bg-red-50"
                    >
                      <XCircle className="h-4 w-4 mr-1" />
                      Reject
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {activeTab === 'pending-experiences' && (
        <div className="bg-white rounded-lg shadow-lg border border-gray-200">
          <div className="px-6 py-4 border-b border-gray-200">
            <h3 className="text-lg font-semibold text-gray-800">Pending Interview Experience Reports ({pendingExperiences.length})</h3>
            <p className="text-sm text-gray-500 mt-1">A new or edited report waits here until approved, so nothing unreviewed reaches a company's public profile.</p>
          </div>
          {pendingExperiences.length > 0 && (
            <div className="px-6 py-3 bg-gray-50 border-b border-gray-200 flex flex-wrap items-center gap-3">
              <label className="flex items-center space-x-2 text-sm font-medium text-gray-700 cursor-pointer">
                <input
                  type="checkbox"
                  checked={selectedExperienceIds.size === pendingExperiences.length}
                  onChange={() => toggleSelectAll(setSelectedExperienceIds, pendingExperiences)}
                  aria-label="Select all pending interview reports"
                  className="h-4 w-4 text-blue-600 rounded border-gray-300"
                />
                <span>Select All</span>
              </label>
              {selectedExperienceIds.size > 0 && (
                <>
                  <span className="text-sm text-gray-500">{selectedExperienceIds.size} selected</span>
                  <button
                    onClick={bulkApproveExperiences}
                    disabled={bulkLoading}
                    className="inline-flex items-center px-3 py-1.5 bg-green-600 text-white rounded-md text-sm font-medium hover:bg-green-700 disabled:opacity-50"
                  >
                    <CheckCircle className="h-4 w-4 mr-1.5" /> Approve Selected
                  </button>
                  <button
                    onClick={bulkRejectExperiences}
                    disabled={bulkLoading}
                    className="inline-flex items-center px-3 py-1.5 bg-red-600 text-white rounded-md text-sm font-medium hover:bg-red-700 disabled:opacity-50"
                  >
                    <XCircle className="h-4 w-4 mr-1.5" /> Reject Selected
                  </button>
                </>
              )}
              {bulkExperienceMessage && <span className="text-sm text-blue-700">{bulkExperienceMessage}</span>}
            </div>
          )}
          {pendingExperiences.length === 0 ? (
            <div className="text-center py-12 text-gray-500">
              <MessageSquare className="mx-auto h-12 w-12 text-gray-300 mb-3" />
              Nothing waiting for review.
            </div>
          ) : (
            <div className="divide-y divide-gray-200">
              {pendingExperiences.map((experience) => (
                <div key={experience.id} className="px-6 py-4 flex items-start justify-between">
                  <div className="flex items-start">
                    <input
                      type="checkbox"
                      checked={selectedExperienceIds.has(experience.id)}
                      onChange={() => toggleSelected(setSelectedExperienceIds, experience.id)}
                      aria-label={`Select report by ${experience.user?.email || 'unknown reporter'}`}
                      className="h-4 w-4 mt-1 mr-3 text-blue-600 rounded border-gray-300"
                    />
                    <div>
                    <div className="text-sm font-medium text-gray-900">
                      {experience.recruiter?.companyName || 'Unknown company'} - difficulty {experience.difficultyRating}/5 - {experience.outcome}
                    </div>
                    <div className="text-sm text-gray-500">
                      by {experience.user?.email || 'unknown reporter'}
                    </div>
                    <p className="text-sm text-gray-600 mt-2 max-w-xl">{experience.processDescription}</p>
                    </div>
                  </div>
                  <div className="flex items-center space-x-3 flex-shrink-0 ml-4">
                    <button
                      onClick={() => approveExperience(experience.id)}
                      className="inline-flex items-center px-3 py-2 border border-green-300 text-green-700 rounded-md text-sm font-medium hover:bg-green-50"
                    >
                      <CheckCircle className="h-4 w-4 mr-1" />
                      Approve
                    </button>
                    <button
                      onClick={() => rejectExperience(experience.id)}
                      className="inline-flex items-center px-3 py-2 border border-red-300 text-red-700 rounded-md text-sm font-medium hover:bg-red-50"
                    >
                      <XCircle className="h-4 w-4 mr-1" />
                      Reject
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {activeTab === 'pending-verifications' && (
        <PendingVerificationsPanel />
      )}

      {/* Applications Tab */}
      {activeTab === 'applications' && (
        <div className="bg-white rounded-lg shadow-lg border border-gray-200">
          <div className="px-6 py-4 border-b border-gray-200">
            <h3 className="text-lg font-semibold text-gray-800">All Applications ({applications.length})</h3>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Applicant</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Job</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Date</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Experience</th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {applications.map((app) => (
                  <tr key={app.id}>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm font-medium text-gray-900">
                        {app.applicant?.fullName || app.applicantName}
                      </div>
                      <div className="text-sm text-gray-500">
                        {app.applicant?.email || app.applicantEmail}
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm font-medium text-gray-900">{app.job?.title}</div>
                      <div className="text-sm text-gray-500">{app.job?.company}</div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                      {new Date(app.applicationDate).toLocaleDateString()}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
                        app.status === 'APPROVED' ? 'bg-green-100 text-green-800' :
                        app.status === 'REJECTED' ? 'bg-red-100 text-red-800' :
                        'bg-yellow-100 text-yellow-800'
                      }`}>
                        {app.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                      {app.applicantExperience} years
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminDashboard;