import React, { useEffect, useState } from 'react';
import { Search, MapPin, FileText, MessageCircle, Sparkles } from 'lucide-react';
import { candidateDiscoveryAPI } from '../services/api';
import DirectMessageThread from './DirectMessageThread';

/** Any recruiter's window into the "open to work" candidate pool - people who opted in to be
 *  discoverable even if they've never applied to one of this recruiter's postings. */
const CandidateDiscoveryPanel = ({ recruiterId }) => {
  const [candidates, setCandidates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [skill, setSkill] = useState('');
  const [location, setLocation] = useState('');
  const [aiQuery, setAiQuery] = useState('');
  const [aiSearching, setAiSearching] = useState(false);
  const [messagingCandidate, setMessagingCandidate] = useState(null);

  const fetchCandidates = async (skillFilter, locationFilter) => {
    setLoading(true);
    try {
      const response = await candidateDiscoveryAPI.discover(skillFilter || undefined, locationFilter || undefined);
      setCandidates(response.data);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to load candidates');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCandidates('', '');
  }, []);

  const handleSearch = (e) => {
    e.preventDefault();
    fetchCandidates(skill, location);
  };

  const handleAiSearch = async (e) => {
    e.preventDefault();
    if (!aiQuery.trim()) return;
    setAiSearching(true);
    setError('');
    try {
      const response = await candidateDiscoveryAPI.aiSearch(aiQuery.trim());
      setCandidates(response.data);
    } catch (err) {
      setError(err.response?.data?.error || 'AI search failed');
    } finally {
      setAiSearching(false);
    }
  };

  return (
    <div>
      <form onSubmit={handleAiSearch} className="flex flex-wrap gap-2 mb-3">
        <div className="flex-1 min-w-[240px] relative">
          <Sparkles className="h-4 w-4 text-purple-500 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={aiQuery}
            onChange={(e) => setAiQuery(e.target.value)}
            placeholder="Ask in plain English, e.g. 'senior React dev in Pune'"
            className="w-full border border-purple-300 rounded-md pl-9 pr-3 py-2 focus:ring-2 focus:ring-purple-500"
          />
        </div>
        <button type="submit" disabled={aiSearching}
          className="px-4 py-2 bg-purple-600 text-white rounded-md hover:bg-purple-700 disabled:opacity-50 flex items-center gap-2">
          <Sparkles className="h-4 w-4" />
          {aiSearching ? 'Thinking...' : 'AI Search'}
        </button>
      </form>

      <form onSubmit={handleSearch} className="flex flex-wrap gap-2 mb-4">
        <input
          type="text"
          value={skill}
          onChange={(e) => setSkill(e.target.value)}
          placeholder="Skill (e.g. React)"
          className="flex-1 min-w-[160px] border border-gray-300 rounded-md px-3 py-2"
        />
        <input
          type="text"
          value={location}
          onChange={(e) => setLocation(e.target.value)}
          placeholder="Location"
          className="flex-1 min-w-[160px] border border-gray-300 rounded-md px-3 py-2"
        />
        <button type="submit" className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 flex items-center gap-2">
          <Search className="h-4 w-4" />
          Search
        </button>
      </form>

      {error && <p className="text-red-600 text-sm mb-4">{error}</p>}

      {loading ? (
        <p className="text-gray-500 text-sm">Loading...</p>
      ) : candidates.length === 0 ? (
        <p className="text-gray-500 text-sm">No open-to-work candidates match these filters.</p>
      ) : (
        <div className="space-y-3">
          {candidates.map((candidate) => (
            <div key={candidate.userId} className="bg-white border border-gray-200 rounded-lg p-4">
              <div className="flex justify-between items-start">
                <div>
                  <p className="font-medium text-gray-900">{candidate.fullName}</p>
                  {candidate.headline && <p className="text-sm text-gray-600">{candidate.headline}</p>}
                  {candidate.location && (
                    <p className="text-xs text-gray-500 flex items-center gap-1 mt-1">
                      <MapPin className="h-3 w-3" /> {candidate.location}
                    </p>
                  )}
                </div>
                <div className="flex items-center gap-2 flex-shrink-0">
                  {candidate.hasResume && (
                    <span className="inline-flex items-center gap-1 text-xs text-green-700 bg-green-100 px-2 py-1 rounded-full">
                      <FileText className="h-3 w-3" /> Resume on file
                    </span>
                  )}
                  <button
                    onClick={() => setMessagingCandidate(candidate)}
                    className="inline-flex items-center gap-1 text-xs text-blue-600 hover:text-blue-800 border border-blue-200 rounded-full px-2 py-1"
                  >
                    <MessageCircle className="h-3 w-3" /> Message
                  </button>
                </div>
              </div>
              {candidate.bio && <p className="text-sm text-gray-600 mt-2">{candidate.bio}</p>}
              {candidate.skills?.length > 0 && (
                <div className="flex flex-wrap gap-1 mt-2">
                  {candidate.skills.map((s) => (
                    <span key={s} className="px-2 py-0.5 rounded-full text-xs bg-indigo-100 text-indigo-700">{s}</span>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {messagingCandidate && (
        <DirectMessageThread
          recruiterId={recruiterId}
          candidateUserId={messagingCandidate.userId}
          title={messagingCandidate.fullName}
          onClose={() => setMessagingCandidate(null)}
        />
      )}
    </div>
  );
};

export default CandidateDiscoveryPanel;
