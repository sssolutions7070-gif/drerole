import React, { useEffect, useState } from 'react';
import { Users, Save } from 'lucide-react';
import api from '../services/api';

const PREFER_NOT_TO_SAY = 'Prefer not to say';

const GENDER_OPTIONS = ['Woman', 'Man', 'Non-binary', PREFER_NOT_TO_SAY];
const ETHNICITY_OPTIONS = [
  'Black or African descent', 'East Asian', 'South Asian', 'Hispanic or Latino',
  'Middle Eastern or North African', 'Native American or Indigenous', 'Pacific Islander',
  'White or Caucasian', 'Two or more ethnicities', PREFER_NOT_TO_SAY,
];
const VETERAN_OPTIONS = ['Veteran', 'Not a veteran', PREFER_NOT_TO_SAY];
const DISABILITY_OPTIONS = ["Yes, I have a disability", "No, I don't have a disability", PREFER_NOT_TO_SAY];

/** Entirely voluntary EEO-style self-identification - used only for anonymous, aggregate
 *  diversity reporting to recruiters (see DiversityAnalyticsService). Never shown against this
 *  candidate's name, and skipping every question is always a valid choice. */
const DiversityInfoPanel = ({ userId }) => {
  const [genderIdentity, setGenderIdentity] = useState('');
  const [ethnicity, setEthnicity] = useState('');
  const [veteranStatus, setVeteranStatus] = useState('');
  const [disabilityStatus, setDisabilityStatus] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const response = await api.get(`/users/${userId}/diversity-info`);
        setGenderIdentity(response.data.genderIdentity || '');
        setEthnicity(response.data.ethnicity || '');
        setVeteranStatus(response.data.veteranStatus || '');
        setDisabilityStatus(response.data.disabilityStatus || '');
      } catch {
        // Leave the form blank - answering is optional either way.
      } finally {
        setLoading(false);
      }
    })();
  }, [userId]);

  const handleSave = async (e) => {
    e.preventDefault();
    try {
      setSaving(true);
      setError('');
      setSuccess('');
      await api.put(`/users/${userId}/diversity-info`, {
        genderIdentity, ethnicity, veteranStatus, disabilityStatus,
      });
      setSuccess('Saved. Thank you for sharing.');
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to save');
    } finally {
      setSaving(false);
    }
  };

  if (loading) return null;

  return (
    <div>
      <h3 className="text-lg font-semibold text-gray-900 mb-2 flex items-center">
        <Users className="h-5 w-5 mr-2 text-indigo-600" />
        Diversity & Inclusion
      </h3>
      <p className="text-sm text-gray-500 mb-4">
        Completely optional. Your answers are never shown to any recruiter individually - only as
        anonymous, aggregate statistics across all applicants, and only once enough people have
        answered to protect everyone's privacy. Skip any question you'd rather not answer.
      </p>

      {error && <p className="text-sm text-red-600 mb-2">{error}</p>}
      {success && <p className="text-sm text-green-600 mb-2">{success}</p>}

      <form onSubmit={handleSave} className="space-y-3">
        <div>
          <label htmlFor="genderIdentity" className="block text-sm font-medium text-gray-700 mb-1">Gender Identity</label>
          <select id="genderIdentity" value={genderIdentity} onChange={(e) => setGenderIdentity(e.target.value)}
            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm">
            <option value="">Select...</option>
            {GENDER_OPTIONS.map((opt) => <option key={opt} value={opt}>{opt}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="ethnicity" className="block text-sm font-medium text-gray-700 mb-1">Ethnicity</label>
          <select id="ethnicity" value={ethnicity} onChange={(e) => setEthnicity(e.target.value)}
            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm">
            <option value="">Select...</option>
            {ETHNICITY_OPTIONS.map((opt) => <option key={opt} value={opt}>{opt}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="veteranStatus" className="block text-sm font-medium text-gray-700 mb-1">Veteran Status</label>
          <select id="veteranStatus" value={veteranStatus} onChange={(e) => setVeteranStatus(e.target.value)}
            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm">
            <option value="">Select...</option>
            {VETERAN_OPTIONS.map((opt) => <option key={opt} value={opt}>{opt}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="disabilityStatus" className="block text-sm font-medium text-gray-700 mb-1">Disability Status</label>
          <select id="disabilityStatus" value={disabilityStatus} onChange={(e) => setDisabilityStatus(e.target.value)}
            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm">
            <option value="">Select...</option>
            {DISABILITY_OPTIONS.map((opt) => <option key={opt} value={opt}>{opt}</option>)}
          </select>
        </div>

        <button
          type="submit"
          disabled={saving}
          className="inline-flex items-center px-4 py-2 bg-indigo-600 text-white rounded-lg font-medium hover:bg-indigo-700 disabled:opacity-50"
        >
          <Save className="h-4 w-4 mr-2" />
          {saving ? 'Saving...' : 'Save'}
        </button>
      </form>
    </div>
  );
};

export default DiversityInfoPanel;
