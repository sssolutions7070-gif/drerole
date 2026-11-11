import React, { useEffect, useState } from 'react';
import { interviewRoundsAPI } from '../services/api';

/** The candidate's own rating of one specific completed round - separate from the general
 *  post-process InterviewExperience review, and tied to this exact interview (see
 *  InterviewRoundFeedbackService on the backend). Feeds the recruiter's public accountability
 *  numbers on their company profile alongside response-time. */
const InterviewRoundFeedbackForm = ({ roundId, onSaved, onCancel }) => {
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    const loadExisting = async () => {
      try {
        const response = await interviewRoundsAPI.getFeedback(roundId);
        if (response.data) {
          setRating(response.data.rating || 0);
          setComment(response.data.comment || '');
          setSaved(true);
        }
      } catch (err) {
        // No feedback yet, or a transient error - either way, start from a blank form.
      } finally {
        setLoading(false);
      }
    };
    loadExisting();
  }, [roundId]);

  const handleSubmit = async () => {
    if (!rating) {
      setError('Pick a star rating for this interview');
      return;
    }
    setSaving(true);
    setError('');
    try {
      const response = await interviewRoundsAPI.submitFeedback(roundId, rating, comment.trim() || null);
      setSaved(true);
      if (onSaved) onSaved(response.data);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to save your feedback');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <p className="text-sm text-gray-500">Loading...</p>;
  }

  return (
    <div className="space-y-3">
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">
          How was this interview itself? (organization, professionalism)
        </label>
        <div className="flex gap-1">
          {[1, 2, 3, 4, 5].map((n) => (
            <button
              key={n}
              type="button"
              aria-label={`${n} star${n === 1 ? '' : 's'}`}
              onClick={() => setRating(n)}
              className={`w-9 h-9 rounded-md text-sm font-medium border ${
                rating === n ? 'bg-amber-500 text-white border-amber-500' : 'bg-white text-gray-600 border-gray-300 hover:bg-gray-50'
              }`}
            >
              {n}
            </button>
          ))}
        </div>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Comments (optional)</label>
        <textarea
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          rows={2}
          placeholder="Anything about how this specific interview was run..."
          className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
        />
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}

      <div className="flex gap-2">
        <button
          onClick={handleSubmit}
          disabled={saving}
          className="px-3 py-1.5 bg-blue-600 text-white rounded-md text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
        >
          {saving ? 'Saving...' : saved ? 'Update Feedback' : 'Submit Feedback'}
        </button>
        {onCancel && (
          <button onClick={onCancel} className="px-3 py-1.5 border border-gray-300 rounded-md text-sm">
            Close
          </button>
        )}
      </div>
    </div>
  );
};

export default InterviewRoundFeedbackForm;
