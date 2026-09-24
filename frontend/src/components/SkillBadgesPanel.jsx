import React, { useEffect, useState } from 'react';
import { Award } from 'lucide-react';
import { skillBadgeAPI } from '../services/api';

const SOURCE_LABELS = {
  APTITUDE_TEST: 'Aptitude Test',
  DREAM_ROLE_TEST: 'Dream Role Test',
};

/** Verified credentials earned by passing an aptitude or Dream Role test - shareable, reusable
 *  proof of a skill instead of re-testing every time a recruiter asks. Publicly viewable, so this
 *  same component works both on a candidate's own profile and (in principle) anyone else's. */
const SkillBadgesPanel = ({ userId }) => {
  const [badges, setBadges] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    skillBadgeAPI.list(userId)
      .then((res) => setBadges(res.data))
      .catch(() => setBadges([]))
      .finally(() => setLoading(false));
  }, [userId]);

  if (loading) {
    return null;
  }

  return (
    <div className="bg-white rounded-lg shadow-lg border border-gray-200 p-6">
      <h3 className="text-lg font-semibold text-gray-800 mb-1 flex items-center">
        <Award className="h-5 w-5 text-gray-600 mr-2" />
        Verified Skill Badges
      </h3>
      <p className="text-sm text-gray-500 mb-4">
        Earned by passing a test - shareable proof recruiters can trust without you re-testing.
      </p>
      {badges.length === 0 ? (
        <p className="text-sm text-gray-500">Pass an aptitude or Dream Role test to earn your first badge.</p>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {badges.map((badge) => (
            <div key={badge.id} className="border border-amber-200 bg-amber-50 rounded-lg p-3 flex items-start gap-3">
              <div className="w-10 h-10 bg-amber-400 rounded-full flex items-center justify-center flex-shrink-0">
                <Award className="h-5 w-5 text-white" />
              </div>
              <div>
                <p className="font-medium text-gray-900 text-sm">{badge.badgeName}</p>
                <p className="text-xs text-gray-500">{SOURCE_LABELS[badge.sourceType] || badge.sourceType} - {badge.score}%</p>
                <p className="text-xs text-gray-400">{new Date(badge.earnedAt).toLocaleDateString()}</p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default SkillBadgesPanel;
