import React, { useState, useEffect } from 'react';
import { X, Star, Plus, Trash2 } from 'lucide-react';

/** Parses the job's screeningQuestions (a JSON string from the backend, or already an array if
 *  a fresh form submission round-tripped through state) into a plain array, tolerating
 *  null/blank/malformed input the same way the backend's own parser does. */
const parseScreeningQuestions = (raw) => {
  if (Array.isArray(raw)) return raw;
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};

const JobPostForm = ({ job, onSubmit, onCancel }) => {
  const [formData, setFormData] = useState({
    title: '',
    company: '',
    description: '',
    requiredSkills: '',
    preferredSkills: '',
    location: '',
    salaryRange: '',
    jobType: '',
    experienceLevel: '',
    scheduledFor: '',
    screeningQuestions: []
  });

  // FIXED: Proper useEffect to set form data when job prop changes
  useEffect(() => {
    if (job) {
      setFormData({
        title: job.title || '',
        company: job.company || '',
        description: job.description || '',
        requiredSkills: job.requiredSkills || '',
        preferredSkills: job.preferredSkills || '',
        location: job.location || '',
        salaryRange: job.salaryRange || '',
        jobType: job.jobType || '',
        experienceLevel: job.experienceLevel || '',
        scheduledFor: '',
        screeningQuestions: parseScreeningQuestions(job.screeningQuestions)
      });
    } else {
      // Reset form when creating new job
      setFormData({
        title: '',
        company: '',
        description: '',
        requiredSkills: '',
        preferredSkills: '',
        location: '',
        salaryRange: '',
        jobType: '',
        experienceLevel: '',
        scheduledFor: '',
        screeningQuestions: []
      });
    }
  }, [job]);

  const handleChange = (e) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value
    });
  };

  const addScreeningQuestion = () => {
    setFormData({
      ...formData,
      screeningQuestions: [
        ...formData.screeningQuestions,
        { questionText: '', expectedAnswer: true, knockout: false }
      ]
    });
  };

  const updateScreeningQuestion = (index, field, value) => {
    setFormData({
      ...formData,
      screeningQuestions: formData.screeningQuestions.map((q, i) =>
        i === index ? { ...q, [field]: value } : q
      )
    });
  };

  const removeScreeningQuestion = (index) => {
    setFormData({
      ...formData,
      screeningQuestions: formData.screeningQuestions.filter((_, i) => i !== index)
    });
  };

  const handleSubmit = (e) => {
    e.preventDefault();

    // Validate required fields
    if (!formData.title || !formData.company || !formData.description ||
        !formData.location || !formData.jobType || !formData.experienceLevel) {
      alert('Please fill in all required fields');
      return;
    }

    // Validate skills format
    try {
      if (formData.requiredSkills) {
        JSON.parse(formData.requiredSkills);
      }
      if (formData.preferredSkills) {
        JSON.parse(formData.preferredSkills);
      }
    } catch (error) {
      alert('Skills must be in valid JSON format: ["skill1", "skill2"]');
      return;
    }

    // Drop any question row the recruiter added but never actually filled in, rather than
    // sending candidates a blank question to answer.
    const screeningQuestions = formData.screeningQuestions.filter((q) => q.questionText.trim());
    for (const q of screeningQuestions) {
      if (typeof q.expectedAnswer !== 'boolean') {
        alert('Each screening question needs a Yes/No expected answer');
        return;
      }
    }

    onSubmit({ ...formData, screeningQuestions });
  };

  const jobTypes = ['Full-time', 'Part-time', 'Contract', 'Internship', 'Remote'];
  const experienceLevels = ['Entry', 'Junior', 'Mid-level', 'Senior', 'Lead', 'Principal'];

  return (
    <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50">
      <div className="relative top-20 mx-auto p-5 border w-full max-w-4xl shadow-lg rounded-md bg-white">
        <div className="flex justify-between items-center mb-6">
          <h2 className="text-2xl font-bold text-gray-900">
            {job ? 'Edit Job Posting' : 'Post New Job'}
          </h2>
          <button
            onClick={onCancel}
            aria-label="Close"
            className="text-gray-400 hover:text-gray-600"
          >
            <X className="h-6 w-6" />
          </button>
        </div>

        {/* New Feature Notice */}
        <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4 mb-6">
          <h4 className="font-semibold text-yellow-800 mb-2 flex items-center">
            <Star className="h-4 w-4 mr-2" />
            New Feature: Job Ratings
          </h4>
          <p className="text-sm text-yellow-700">
            Job seekers can now rate your job postings. This helps improve visibility and provides valuable feedback.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6">
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
            <div>
              <label htmlFor="title" className="block text-sm font-medium text-gray-700">
                Job Title *
              </label>
              <input
                type="text"
                name="title"
                id="title"
                required
                value={formData.title}
                onChange={handleChange}
                className="mt-1 block w-full border border-gray-300 rounded-md shadow-sm py-2 px-3 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                placeholder="e.g., Senior Frontend Developer"
              />
            </div>

            <div>
              <label htmlFor="company" className="block text-sm font-medium text-gray-700">
                Company *
              </label>
              <input
                type="text"
                name="company"
                id="company"
                required
                value={formData.company}
                onChange={handleChange}
                className="mt-1 block w-full border border-gray-300 rounded-md shadow-sm py-2 px-3 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                placeholder="Company name"
              />
            </div>
          </div>

          <div>
            <label htmlFor="description" className="block text-sm font-medium text-gray-700">
              Job Description *
            </label>
            <textarea
              name="description"
              id="description"
              rows={4}
              required
              value={formData.description}
              onChange={handleChange}
              className="mt-1 block w-full border border-gray-300 rounded-md shadow-sm py-2 px-3 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
              placeholder="Detailed job description, responsibilities, and requirements..."
            />
          </div>

          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
            <div>
              <label htmlFor="requiredSkills" className="block text-sm font-medium text-gray-700">
                Required Skills (JSON array) *
              </label>
              <textarea
                name="requiredSkills"
                id="requiredSkills"
                rows={3}
                required
                value={formData.requiredSkills}
                onChange={handleChange}
                className="mt-1 block w-full border border-gray-300 rounded-md shadow-sm py-2 px-3 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                placeholder='["JavaScript", "React", "Node.js"]'
              />
              <p className="mt-1 text-sm text-gray-500">Enter skills as a JSON array</p>
            </div>

            <div>
              <label htmlFor="preferredSkills" className="block text-sm font-medium text-gray-700">
                Preferred Skills (JSON array)
              </label>
              <textarea
                name="preferredSkills"
                id="preferredSkills"
                rows={3}
                value={formData.preferredSkills}
                onChange={handleChange}
                className="mt-1 block w-full border border-gray-300 rounded-md shadow-sm py-2 px-3 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                placeholder='["TypeScript", "AWS", "Docker"]'
              />
              <p className="mt-1 text-sm text-gray-500">Enter skills as a JSON array</p>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-6 sm:grid-cols-3">
            <div>
              <label htmlFor="location" className="block text-sm font-medium text-gray-700">
                Location *
              </label>
              <input
                type="text"
                name="location"
                id="location"
                required
                value={formData.location}
                onChange={handleChange}
                className="mt-1 block w-full border border-gray-300 rounded-md shadow-sm py-2 px-3 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                placeholder="e.g., Remote, New York, NY"
              />
            </div>

            <div>
              <label htmlFor="salaryRange" className="block text-sm font-medium text-gray-700">
                Salary Range
              </label>
              <input
                type="text"
                name="salaryRange"
                id="salaryRange"
                value={formData.salaryRange}
                onChange={handleChange}
                className="mt-1 block w-full border border-gray-300 rounded-md shadow-sm py-2 px-3 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                placeholder="e.g., $80,000 - $120,000"
              />
            </div>

            <div>
              <label htmlFor="jobType" className="block text-sm font-medium text-gray-700">
                Job Type *
              </label>
              <select
                name="jobType"
                id="jobType"
                required
                value={formData.jobType}
                onChange={handleChange}
                className="mt-1 block w-full border border-gray-300 rounded-md shadow-sm py-2 px-3 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
              >
                <option value="">Select job type</option>
                {jobTypes.map(type => (
                  <option key={type} value={type}>{type}</option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <label htmlFor="experienceLevel" className="block text-sm font-medium text-gray-700">
              Experience Level *
            </label>
            <select
              name="experienceLevel"
              id="experienceLevel"
              required
              value={formData.experienceLevel}
              onChange={handleChange}
              className="mt-1 block w-full border border-gray-300 rounded-md shadow-sm py-2 px-3 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
            >
              <option value="">Select experience level</option>
              {experienceLevels.map(level => (
                <option key={level} value={level}>{level}</option>
              ))}
            </select>
          </div>

          {!job && (
            <div>
              <label htmlFor="scheduledFor" className="block text-sm font-medium text-gray-700">
                Schedule for later (optional)
              </label>
              <input
                type="datetime-local"
                name="scheduledFor"
                id="scheduledFor"
                value={formData.scheduledFor}
                onChange={handleChange}
                min={new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16)}
                className="mt-1 block w-full border border-gray-300 rounded-md shadow-sm py-2 px-3 focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
              />
              <p className="mt-1 text-xs text-gray-500">
                Leave blank to go live as soon as it's approved. Otherwise it stays hidden from candidates until this date/time, even once approved.
              </p>
            </div>
          )}

          <div className="pt-4 border-t border-gray-200">
            <div className="flex items-center justify-between mb-2">
              <label className="block text-sm font-medium text-gray-700">
                Screening Questions (optional)
              </label>
              <button
                type="button"
                onClick={addScreeningQuestion}
                className="inline-flex items-center text-sm text-blue-600 hover:text-blue-800 font-medium"
              >
                <Plus className="h-4 w-4 mr-1" /> Add question
              </button>
            </div>
            <p className="text-xs text-gray-500 mb-3">
              Candidates answer these Yes/No when applying. Mark one "Auto-reject" and an answer
              that doesn't match the expected one rejects the application automatically, before
              it reaches your queue.
            </p>
            <div className="space-y-3">
              {formData.screeningQuestions.map((q, index) => (
                <div key={index} className="border border-gray-200 rounded-md p-3 bg-gray-50">
                  <div className="flex items-start gap-2">
                    <input
                      type="text"
                      value={q.questionText}
                      onChange={(e) => updateScreeningQuestion(index, 'questionText', e.target.value)}
                      placeholder="e.g., Are you authorized to work in this country?"
                      className="flex-1 border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500"
                    />
                    <button
                      type="button"
                      onClick={() => removeScreeningQuestion(index)}
                      aria-label={`Remove screening question ${index + 1}`}
                      className="p-2 text-gray-400 hover:text-red-600"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                  <div className="flex items-center gap-4 mt-2 text-sm">
                    <label className="flex items-center gap-1.5">
                      <span className="text-gray-600">Expected answer:</span>
                      <select
                        value={q.expectedAnswer ? 'yes' : 'no'}
                        onChange={(e) => updateScreeningQuestion(index, 'expectedAnswer', e.target.value === 'yes')}
                        className="border border-gray-300 rounded-md px-2 py-1 text-sm"
                      >
                        <option value="yes">Yes</option>
                        <option value="no">No</option>
                      </select>
                    </label>
                    <label className="flex items-center gap-1.5 text-gray-600 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={!!q.knockout}
                        onChange={(e) => updateScreeningQuestion(index, 'knockout', e.target.checked)}
                        className="h-4 w-4 text-red-600 rounded border-gray-300"
                      />
                      Auto-reject if answered incorrectly
                    </label>
                  </div>
                </div>
              ))}
              {formData.screeningQuestions.length === 0 && (
                <p className="text-sm text-gray-400">No screening questions yet.</p>
              )}
            </div>
          </div>

          <div className="flex justify-end space-x-4 pt-6 border-t border-gray-200">
            <button
              type="button"
              onClick={onCancel}
              className="px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
            >
              {job ? 'Update Job' : 'Post Job'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default JobPostForm;