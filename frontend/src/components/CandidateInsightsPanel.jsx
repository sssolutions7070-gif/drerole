import React, { useEffect, useState } from 'react';
import { FileText, Target, Eye, Lightbulb } from 'lucide-react';
import { applicationStatusAPI } from '../services/api';

const STATUS_LABELS = {
  APPLICATION_SUBMITTED: 'Submitted',
  VIEWED_BY_RECRUITER: 'Viewed',
  SHORTLISTED: 'Shortlisted',
  INTERVIEW_SCHEDULED: 'Interview scheduled',
  OFFER_SENT: 'Offer sent',
  OFFER_ACCEPTED: 'Offer accepted',
  REJECTED: 'Rejected',
  WITHDRAWN: 'Withdrawn',
};

const StatCard = ({ icon: Icon, label, value, color }) => (
  <div className="bg-white p-4 rounded-lg shadow border border-gray-200 flex items-center">
    <Icon className={`h-7 w-7 mr-3 ${color}`} />
    <div>
      <p className="text-xs font-medium text-gray-500">{label}</p>
      <p className="text-xl font-bold text-gray-900">{value}</p>
    </div>
  </div>
);

/** "My Insights" for a candidate - recruiters and admins have had an analytics view of their own
 *  for a while; candidates previously had none. Rolls up this candidate's own applications
 *  (status breakdown, average match score), which skills come up missing most often across the
 *  jobs they've applied to, and how many recruiters have viewed their profile. */
const CandidateInsightsPanel = ({ userId }) => {
  const [insights, setInsights] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const response = await applicationStatusAPI.getUserInsights(userId);
        setInsights(response.data);
      } catch (err) {
        setError(err.response?.data?.error || 'Failed to load insights');
      } finally {
        setLoading(false);
      }
    })();
  }, [userId]);

  if (loading) return <p className="text-sm text-gray-500">Loading...</p>;
  if (error) return <p className="text-sm text-red-600">{error}</p>;
  if (!insights) return null;

  if (insights.totalApplications === 0) {
    return (
      <div className="text-center py-12">
        <Target className="mx-auto h-16 w-16 text-gray-400 mb-4" />
        <h3 className="text-lg font-medium text-gray-900 mb-2">No insights yet</h3>
        <p className="text-gray-500">Apply to a few jobs and your application trends will show up here.</p>
      </div>
    );
  }

  const byStatus = insights.byStatus || {};

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard icon={FileText} label="Applications" value={insights.totalApplications} color="text-blue-600" />
        <StatCard
          icon={Target}
          label="Avg. Match Score"
          value={insights.averageMatchPercentage != null ? `${insights.averageMatchPercentage}%` : '—'}
          color="text-green-600"
        />
        <StatCard icon={Eye} label="Profile Views" value={insights.profileViewCount} color="text-purple-600" />
      </div>

      <div className="bg-white rounded-lg shadow border border-gray-200 p-4">
        <p className="text-sm font-semibold text-gray-700 mb-3">Where your applications stand</p>
        <div className="flex flex-wrap gap-3 text-sm">
          {Object.entries(byStatus).map(([status, count]) => (
            <span key={status} className="text-gray-700">
              {STATUS_LABELS[status] || status}: {count}
            </span>
          ))}
        </div>
      </div>

      {(insights.topMissingSkills || []).length > 0 && (
        <div className="bg-white rounded-lg shadow border border-gray-200 p-4">
          <p className="text-sm font-semibold text-gray-700 mb-1 flex items-center gap-2">
            <Lightbulb className="h-4 w-4 text-amber-500" /> Skills worth adding to your resume
          </p>
          <p className="text-xs text-gray-500 mb-3">
            Most often missing across the jobs you've applied to, based on resume matching at apply time.
          </p>
          <div className="flex flex-wrap gap-2">
            {insights.topMissingSkills.map((row) => (
              <span key={row.skill} className="bg-amber-50 text-amber-800 border border-amber-200 px-2.5 py-1 rounded-full text-xs font-medium">
                {row.skill} <span className="text-amber-600">({row.count})</span>
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default CandidateInsightsPanel;
