import React, { useState } from 'react';
import { Image, Plus, Trash2, Save } from 'lucide-react';
import api from '../services/api';
import { BACKEND_ORIGIN } from '../config';

const toAbsoluteUrl = (path) => {
  if (!path) return null;
  return path.startsWith('http') ? path : `${BACKEND_ORIGIN}${path}`;
};

const parseBenefits = (raw) => {
  if (Array.isArray(raw)) return raw;
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};

/** Lets a recruiter fill in the richer employer-branding fields shown on their public company
 *  profile page (see CompanyService.getCompanyProfile) - logo, banner, mission statement,
 *  benefits/perks, social links, founded year and headquarters. Wires up the previously-unused
 *  authAPI.updateRecruiterProfile call. */
const CompanyBrandingPanel = ({ userId, recruiterProfile, onUpdated }) => {
  const [missionStatement, setMissionStatement] = useState(recruiterProfile?.missionStatement || '');
  const [benefits, setBenefits] = useState(parseBenefits(recruiterProfile?.benefits));
  const [newBenefit, setNewBenefit] = useState('');
  const [companyLinkedin, setCompanyLinkedin] = useState(recruiterProfile?.companyLinkedin || '');
  const [companyTwitter, setCompanyTwitter] = useState(recruiterProfile?.companyTwitter || '');
  const [foundedYear, setFoundedYear] = useState(recruiterProfile?.foundedYear || '');
  const [headquarters, setHeadquarters] = useState(recruiterProfile?.headquarters || '');

  const [uploadingLogo, setUploadingLogo] = useState(false);
  const [uploadingBanner, setUploadingBanner] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const handleImageUpload = async (e, kind) => {
    const file = e.target.files[0];
    e.target.value = '';
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      setError('Please upload an image file');
      return;
    }

    const setUploading = kind === 'logo' ? setUploadingLogo : setUploadingBanner;
    try {
      setUploading(true);
      setError('');
      const formData = new FormData();
      formData.append('file', file);
      await api.post(`/recruiters/user/${userId}/${kind}`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      setSuccess(`Company ${kind} updated`);
      onUpdated?.();
    } catch (err) {
      setError(err.response?.data?.error || `Failed to upload ${kind}`);
    } finally {
      setUploading(false);
    }
  };

  const addBenefit = () => {
    if (!newBenefit.trim()) return;
    setBenefits((prev) => [...prev, newBenefit.trim()]);
    setNewBenefit('');
  };

  const removeBenefit = (index) => {
    setBenefits((prev) => prev.filter((_, i) => i !== index));
  };

  const handleSave = async (e) => {
    e.preventDefault();
    try {
      setSaving(true);
      setError('');
      setSuccess('');
      await api.put(`/recruiters/user/${userId}/profile`, {
        missionStatement,
        benefits: JSON.stringify(benefits),
        companyLinkedin,
        companyTwitter,
        foundedYear: foundedYear ? String(foundedYear) : '',
        headquarters,
      });
      setSuccess('Company profile updated');
      onUpdated?.();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to update company profile');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="bg-white rounded-lg shadow-lg border border-gray-200 p-6">
      <h2 className="text-lg font-semibold text-gray-900 mb-4 flex items-center">
        <Image className="h-5 w-5 mr-2 text-blue-600" />
        Employer Branding
      </h2>
      <p className="text-sm text-gray-500 mb-4">
        Shown on your public company profile page, visible to every candidate browsing your jobs.
      </p>

      {error && <p className="text-sm text-red-600 mb-3">{error}</p>}
      {success && <p className="text-sm text-green-600 mb-3">{success}</p>}

      <div className="grid sm:grid-cols-2 gap-4 mb-6">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">Company Logo</label>
          {recruiterProfile?.companyLogo && (
            <img
              src={toAbsoluteUrl(recruiterProfile.companyLogo)}
              alt="Company logo"
              className="w-16 h-16 rounded-full object-cover border border-gray-200 mb-2"
            />
          )}
          <label className="inline-flex items-center px-3 py-1.5 text-sm bg-blue-50 text-blue-700 rounded-lg hover:bg-blue-100 cursor-pointer">
            {uploadingLogo ? 'Uploading...' : 'Upload logo'}
            <input type="file" accept="image/*" className="hidden" onChange={(e) => handleImageUpload(e, 'logo')} disabled={uploadingLogo} />
          </label>
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">Company Banner</label>
          {recruiterProfile?.companyBanner && (
            <img
              src={toAbsoluteUrl(recruiterProfile.companyBanner)}
              alt="Company banner"
              className="w-full h-16 rounded object-cover border border-gray-200 mb-2"
            />
          )}
          <label className="inline-flex items-center px-3 py-1.5 text-sm bg-blue-50 text-blue-700 rounded-lg hover:bg-blue-100 cursor-pointer">
            {uploadingBanner ? 'Uploading...' : 'Upload banner'}
            <input type="file" accept="image/*" className="hidden" onChange={(e) => handleImageUpload(e, 'banner')} disabled={uploadingBanner} />
          </label>
        </div>
      </div>

      <form onSubmit={handleSave} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Mission Statement</label>
          <textarea
            value={missionStatement}
            onChange={(e) => setMissionStatement(e.target.value)}
            rows={2}
            placeholder="What is your company working toward?"
            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Benefits & Perks</label>
          <div className="flex flex-wrap gap-2 mb-2">
            {benefits.map((benefit, i) => (
              <span key={i} className="inline-flex items-center gap-1 text-sm bg-green-50 text-green-700 px-3 py-1 rounded-full">
                {benefit}
                <button type="button" onClick={() => removeBenefit(i)} aria-label={`Remove ${benefit}`}>
                  <Trash2 className="h-3 w-3" />
                </button>
              </span>
            ))}
          </div>
          <div className="flex gap-2">
            <input
              type="text"
              value={newBenefit}
              onChange={(e) => setNewBenefit(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addBenefit(); } }}
              placeholder="e.g. Remote-first, Unlimited PTO"
              className="flex-1 border border-gray-300 rounded-lg px-3 py-2 text-sm"
            />
            <button type="button" onClick={addBenefit} aria-label="Add benefit" className="px-3 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200">
              <Plus className="h-4 w-4" />
            </button>
          </div>
        </div>

        <div className="grid sm:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">LinkedIn</label>
            <input
              type="text"
              value={companyLinkedin}
              onChange={(e) => setCompanyLinkedin(e.target.value)}
              placeholder="https://linkedin.com/company/..."
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Twitter / X</label>
            <input
              type="text"
              value={companyTwitter}
              onChange={(e) => setCompanyTwitter(e.target.value)}
              placeholder="https://twitter.com/..."
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Founded Year</label>
            <input
              type="number"
              value={foundedYear}
              onChange={(e) => setFoundedYear(e.target.value)}
              placeholder="e.g. 2015"
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Headquarters</label>
            <input
              type="text"
              value={headquarters}
              onChange={(e) => setHeadquarters(e.target.value)}
              placeholder="e.g. Austin, TX"
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
            />
          </div>
        </div>

        <button
          type="submit"
          disabled={saving}
          className="inline-flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 disabled:opacity-50"
        >
          <Save className="h-4 w-4 mr-2" />
          {saving ? 'Saving...' : 'Save Branding'}
        </button>
      </form>
    </div>
  );
};

export default CompanyBrandingPanel;
