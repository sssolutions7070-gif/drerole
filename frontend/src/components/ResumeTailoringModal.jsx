// src/components/ResumeTailoringModal.jsx
import React, { useState, useEffect } from 'react';
import {
  X, Sparkles, TrendingUp, Check, Copy, Target,
  Zap, Lightbulb, FileText, CheckCircle, ArrowRight, Loader2
} from 'lucide-react';
import api from '../services/api';

const ResumeTailoringModal = ({
  job,
  userSkills = [],
  missingSkills = [],
  currentMatchScore = 0,
  onClose
}) => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [tailoringData, setTailoringData] = useState(null);
  const [copiedIndex, setCopiedIndex] = useState(null);
  const [copiedKeyword, setCopiedKeyword] = useState(null);

  useEffect(() => {
    fetchTailoringAdvice();
  }, [job]);

  const fetchTailoringAdvice = async () => {
    try {
      setLoading(true);
      setError('');

      const response = await api.post('/resume-matching/tailor-resume', {
        jobTitle: job?.title || 'Target Role',
        jobDescription: job?.description || '',
        userSkills: userSkills || [],
        missingSkills: missingSkills || job?.missingSkills || [],
        currentMatchScore: currentMatchScore || job?.matchScore || 0
      });

      setTailoringData(response.data);
    } catch (err) {
      console.error('Failed to generate resume tailoring advice:', err);
      setError(err.response?.data?.error || 'Failed to load AI resume tailoring advice. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const copyToClipboard = (text, index = null, isKeyword = false) => {
    navigator.clipboard.writeText(text);
    if (isKeyword) {
      setCopiedKeyword(text);
      setTimeout(() => setCopiedKeyword(null), 2000);
    } else {
      setCopiedIndex(index);
      setTimeout(() => setCopiedIndex(null), 2000);
    }
  };

  const score = currentMatchScore || job?.matchScore || 0;
  const projectedScore = tailoringData?.projectedScore || Math.min(95, score + 22);
  const boostDiff = Math.max(0, projectedScore - score);

  return (
    <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-sm overflow-y-auto h-full w-full z-50 flex items-center justify-center p-4">
      <div className="relative w-full max-w-3xl bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden my-8 max-h-[90vh] flex flex-col">
        {/* Header with Purple-Indigo AI Gradient */}
        <div className="bg-gradient-to-r from-purple-700 via-indigo-700 to-blue-600 px-6 py-5 text-white flex items-center justify-between flex-shrink-0">
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 rounded-xl bg-white/10 backdrop-blur border border-white/20 flex items-center justify-center shadow-inner">
              <Sparkles className="h-5 w-5 text-yellow-300 animate-pulse" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <h3 className="text-xl font-bold text-white">AI Resume Tailoring Copilot</h3>
                <span className="bg-white/20 text-yellow-200 text-[11px] font-semibold px-2 py-0.5 rounded-full border border-white/20">
                  Match Boost
                </span>
              </div>
              <p className="text-purple-100 text-xs mt-0.5">
                Tailored for <span className="font-semibold text-white">{job?.title}</span> {job?.company ? `at ${job?.company}` : ''}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            aria-label="Close modal"
            className="text-white/80 hover:text-white hover:bg-white/10 p-2 rounded-lg transition-colors"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-6 overflow-y-auto space-y-6 flex-1 bg-slate-50/50">
          {loading ? (
            <div className="py-16 text-center space-y-4">
              <div className="relative inline-flex items-center justify-center">
                <Loader2 className="h-10 w-10 text-indigo-600 animate-spin" />
                <Sparkles className="h-4 w-4 text-purple-600 absolute" />
              </div>
              <h4 className="text-base font-semibold text-slate-800">
                Analyzing Job Description & Synthesizing Tailoring Advice...
              </h4>
              <p className="text-xs text-slate-500 max-w-sm mx-auto">
                Comparing your current skills with role requirements to calculate high-impact bullet points and keyword optimizations.
              </p>
            </div>
          ) : error ? (
            <div className="p-4 bg-rose-50 border border-rose-200 text-rose-700 rounded-xl text-sm">
              {error}
            </div>
          ) : (
            <>
              {/* Score Boost Comparison Card */}
              <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-sm">
                <div className="flex flex-wrap items-center justify-between gap-4 mb-4">
                  <div>
                    <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                      Match Score Projection
                    </span>
                    <h4 className="text-lg font-bold text-slate-900">
                      Potential Boost: <span className="text-emerald-600">+{boostDiff}% Match</span>
                    </h4>
                  </div>
                  <div className="inline-flex items-center bg-emerald-50 text-emerald-700 px-3 py-1.5 rounded-full border border-emerald-200 text-xs font-bold">
                    <TrendingUp className="h-4 w-4 mr-1.5" />
                    Shortlist Probability: High
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {/* Current Score */}
                  <div className="bg-slate-50 rounded-lg p-3.5 border border-slate-200">
                    <div className="flex items-center justify-between text-xs text-slate-600 mb-1.5 font-medium">
                      <span>Current Match</span>
                      <span className="font-bold text-slate-900">{score}%</span>
                    </div>
                    <div className="w-full bg-slate-200 h-2.5 rounded-full overflow-hidden">
                      <div
                        className={`h-full rounded-full ${
                          score >= 80 ? 'bg-emerald-500' : score >= 60 ? 'bg-blue-500' : 'bg-amber-500'
                        }`}
                        style={{ width: `${Math.min(100, Math.max(10, score))}%` }}
                      />
                    </div>
                  </div>

                  {/* Projected Boosted Score */}
                  <div className="bg-gradient-to-r from-emerald-50 to-teal-50 rounded-lg p-3.5 border border-emerald-200">
                    <div className="flex items-center justify-between text-xs text-emerald-800 mb-1.5 font-semibold">
                      <span className="flex items-center">
                        <Zap className="h-3.5 w-3.5 text-emerald-600 mr-1" />
                        With Suggested Tailoring
                      </span>
                      <span className="font-bold text-emerald-700 text-sm">{projectedScore}%</span>
                    </div>
                    <div className="w-full bg-emerald-200/60 h-2.5 rounded-full overflow-hidden">
                      <div
                        className="h-full bg-gradient-to-r from-emerald-500 to-teal-500 rounded-full"
                        style={{ width: `${projectedScore}%` }}
                      />
                    </div>
                  </div>
                </div>

                {/* AI Executive Summary Message */}
                {tailoringData?.boostSummary && (
                  <div className="mt-4 p-3.5 bg-purple-50/70 border border-purple-200 rounded-lg flex items-start space-x-3">
                    <Lightbulb className="h-5 w-5 text-purple-600 flex-shrink-0 mt-0.5" />
                    <p className="text-xs text-purple-900 font-medium leading-relaxed">
                      {tailoringData.boostSummary}
                    </p>
                  </div>
                )}
              </div>

              {/* Missing Skills Roadmap */}
              {tailoringData?.missingSkillRecommendations && tailoringData.missingSkillRecommendations.length > 0 && (
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <h4 className="text-sm font-bold text-slate-900 flex items-center">
                      <Target className="h-4 w-4 text-purple-600 mr-1.5" />
                      Skills to Incorporate into Your Resume
                    </h4>
                    <span className="text-xs text-slate-500">
                      {tailoringData.missingSkillRecommendations.length} recommendations
                    </span>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    {tailoringData.missingSkillRecommendations.map((item, idx) => (
                      <div
                        key={idx}
                        className="bg-white p-3.5 rounded-xl border border-slate-200 shadow-sm hover:border-purple-300 transition-colors"
                      >
                        <div className="flex items-center justify-between mb-1.5">
                          <span className="font-bold text-sm text-slate-900">{item.skill}</span>
                          <span
                            className={`text-[10px] font-bold px-2 py-0.5 rounded-full uppercase tracking-wider ${
                              item.priority?.toLowerCase().includes('high')
                                ? 'bg-rose-100 text-rose-700'
                                : 'bg-amber-100 text-amber-700'
                            }`}
                          >
                            {item.priority || 'High Priority'}
                          </span>
                        </div>
                        <p className="text-xs text-slate-600 mb-1.5 leading-relaxed">
                          {item.impactReason}
                        </p>
                        {item.actionTip && (
                          <div className="text-[11px] text-purple-700 bg-purple-50/60 px-2.5 py-1 rounded-md border border-purple-100 flex items-center">
                            <span className="font-semibold mr-1">Tip:</span> {item.actionTip}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Experience Bullet Point Rewrites */}
              {tailoringData?.bulletPointSuggestions && tailoringData.bulletPointSuggestions.length > 0 && (
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <h4 className="text-sm font-bold text-slate-900 flex items-center">
                      <FileText className="h-4 w-4 text-indigo-600 mr-1.5" />
                      1-Click Resume Bullet Point Rewrites
                    </h4>
                    <span className="text-xs text-slate-500">
                      Click copy icon to paste directly into your resume
                    </span>
                  </div>

                  <div className="space-y-3">
                    {tailoringData.bulletPointSuggestions.map((bullet, idx) => (
                      <div
                        key={idx}
                        className="bg-white rounded-xl border border-slate-200 p-4 shadow-sm hover:border-indigo-300 transition-all group"
                      >
                        <div className="flex items-center justify-between gap-2 mb-2">
                          <span className="text-xs font-bold text-indigo-600 bg-indigo-50 px-2 py-0.5 rounded">
                            {bullet.category || 'Experience'}
                          </span>

                          <button
                            type="button"
                            onClick={() => copyToClipboard(bullet.enhancedBullet, idx)}
                            className={`inline-flex items-center text-xs font-semibold px-2.5 py-1 rounded-md transition-colors ${
                              copiedIndex === idx
                                ? 'bg-emerald-100 text-emerald-800'
                                : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
                            }`}
                            title="Copy to clipboard"
                          >
                            {copiedIndex === idx ? (
                              <>
                                <Check className="h-3.5 w-3.5 mr-1 text-emerald-600" />
                                Copied!
                              </>
                            ) : (
                              <>
                                <Copy className="h-3.5 w-3.5 mr-1 text-slate-500" />
                                Copy Bullet
                              </>
                            )}
                          </button>
                        </div>

                        <p className="text-xs text-slate-800 font-medium leading-relaxed mb-2.5">
                          "{bullet.enhancedBullet}"
                        </p>

                        <div className="flex flex-wrap items-center justify-between gap-2 text-[11px] pt-2 border-t border-slate-100">
                          {bullet.skillsHighlighted && bullet.skillsHighlighted.length > 0 && (
                            <div className="flex items-center space-x-1">
                              <span className="text-slate-400">Target Skills:</span>
                              {bullet.skillsHighlighted.map((sk, sIdx) => (
                                <span
                                  key={sIdx}
                                  className="bg-purple-100 text-purple-800 font-semibold px-1.5 py-0.5 rounded text-[10px]"
                                >
                                  {sk}
                                </span>
                              ))}
                            </div>
                          )}
                          {bullet.metricImpact && (
                            <span className="text-emerald-700 font-semibold flex items-center">
                              <ArrowRight className="h-3 w-3 mr-1 text-emerald-500" />
                              {bullet.metricImpact}
                            </span>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* High-Impact ATS Keywords */}
              {tailoringData?.keywordOptimization && tailoringData.keywordOptimization.length > 0 && (
                <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-sm">
                  <h4 className="text-xs font-bold text-slate-700 uppercase tracking-wider mb-2.5 flex items-center">
                    <Sparkles className="h-3.5 w-3.5 text-amber-500 mr-1.5" />
                    Essential ATS Keywords to Include
                  </h4>
                  <div className="flex flex-wrap gap-1.5">
                    {tailoringData.keywordOptimization.map((kw, idx) => (
                      <button
                        key={idx}
                        type="button"
                        onClick={() => copyToClipboard(kw, null, true)}
                        className={`text-xs px-2.5 py-1 rounded-lg border transition-all ${
                          copiedKeyword === kw
                            ? 'bg-emerald-50 border-emerald-300 text-emerald-800 font-bold'
                            : 'bg-slate-50 border-slate-200 text-slate-700 hover:bg-purple-50 hover:border-purple-300'
                        }`}
                        title="Click to copy"
                      >
                        {kw} {copiedKeyword === kw ? '✓' : ''}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 bg-white border-t border-slate-200 flex items-center justify-between flex-shrink-0">
          <p className="text-xs text-slate-500">
            Copy recommendations to update your resume on the Resume Upload page.
          </p>
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 bg-slate-900 text-white rounded-lg text-sm font-semibold hover:bg-slate-800 transition-colors shadow-sm"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};

export default ResumeTailoringModal;
