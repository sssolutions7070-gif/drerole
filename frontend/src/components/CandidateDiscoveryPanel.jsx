import React, { useEffect, useState } from 'react';
import { Search, MapPin, FileText, MessageCircle, Sparkles, Bookmark, Bell, BellOff, Trash2, X, Check, UserCircle } from 'lucide-react';
import { candidateDiscoveryAPI, savedCandidateSearchAPI } from '../services/api';
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
  const [viewingCandidate, setViewingCandidate] = useState(null);

  const handleViewProfile = (candidate) => {
    setViewingCandidate(candidate);
    candidateDiscoveryAPI.recordView(candidate.userId).catch(() => {
      // Best-effort - a failed view record shouldn't block the recruiter from seeing the profile.
    });
  };

  // Saved Searches state
  const [savedSearches, setSavedSearches] = useState([]);
  const [showSaveModal, setShowSaveModal] = useState(false);
  const [saveSearchName, setSaveSearchName] = useState('');
  const [saveAlertEnabled, setSaveAlertEnabled] = useState(true);
  const [savingSearch, setSavingSearch] = useState(false);
  const [saveSuccessMessage, setSaveSuccessMessage] = useState('');

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

  const fetchSavedSearches = async () => {
    if (!recruiterId) return;
    try {
      const response = await savedCandidateSearchAPI.list(recruiterId);
      setSavedSearches(response.data || []);
    } catch (err) {
      console.error('Failed to load saved searches:', err);
    }
  };

  useEffect(() => {
    fetchCandidates('', '');
    if (recruiterId) {
      fetchSavedSearches();
    }
  }, [recruiterId]);

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

  const handleSaveSearch = async (e) => {
    e.preventDefault();
    if (!saveSearchName.trim() || !recruiterId) return;
    setSavingSearch(true);
    setError('');
    try {
      await savedCandidateSearchAPI.create(recruiterId, {
        name: saveSearchName.trim(),
        skillFilter: skill.trim() || undefined,
        locationFilter: location.trim() || undefined,
        aiQuery: aiQuery.trim() || undefined,
        alertEnabled: saveAlertEnabled,
      });
      setSaveSearchName('');
      setShowSaveModal(false);
      setSaveSuccessMessage('Search query saved successfully with candidate alerts!');
      setTimeout(() => setSaveSuccessMessage(''), 4000);
      fetchSavedSearches();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to save search');
    } finally {
      setSavingSearch(false);
    }
  };

  const handleApplySavedSearch = (saved) => {
    if (saved.aiQuery) {
      setAiQuery(saved.aiQuery);
      setSkill('');
      setLocation('');
      setAiSearching(true);
      setError('');
      candidateDiscoveryAPI.aiSearch(saved.aiQuery)
        .then((res) => setCandidates(res.data))
        .catch((err) => setError(err.response?.data?.error || 'AI search failed'))
        .finally(() => setAiSearching(false));
    } else {
      setSkill(saved.skillFilter || '');
      setLocation(saved.locationFilter || '');
      setAiQuery('');
      fetchCandidates(saved.skillFilter || '', saved.locationFilter || '');
    }
  };

  const handleDeleteSavedSearch = async (searchId) => {
    if (!recruiterId) return;
    try {
      await savedCandidateSearchAPI.delete(recruiterId, searchId);
      setSavedSearches((prev) => prev.filter((s) => s.id !== searchId));
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to delete saved search');
    }
  };

  const handleToggleAlert = async (searchId, currentStatus) => {
    if (!recruiterId) return;
    try {
      await savedCandidateSearchAPI.toggleAlert(recruiterId, searchId, !currentStatus);
      setSavedSearches((prev) =>
        prev.map((s) => (s.id === searchId ? { ...s, alertEnabled: !currentStatus } : s))
      );
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to toggle alert status');
    }
  };

  const hasActiveFilters = Boolean(skill.trim() || location.trim() || aiQuery.trim());

  return (
    <div>
      {/* Saved Searches Bar */}
      {recruiterId && savedSearches.length > 0 && (
        <div className="mb-4 p-3 bg-purple-50/60 border border-purple-200 rounded-lg">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs font-semibold text-purple-900 uppercase tracking-wide flex items-center gap-1.5">
              <Bookmark className="h-3.5 w-3.5 text-purple-700" />
              Saved Searches & Alerts ({savedSearches.length})
            </span>
          </div>
          <div className="flex flex-wrap gap-2">
            {savedSearches.map((s) => (
              <div
                key={s.id}
                className="inline-flex items-center gap-1.5 px-3 py-1 bg-white border border-purple-200 rounded-full text-xs shadow-sm hover:border-purple-400 transition-colors"
              >
                <button
                  type="button"
                  onClick={() => handleApplySavedSearch(s)}
                  className="font-medium text-purple-900 hover:text-purple-700 hover:underline"
                >
                  {s.name}
                </button>
                <button
                  type="button"
                  onClick={() => handleToggleAlert(s.id, s.alertEnabled)}
                  title={s.alertEnabled ? 'Alerts active (click to mute)' : 'Alerts muted (click to enable)'}
                  className="text-gray-400 hover:text-purple-600 transition-colors"
                >
                  {s.alertEnabled ? (
                    <Bell className="h-3.5 w-3.5 text-green-600" />
                  ) : (
                    <BellOff className="h-3.5 w-3.5 text-gray-400" />
                  )}
                </button>
                <button
                  type="button"
                  onClick={() => handleDeleteSavedSearch(s.id)}
                  title="Delete saved search"
                  className="text-gray-400 hover:text-red-600 transition-colors"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {saveSuccessMessage && (
        <div className="mb-3 p-2.5 bg-green-50 border border-green-200 text-green-800 text-xs rounded-md flex items-center gap-2">
          <Check className="h-4 w-4 text-green-600 flex-shrink-0" />
          <span>{saveSuccessMessage}</span>
        </div>
      )}

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

        {recruiterId && hasActiveFilters && (
          <button
            type="button"
            onClick={() => {
              const defaultName = aiQuery.trim() || `${skill.trim()} ${location.trim()}`.trim() || 'Candidate Search';
              setSaveSearchName(defaultName);
              setShowSaveModal(true);
            }}
            className="px-3.5 py-2 border border-purple-300 text-purple-700 bg-purple-50 rounded-md hover:bg-purple-100 flex items-center gap-1.5 text-sm font-medium transition-colors"
          >
            <Bookmark className="h-4 w-4" />
            Save Search & Alert
          </button>
        )}
      </form>

      {/* Save Search Modal */}
      {showSaveModal && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-5 relative">
            <button
              onClick={() => setShowSaveModal(false)}
              className="absolute top-4 right-4 text-gray-400 hover:text-gray-600"
            >
              <X className="h-5 w-5" />
            </button>
            <h3 className="text-base font-semibold text-gray-900 mb-2 flex items-center gap-2">
              <Bookmark className="h-5 w-5 text-purple-600" />
              Save Candidate Search
            </h3>
            <p className="text-xs text-gray-500 mb-4">
              Get notified whenever a candidate matching this search updates their profile or opts into discovery.
            </p>

            <form onSubmit={handleSaveSearch} className="space-y-3">
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Search Alert Name</label>
                <input
                  type="text"
                  required
                  value={saveSearchName}
                  onChange={(e) => setSaveSearchName(e.target.value)}
                  placeholder="e.g. Senior React Devs in Pune"
                  className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:ring-2 focus:ring-purple-500"
                />
              </div>

              <div className="text-xs text-gray-600 bg-gray-50 p-2.5 rounded border border-gray-200 space-y-1">
                {aiQuery.trim() ? (
                  <div><span className="font-semibold">AI Query:</span> "{aiQuery}"</div>
                ) : (
                  <>
                    {skill.trim() && <div><span className="font-semibold">Skill:</span> {skill}</div>}
                    {location.trim() && <div><span className="font-semibold">Location:</span> {location}</div>}
                  </>
                )}
              </div>

              <label className="flex items-center gap-2 cursor-pointer text-xs text-gray-700 pt-1">
                <input
                  type="checkbox"
                  checked={saveAlertEnabled}
                  onChange={(e) => setSaveAlertEnabled(e.target.checked)}
                  className="h-4 w-4 text-purple-600 rounded border-gray-300 focus:ring-purple-500"
                />
                <span>Enable notifications and dashboard alerts for new matches</span>
              </label>

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowSaveModal(false)}
                  className="px-3 py-1.5 text-xs text-gray-600 hover:text-gray-800"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={savingSearch || !saveSearchName.trim()}
                  className="px-4 py-1.5 bg-purple-600 text-white rounded-md text-xs font-medium hover:bg-purple-700 disabled:opacity-50"
                >
                  {savingSearch ? 'Saving...' : 'Save & Track'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

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
                    onClick={() => handleViewProfile(candidate)}
                    className="inline-flex items-center gap-1 text-xs text-gray-600 hover:text-gray-800 border border-gray-200 rounded-full px-2 py-1"
                  >
                    <UserCircle className="h-3 w-3" /> View Profile
                  </button>
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

      {viewingCandidate && (
        <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50">
          <div className="relative top-20 mx-auto p-6 border w-full max-w-lg shadow-lg rounded-md bg-white">
            <div className="flex justify-between items-start mb-4">
              <div>
                <h3 className="text-lg font-semibold text-gray-900">{viewingCandidate.fullName}</h3>
                {viewingCandidate.headline && <p className="text-sm text-gray-600">{viewingCandidate.headline}</p>}
              </div>
              <button onClick={() => setViewingCandidate(null)} className="text-gray-400 hover:text-gray-600">
                <X className="h-5 w-5" />
              </button>
            </div>
            {viewingCandidate.location && (
              <p className="text-sm text-gray-500 flex items-center gap-1 mb-3">
                <MapPin className="h-4 w-4" /> {viewingCandidate.location}
              </p>
            )}
            {viewingCandidate.bio && <p className="text-sm text-gray-700 mb-3">{viewingCandidate.bio}</p>}
            {viewingCandidate.skills?.length > 0 && (
              <div className="mb-3">
                <p className="text-xs font-semibold text-gray-500 uppercase mb-1">Skills</p>
                <div className="flex flex-wrap gap-1">
                  {viewingCandidate.skills.map((s) => (
                    <span key={s} className="px-2 py-0.5 rounded-full text-xs bg-indigo-100 text-indigo-700">{s}</span>
                  ))}
                </div>
              </div>
            )}
            {viewingCandidate.hasResume && (
              <span className="inline-flex items-center gap-1 text-xs text-green-700 bg-green-100 px-2 py-1 rounded-full mb-3">
                <FileText className="h-3 w-3" /> Resume on file
              </span>
            )}
            <div className="flex justify-end pt-2 border-t border-gray-100 mt-3">
              <button
                onClick={() => { setMessagingCandidate(viewingCandidate); setViewingCandidate(null); }}
                className="inline-flex items-center gap-1 text-sm text-blue-600 hover:text-blue-800 border border-blue-200 rounded-full px-3 py-1.5"
              >
                <MessageCircle className="h-4 w-4" /> Message Candidate
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default CandidateDiscoveryPanel;
