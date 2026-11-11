import React, { useEffect, useState } from 'react';
import { X, MessageSquare, Trash2 } from 'lucide-react';
import api from '../services/api';

const formatTimestamp = (value) => {
  if (!value) return '';
  try {
    return new Date(value).toLocaleString();
  } catch {
    return '';
  }
};

/** Internal, recruiter-team-only notes on an application - never shown to the candidate.
 *  Any teammate on the recruiter account can read and add notes (see AccessPolicy's
 *  recruiterManagesApplication); only the original author can delete their own. */
const ApplicationCommentsPanel = ({ applicationId, onClose }) => {
  const [comments, setComments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [draft, setDraft] = useState('');
  const [posting, setPosting] = useState(false);

  const currentUserId = (() => {
    try {
      return JSON.parse(localStorage.getItem('user'))?.id ?? null;
    } catch {
      return null;
    }
  })();

  const fetchComments = async () => {
    try {
      setLoading(true);
      setError('');
      const response = await api.get(`/applications/${applicationId}/comments`);
      setComments(response.data || []);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to load comments');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchComments();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [applicationId]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!draft.trim()) return;
    try {
      setPosting(true);
      setError('');
      const response = await api.post(`/applications/${applicationId}/comments`, { body: draft.trim() });
      setComments((prev) => [...prev, response.data]);
      setDraft('');
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to post comment');
    } finally {
      setPosting(false);
    }
  };

  const handleDelete = async (commentId) => {
    try {
      setError('');
      await api.delete(`/applications/${applicationId}/comments/${commentId}`);
      setComments((prev) => prev.filter((c) => c.id !== commentId));
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to delete comment');
    }
  };

  return (
    <div className="fixed inset-0 bg-gray-600 bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg shadow-lg w-full max-w-lg max-h-[85vh] flex flex-col">
        <div className="flex justify-between items-center p-4 border-b border-gray-200">
          <h2 className="text-lg font-semibold text-gray-900 flex items-center">
            <MessageSquare className="h-5 w-5 mr-2 text-blue-600" /> Team Notes
          </h2>
          <button onClick={onClose} aria-label="Close" className="text-gray-400 hover:text-gray-600">
            <X className="h-6 w-6" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          <p className="text-xs text-gray-500 -mt-1 mb-2">
            Internal notes visible only to your hiring team - the candidate never sees these.
          </p>

          {error && <p className="text-sm text-red-600">{error}</p>}

          {loading ? (
            <p className="text-gray-500 text-sm">Loading...</p>
          ) : comments.length === 0 ? (
            <p className="text-gray-500 text-sm">No notes yet. Be the first to leave one.</p>
          ) : (
            comments.map((comment) => (
              <div key={comment.id} className="bg-gray-50 border border-gray-200 rounded-lg p-3">
                <div className="flex justify-between items-start gap-2">
                  <div>
                    <span className="text-sm font-medium text-gray-900">{comment.authorName}</span>
                    <span className="text-xs text-gray-400 ml-2">{formatTimestamp(comment.createdAt)}</span>
                  </div>
                  {comment.authorUserId === currentUserId && (
                    <button
                      onClick={() => handleDelete(comment.id)}
                      aria-label="Delete note"
                      className="text-gray-400 hover:text-red-600 flex-shrink-0"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  )}
                </div>
                <p className="text-sm text-gray-700 mt-1 whitespace-pre-wrap">{comment.body}</p>
              </div>
            ))
          )}
        </div>

        <form onSubmit={handleSubmit} className="p-4 border-t border-gray-200 flex gap-2">
          <input
            type="text"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="Add a note for your team..."
            className="flex-1 border border-gray-300 rounded-md px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500"
          />
          <button
            type="submit"
            disabled={posting || !draft.trim()}
            className="px-4 py-2 bg-blue-600 text-white rounded-md text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
          >
            {posting ? 'Posting...' : 'Post'}
          </button>
        </form>
      </div>
    </div>
  );
};

export default ApplicationCommentsPanel;
