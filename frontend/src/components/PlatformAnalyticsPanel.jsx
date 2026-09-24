import React, { useEffect, useState } from 'react';
import { Users, Briefcase, FileText, TrendingUp } from 'lucide-react';
import { platformAnalyticsAPI } from '../services/api';

const FUNNEL_STAGES = [
  { key: 'submitted', label: 'Submitted' },
  { key: 'viewedByRecruiter', label: 'Viewed' },
  { key: 'shortlisted', label: 'Shortlisted' },
  { key: 'interviewScheduled', label: 'Interview' },
  { key: 'offerSent', label: 'Offer sent' },
  { key: 'offerAccepted', label: 'Offer accepted' },
];

const StatCard = ({ icon: Icon, label, value, color }) => (
  <div className="bg-white p-4 rounded-lg shadow border border-gray-200 flex items-center">
    <Icon className={`h-7 w-7 mr-3 ${color}`} />
    <div>
      <p className="text-xs font-medium text-gray-500">{label}</p>
      <p className="text-xl font-bold text-gray-900">{value}</p>
    </div>
  </div>
);

const Trend = ({ title, points }) => {
  const max = Math.max(1, ...points.map((p) => p.count));
  return (
    <div className="bg-white rounded-lg shadow border border-gray-200 p-4">
      <p className="text-sm font-semibold text-gray-700 mb-3">{title} (last 30 days)</p>
      <div className="flex items-end gap-0.5 h-24">
        {points.map((p) => (
          <div
            key={p.date}
            title={`${p.date}: ${p.count}`}
            className="flex-1 bg-blue-500 rounded-t"
            style={{ height: `${Math.max(2, (p.count / max) * 100)}%` }}
          />
        ))}
      </div>
    </div>
  );
};

/** Platform-wide growth and hiring-funnel metrics for admins - separate from the per-recruiter
 *  analytics recruiters see about their own jobs. */
const PlatformAnalyticsPanel = () => {
  const [overview, setOverview] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const response = await platformAnalyticsAPI.getOverview();
        setOverview(response.data);
      } catch (err) {
        setError(err.response?.data?.error || 'Failed to load platform analytics');
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  if (loading) return <p className="text-gray-500 text-sm">Loading...</p>;
  if (error) return <p className="text-red-600 text-sm">{error}</p>;
  if (!overview) return null;

  const maxFunnel = Math.max(1, overview.conversionFunnel.submitted);

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard icon={Users} label="Candidates" value={overview.users.candidates} color="text-blue-600" />
        <StatCard icon={Users} label="Recruiters" value={overview.users.recruiters} color="text-green-600" />
        <StatCard icon={Briefcase} label="Job Postings" value={overview.jobPostings.total} color="text-purple-600" />
        <StatCard icon={FileText} label="Applications" value={overview.applications.total} color="text-orange-600" />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <Trend title="Signups" points={overview.signupsByDay} />
        <Trend title="Applications" points={overview.applicationsByDay} />
      </div>

      <div className="bg-white rounded-lg shadow border border-gray-200 p-4">
        <p className="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-2">
          <TrendingUp className="h-4 w-4" /> Hiring funnel (platform-wide)
        </p>
        <div className="space-y-2">
          {FUNNEL_STAGES.map((stage) => {
            const value = overview.conversionFunnel[stage.key] || 0;
            const pct = Math.round((value / maxFunnel) * 100);
            return (
              <div key={stage.key} className="flex items-center gap-3">
                <span className="w-28 text-xs text-gray-600">{stage.label}</span>
                <div className="flex-1 bg-gray-100 rounded-full h-4 overflow-hidden">
                  <div className="bg-blue-500 h-4 rounded-full" style={{ width: `${pct}%` }} />
                </div>
                <span className="w-10 text-xs text-gray-700 text-right">{value}</span>
              </div>
            );
          })}
        </div>
      </div>

      <div className="bg-white rounded-lg shadow border border-gray-200 p-4">
        <p className="text-sm font-semibold text-gray-700 mb-3">Job postings by moderation status</p>
        <div className="flex gap-4 text-sm">
          <span className="text-yellow-700">Pending: {overview.jobPostings.pending}</span>
          <span className="text-green-700">Approved: {overview.jobPostings.approved}</span>
          <span className="text-red-700">Rejected: {overview.jobPostings.rejected}</span>
        </div>
      </div>
    </div>
  );
};

export default PlatformAnalyticsPanel;
