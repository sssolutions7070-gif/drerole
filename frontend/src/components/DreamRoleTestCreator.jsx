// File: src/components/DreamRoleTestCreator.jsx
import React, { useState, useEffect } from 'react';
import {
  X, Plus, Trash2, BookOpen, Clock, Target, AlertCircle,
  Sparkles, Wand2, Check, RefreshCw, CheckCircle2, ChevronRight, Eye
} from 'lucide-react';
import api from '../services/api';

const DreamRoleTestCreator = ({ recruiterId, onClose, onSuccess }) => {
  const [step, setStep] = useState(1); // 1: Basic Info, 2: Add Technical Questions
  const [branches, setBranches] = useState([]);
  const [branchStats, setBranchStats] = useState({});
  const [loading, setLoading] = useState(true);
  const [testId, setTestId] = useState(null);

  // Recruiter Jobs & AI Generator state
  const [recruiterJobs, setRecruiterJobs] = useState([]);
  const [selectedJobId, setSelectedJobId] = useState('');
  const [selectedJob, setSelectedJob] = useState(null);
  const [isGeneratingAi, setIsGeneratingAi] = useState(false);
  const [aiGeneratedQuestions, setAiGeneratedQuestions] = useState([]);
  const [showAiPreviewModal, setShowAiPreviewModal] = useState(false);
  const [aiNotification, setAiNotification] = useState('');
  
  const [testInfo, setTestInfo] = useState({
    testName: '',
    description: '',
    durationMinutes: 60,
    passingScore: 60,
    targetBranch: ''
  });
  
  const [technicalQuestions, setTechnicalQuestions] = useState([]);
  const [currentQuestion, setCurrentQuestion] = useState({
    question: '',
    optionA: '',
    optionB: '',
    optionC: '',
    optionD: '',
    correctAnswer: 'A',
    explanation: '',
    difficultyLevel: 'Medium'
  });

  useEffect(() => {
    fetchBranchStats();
    if (recruiterId) {
      fetchRecruiterJobs();
    }
  }, [recruiterId]);

  const fetchBranchStats = async () => {
    try {
      setLoading(true);
      const response = await api.get('/dream-role-tests/branches/stats');
      setBranches(response.data.branches || []);
      setBranchStats(response.data.branchCounts || {});
    } catch (error) {
      console.error('Error fetching branch stats:', error);
    } finally {
      setLoading(false);
    }
  };

  const fetchRecruiterJobs = async () => {
    try {
      const res = await api.get(`/recruiters/jobs/recruiter/${recruiterId}`);
      setRecruiterJobs(res.data || []);
    } catch (err) {
      // Non-blocking: recruiter might have no active postings yet
    }
  };

  const handleSelectJob = (jobId) => {
    setSelectedJobId(jobId);
    if (!jobId) {
      setSelectedJob(null);
      return;
    }

    const job = recruiterJobs.find(j => String(j.id) === String(jobId));
    if (job) {
      setSelectedJob(job);
      // Auto-populate test name and description
      const updatedInfo = {
        ...testInfo,
        testName: testInfo.testName || `${job.title} Technical Assessment`,
        description: testInfo.description || job.description || `Assessment for ${job.title} role.`
      };

      // Auto-match best branch
      const jobText = `${job.title} ${job.skills || ''} ${job.requiredSkills || ''}`.toLowerCase();
      if (!testInfo.targetBranch && branches.length > 0) {
        if (jobText.includes('software') || jobText.includes('developer') || jobText.includes('full stack') || jobText.includes('frontend') || jobText.includes('backend') || jobText.includes('react') || jobText.includes('java') || jobText.includes('python')) {
          const cseBranch = branches.find(b => b.toLowerCase().includes('computer'));
          if (cseBranch) updatedInfo.targetBranch = cseBranch;
        } else if (jobText.includes('data') || jobText.includes('ai') || jobText.includes('machine learning')) {
          const aiBranch = branches.find(b => b.toLowerCase().includes('data') || b.toLowerCase().includes('artificial') || b.toLowerCase().includes('computer'));
          if (aiBranch) updatedInfo.targetBranch = aiBranch;
        } else if (branches.length > 0) {
          updatedInfo.targetBranch = branches[0];
        }
      }

      setTestInfo(updatedInfo);
    }
  };

  const handleGenerateAiQuestions = async () => {
    setIsGeneratingAi(true);
    setAiNotification('');
    try {
      const response = await api.post('/dream-role-tests/generate-questions', {
        jobTitle: testInfo.testName || selectedJob?.title || 'Software Engineer',
        jobDescription: testInfo.description || selectedJob?.description || '',
        skills: selectedJob?.skills || selectedJob?.requiredSkills || '',
        targetBranch: testInfo.targetBranch || '',
        count: 10
      });

      const generated = response.data?.questions || [];
      if (generated.length === 0) {
        alert('Could not generate questions. Please try again.');
        return;
      }

      setAiGeneratedQuestions(generated);
      setShowAiPreviewModal(true);
    } catch (err) {
      console.error('Failed to generate AI questions:', err);
      alert(err.response?.data?.error || 'Failed to generate technical questions with AI.');
    } finally {
      setIsGeneratingAi(false);
    }
  };

  const handleApplyAllAiQuestions = () => {
    const formatted = aiGeneratedQuestions.map((q, idx) => ({
      ...q,
      id: Date.now() + idx
    }));
    setTechnicalQuestions(formatted);
    setShowAiPreviewModal(false);
    setAiNotification('Successfully applied 10 AI-generated technical questions!');
  };

  const handleCreateTest = async () => {
    if (!testInfo.testName || !testInfo.targetBranch) {
      alert('Please fill all required fields');
      return;
    }

    try {
      const response = await api.post('/dream-role-tests/create', {
        recruiterId,
        ...testInfo
      });
      
      setTestId(response.data.test.id);
      setStep(2);
      alert('Test created successfully! Now add 10 technical questions.');
      
    } catch (error) {
      alert(error.response?.data?.error || 'Failed to create test');
    }
  };

  const handleAddQuestion = () => {
    if (!currentQuestion.question || !currentQuestion.optionA || !currentQuestion.optionB || 
        !currentQuestion.optionC || !currentQuestion.optionD || !currentQuestion.correctAnswer) {
      alert('Please fill all fields');
      return;
    }
    
    setTechnicalQuestions([...technicalQuestions, { ...currentQuestion, id: Date.now() }]);
    setCurrentQuestion({
      question: '',
      optionA: '',
      optionB: '',
      optionC: '',
      optionD: '',
      correctAnswer: 'A',
      explanation: '',
      difficultyLevel: 'Medium'
    });
  };

  const handleRemoveQuestion = (questionId) => {
    setTechnicalQuestions(technicalQuestions.filter(q => q.id !== questionId));
  };

  const handleSaveQuestions = async () => {
    if (technicalQuestions.length !== 10) {
      alert(`Please add exactly 10 technical questions. Current: ${technicalQuestions.length}`);
      return;
    }

    try {
      await api.post(`/dream-role-tests/${testId}/add-technical-questions`, technicalQuestions);
      alert('Technical questions added successfully!');
      if (onSuccess) onSuccess();
      if (onClose) onClose();
    } catch (error) {
      alert(error.response?.data?.error || 'Failed to save questions');
    }
  };

  if (loading) {
    return (
      <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50">
        <div className="relative top-20 mx-auto p-5 border w-full max-w-2xl shadow-lg rounded-md bg-white">
          <div className="text-center py-8">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600 mx-auto"></div>
            <p className="mt-2 text-gray-600">Loading branch statistics...</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50">
      <div className="relative top-10 mx-auto p-5 border w-full max-w-4xl shadow-lg rounded-md bg-white">
        <div className="flex justify-between items-center mb-6">
          <h2 className="text-2xl font-bold text-gray-900">
            {step === 1 ? 'Create DreamRole Test' : 'Add Technical Questions'}
          </h2>
          <button onClick={onClose} aria-label="Close" className="text-gray-400 hover:text-gray-600">
            <X className="h-6 w-6" />
          </button>
        </div>

        {/* Progress Steps */}
        <div className="flex justify-center mb-8">
          <div className="flex items-center">
            <div className={`flex items-center justify-center w-8 h-8 rounded-full ${
              step >= 1 ? 'bg-blue-600 text-white' : 'bg-gray-200 text-gray-500'
            }`}>1</div>
            <div className={`ml-2 text-sm font-medium ${step >= 1 ? 'text-blue-600' : 'text-gray-500'}`}>
              Basic Info
            </div>
          </div>
          <div className="flex items-center mx-4">
            <div className="w-12 h-0.5 bg-gray-300"></div>
          </div>
          <div className="flex items-center">
            <div className={`flex items-center justify-center w-8 h-8 rounded-full ${
              step >= 2 ? 'bg-blue-600 text-white' : 'bg-gray-200 text-gray-500'
            }`}>2</div>
            <div className={`ml-2 text-sm font-medium ${step >= 2 ? 'text-blue-600' : 'text-gray-500'}`}>
              Technical Questions (10)
            </div>
          </div>
        </div>

        {step === 1 && (
          <div className="space-y-6">
            {/* Auto-Fill from Posted Job */}
            {recruiterJobs.length > 0 && (
              <div className="bg-gradient-to-r from-blue-50 to-indigo-50 border border-blue-200 p-4 rounded-xl">
                <div className="flex items-center justify-between mb-2">
                  <label className="text-xs font-semibold text-blue-900 flex items-center gap-1.5 uppercase tracking-wider">
                    <Sparkles className="h-4 w-4 text-blue-600" />
                    Auto-Fill Test Details from Posted Job
                  </label>
                  <span className="text-[11px] text-blue-600 bg-white px-2 py-0.5 rounded border border-blue-200 font-medium">Optional</span>
                </div>
                <select
                  value={selectedJobId}
                  onChange={(e) => handleSelectJob(e.target.value)}
                  aria-label="Auto-fill from posted job"
                  className="w-full p-2.5 bg-white border border-blue-300 rounded-lg text-sm text-gray-800 focus:ring-2 focus:ring-blue-500"
                >
                  <option value="">-- Choose a job to auto-fill title, description & branch --</option>
                  {recruiterJobs.map(job => (
                    <option key={job.id} value={job.id}>
                      {job.title} ({job.location || 'Remote'})
                    </option>
                  ))}
                </select>
                {selectedJob && (
                  <p className="mt-2 text-xs text-blue-700 flex items-center gap-1">
                    <CheckCircle2 className="h-3.5 w-3.5 text-green-600 flex-shrink-0" />
                    Auto-populated from "{selectedJob.title}". You can adjust any fields below.
                  </p>
                )}
              </div>
            )}

            {/* Branch Selection with Stats */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Select Branch <span className="text-red-500">*</span>
              </label>
              <select
                value={testInfo.targetBranch}
                onChange={(e) => setTestInfo({...testInfo, targetBranch: e.target.value})}
                className="w-full p-2 border border-gray-300 rounded-lg"
              >
                <option value="">Choose a branch</option>
                {branches.map(branch => (
                  <option key={branch} value={branch}>
                    {branch} ({branchStats[branch] || 0} questions available)
                  </option>
                ))}
              </select>
              {testInfo.targetBranch && branchStats[testInfo.targetBranch] < 40 && (
                <p className="mt-2 text-sm text-red-600 flex items-center">
                  <AlertCircle className="h-4 w-4 mr-1" />
                  Only {branchStats[testInfo.targetBranch]} questions available. Need at least 40.
                </p>
              )}
            </div>

            {/* Test Name */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Test Name <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={testInfo.testName}
                onChange={(e) => setTestInfo({...testInfo, testName: e.target.value})}
                className="w-full p-2 border border-gray-300 rounded-lg"
                placeholder="e.g., Software Developer DreamRole Test"
              />
            </div>

            {/* Description */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Description
              </label>
              <textarea
                value={testInfo.description}
                onChange={(e) => setTestInfo({...testInfo, description: e.target.value})}
                className="w-full p-2 border border-gray-300 rounded-lg"
                rows="3"
                placeholder="Brief description of the test"
              />
            </div>

            {/* Duration and Passing Score */}
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Duration (minutes) <span className="text-red-500">*</span>
                </label>
                <input
                  type="number"
                  value={testInfo.durationMinutes}
                  onChange={(e) => setTestInfo({...testInfo, durationMinutes: parseInt(e.target.value)})}
                  className="w-full p-2 border border-gray-300 rounded-lg"
                  min="30"
                  max="180"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Passing Score (%) <span className="text-red-500">*</span>
                </label>
                <input
                  type="number"
                  value={testInfo.passingScore}
                  onChange={(e) => setTestInfo({...testInfo, passingScore: parseInt(e.target.value)})}
                  className="w-full p-2 border border-gray-300 rounded-lg"
                  min="30"
                  max="90"
                />
              </div>
            </div>

            {/* Test Summary */}
            <div className="bg-blue-50 p-4 rounded-lg">
              <h3 className="font-semibold text-blue-800 mb-2">Test Configuration:</h3>
              <ul className="space-y-1 text-sm text-blue-700">
                <li>• 40 Aptitude questions (auto-selected from bank)</li>
                <li>• 10 Technical questions (you'll add manually)</li>
                <li>• Total: 50 questions</li>
                <li>• Questions are shuffled for each candidate</li>
              </ul>
            </div>

            <div className="flex justify-end space-x-3">
              <button
                onClick={onClose}
                className="px-4 py-2 border border-gray-300 rounded-lg text-sm"
              >
                Cancel
              </button>
              <button
                onClick={handleCreateTest}
                disabled={!testInfo.testName || !testInfo.targetBranch || branchStats[testInfo.targetBranch] < 40}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm hover:bg-blue-700 disabled:opacity-50"
              >
                Create Test & Continue
              </button>
            </div>
          </div>
        )}

        {step === 2 && (
          <div className="space-y-6">
            {aiNotification && (
              <div className="p-3 bg-green-50 border border-green-200 rounded-lg text-sm text-green-800 flex items-center justify-between">
                <span className="flex items-center gap-1.5 font-medium">
                  <CheckCircle2 className="h-4 w-4 text-green-600 flex-shrink-0" />
                  {aiNotification}
                </span>
                <button onClick={() => setAiNotification('')} className="text-green-600 hover:text-green-800">
                  <X className="h-3.5 w-3.5" />
                </button>
              </div>
            )}

            {/* AI Generator Banner */}
            <div className="bg-gradient-to-r from-indigo-900 via-indigo-800 to-purple-900 text-white p-4 rounded-xl shadow-md border border-indigo-700/50 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div className="flex items-start gap-3">
                <div className="p-2 bg-indigo-700/80 rounded-lg text-yellow-300 flex-shrink-0">
                  <Sparkles className="h-5 w-5" />
                </div>
                <div>
                  <h4 className="font-bold text-sm text-white flex items-center gap-2">
                    Claude AI Assessment Generator
                    <span className="text-[10px] px-2 py-0.5 bg-yellow-400 text-gray-950 rounded font-bold uppercase tracking-wider">Instant</span>
                  </h4>
                  <p className="text-xs text-indigo-200 mt-0.5">
                    Generate all 10 multiple-choice technical questions tailored to {testInfo.testName || 'this role'} with answers & explanations.
                  </p>
                </div>
              </div>
              <button
                onClick={handleGenerateAiQuestions}
                disabled={isGeneratingAi}
                aria-label="Generate 10 questions with AI"
                className="flex items-center justify-center gap-2 px-4 py-2.5 bg-gradient-to-r from-amber-400 to-yellow-400 hover:from-amber-300 hover:to-yellow-300 text-gray-950 text-xs font-bold rounded-lg shadow transition disabled:opacity-50 whitespace-nowrap"
              >
                {isGeneratingAi ? (
                  <>
                    <RefreshCw className="h-4 w-4 animate-spin" />
                    <span>Generating with AI...</span>
                  </>
                ) : (
                  <>
                    <Wand2 className="h-4 w-4" />
                    <span>Generate 10 Questions with AI</span>
                  </>
                )}
              </button>
            </div>

            <div className="bg-yellow-50 p-4 rounded-lg flex items-center justify-between">
              <p className="text-sm text-yellow-800 font-medium">
                Add 10 technical questions. Added: {technicalQuestions.length}/10
              </p>
              {technicalQuestions.length > 0 && (
                <button
                  onClick={() => setTechnicalQuestions([])}
                  className="text-xs text-red-600 hover:text-red-800 font-medium"
                >
                  Clear all
                </button>
              )}
            </div>

            <div className="grid grid-cols-2 gap-6">
              {/* Question Form */}
              <div className="bg-gray-50 p-4 rounded-lg">
                <h3 className="font-medium mb-3">Add Technical Question</h3>
                
                <div className="space-y-3">
                  <div>
                    <label className="block text-xs font-medium mb-1">Question *</label>
                    <textarea
                      value={currentQuestion.question}
                      onChange={(e) => setCurrentQuestion({...currentQuestion, question: e.target.value})}
                      className="w-full p-2 border border-gray-300 rounded-lg text-sm"
                      rows="2"
                      placeholder="Enter technical question"
                    />
                  </div>
                  
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="block text-xs font-medium mb-1">Option A *</label>
                      <input
                        type="text"
                        value={currentQuestion.optionA}
                        onChange={(e) => setCurrentQuestion({...currentQuestion, optionA: e.target.value})}
                        className="w-full p-2 border border-gray-300 rounded-lg text-sm"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium mb-1">Option B *</label>
                      <input
                        type="text"
                        value={currentQuestion.optionB}
                        onChange={(e) => setCurrentQuestion({...currentQuestion, optionB: e.target.value})}
                        className="w-full p-2 border border-gray-300 rounded-lg text-sm"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium mb-1">Option C *</label>
                      <input
                        type="text"
                        value={currentQuestion.optionC}
                        onChange={(e) => setCurrentQuestion({...currentQuestion, optionC: e.target.value})}
                        className="w-full p-2 border border-gray-300 rounded-lg text-sm"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium mb-1">Option D *</label>
                      <input
                        type="text"
                        value={currentQuestion.optionD}
                        onChange={(e) => setCurrentQuestion({...currentQuestion, optionD: e.target.value})}
                        className="w-full p-2 border border-gray-300 rounded-lg text-sm"
                      />
                    </div>
                  </div>
                  
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="block text-xs font-medium mb-1">Correct Answer *</label>
                      <select
                        value={currentQuestion.correctAnswer}
                        onChange={(e) => setCurrentQuestion({...currentQuestion, correctAnswer: e.target.value})}
                        className="w-full p-2 border border-gray-300 rounded-lg text-sm"
                      >
                        <option value="A">A</option>
                        <option value="B">B</option>
                        <option value="C">C</option>
                        <option value="D">D</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium mb-1">Difficulty</label>
                      <select
                        value={currentQuestion.difficultyLevel}
                        onChange={(e) => setCurrentQuestion({...currentQuestion, difficultyLevel: e.target.value})}
                        className="w-full p-2 border border-gray-300 rounded-lg text-sm"
                      >
                        <option value="Easy">Easy</option>
                        <option value="Medium">Medium</option>
                        <option value="Hard">Hard</option>
                      </select>
                    </div>
                  </div>
                  
                  <div>
                    <label className="block text-xs font-medium mb-1">Explanation (Optional)</label>
                    <textarea
                      value={currentQuestion.explanation}
                      onChange={(e) => setCurrentQuestion({...currentQuestion, explanation: e.target.value})}
                      className="w-full p-2 border border-gray-300 rounded-lg text-sm"
                      rows="2"
                      placeholder="Explain the correct answer"
                    />
                  </div>
                  
                  <button
                    onClick={handleAddQuestion}
                    className="w-full bg-blue-600 text-white py-2 rounded-lg text-sm font-medium hover:bg-blue-700"
                  >
                    <Plus className="h-4 w-4 inline mr-1" />
                    Add Question
                  </button>
                </div>
              </div>

              {/* Questions List */}
              <div>
                <h3 className="font-medium mb-3">Added Questions ({technicalQuestions.length}/10)</h3>
                <div className="space-y-2 max-h-96 overflow-y-auto">
                  {technicalQuestions.map((q, index) => (
                    <div key={q.id} className="bg-white border border-gray-200 rounded-lg p-3 text-sm relative group">
                      <div className="flex justify-between items-start">
                        <span className="font-medium">Q{index + 1}: {q.question.substring(0, 50)}...</span>
                        <button
                          onClick={() => handleRemoveQuestion(q.id)}
                          className="text-red-500 hover:text-red-700"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </div>
                      <div className="flex items-center space-x-2 mt-1">
                        <span className={`px-2 py-0.5 rounded text-xs ${
                          q.difficultyLevel === 'Easy' ? 'bg-green-100 text-green-800' :
                          q.difficultyLevel === 'Medium' ? 'bg-yellow-100 text-yellow-800' :
                          'bg-red-100 text-red-800'
                        }`}>
                          {q.difficultyLevel}
                        </span>
                        <span className="text-xs text-gray-500">Correct: {q.correctAnswer}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            <div className="flex justify-end space-x-3 mt-6">
              <button
                onClick={onClose}
                className="px-4 py-2 border border-gray-300 rounded-lg text-sm"
              >
                Cancel
              </button>
              <button
                onClick={handleSaveQuestions}
                disabled={technicalQuestions.length !== 10}
                className="px-4 py-2 bg-green-600 text-white rounded-lg text-sm hover:bg-green-700 disabled:opacity-50"
              >
                Save & Complete Test
              </button>
            </div>
          </div>
        )}

        {/* AI Questions Review Modal */}
        {showAiPreviewModal && (
          <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-[60] p-4">
            <div className="bg-white rounded-xl shadow-2xl max-w-3xl w-full max-h-[85vh] flex flex-col border border-gray-200">
              <div className="flex items-center justify-between p-4 border-b border-gray-200 bg-gradient-to-r from-indigo-50 to-purple-50 rounded-t-xl">
                <div className="flex items-center gap-2">
                  <div className="p-1.5 bg-indigo-600 text-white rounded-lg">
                    <Sparkles className="h-4 w-4" />
                  </div>
                  <div>
                    <h3 className="font-bold text-gray-900 text-base">Generated Questions ({aiGeneratedQuestions.length})</h3>
                    <p className="text-xs text-gray-500">Tailored to "{testInfo.testName || 'Role'}"</p>
                  </div>
                </div>
                <button
                  onClick={() => setShowAiPreviewModal(false)}
                  aria-label="Close preview modal"
                  className="text-gray-400 hover:text-gray-600 p-1"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>

              <div className="flex-1 overflow-y-auto p-4 space-y-4 text-sm">
                {aiGeneratedQuestions.map((q, idx) => (
                  <div key={idx} className="p-3.5 bg-gray-50 border border-gray-200 rounded-lg space-y-2">
                    <div className="flex items-start justify-between gap-2">
                      <span className="font-semibold text-gray-900">
                        {idx + 1}. {q.question}
                      </span>
                      <span className={`text-[10px] px-2 py-0.5 rounded font-semibold uppercase ${
                        q.difficultyLevel === 'Hard' ? 'bg-red-100 text-red-700' :
                        q.difficultyLevel === 'Easy' ? 'bg-green-100 text-green-700' :
                        'bg-yellow-100 text-yellow-800'
                      }`}>
                        {q.difficultyLevel || 'Medium'}
                      </span>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
                      <div className={`p-2 rounded border ${q.correctAnswer === 'A' ? 'bg-green-50 border-green-300 text-green-900 font-medium' : 'bg-white border-gray-200 text-gray-700'}`}>
                        <span className="font-bold mr-1">A:</span> {q.optionA}
                      </div>
                      <div className={`p-2 rounded border ${q.correctAnswer === 'B' ? 'bg-green-50 border-green-300 text-green-900 font-medium' : 'bg-white border-gray-200 text-gray-700'}`}>
                        <span className="font-bold mr-1">B:</span> {q.optionB}
                      </div>
                      <div className={`p-2 rounded border ${q.correctAnswer === 'C' ? 'bg-green-50 border-green-300 text-green-900 font-medium' : 'bg-white border-gray-200 text-gray-700'}`}>
                        <span className="font-bold mr-1">C:</span> {q.optionC}
                      </div>
                      <div className={`p-2 rounded border ${q.correctAnswer === 'D' ? 'bg-green-50 border-green-300 text-green-900 font-medium' : 'bg-white border-gray-200 text-gray-700'}`}>
                        <span className="font-bold mr-1">D:</span> {q.optionD}
                      </div>
                    </div>

                    {q.explanation && (
                      <p className="text-xs text-gray-600 bg-white p-2 rounded border border-gray-100 italic">
                        <span className="font-medium not-italic text-gray-800">Explanation:</span> {q.explanation}
                      </p>
                    )}
                  </div>
                ))}
              </div>

              <div className="flex items-center justify-between p-4 border-t border-gray-200 bg-gray-50 rounded-b-xl">
                <button
                  onClick={handleGenerateAiQuestions}
                  disabled={isGeneratingAi}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs text-gray-700 hover:text-gray-900 font-medium"
                >
                  <RefreshCw className={`h-3.5 w-3.5 ${isGeneratingAi ? 'animate-spin' : ''}`} />
                  Regenerate
                </button>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setShowAiPreviewModal(false)}
                    className="px-4 py-2 border border-gray-300 rounded-lg text-xs font-medium text-gray-700 hover:bg-gray-100"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={handleApplyAllAiQuestions}
                    aria-label="Apply AI questions"
                    className="px-4 py-2 bg-gradient-to-r from-green-600 to-emerald-600 hover:from-green-500 hover:to-emerald-500 text-white rounded-lg text-xs font-bold shadow transition flex items-center gap-1.5"
                  >
                    <Check className="h-4 w-4" />
                    Apply All ({aiGeneratedQuestions.length}) to Test
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default DreamRoleTestCreator;