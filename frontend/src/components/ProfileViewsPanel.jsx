import React, { useEffect, useState } from 'react';
import { Eye, Building2 } from 'lucide-react';
import { accountAPI } from '../services/api';

/** Shows a candidate which recruiters viewed their expanded profile in Discover Candidates - the
 *  symmetric counterpart to a recruiter already seeing who viewed one of their applications. */
const ProfileViewsPanel = ({ userId }) => {
  const [data, setData] = useState({ count: 0, views: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const response = await accountAPI.getProfileViews(userId);
        setData(response.data);
      } catch (err) {
        setError('Failed to load profile views');
      } finally {
        setLoading(false);
      }
    })();
  }, [userId]);

  const formatDate = (dateString) => {
    if (!dateString) return '';
    return new Date(dateString).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
  };

  if (loading) return <p className="text-sm text-gray-500">Loading...</p>;
  if (error) return <p className="text-sm text-red-600">{error}</p>;

  return (
    <div>
      <p className="text-sm text-gray-600 mb-3">
        <span className="font-semibold text-gray-900">{data.count}</span> recruiter{data.count === 1 ? '' : 's'}{' '}
        {data.count === 1 ? 'has' : 'have'} viewed your profile.
      </p>
      {data.views.length === 0 ? (
        <p className="text-sm text-gray-400">
          Turn on "Open to work" above so recruiters can find and view your profile.
        </p>
      ) : (
        <div className="space-y-2">
          {data.views.map((view, i) => (
            <div key={i} className="flex items-center justify-between px-3 py-2 rounded-lg border border-gray-200">
              <span className="flex items-center gap-2 text-sm text-gray-800">
                <Building2 className="h-4 w-4 text-gray-400" />
                {view.companyName}
              </span>
              <span className="flex items-center gap-1 text-xs text-gray-500">
                <Eye className="h-3 w-3" /> {formatDate(view.viewedAt)}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default ProfileViewsPanel;
