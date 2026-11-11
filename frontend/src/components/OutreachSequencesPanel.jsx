import React, { useEffect, useState } from 'react';
import { Send, Ban } from 'lucide-react';
import { outreachSequenceAPI } from '../services/api';

const STATUS_STYLE = {
  ACTIVE: 'bg-blue-100 text-blue-700',
  COMPLETED: 'bg-gray-100 text-gray-700',
  STOPPED: 'bg-red-100 text-red-700',
  REPLIED: 'bg-green-100 text-green-700',
};

/** Lists this recruiter's outreach sequences (active, completed, stopped, or ended because the
 *  candidate replied) with a Stop action for the active ones. */
const OutreachSequencesPanel = ({ recruiterId, refreshKey }) => {
  const [sequences, setSequences] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [stoppingId, setStoppingId] = useState(null);

  const fetchSequences = async () => {
    try {
      setLoading(true);
      const response = await outreachSequenceAPI.list(recruiterId);
      setSequences(response.data || []);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to load outreach sequences');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (recruiterId) fetchSequences();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recruiterId, refreshKey]);

  const handleStop = async (sequenceId) => {
    try {
      setStoppingId(sequenceId);
      await outreachSequenceAPI.stop(recruiterId, sequenceId);
      setSequences((prev) => prev.map((s) => (s.id === sequenceId ? { ...s, status: 'STOPPED' } : s)));
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to stop sequence');
    } finally {
      setStoppingId(null);
    }
  };

  if (loading) return <p className="text-sm text-gray-500">Loading outreach sequences...</p>;

  return (
    <div className="mt-6">
      <h3 className="text-sm font-semibold text-gray-700 uppercase tracking-wide mb-3 flex items-center gap-1.5">
        <Send className="h-4 w-4 text-purple-600" />
        Outreach Sequences ({sequences.length})
      </h3>

      {error && <p className="text-sm text-red-600 mb-2">{error}</p>}

      {sequences.length === 0 ? (
        <p className="text-sm text-gray-500">No outreach sequences yet.</p>
      ) : (
        <div className="space-y-2">
          {sequences.map((sequence) => (
            <div key={sequence.id} className="flex items-center justify-between bg-white border border-gray-200 rounded-lg px-3 py-2">
              <div>
                <span className="text-sm font-medium text-gray-900">{sequence.candidateName}</span>
                <span className="text-xs text-gray-500 ml-2">
                  Step {sequence.currentStep} of {sequence.totalSteps}
                </span>
              </div>
              <div className="flex items-center gap-2">
                <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${STATUS_STYLE[sequence.status] || 'bg-gray-100 text-gray-700'}`}>
                  {sequence.status}
                </span>
                {sequence.status === 'ACTIVE' && (
                  <button
                    onClick={() => handleStop(sequence.id)}
                    disabled={stoppingId === sequence.id}
                    aria-label={`Stop sequence for ${sequence.candidateName}`}
                    className="text-gray-400 hover:text-red-600 disabled:opacity-50"
                  >
                    <Ban className="h-4 w-4" />
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

export default OutreachSequencesPanel;
