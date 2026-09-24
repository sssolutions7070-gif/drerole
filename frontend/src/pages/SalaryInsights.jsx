// src/pages/SalaryInsights.jsx
import React, { useEffect, useState } from 'react';
import { DollarSign, Search } from 'lucide-react';
import { salaryInsightsAPI } from '../services/api';

const EXPERIENCE_LEVELS = ['Entry', 'Mid', 'Senior', 'Lead'];

const formatCurrency = (value) => {
  if (value === null || value === undefined) return '—';
  return `$${Number(value).toLocaleString()}`;
};

const SalaryInsights = () => {
  const [title, setTitle] = useState('');
  const [location, setLocation] = useState('');
  const [experienceLevel, setExperienceLevel] = useState('');
  const [insights, setInsights] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');

  const fetchInsights = async (e) => {
    if (e) e.preventDefault();
    setIsLoading(true);
    setError('');
    try {
      const response = await salaryInsightsAPI.get(title || undefined, location || undefined, experienceLevel || undefined);
      setInsights(response.data);
    } catch (err) {
      setError('Failed to load salary insights.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchInsights();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const overall = insights?.overall;
  const byExperienceLevel = insights?.byExperienceLevel || {};

  return (
    <div className="max-w-4xl mx-auto py-8 px-4">
      <div className="flex items-center gap-2 mb-2">
        <DollarSign className="h-7 w-7 text-green-600" />
        <h1 className="text-3xl font-bold text-gray-800">Salary Insights</h1>
      </div>
      <p className="text-gray-600 mb-6">
        See what roles are actually paying on DreamRole, based on posted job ranges - no account needed.
      </p>

      <form onSubmit={fetchInsights} className="bg-white rounded-lg shadow p-4 mb-6 grid sm:grid-cols-4 gap-3">
        <input
          type="text"
          placeholder="Job title (e.g. Backend Engineer)"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          className="sm:col-span-2 px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        />
        <input
          type="text"
          placeholder="Location"
          value={location}
          onChange={(e) => setLocation(e.target.value)}
          className="px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        />
        <select
          value={experienceLevel}
          onChange={(e) => setExperienceLevel(e.target.value)}
          className="px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="">Any experience level</option>
          {EXPERIENCE_LEVELS.map((level) => (
            <option key={level} value={level}>{level}</option>
          ))}
        </select>
        <button
          type="submit"
          className="sm:col-span-4 flex items-center justify-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors"
        >
          <Search className="h-4 w-4" />
          Search
        </button>
      </form>

      {error && <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-4">{error}</div>}

      {isLoading ? (
        <div className="text-center py-12 text-gray-500">Loading...</div>
      ) : !overall || overall.count === 0 ? (
        <div className="text-center py-12 bg-white rounded-lg shadow">
          <DollarSign className="mx-auto h-16 w-16 text-gray-400 mb-4" />
          <p className="text-gray-500">No posted salary data matches these filters yet.</p>
        </div>
      ) : (
        <div className="space-y-6">
          <div className="bg-white rounded-lg shadow p-6">
            <p className="text-sm text-gray-500 mb-4">
              Based on {insights.matchingPostings} matching job posting{insights.matchingPostings === 1 ? '' : 's'}
            </p>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-center">
              <div>
                <p className="text-xs text-gray-500 uppercase">Min</p>
                <p className="text-xl font-bold text-gray-800">{formatCurrency(overall.minSalary)}</p>
              </div>
              <div>
                <p className="text-xs text-gray-500 uppercase">Average</p>
                <p className="text-xl font-bold text-blue-600">{formatCurrency(overall.averageSalary)}</p>
              </div>
              <div>
                <p className="text-xs text-gray-500 uppercase">Median</p>
                <p className="text-xl font-bold text-gray-800">{formatCurrency(overall.medianSalary)}</p>
              </div>
              <div>
                <p className="text-xs text-gray-500 uppercase">Max</p>
                <p className="text-xl font-bold text-gray-800">{formatCurrency(overall.maxSalary)}</p>
              </div>
            </div>
          </div>

          {Object.keys(byExperienceLevel).length > 0 && (
            <div className="bg-white rounded-lg shadow p-6">
              <h2 className="text-lg font-semibold text-gray-800 mb-4">By experience level</h2>
              <div className="divide-y divide-gray-100">
                {Object.entries(byExperienceLevel).map(([level, stats]) => (
                  <div key={level} className="py-3 flex items-center justify-between">
                    <div>
                      <p className="font-medium text-gray-800">{level}</p>
                      <p className="text-xs text-gray-500">{stats.count} posting{stats.count === 1 ? '' : 's'}</p>
                    </div>
                    <p className="text-sm text-gray-700">
                      {formatCurrency(stats.minSalary)} – {formatCurrency(stats.maxSalary)}
                      <span className="text-gray-400"> (avg {formatCurrency(stats.averageSalary)})</span>
                    </p>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default SalaryInsights;
