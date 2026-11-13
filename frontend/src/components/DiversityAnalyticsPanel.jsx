import React, { useEffect, useState } from 'react';
import { Users } from 'lucide-react';
import api from '../services/api';

const CATEGORY_LABELS = {
  genderIdentity: 'Gender Identity',
  ethnicity: 'Ethnicity',
  veteranStatus: 'Veteran Status',
  disabilityStatus: 'Disability Status',
};

const BAR_COLORS = ['bg-indigo-500', 'bg-blue-500', 'bg-teal-500', 'bg-amber-500', 'bg-pink-500', 'bg-purple-500'];

/** Aggregate, anonymous breakdown of applicants' voluntary self-identification answers (see
 *  DiversityAnalyticsService) - a category only renders its breakdown once enough candidates
 *  have answered to protect anyone's individual privacy. */
const DiversityAnalyticsPanel = ({ recruiterId }) => {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    (async () => {
      try {
        setLoading(true);
        const response = await api.get(`/recruiters/${recruiterId}/diversity-analytics`);
        setData(response.data);
      } catch (err) {
        setError(err.response?.data?.error || 'Failed to load diversity analytics');
      } finally {
        setLoading(false);
      }
    })();
  }, [recruiterId]);

  if (loading) return <p className="text-sm text-gray-500">Loading diversity analytics...</p>;
  if (error) return <p className="text-sm text-red-600">{error}</p>;
  if (!data) return null;

  return (
    <div className="mt-8 pt-6 border-t border-gray-200">
      <h4 className="font-semibold text-gray-800 mb-1 flex items-center">
        <Users className="h-4 w-4 mr-1.5 text-indigo-600" />
        Diversity & Inclusion
      </h4>
      <p className="text-xs text-gray-500 mb-4">
        Aggregate, anonymous breakdown of applicants' voluntary self-identification answers across
        all {data.totalApplicants} applicants. A category only appears once enough candidates have
        answered to protect everyone's privacy.
      </p>

      <div className="grid sm:grid-cols-2 gap-6">
        {Object.entries(CATEGORY_LABELS).map(([key, label]) => {
          const breakdown = data[key];
          if (!breakdown) return null;
          return (
            <div key={key}>
              <p className="text-sm font-medium text-gray-700 mb-2">{label}</p>
              {!breakdown.reportable ? (
                <p className="text-xs text-gray-400">
                  Not enough responses yet to show a breakdown ({breakdown.responseCount} so far).
                </p>
              ) : (
                <div className="space-y-1.5">
                  {Object.entries(breakdown.percentages).map(([value, pct], i) => (
                    <div key={value}>
                      <div className="flex justify-between text-xs text-gray-600 mb-0.5">
                        <span>{value}</span>
                        <span>{pct}%</span>
                      </div>
                      <div className="w-full bg-gray-100 rounded-full h-2">
                        <div
                          className={`h-2 rounded-full ${BAR_COLORS[i % BAR_COLORS.length]}`}
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default DiversityAnalyticsPanel;
