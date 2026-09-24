import React, { useEffect, useState } from 'react';
import { UserPlus, Trash2, ShieldCheck } from 'lucide-react';
import { recruiterTeamAPI } from '../services/api';

const ROLES = [
  { value: 'ADMIN', label: 'Admin' },
  { value: 'HIRING_MANAGER', label: 'Hiring Manager' },
  { value: 'INTERVIEWER', label: 'Interviewer' },
];

const ROLE_STYLES = {
  OWNER: 'bg-blue-100 text-blue-700',
  ADMIN: 'bg-purple-100 text-purple-700',
  HIRING_MANAGER: 'bg-green-100 text-green-700',
  INTERVIEWER: 'bg-gray-100 text-gray-700',
};

/** Who's on this company's hiring team - invited teammates share the same jobs, applications,
 *  and interview pipeline as the account owner (see AccessPolicy.ownsRecruiterId). */
const TeamPanel = ({ recruiterId }) => {
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [email, setEmail] = useState('');
  const [role, setRole] = useState('HIRING_MANAGER');
  const [inviting, setInviting] = useState(false);

  const fetchMembers = async () => {
    setLoading(true);
    try {
      const response = await recruiterTeamAPI.list(recruiterId);
      setMembers(response.data);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to load the team');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchMembers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recruiterId]);

  const handleInvite = async (e) => {
    e.preventDefault();
    setError('');
    setInviting(true);
    try {
      await recruiterTeamAPI.invite(recruiterId, email.trim(), role);
      setEmail('');
      await fetchMembers();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to invite that person');
    } finally {
      setInviting(false);
    }
  };

  const handleRemove = async (membershipId) => {
    setError('');
    try {
      await recruiterTeamAPI.remove(recruiterId, membershipId);
      await fetchMembers();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to remove that member');
    }
  };

  return (
    <div>
      <form onSubmit={handleInvite} className="flex flex-wrap gap-2 mb-4">
        <input
          type="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="teammate@company.com"
          className="flex-1 min-w-[200px] border border-gray-300 rounded-md px-3 py-2"
        />
        <select
          value={role}
          onChange={(e) => setRole(e.target.value)}
          className="border border-gray-300 rounded-md px-3 py-2"
        >
          {ROLES.map((r) => (
            <option key={r.value} value={r.value}>{r.label}</option>
          ))}
        </select>
        <button
          type="submit"
          disabled={inviting}
          className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2"
        >
          <UserPlus className="h-4 w-4" />
          {inviting ? 'Inviting...' : 'Invite'}
        </button>
      </form>
      <p className="text-xs text-gray-500 mb-4">
        They need an existing DreamRole account. Once invited, they see your jobs, applications, and interview pipeline.
      </p>

      {error && <p className="text-red-600 text-sm mb-4">{error}</p>}

      {loading ? (
        <p className="text-gray-500 text-sm">Loading...</p>
      ) : (
        <div className="space-y-2">
          {members.map((member) => (
            <div key={member.membershipId ?? 'owner'} className="bg-white border border-gray-200 rounded-lg p-3 flex items-center justify-between">
              <div>
                <p className="font-medium text-gray-900 flex items-center gap-2">
                  {member.fullName}
                  {member.role === 'OWNER' && <ShieldCheck className="h-4 w-4 text-blue-600" aria-label="Account owner" />}
                </p>
                <p className="text-sm text-gray-500">{member.email}</p>
              </div>
              <div className="flex items-center gap-3">
                <span className={`px-2 py-1 rounded-full text-xs font-medium ${ROLE_STYLES[member.role] || 'bg-gray-100 text-gray-700'}`}>
                  {member.role.replace('_', ' ')}
                </span>
                {member.membershipId != null && (
                  <button
                    onClick={() => handleRemove(member.membershipId)}
                    aria-label={`Remove ${member.fullName}`}
                    className="text-red-500 hover:text-red-700"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default TeamPanel;
