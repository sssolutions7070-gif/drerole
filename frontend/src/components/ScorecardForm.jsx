import React, { useState } from 'react';
import { scorecardAPI } from '../services/api';

const RECOMMENDATIONS = [
  { value: 'STRONG_YES', label: 'Strong yes' },
  { value: 'YES', label: 'Yes' },
  { value: 'NEUTRAL', label: 'Neutral' },
  { value: 'NO', label: 'No' },
  { value: 'STRONG_NO', label: 'Strong no' },
];

const RatingInput = ({ label, value, onChange }) => (
  <div>
    <label className="block text-sm font-medium text-gray-700 mb-1">{label}</label>
    <div className="flex gap-1">
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          onClick={() => onChange(n)}
          className={`w-9 h-9 rounded-md text-sm font-medium border ${
            value === n ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-600 border-gray-300 hover:bg-gray-50'
          }`}
        >
          {n}
        </button>
      ))}
    </div>
  </div>
);

// A fixed rubric (communication/technical/culture-fit, each 1-5, plus an overall recommendation)
// so a candidate's rounds are comparable, rather than relying on free-form notes alone.
const ScorecardForm = ({ roundId, existingScorecard, onSaved, onCancel }) => {
  const [communicationRating, setCommunicationRating] = useState(existingScorecard?.communicationRating || 0);
  const [technicalRating, setTechnicalRating] = useState(existingScorecard?.technicalRating || 0);
  const [cultureFitRating, setCultureFitRating] = useState(existingScorecard?.cultureFitRating || 0);
  const [recommendation, setRecommendation] = useState(existingScorecard?.recommendation || '');
  const [notes, setNotes] = useState(existingScorecard?.notes || '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async () => {
    if (!communicationRating || !technicalRating || !cultureFitRating || !recommendation) {
      setError('Rate every category and pick a recommendation');
      return;
    }
    setSaving(true);
    setError('');
    try {
      const response = await scorecardAPI.submit(roundId, {
        communicationRating, technicalRating, cultureFitRating, recommendation, notes,
      });
      onSaved(response.data);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to save the scorecard');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-4">
      <RatingInput label="Communication" value={communicationRating} onChange={setCommunicationRating} />
      <RatingInput label="Technical skill" value={technicalRating} onChange={setTechnicalRating} />
      <RatingInput label="Culture fit" value={cultureFitRating} onChange={setCultureFitRating} />

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Recommendation</label>
        <select
          value={recommendation}
          onChange={(e) => setRecommendation(e.target.value)}
          className="w-full border border-gray-300 rounded-md px-3 py-2"
        >
          <option value="">Select...</option>
          {RECOMMENDATIONS.map((r) => (
            <option key={r.value} value={r.value}>{r.label}</option>
          ))}
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          rows={3}
          className="w-full border border-gray-300 rounded-md px-3 py-2"
          placeholder="Anything else worth remembering about this round..."
        />
      </div>

      {error && <p className="text-red-500 text-xs">{error}</p>}

      <div className="flex justify-end gap-2">
        <button onClick={onCancel} className="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 hover:bg-gray-50">
          Cancel
        </button>
        <button
          onClick={handleSubmit}
          disabled={saving}
          className="px-4 py-2 bg-blue-600 text-white rounded-md text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
        >
          {saving ? 'Saving...' : 'Save Scorecard'}
        </button>
      </div>
    </div>
  );
};

export default ScorecardForm;
