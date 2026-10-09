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
        <div className="flex items-center justify-between mb-3">
          <p className="text-sm font-semibold text-gray-700 flex items-center gap-2">
            <TrendingUp className="h-4 w-4 text-blue-600" /> Hiring Funnel & Stage Drop-Off (Platform-wide)
          </p>
          <span className="text-xs bg-blue-50 text-blue-700 border border-blue-200 px-2 py-0.5 rounded-full font-medium">
            Live Conversion Pipeline
          </span>
        </div>
        <div className="space-y-3">
          {FUNNEL_STAGES.map((stage, idx) => {
            const value = overview.conversionFunnel[stage.key] || 0;
            const pct = Math.round((value / maxFunnel) * 100);
            const prevValue = idx > 0 ? (overview.conversionFunnel[FUNNEL_STAGES[idx - 1].key] || 0) : null;
            const stepConversion = prevValue && prevValue > 0 ? Math.round((value / prevValue) * 100) : null;

            return (
              <div key={stage.key} className="space-y-1">
                <div className="flex items-center justify-between text-xs">
                  <div className="flex items-center gap-2">
                    <span className="font-medium text-gray-700 w-28">{stage.label}</span>
                    {stepConversion !== null && (
                      <span className="text-[11px] text-gray-500 font-mono">
                        ({stepConversion}% of previous stage)
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-gray-500 font-mono text-[11px]">{pct}% overall</span>
                    <span className="font-bold text-gray-800 w-10 text-right">{value}</span>
                  </div>
                </div>
                <div className="w-full bg-gray-100 rounded-full h-3 overflow-hidden">
                  <div
                    className="bg-gradient-to-r from-blue-500 to-indigo-600 h-3 rounded-full transition-all duration-500"
                    style={{ width: `${pct}%` }}
                  />
                </div>
              </div>
            );
          })}
        </div>

        {/* Real, computed benchmarks - not hardcoded placeholders. Time-to-hire comes from actual
            application/offer timestamps and proctoring integrity from actual recorded violation
            counts, so both show "—" rather than a fabricated number when there's no data yet. */}
        <div className="mt-5 pt-4 border-t border-gray-100 grid grid-cols-1 sm:grid-cols-3 gap-3 text-center">
          <div className="bg-slate-50 border border-slate-200 rounded p-2.5">
            <p className="text-xs text-slate-500 font-medium">Avg. Time-to-Hire</p>
            <p className="text-lg font-bold text-slate-800">
              {overview.timeToHire?.averageDays != null ? `${overview.timeToHire.averageDays} Days` : '—'}
            </p>
            <p className="text-[11px] text-slate-500">
              {overview.timeToHire?.sampleSize > 0
                ? `From ${overview.timeToHire.sampleSize} offer${overview.timeToHire.sampleSize === 1 ? '' : 's'} sent`
                : 'No offers sent yet'}
            </p>
          </div>
          <div className="bg-slate-50 border border-slate-200 rounded p-2.5">
            <p className="text-xs text-slate-500 font-medium">Proctoring Integrity</p>
            <p className="text-lg font-bold text-emerald-700">
              {overview.proctoringIntegrity?.cleanPercentage != null ? `${overview.proctoringIntegrity.cleanPercentage}% Clean` : '—'}
            </p>
            <p className="text-[11px] text-slate-500">
              {overview.proctoringIntegrity?.totalProctoredSessions > 0
                ? `${overview.proctoringIntegrity.cleanSessions} of ${overview.proctoringIntegrity.totalProctoredSessions} sessions clean`
                : 'No proctored sessions yet'}
            </p>
          </div>
          <div className="bg-slate-50 border border-slate-200 rounded p-2.5">
            <p className="text-xs text-slate-500 font-medium">Offer Acceptance Rate</p>
            <p className="text-lg font-bold text-blue-700">
              {overview.conversionFunnel.offerSent > 0
                ? `${Math.round(((overview.conversionFunnel.offerAccepted || 0) / overview.conversionFunnel.offerSent) * 100)}%`
                : '—'}
            </p>
            <p className="text-[11px] text-slate-500">Candidate offer conversion</p>
          </div>
        </div>
      </div>

      {(overview.topMissingSkills || []).length > 0 && (
        <div className="bg-white rounded-lg shadow border border-gray-200 p-4">
          <p className="text-sm font-semibold text-gray-700 mb-3">Most commonly missing skills (platform-wide)</p>
          <div className="flex flex-wrap gap-2">
            {overview.topMissingSkills.map((row) => (
              <span key={row.skill} className="bg-amber-50 text-amber-800 border border-amber-200 px-2.5 py-1 rounded-full text-xs font-medium">
                {row.skill} <span className="text-amber-600">({row.count})</span>
              </span>
            ))}
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="bg-white rounded-lg shadow border border-gray-200 p-4">
          <p className="text-sm font-semibold text-gray-700 mb-3">Job postings by experience level</p>
          <div className="flex flex-wrap gap-3 text-sm">
            {Object.entries(overview.jobPostingsByExperienceLevel || {}).map(([level, count]) => (
              <span key={level} className="text-gray-700">{level}: {count}</span>
            ))}
          </div>
        </div>
        <div className="bg-white rounded-lg shadow border border-gray-200 p-4">
          <p className="text-sm font-semibold text-gray-700 mb-3">Job postings by type</p>
          <div className="flex flex-wrap gap-3 text-sm">
            {Object.entries(overview.jobPostingsByType || {}).map(([type, count]) => (
              <span key={type} className="text-gray-700">{type}: {count}</span>
            ))}
          </div>
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

      <div className="bg-white rounded-lg shadow border border-gray-200 p-4">
        <p className="text-sm font-semibold text-gray-700 mb-3">Source of hire</p>
        <div className="flex flex-wrap gap-4 text-sm">
          {Object.entries(overview.applications.bySource || {}).map(([source, count]) => (
            <span key={source} className="text-gray-700">
              {`${source.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (l) => l.toUpperCase())}: ${count}`}
            </span>
          ))}
          {Object.keys(overview.applications.bySource || {}).length === 0 && (
            <span className="text-gray-400">No applications yet</span>
          )}
        </div>
      </div>
    </div>
  );
};

export default PlatformAnalyticsPanel;
