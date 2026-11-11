import React, { useState } from 'react';
import { X, Send, Plus, Trash2 } from 'lucide-react';
import { outreachSequenceAPI } from '../services/api';

/** Lets a recruiter queue a multi-step outreach campaign to one sourced candidate - step 0 sends
 *  immediately, later steps fire automatically after their delay unless the candidate replies
 *  first (see OutreachSequenceService). */
const OutreachSequenceModal = ({ recruiterId, candidate, onClose, onStarted }) => {
  const [steps, setSteps] = useState([
    { delayDays: 0, message: '' },
    { delayDays: 3, message: '' },
  ]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const updateStep = (index, field, value) => {
    setSteps((prev) => prev.map((step, i) => (i === index ? { ...step, [field]: value } : step)));
  };

  const addStep = () => {
    setSteps((prev) => [...prev, { delayDays: 3, message: '' }]);
  };

  const removeStep = (index) => {
    setSteps((prev) => prev.filter((_, i) => i !== index));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const filledSteps = steps
      .filter((step) => step.message.trim())
      .map((step) => ({ delayDays: Number(step.delayDays) || 0, message: step.message.trim() }));
    if (filledSteps.length === 0) {
      setError('Add at least one step with a message');
      return;
    }
    try {
      setSubmitting(true);
      setError('');
      await outreachSequenceAPI.start(recruiterId, candidate.userId, filledSteps);
      onStarted?.();
      onClose();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to start outreach sequence');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-gray-600 bg-opacity-50 flex items-center justify-center z-50 p-4 overflow-y-auto">
      <div className="bg-white rounded-lg shadow-lg w-full max-w-lg max-h-[85vh] overflow-y-auto">
        <div className="flex justify-between items-center p-4 border-b border-gray-200">
          <h2 className="text-lg font-semibold text-gray-900 flex items-center">
            <Send className="h-5 w-5 mr-2 text-purple-600" />
            Outreach Sequence for {candidate.fullName}
          </h2>
          <button onClick={onClose} aria-label="Close" className="text-gray-400 hover:text-gray-600">
            <X className="h-6 w-6" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-4 space-y-4">
          <p className="text-xs text-gray-500">
            The first step sends as soon as you start the sequence. Later steps send automatically
            after their delay, unless {candidate.fullName} replies first - the sequence stops on its own then.
          </p>

          {error && <p className="text-sm text-red-600">{error}</p>}

          <div className="space-y-3">
            {steps.map((step, index) => (
              <div key={index} className="border border-gray-200 rounded-md p-3 bg-gray-50">
                <div className="flex items-center justify-between mb-2">
                  <label className="text-xs font-medium text-gray-600">
                    Step {index + 1} - {index === 0 ? 'sent immediately' : `wait ${step.delayDays} day${step.delayDays === 1 ? '' : 's'}`}
                  </label>
                  {steps.length > 1 && (
                    <button
                      type="button"
                      onClick={() => removeStep(index)}
                      aria-label={`Remove step ${index + 1}`}
                      className="text-gray-400 hover:text-red-600"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  )}
                </div>
                {index > 0 && (
                  <input
                    type="number"
                    min="1"
                    value={step.delayDays}
                    onChange={(e) => updateStep(index, 'delayDays', e.target.value)}
                    className="w-20 border border-gray-300 rounded-md px-2 py-1 text-sm mb-2"
                  />
                )}
                <textarea
                  value={step.message}
                  onChange={(e) => updateStep(index, 'message', e.target.value)}
                  placeholder="Message to send..."
                  rows={2}
                  className="w-full border border-gray-300 rounded-md px-2 py-1.5 text-sm"
                />
              </div>
            ))}
          </div>

          <button
            type="button"
            onClick={addStep}
            className="inline-flex items-center text-sm text-blue-600 hover:text-blue-800 font-medium"
          >
            <Plus className="h-4 w-4 mr-1" /> Add step
          </button>

          <div className="flex justify-end gap-2 pt-2 border-t border-gray-100">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-4 py-2 bg-purple-600 text-white rounded-md text-sm font-medium hover:bg-purple-700 disabled:opacity-50"
            >
              {submitting ? 'Starting...' : 'Start Sequence'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default OutreachSequenceModal;
