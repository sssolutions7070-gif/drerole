// src/components/CareerRoadmapModal.jsx
import React, { useState, useEffect } from 'react';
import {
  X, Compass, Sparkles, CheckCircle2, Circle, ArrowRight,
  ExternalLink, BookOpen, Code, Layers, Rocket, Check, Loader2, Trophy
} from 'lucide-react';
import api from '../services/api';

const CareerRoadmapModal = ({
  job,
  userSkills = [],
  missingSkills = [],
  currentMatchScore = 0,
  onClose,
  onStartPractice
}) => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [roadmapData, setRoadmapData] = useState(null);
  const [activeWeek, setActiveWeek] = useState(1);
  const [completedTasks, setCompletedTasks] = useState({});

  useEffect(() => {
    fetchCareerRoadmap();
  }, [job]);

  // Load saved checklist from localStorage
  useEffect(() => {
    if (job?.id) {
      try {
        const saved = localStorage.getItem(`dreamrole_roadmap_tasks_${job.id}`);
        if (saved) {
          setCompletedTasks(JSON.parse(saved));
        }
      } catch (e) {
        console.error('Failed to parse saved roadmap tasks:', e);
      }
    }
  }, [job?.id]);

  const toggleTask = (taskKey) => {
    setCompletedTasks((prev) => {
      const next = { ...prev, [taskKey]: !prev[taskKey] };
      if (job?.id) {
        try {
          localStorage.setItem(`dreamrole_roadmap_tasks_${job.id}`, JSON.stringify(next));
        } catch (e) {
          console.error('Failed to save roadmap tasks:', e);
        }
      }
      return next;
    });
  };

  const fetchCareerRoadmap = async () => {
    try {
      setLoading(true);
      setError('');

      const response = await api.post('/resume-matching/career-roadmap', {
        jobTitle: job?.title || 'Target Role',
        jobDescription: job?.description || '',
        userSkills: userSkills || [],
        missingSkills: missingSkills || job?.missingSkills || [],
        currentMatchScore: currentMatchScore || job?.matchScore || 0
      });

      setRoadmapData(response.data);
    } catch (err) {
      console.error('Failed to generate career roadmap:', err);
      setError(err.response?.data?.error || 'Failed to load AI career roadmap. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const score = currentMatchScore || job?.matchScore || 0;
  const projectedScore = roadmapData?.projectedScore || Math.min(96, Math.max(score + 25, 88));

  // Compute total task completion
  const allTasks = roadmapData?.milestones?.flatMap((m, mIdx) => 
    (m.tasks || []).map((t, tIdx) => `w${m.week || mIdx + 1}_t${tIdx}`)
  ) || [];
  const completedCount = allTasks.filter(k => completedTasks[k]).length;
  const progressPercent = allTasks.length > 0 ? Math.round((completedCount / allTasks.length) * 100) : 0;

  return (
    <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-sm overflow-y-auto h-full w-full z-50 flex items-center justify-center p-4">
      <div className="relative w-full max-w-4xl bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden my-8 max-h-[90vh] flex flex-col">
        {/* Header with Emerald-Teal AI Gradient */}
        <div className="bg-gradient-to-r from-teal-700 via-emerald-700 to-cyan-700 px-6 py-5 text-white flex items-center justify-between flex-shrink-0">
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 rounded-xl bg-white/10 backdrop-blur border border-white/20 flex items-center justify-center shadow-inner">
              <Compass className="h-5 w-5 text-emerald-200 animate-pulse" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <h3 className="text-xl font-bold text-white">Candidate AI Career Roadmap</h3>
                <span className="bg-white/20 text-emerald-100 text-[11px] font-semibold px-2 py-0.5 rounded-full border border-white/20">
                  4-Week Fast Track
                </span>
              </div>
              <p className="text-emerald-100 text-xs mt-0.5">
                Targeted skill-bridge curriculum for <span className="font-semibold text-white">{job?.title}</span> {job?.company ? `at ${job?.company}` : ''}
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
            <div className="py-20 text-center space-y-4">
              <div className="relative inline-flex items-center justify-center">
                <Loader2 className="h-10 w-10 text-emerald-600 animate-spin" />
                <Compass className="h-4 w-4 text-teal-600 absolute" />
              </div>
              <div>
                <p className="font-medium text-slate-800">Generating Personalized 4-Week Curriculum...</p>
                <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
                  Synthesizing skill prerequisites, interactive tutorials, and capstone milestones tailored to your target role.
                </p>
              </div>
            </div>
          ) : error ? (
            <div className="py-12 text-center space-y-4">
              <div className="p-4 bg-red-50 text-red-700 border border-red-200 rounded-xl max-w-md mx-auto text-sm">
                {error}
              </div>
              <button
                onClick={fetchCareerRoadmap}
                className="px-4 py-2 bg-emerald-600 text-white rounded-lg text-sm font-medium hover:bg-emerald-700 transition"
              >
                Retry Generation
              </button>
            </div>
          ) : (
            <>
              {/* Match Boost & Progress Banner */}
              <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm grid grid-cols-1 md:grid-cols-3 gap-4 items-center">
                <div className="md:col-span-2">
                  <div className="flex items-center space-x-2 text-xs font-semibold uppercase tracking-wider text-emerald-700 mb-1">
                    <Sparkles className="h-3.5 w-3.5" />
                    <span>Skill Acceleration Target</span>
                  </div>
                  <h4 className="text-sm font-semibold text-slate-900 leading-snug">
                    {roadmapData?.roadmapSummary || `Bridge critical skill gaps to qualify for ${job?.title}.`}
                  </h4>
                  {/* Interactive Progress bar */}
                  <div className="mt-3">
                    <div className="flex items-center justify-between text-xs text-slate-600 mb-1">
                      <span>Curriculum Completion</span>
                      <span className="font-semibold text-emerald-700">{completedCount} of {allTasks.length} tasks ({progressPercent}%)</span>
                    </div>
                    <div className="w-full bg-slate-100 rounded-full h-2.5 overflow-hidden">
                      <div
                        className="bg-emerald-600 h-2.5 rounded-full transition-all duration-500 ease-out"
                        style={{ width: `${progressPercent}%` }}
                      />
                    </div>
                  </div>
                </div>

                <div className="bg-gradient-to-br from-emerald-50 to-teal-50 border border-emerald-200 rounded-xl p-3.5 text-center">
                  <div className="text-[11px] font-semibold text-emerald-800 uppercase tracking-wider">
                    Projected Match Score
                  </div>
                  <div className="flex items-center justify-center space-x-3 mt-1.5">
                    <span className="text-xl font-bold text-slate-500 line-through">
                      {score}%
                    </span>
                    <ArrowRight className="h-4 w-4 text-emerald-600" />
                    <span className="text-2xl font-black text-emerald-700">
                      {projectedScore}%
                    </span>
                  </div>
                  <div className="text-[10px] text-emerald-600 mt-1">
                    +{Math.max(0, projectedScore - score)}% match improvement
                  </div>
                </div>
              </div>

              {/* Week Navigation Pills */}
              <div className="flex border-b border-slate-200 overflow-x-auto space-x-2 pb-2">
                {(roadmapData?.milestones || []).map((milestone, idx) => {
                  const weekNum = milestone.week || idx + 1;
                  const isSelected = activeWeek === weekNum;
                  return (
                    <button
                      key={weekNum}
                      onClick={() => setActiveWeek(weekNum)}
                      className={`px-4 py-2.5 rounded-lg text-sm font-semibold transition-all whitespace-nowrap flex items-center space-x-2 ${
                        isSelected
                          ? 'bg-emerald-600 text-white shadow-sm'
                          : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
                      }`}
                    >
                      <span>Week {weekNum}</span>
                      <span className={`text-xs px-1.5 py-0.5 rounded-full ${
                        isSelected ? 'bg-white/20 text-white' : 'bg-slate-100 text-slate-600'
                      }`}>
                        {milestone.title?.split(' ')[0] || 'Phase'}
                      </span>
                    </button>
                  );
                })}
              </div>

              {/* Active Milestone Card */}
              {roadmapData?.milestones && roadmapData.milestones.length > 0 && (() => {
                const milestone = roadmapData.milestones.find(m => (m.week || 1) === activeWeek) || roadmapData.milestones[0];
                const weekNum = milestone.week || activeWeek;

                return (
                  <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-sm space-y-5">
                    <div>
                      <div className="flex items-center space-x-2 text-xs font-semibold text-emerald-700 uppercase tracking-wider mb-1">
                        <BookOpen className="h-4 w-4" />
                        <span>Week {weekNum} Milestone</span>
                      </div>
                      <h3 className="text-lg font-bold text-slate-900">{milestone.title}</h3>
                      <p className="text-sm text-slate-600 mt-1">{milestone.description}</p>
                    </div>

                    {/* Targeted Skills Tags */}
                    {milestone.skills && milestone.skills.length > 0 && (
                      <div>
                        <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">
                          Core Competencies
                        </div>
                        <div className="flex flex-wrap gap-2">
                          {milestone.skills.map((skill, sIdx) => (
                            <span
                              key={sIdx}
                              className="bg-emerald-50 text-emerald-800 border border-emerald-200 px-3 py-1 rounded-full text-xs font-medium"
                            >
                              {skill}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Interactive Actionable Tasks */}
                    <div>
                      <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">
                        Weekly Tasks (Check as you complete)
                      </div>
                      <div className="space-y-2">
                        {(milestone.tasks || []).map((task, tIdx) => {
                          const taskKey = `w${weekNum}_t${tIdx}`;
                          const isDone = !!completedTasks[taskKey];
                          return (
                            <div
                              key={tIdx}
                              onClick={() => toggleTask(taskKey)}
                              className={`flex items-start space-x-3 p-3 rounded-lg border cursor-pointer transition-colors ${
                                isDone
                                  ? 'bg-emerald-50/70 border-emerald-300 text-slate-500'
                                  : 'bg-slate-50 border-slate-200 hover:bg-slate-100 text-slate-800'
                              }`}
                            >
                              <div className="mt-0.5 flex-shrink-0">
                                {isDone ? (
                                  <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                                ) : (
                                  <Circle className="h-4 w-4 text-slate-400" />
                                )}
                              </div>
                              <span className={`text-sm ${isDone ? 'line-through text-slate-500' : 'font-medium'}`}>
                                {task}
                              </span>
                            </div>
                          );
                        })}
                      </div>
                    </div>

                    {/* Curated Resources */}
                    {milestone.resources && milestone.resources.length > 0 && (
                      <div className="pt-2">
                        <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">
                          Recommended Free Resources
                        </div>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                          {milestone.resources.map((res, rIdx) => (
                            <a
                              key={rIdx}
                              href={res.url}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="flex items-center justify-between p-3 rounded-lg border border-slate-200 hover:border-emerald-300 hover:bg-emerald-50/50 transition-all group"
                            >
                              <div className="flex items-center space-x-2.5 truncate">
                                <Code className="h-4 w-4 text-emerald-600 flex-shrink-0" />
                                <div className="truncate">
                                  <p className="text-xs font-semibold text-slate-800 group-hover:text-emerald-700 truncate">
                                    {res.name}
                                  </p>
                                  <span className="text-[10px] text-slate-500">{res.type || 'Resource'}</span>
                                </div>
                              </div>
                              <ExternalLink className="h-3.5 w-3.5 text-slate-400 group-hover:text-emerald-600 flex-shrink-0 ml-2" />
                            </a>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                );
              })()}

              {/* Capstone Project Showcase */}
              {roadmapData?.capstoneProject && (
                <div className="bg-gradient-to-br from-slate-900 to-indigo-950 text-white rounded-xl p-5 shadow-md">
                  <div className="flex items-center space-x-2 text-xs font-semibold text-yellow-300 uppercase tracking-wider mb-2">
                    <Trophy className="h-4 w-4" />
                    <span>Capstone Portfolio Project</span>
                  </div>
                  <h4 className="text-base font-bold text-white">
                    {roadmapData.capstoneProject.title}
                  </h4>
                  <p className="text-xs text-slate-300 mt-1 leading-relaxed">
                    {roadmapData.capstoneProject.description}
                  </p>

                  {roadmapData.capstoneProject.deliverables && (
                    <div className="mt-3 pt-3 border-t border-white/10 space-y-1.5">
                      <div className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                        Key Deliverables for Your Portfolio
                      </div>
                      <div className="grid grid-cols-1 md:grid-cols-3 gap-2 mt-1">
                        {roadmapData.capstoneProject.deliverables.map((del, dIdx) => (
                          <div key={dIdx} className="bg-white/10 rounded-lg p-2.5 text-xs text-slate-200 flex items-center space-x-2">
                            <Rocket className="h-3.5 w-3.5 text-yellow-300 flex-shrink-0" />
                            <span className="truncate">{del}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}
            </>
          )}
        </div>

        {/* Footer Actions */}
        <div className="bg-white px-6 py-4 border-t border-slate-200 flex items-center justify-between flex-shrink-0">
          <p className="text-xs text-slate-500">
            Progress is automatically saved to your browser session.
          </p>
          <div className="flex items-center space-x-3">
            {onStartPractice && (
              <button
                type="button"
                onClick={() => {
                  onClose();
                  onStartPractice();
                }}
                className="px-4 py-2 bg-purple-50 text-purple-700 hover:bg-purple-100 border border-purple-200 rounded-lg text-xs font-semibold transition-colors flex items-center space-x-1.5"
              >
                <Sparkles className="h-3.5 w-3.5" />
                <span>Practice Technical Mock (AI)</span>
              </button>
            )}
            <button
              type="button"
              onClick={onClose}
              className="px-5 py-2 bg-slate-900 text-white rounded-lg text-xs font-semibold hover:bg-slate-800 transition-colors"
            >
              Done
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default CareerRoadmapModal;
