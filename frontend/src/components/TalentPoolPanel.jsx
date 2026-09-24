import React, { useEffect, useState } from 'react';
import { Search, Tag, Edit2 } from 'lucide-react';
import { talentPoolAPI } from '../services/api';

const STATUS_STYLES = {
  APPLICATION_SUBMITTED: 'bg-gray-100 text-gray-700',
  VIEWED_BY_RECRUITER: 'bg-blue-100 text-blue-700',
  SHORTLISTED: 'bg-green-100 text-green-700',
  INTERVIEW_SCHEDULED: 'bg-yellow-100 text-yellow-800',
  REJECTED: 'bg-red-100 text-red-700',
  OFFER_SENT: 'bg-purple-100 text-purple-700',
};

const parseTags = (tagsJson) => {
  if (!tagsJson) return [];
  try {
    const parsed = JSON.parse(tagsJson);
    return Array.isArray(parsed) ? parsed : [];
  } catch (e) {
    return [];
  }
};

/** A recruiter's talent pool: every candidate who has ever applied to any of their postings,
 *  searchable by name/email/tag, with a private note independent of any one job's lifecycle. */
const TalentPoolPanel = ({ recruiterId }) => {
  const [candidates, setCandidates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [editingCandidateId, setEditingCandidateId] = useState(null);
  const [notesDraft, setNotesDraft] = useState('');
  const [tagsDraft, setTagsDraft] = useState('');
  const [saving, setSaving] = useState(false);

  const fetchPool = async (searchTerm) => {
    setLoading(true);
    try {
      const response = await talentPoolAPI.search(recruiterId, searchTerm);
      setCandidates(response.data);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to load the talent pool');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPool('');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recruiterId]);

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    fetchPool(search);
  };

  const startEditing = (candidate) => {
    setEditingCandidateId(candidate.candidateId);
    setNotesDraft(candidate.notes || '');
    setTagsDraft(parseTags(candidate.tags).join(', '));
  };

  const saveNote = async (candidateId) => {
    setSaving(true);
    try {
      const tagsArray = tagsDraft.split(',').map((t) => t.trim()).filter(Boolean);
      await talentPoolAPI.upsertNote(recruiterId, candidateId, notesDraft, JSON.stringify(tagsArray));
      setEditingCandidateId(null);
      await fetchPool(search);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to save the note');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      <form onSubmit={handleSearchSubmit} className="flex gap-2 mb-4 max-w-md">
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search by name, email, or tag..."
          className="flex-1 border border-gray-300 rounded-md px-3 py-2"
        />
        <button type="submit" aria-label="Search" className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 flex items-center">
          <Search className="h-4 w-4" />
        </button>
      </form>

      {error && <p className="text-red-600 text-sm mb-4">{error}</p>}

      {loading ? (
        <p className="text-gray-500 text-sm">Loading...</p>
      ) : candidates.length === 0 ? (
        <p className="text-gray-500 text-sm">No candidates found.</p>
      ) : (
        <div className="space-y-3">
          {candidates.map((candidate) => (
            <div key={candidate.candidateId} className="bg-white border border-gray-200 rounded-lg p-4">
              <div className="flex justify-between items-start">
                <div>
                  <p className="font-medium text-gray-900">{candidate.fullName}</p>
                  <p className="text-sm text-gray-500">{candidate.email}</p>
                </div>
                <button
                  onClick={() => startEditing(candidate)}
                  className="inline-flex items-center px-2 py-1 text-sm text-blue-600 hover:text-blue-800"
                >
                  <Edit2 className="h-4 w-4 mr-1" /> Edit note
                </button>
              </div>

              <div className="flex flex-wrap gap-1 mt-2">
                {candidate.applications.map((app) => (
                  <span key={app.applicationId} className={`px-2 py-1 rounded-full text-xs font-medium ${STATUS_STYLES[app.status] || 'bg-gray-100 text-gray-600'}`}>
                    {app.jobTitle}: {app.status}
                  </span>
                ))}
              </div>

              {parseTags(candidate.tags).length > 0 && (
                <div className="flex flex-wrap gap-1 mt-2">
                  {parseTags(candidate.tags).map((tag) => (
                    <span key={tag} className="inline-flex items-center px-2 py-0.5 rounded-full text-xs bg-indigo-100 text-indigo-700">
                      <Tag className="h-3 w-3 mr-1" /> {tag}
                    </span>
                  ))}
                </div>
              )}

              {candidate.notes && editingCandidateId !== candidate.candidateId && (
                <p className="text-sm text-gray-600 mt-2 italic">"{candidate.notes}"</p>
              )}

              {editingCandidateId === candidate.candidateId && (
                <div className="mt-3 pt-3 border-t border-gray-200 space-y-2">
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">Tags (comma-separated)</label>
                    <input
                      type="text"
                      value={tagsDraft}
                      onChange={(e) => setTagsDraft(e.target.value)}
                      placeholder="e.g. strong-backend, keep-warm"
                      className="w-full border border-gray-300 rounded-md px-3 py-1.5 text-sm"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-700 mb-1">Note</label>
                    <textarea
                      value={notesDraft}
                      onChange={(e) => setNotesDraft(e.target.value)}
                      rows={2}
                      placeholder="Private note about this candidate..."
                      className="w-full border border-gray-300 rounded-md px-3 py-1.5 text-sm"
                    />
                  </div>
                  <div className="flex justify-end gap-2">
                    <button onClick={() => setEditingCandidateId(null)} className="px-3 py-1 border border-gray-300 rounded-md text-sm">
                      Cancel
                    </button>
                    <button
                      onClick={() => saveNote(candidate.candidateId)}
                      disabled={saving}
                      className="px-3 py-1 bg-blue-600 text-white rounded-md text-sm disabled:opacity-50"
                    >
                      {saving ? 'Saving...' : 'Save'}
                    </button>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default TalentPoolPanel;
