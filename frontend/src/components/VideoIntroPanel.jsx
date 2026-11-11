import React, { useState } from 'react';
import { Video, Upload } from 'lucide-react';
import api from '../services/api';
import { BACKEND_ORIGIN } from '../config';

const MAX_SIZE_BYTES = 50 * 1024 * 1024;
const ALLOWED_TYPES = ['video/mp4', 'video/webm', 'video/quicktime'];
const ALLOWED_EXTENSIONS = /\.(mp4|webm|mov)$/i;

const getVideoUrl = (path) => {
  if (!path) return null;
  return path.startsWith('http') ? path : `${BACKEND_ORIGIN}${path}`;
};

/** Candidate-facing self-recorded video intro - shown alongside the resume to recruiters
 *  (see CandidateDiscoveryService.toCandidateView). Upload-only, matching the resume/profile
 *  picture pattern rather than adding an in-browser recorder. */
const VideoIntroPanel = ({ userId, videoIntroUrl, onChange }) => {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');

  const handleUpload = async (e) => {
    const file = e.target.files[0];
    e.target.value = '';
    if (!file) return;

    if (!ALLOWED_TYPES.includes(file.type) && !ALLOWED_EXTENSIONS.test(file.name)) {
      setError('Please upload an MP4, WebM, or MOV video');
      return;
    }
    if (file.size > MAX_SIZE_BYTES) {
      setError('Video must be under 50MB');
      return;
    }

    try {
      setUploading(true);
      setError('');
      const formData = new FormData();
      formData.append('file', file);
      const response = await api.post(`/users/${userId}/video-intro`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      onChange(response.data.videoIntroUrl);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to upload video');
    } finally {
      setUploading(false);
    }
  };

  const handleRemove = async () => {
    try {
      setError('');
      await api.delete(`/users/${userId}/video-intro`);
      onChange(null);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to remove video');
    }
  };

  return (
    <div>
      <h3 className="text-lg font-semibold text-gray-900 mb-3 flex items-center">
        <Video className="h-5 w-5 mr-2 text-indigo-600" />
        Video Introduction
      </h3>
      <p className="text-sm text-gray-500 mb-3">
        Add a short video introducing yourself. Recruiters see it alongside your resume.
      </p>

      {error && <p className="text-sm text-red-600 mb-2">{error}</p>}

      {videoIntroUrl ? (
        <div className="space-y-3">
          <video
            data-testid="video-intro-player"
            src={getVideoUrl(videoIntroUrl)}
            controls
            className="w-full rounded-lg max-h-64 bg-black"
          />
          <div className="flex gap-2">
            <label className="cursor-pointer px-3 py-1.5 text-sm bg-indigo-50 text-indigo-700 rounded-lg hover:bg-indigo-100 transition-colors">
              {uploading ? 'Uploading...' : 'Replace'}
              <input
                type="file"
                accept="video/mp4,video/webm,video/quicktime"
                className="hidden"
                onChange={handleUpload}
                disabled={uploading}
              />
            </label>
            <button
              onClick={handleRemove}
              className="px-3 py-1.5 text-sm bg-red-50 text-red-700 rounded-lg hover:bg-red-100 transition-colors"
            >
              Remove
            </button>
          </div>
        </div>
      ) : (
        <label className="flex flex-col items-center justify-center gap-2 border-2 border-dashed border-gray-300 rounded-lg p-6 cursor-pointer hover:border-indigo-400 transition-colors">
          <Upload className="h-6 w-6 text-gray-400" />
          <span className="text-sm text-gray-500 text-center">
            {uploading ? 'Uploading...' : 'Click to upload a video (MP4, WebM, or MOV - max 50MB)'}
          </span>
          <input
            type="file"
            accept="video/mp4,video/webm,video/quicktime"
            className="hidden"
            onChange={handleUpload}
            disabled={uploading}
          />
        </label>
      )}
    </div>
  );
};

export default VideoIntroPanel;
