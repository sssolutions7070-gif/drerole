import React, { useEffect, useState } from 'react';
import { CheckCircle2, Circle, Gauge } from 'lucide-react';
import api from '../services/api';

const scoreColor = (score) => {
  if (score >= 80) return { ring: 'text-green-600', bg: 'bg-green-50', text: 'text-green-700' };
  if (score >= 50) return { ring: 'text-yellow-600', bg: 'bg-yellow-50', text: 'text-yellow-700' };
  return { ring: 'text-red-600', bg: 'bg-red-50', text: 'text-red-700' };
};

/** How complete/attractive a candidate's own profile is, as a single score plus a concrete,
 *  actionable checklist - see ProfileStrengthService on the backend. Unfinished items are
 *  sorted to the top so the most useful next step is always the first thing shown. */
const ProfileStrengthPanel = ({ userId }) => {
  const [strength, setStrength] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchStrength = async () => {
      try {
        const response = await api.get(`/users/${userId}/profile-strength`);
        setStrength(response.data);
      } catch (err) {
        setError('Failed to load profile strength');
      } finally {
        setLoading(false);
      }
    };
    fetchStrength();
  }, [userId]);

  if (loading) {
    return <p className="text-sm text-gray-500">Loading...</p>;
  }
  if (error || !strength) {
    return <p className="text-sm text-red-600">{error || 'Profile strength unavailable'}</p>;
  }

  const colors = scoreColor(strength.score);
  const checks = [...strength.checks].sort((a, b) => Number(a.passed) - Number(b.passed));
  const completedCount = strength.checks.filter((c) => c.passed).length;

  return (
    <div>
      <div className="flex items-center gap-3 mb-4">
        <div className={`w-14 h-14 rounded-full flex items-center justify-center ${colors.bg}`}>
          <span className={`text-lg font-bold ${colors.text}`}>{strength.score}</span>
        </div>
        <div>
          <p className="text-sm font-semibold text-gray-800 flex items-center gap-1.5">
            <Gauge className="h-4 w-4 text-gray-500" />
            Profile Strength
          </p>
          <p className="text-xs text-gray-500">{completedCount} of {strength.checks.length} complete</p>
        </div>
      </div>

      <ul className="space-y-2">
        {checks.map((check) => (
          <li key={check.id} className="flex items-start gap-2 text-sm">
            {check.passed ? (
              <CheckCircle2 className="h-4 w-4 text-green-500 mt-0.5 flex-shrink-0" />
            ) : (
              <Circle className="h-4 w-4 text-gray-300 mt-0.5 flex-shrink-0" />
            )}
            <span className={check.passed ? 'text-gray-500' : 'text-gray-700'}>{check.message}</span>
          </li>
        ))}
      </ul>
    </div>
  );
};

export default ProfileStrengthPanel;
