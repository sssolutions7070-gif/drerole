// src/components/ExternalJobManagement.jsx
import React, { useState, useEffect } from 'react';
import { 
  Plus, Upload, Users, Download, Loader, Mail, Phone, 
  Briefcase, MapPin, DollarSign, X, Eye, ChevronDown, ChevronUp,
  CheckCircle, XCircle, Search, GraduationCap, Code, FileText, Trash2, Key, UserCheck,
  BarChart3, AlertCircle
} from 'lucide-react';
import api from '../services/api';
import ExternalJobResults from './ExternalJobResults';

const ExternalJobManagement = ({ recruiterId }) => {
  const [jobs, setJobs] = useState([]);
  const [selectedJob, setSelectedJob] = useState(null);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [showSelectModal, setShowSelectModal] = useState(false);
  const [showCandidateDetails, setShowCandidateDetails] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [showResultsModal, setShowResultsModal] = useState(false);
  const [selectedJobForResults, setSelectedJobForResults] = useState(null);
  const [jobToDelete, setJobToDelete] = useState(null);
  const [candidateAnalysis, setCandidateAnalysis] = useState(null);
  const [shortlistedCandidates, setShortlistedCandidates] = useState([]);
  const [allCandidates, setAllCandidates] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [selecting, setSelecting] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingCandidates, setLoadingCandidates] = useState(false);
  const [selectedFiles, setSelectedFiles] = useState([]);
  const [numberOfCandidates, setNumberOfCandidates] = useState(10);
  const [downloadUrl, setDownloadUrl] = useState('');
  const [expandedCandidate, setExpandedCandidate] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [viewMode, setViewMode] = useState('pending');
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadStatus, setUploadStatus] = useState(null);
  const [uploadMessage, setUploadMessage] = useState('');
  const [currentChunk, setCurrentChunk] = useState(0);
  const [totalChunks, setTotalChunks] = useState(0);
  const [chunkResults, setChunkResults] = useState([]);
  
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
    requiredCandidates: 10
  });
  
  const jobTypes = ['Full-time', 'Part-time', 'Contract', 'Remote', 'Internship'];
  const experienceLevels = ['Entry', 'Junior', 'Mid-level', 'Senior', 'Lead'];
  
  // Chunk size - process 10 files at a time
  const CHUNK_SIZE = 10;
  
  useEffect(() => {
    if (recruiterId) {
      fetchExternalJobs();
    }
  }, [recruiterId]);
  
  const fetchExternalJobs = async () => {
    try {
      setLoading(true);
      const response = await api.get(`/external-jobs/recruiter/${recruiterId}`);
      setJobs(response.data || []);
    } catch (error) {
      console.error('Error fetching external jobs:', error);
      alert('Failed to load external jobs. Please refresh the page.');
    } finally {
      setLoading(false);
    }
  };
  
  const fetchCandidates = async (jobId) => {
    try {
      setLoadingCandidates(true);
      const response = await api.get(`/external-jobs/${jobId}/all-candidates`);
      setAllCandidates(response.data || []);
      
      const shortlistedResponse = await api.get(`/external-jobs/${jobId}/shortlisted-candidates`);
      setShortlistedCandidates(shortlistedResponse.data || []);
    } catch (error) {
      console.error('Error fetching candidates:', error);
    } finally {
      setLoadingCandidates(false);
    }
  };
  
  const fetchCandidateAnalysis = async (candidateId) => {
    try {
      const response = await api.get(`/external-jobs/candidates/${candidateId}/analysis`);
      setCandidateAnalysis(response.data);
      setShowCandidateDetails(true);
    } catch (error) {
      console.error('Error fetching candidate analysis:', error);
      alert('Failed to load candidate details');
    }
  };
  
  const handleCreateJob = async (e) => {
    e.preventDefault();
    
    if (!formData.title || !formData.company || !formData.description) {
      alert('Please fill all required fields: Title, Company, and Description');
      return;
    }
    
    let requiredSkillsArray = [];
    let preferredSkillsArray = [];
    
    try {
      if (formData.requiredSkills && formData.requiredSkills.trim()) {
        requiredSkillsArray = JSON.parse(formData.requiredSkills);
        if (!Array.isArray(requiredSkillsArray)) {
          requiredSkillsArray = [];
        }
      }
    } catch (error) {
      requiredSkillsArray = [];
    }
    
    try {
      if (formData.preferredSkills && formData.preferredSkills.trim()) {
        preferredSkillsArray = JSON.parse(formData.preferredSkills);
        if (!Array.isArray(preferredSkillsArray)) {
          preferredSkillsArray = [];
        }
      }
    } catch (error) {
      preferredSkillsArray = [];
    }
    
    const jobData = {
      title: formData.title,
      company: formData.company,
      description: formData.description,
      requiredSkills: JSON.stringify(requiredSkillsArray),
      preferredSkills: JSON.stringify(preferredSkillsArray),
      location: formData.location || '',
      salaryRange: formData.salaryRange || '',
      jobType: formData.jobType || '',
      experienceLevel: formData.experienceLevel || '',
      requiredCandidates: formData.requiredCandidates || 10
    };
    
    try {
      const response = await api.post(`/external-jobs/create?recruiterId=${recruiterId}`, jobData);
      if (response.data.job) {
        alert('External job created successfully!');
        setShowCreateForm(false);
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
          requiredCandidates: 10
        });
        fetchExternalJobs();
      }
    } catch (error) {
      console.error('Error creating job:', error);
      const errorMsg = error.response?.data?.error || error.message || 'Failed to create job';
      alert('Failed to create job: ' + errorMsg);
    }
  };
  
  // NEW: Upload files in chunks
  const uploadFilesInChunks = async (files, jobId) => {
    const chunks = [];
    for (let i = 0; i < files.length; i += CHUNK_SIZE) {
      chunks.push(files.slice(i, i + CHUNK_SIZE));
    }
    
    setTotalChunks(chunks.length);
    setCurrentChunk(0);
    setChunkResults([]);
    
    let totalSuccess = 0;
    let totalFail = 0;
    let totalSkip = 0;
    const allErrors = [];
    const allCandidates = [];
    
    for (let i = 0; i < chunks.length; i++) {
      setCurrentChunk(i + 1);
      setUploadProgress(((i) / chunks.length) * 100);
      setUploadMessage(`Uploading chunk ${i + 1} of ${chunks.length} (${chunks[i].length} files)...`);
      
      const formData = new FormData();
      chunks[i].forEach(file => {
        formData.append('files', file);
      });
      
      try {
        
        const response = await api.post(`/external-jobs/${jobId}/upload-resumes`, formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
          timeout: 300000 // 5 minutes per chunk
        });
        
        const successCount = response.data.successfulUploads || 0;
        const failCount = response.data.failedUploads || 0;
        const skipCount = response.data.skipCount || 0;
        
        totalSuccess += successCount;
        totalFail += failCount;
        totalSkip += skipCount;
        
        if (response.data.errors) {
          allErrors.push(...response.data.errors);
        }
        if (response.data.candidates) {
          allCandidates.push(...response.data.candidates);
        }
        
        setUploadProgress(((i + 1) / chunks.length) * 100);
        
      } catch (error) {
        console.error(`Error uploading chunk ${i + 1}:`, error);
        totalFail += chunks[i].length;
        allErrors.push(`Chunk ${i + 1} failed: ${error.message}`);
      }
    }
    
    return {
      successfulUploads: totalSuccess,
      failedUploads: totalFail,
      skipCount: totalSkip,
      errors: allErrors,
      candidates: allCandidates,
      totalFiles: files.length,
      chunksProcessed: chunks.length
    };
  };
  
  const handleFileUpload = async () => {
    if (selectedFiles.length === 0) {
      alert('Please select files to upload');
      return;
    }

    setUploading(true);
    setUploadProgress(0);
    setUploadStatus('uploading');
    setCurrentChunk(0);
    setTotalChunks(0);
    setUploadMessage(`Preparing to upload ${selectedFiles.length} files in chunks of ${CHUNK_SIZE}...`);
    
    try {
      
      const result = await uploadFilesInChunks(selectedFiles, selectedJob.id);
      
      
      const successCount = result.successfulUploads || 0;
      const failCount = result.failedUploads || 0;
      const skipCount = result.skipCount || 0;
      
      let message = `📊 UPLOAD COMPLETE!\n\n`;
      message += `✅ Successful: ${successCount}\n`;
      message += `⚠️ Already Applied (Skipped): ${skipCount}\n`;
      message += `❌ Failed: ${failCount}\n`;
      message += `📦 Processed in ${result.chunksProcessed} chunks\n\n`;
      
      if (successCount > 0) {
        message += `✨ ${successCount} candidates have been added successfully!\n`;
      }
      if (skipCount > 0) {
        message += `📌 ${skipCount} candidates already applied to this job.\n`;
      }
      if (failCount > 0) {
        message += `⚠️ ${failCount} files failed to process.\n`;
      }
      
      if (result.errors && result.errors.length > 0) {
        message += `\nFirst few errors:\n${result.errors.slice(0, 5).join('\n')}`;
      }
      
      alert(message);
      
      if (successCount > 0) {
        await fetchCandidates(selectedJob.id);
      }
      
      setUploadProgress(100);
      setUploadStatus('completed');
      
      // Close modal after 3 seconds
      setTimeout(() => {
        setShowUploadModal(false);
        setSelectedFiles([]);
        setUploadStatus(null);
        setUploadProgress(0);
      }, 3000);
      
    } catch (error) {
      console.error('Upload error:', error);
      
      let errorMessage = '❌ Failed to upload resumes: ';
      
      if (error.code === 'ECONNABORTED') {
        errorMessage += 'Request timed out. ';
      } else if (error.response?.data?.error) {
        errorMessage += error.response.data.error;
      } else if (error.message) {
        errorMessage += error.message;
      } else {
        errorMessage += 'Unknown error occurred.';
      }
      
      errorMessage += '\n\nTry with fewer files per batch or check the server logs.';
      
      alert(errorMessage);
      setUploadStatus('failed');
      setUploadMessage('Upload failed. Please try again.');
      setUploadProgress(0);
    } finally {
      setUploading(false);
      setTimeout(() => {
        setUploadStatus(null);
        setUploadMessage('');
        setCurrentChunk(0);
        setTotalChunks(0);
      }, 5000);
    }
  };
  
  const handleSelectCandidates = async () => {
    if (!numberOfCandidates || numberOfCandidates < 1) {
      alert('Please enter a valid number of candidates');
      return;
    }
    
    setSelecting(true);
    
    try {
      const response = await api.post(`/external-jobs/${selectedJob.id}/select-candidates`, {
        numberOfCandidates: numberOfCandidates
      });
      
      const shortlistedData = response.data.shortlistedCandidates || [];
      const newUsersCount = response.data.newUsersCount || 0;
      const existingUsersCount = response.data.existingUsersCount || 0;
      const downloadUrlFromResponse = response.data.downloadUrl || '';
      
      setShortlistedCandidates(shortlistedData);
      setDownloadUrl(downloadUrlFromResponse);
      
      alert(
        `✅ ${shortlistedData.length} candidates shortlisted successfully!\n\n` +
        `🆕 New Users: ${newUsersCount}\n` +
        `👤 Existing Users: ${existingUsersCount}\n\n` +
        `📧 Existing users can login with their DreamRole credentials\n` +
        `🔑 New users have generated passwords in the CSV file`
      );
      
      setShowSelectModal(false);
      await fetchCandidates(selectedJob.id);
      
    } catch (error) {
      console.error('Selection error:', error);
      alert('Failed to select candidates: ' + (error.response?.data?.error || error.message));
    } finally {
      setSelecting(false);
    }
  };
  
  const handleDeleteJob = async () => {
    if (!jobToDelete) return;
    
    setDeleting(true);
    try {
      const response = await api.delete(`/external-jobs/${jobToDelete.id}?recruiterId=${recruiterId}`);
      if (response.data.message) {
        alert(`Job "${jobToDelete.title}" deleted successfully!`);
        setShowDeleteConfirm(false);
        setJobToDelete(null);
        if (selectedJob?.id === jobToDelete.id) {
          setSelectedJob(null);
          setAllCandidates([]);
          setShortlistedCandidates([]);
        }
        fetchExternalJobs();
      }
    } catch (error) {
      console.error('Error deleting job:', error);
      alert('Failed to delete job: ' + (error.response?.data?.error || error.message));
    } finally {
      setDeleting(false);
    }
  };
  
  const viewJobDetails = async (job) => {
    setSelectedJob(job);
    await fetchCandidates(job.id);
  };
  
  const viewJobResults = (job) => {
    setSelectedJobForResults(job);
    setShowResultsModal(true);
  };
  
  const downloadCredentials = async () => {
    if (!downloadUrl) return;
    try {
      const response = await api.get(downloadUrl, { responseType: 'blob' });
      const blobUrl = window.URL.createObjectURL(response.data);
      const link = document.createElement('a');
      link.href = blobUrl;
      link.download = downloadUrl.split('/').pop();
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(blobUrl);
    } catch (error) {
      alert('Failed to download credentials file. Please try again.');
    }
  };
  
  const getMatchColor = (percentage) => {
    if (percentage >= 80) return 'bg-green-100 text-green-800';
    if (percentage >= 60) return 'bg-yellow-100 text-yellow-800';
    if (percentage >= 40) return 'bg-orange-100 text-orange-800';
    return 'bg-red-100 text-red-800';
  };
  
  const getMatchBarColor = (percentage) => {
    if (percentage >= 80) return 'bg-green-500';
    if (percentage >= 60) return 'bg-yellow-500';
    if (percentage >= 40) return 'bg-orange-500';
    return 'bg-red-500';
  };
  
  const getLoginStatusBadge = (candidate) => {
    if (candidate.isExistingUser || candidate.passwordStatus === 'EXISTING_USER') {
      return (
        <span className="bg-blue-100 text-blue-800 px-2 py-1 rounded-full text-xs font-medium flex items-center">
          <UserCheck className="h-3 w-3 mr-1" />
          Existing User
        </span>
      );
    } else {
      return (
        <span className="bg-green-100 text-green-800 px-2 py-1 rounded-full text-xs font-medium flex items-center">
          <Key className="h-3 w-3 mr-1" />
          New User
        </span>
      );
    }
  };
  
  const getSkillsArray = (skills) => {
    if (!skills) return [];
    if (Array.isArray(skills)) return skills;
    try {
      const parsed = JSON.parse(skills);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  };
  
  const getFilteredCandidates = () => {
    let candidates = [];
    if (viewMode === 'pending') {
      candidates = allCandidates.filter(c => c.status === 'PENDING');
    } else if (viewMode === 'shortlisted') {
      candidates = shortlistedCandidates;
    } else {
      candidates = allCandidates;
    }
    
    return candidates.filter(candidate => {
      return (candidate.fullName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
             candidate.email?.toLowerCase().includes(searchTerm.toLowerCase()));
    });
  };
  
  const CandidateCard = ({ candidate }) => {
    const isExpanded = expandedCandidate === candidate.id;
    const candidateSkills = getSkillsArray(candidate.extractedSkills);
    const matchedSkillsList = getSkillsArray(candidate.matchedSkills);
    const missingSkillsList = getSkillsArray(candidate.missingSkills);
    const matchPercentage = candidate.matchPercentage || 0;
    const isExistingUser = candidate.isExistingUser || candidate.passwordStatus === 'EXISTING_USER';
    
    return (
      <div className="border border-gray-200 rounded-lg p-4 hover:shadow-md transition-shadow">
        <div className="flex justify-between items-start">
          <div className="flex-1">
            <div className="flex items-center space-x-3 flex-wrap gap-2">
              <h4 className="font-semibold text-gray-800">{candidate.fullName}</h4>
              <span className={`px-2 py-1 rounded-full text-xs font-medium ${getMatchColor(matchPercentage)}`}>
                {Math.round(matchPercentage)}% Match
              </span>
              {getLoginStatusBadge(candidate)}
              {candidate.status === 'SHORTLISTED' && (
                <span className="bg-green-100 text-green-800 px-2 py-1 rounded-full text-xs font-medium flex items-center">
                  <CheckCircle className="h-3 w-3 mr-1" />
                  Shortlisted
                </span>
              )}
            </div>
            <div className="flex items-center space-x-3 mt-1 text-sm text-gray-500 flex-wrap gap-2">
              <span className="flex items-center"><Mail className="h-3 w-3 mr-1" />{candidate.email}</span>
              {candidate.phone && <span className="flex items-center"><Phone className="h-3 w-3 mr-1" />{candidate.phone}</span>}
            </div>
            <div className="flex flex-wrap gap-3 mt-2 text-sm">
              <span className="flex items-center text-gray-600"><Briefcase className="h-3 w-3 mr-1" />{candidate.experienceYears || 0} years exp</span>
              <span className="flex items-center text-gray-600"><GraduationCap className="h-3 w-3 mr-1" />{candidate.educationLevel || 'Not specified'}</span>
            </div>
            
            <div className="mt-3">
              <div className="flex justify-between text-xs text-gray-500 mb-1"><span>Match Score</span><span className="font-medium">{Math.round(matchPercentage)}%</span></div>
              <div className="w-full bg-gray-200 rounded-full h-2"><div className={`h-2 rounded-full ${getMatchBarColor(matchPercentage)} transition-all duration-500`} style={{ width: `${Math.min(matchPercentage, 100)}%` }} /></div>
            </div>
            
            {candidateSkills.length > 0 && (
              <div className="mt-3"><div className="flex flex-wrap gap-1">{candidateSkills.slice(0, 5).map((skill, idx) => (<span key={idx} className="bg-blue-100 text-blue-800 px-2 py-0.5 rounded text-xs">{skill}</span>))}{candidateSkills.length > 5 && <span className="text-gray-500 text-xs">+{candidateSkills.length - 5} more</span>}</div></div>
            )}
            
            <button onClick={() => setExpandedCandidate(isExpanded ? null : candidate.id)} className="mt-2 text-blue-600 hover:text-blue-800 text-sm flex items-center">
              {isExpanded ? <ChevronUp className="h-4 w-4 mr-1" /> : <ChevronDown className="h-4 w-4 mr-1" />}
              {isExpanded ? 'Show Less' : 'View Full Analysis'}
            </button>
            
            {isExpanded && (
              <div className="mt-4 pt-3 border-t border-gray-200 space-y-3">
                {candidateSkills.length > 0 && (<div><h5 className="text-sm font-medium text-gray-700 mb-2 flex items-center"><Code className="h-4 w-4 mr-1 text-blue-600" />All Extracted Skills ({candidateSkills.length})</h5><div className="flex flex-wrap gap-1">{candidateSkills.map((skill, idx) => (<span key={idx} className="bg-gray-100 text-gray-700 px-2 py-0.5 rounded text-xs">{skill}</span>))}</div></div>)}
                {matchedSkillsList.length > 0 && (<div className="p-2 bg-green-50 rounded"><h5 className="text-sm font-medium text-green-700 mb-2 flex items-center"><CheckCircle className="h-4 w-4 mr-1" />Matched Skills ({matchedSkillsList.length})</h5><div className="flex flex-wrap gap-1">{matchedSkillsList.map((skill, idx) => (<span key={idx} className="bg-green-100 text-green-800 px-2 py-0.5 rounded text-xs">{skill}</span>))}</div></div>)}
                {missingSkillsList.length > 0 && (<div className="p-2 bg-red-50 rounded"><h5 className="text-sm font-medium text-red-700 mb-2 flex items-center"><XCircle className="h-4 w-4 mr-1" />Missing Skills ({missingSkillsList.length})</h5><div className="flex flex-wrap gap-1">{missingSkillsList.map((skill, idx) => (<span key={idx} className="bg-red-100 text-red-800 px-2 py-0.5 rounded text-xs">{skill}</span>))}</div></div>)}
              </div>
            )}
          </div>
          
          <button onClick={() => fetchCandidateAnalysis(candidate.id)} className="px-3 py-1 bg-blue-600 text-white rounded text-sm hover:bg-blue-700 flex items-center ml-2"><Eye className="h-3 w-3 mr-1" />Details</button>
        </div>
      </div>
    );
  };
  
  const CandidateDetailsModal = () => {
    if (!candidateAnalysis) return null;
    const skills = getSkillsArray(candidateAnalysis.extractedSkills);
    const matchedSkills = getSkillsArray(candidateAnalysis.matchedSkills);
    const missingSkills = getSkillsArray(candidateAnalysis.missingSkills);
    const isExistingUser = candidateAnalysis.hasAccount || candidateAnalysis.isExistingUser;
    
    return (
      <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50">
        <div className="relative top-10 mx-auto p-5 border w-full max-w-2xl shadow-lg rounded-md bg-white max-h-[90vh] overflow-y-auto">
          <div className="flex justify-between items-center mb-6"><h2 className="text-2xl font-bold text-gray-900">Candidate Details</h2><button onClick={() => setShowCandidateDetails(false)} className="text-gray-400 hover:text-gray-600"><X className="h-6 w-6" /></button></div>
          <div className="bg-gray-50 rounded-lg p-4 mb-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div><p className="text-sm text-gray-500">Full Name</p><p className="font-semibold text-lg">{candidateAnalysis.fullName}</p></div>
              <div><p className="text-sm text-gray-500">Email</p><p className="font-semibold">{candidateAnalysis.email}</p></div>
              <div><p className="text-sm text-gray-500">Phone</p><p className="font-semibold">{candidateAnalysis.phone || 'Not provided'}</p></div>
              <div><p className="text-sm text-gray-500">Experience</p><p className="font-semibold">{candidateAnalysis.experienceYears || 0} years</p></div>
              <div><p className="text-sm text-gray-500">Education</p><p className="font-semibold">{candidateAnalysis.educationLevel || 'Not specified'}</p></div>
              <div><p className="text-sm text-gray-500">Match Score</p><p className="font-semibold text-2xl text-blue-600">{Math.round(candidateAnalysis.matchPercentage || 0)}%</p></div>
            </div>
            {isExistingUser && (
              <div className="mt-3 p-2 bg-blue-50 rounded">
                <p className="text-sm text-blue-700"><UserCheck className="h-4 w-4 inline mr-1" /> This user already has a DreamRole account</p>
              </div>
            )}
            {!isExistingUser && candidateAnalysis.passwordStatus === 'NEW_USER' && (
              <div className="mt-3 p-2 bg-green-50 rounded">
                <p className="text-sm text-green-700"><Key className="h-4 w-4 inline mr-1" /> New user - password generated in CSV</p>
              </div>
            )}
          </div>
          <div className="mb-6"><div className="flex justify-between text-sm text-gray-600 mb-1"><span>Match Score</span><span className="font-bold">{Math.round(candidateAnalysis.matchPercentage || 0)}%</span></div><div className="w-full bg-gray-200 rounded-full h-3"><div className={`h-3 rounded-full ${getMatchBarColor(candidateAnalysis.matchPercentage || 0)}`} style={{ width: `${Math.min(candidateAnalysis.matchPercentage || 0, 100)}%` }} /></div></div>
          {skills.length > 0 && (<div className="mb-6"><h3 className="font-semibold text-gray-800 mb-3 flex items-center"><Code className="h-5 w-5 mr-2 text-blue-600" />Extracted Skills ({skills.length})</h3><div className="flex flex-wrap gap-2">{skills.map((skill, idx) => (<span key={idx} className="bg-blue-100 text-blue-800 px-3 py-1 rounded-full text-sm">{skill}</span>))}</div></div>)}
          {matchedSkills.length > 0 && (<div className="mb-6 p-4 bg-green-50 rounded-lg border border-green-200"><h3 className="font-semibold text-green-800 mb-2 flex items-center"><CheckCircle className="h-5 w-5 mr-2" />Matched Skills ({matchedSkills.length})</h3><div className="flex flex-wrap gap-1">{matchedSkills.map((skill, idx) => (<span key={idx} className="bg-green-200 text-green-800 px-2 py-0.5 rounded text-xs">{skill}</span>))}</div></div>)}
          {missingSkills.length > 0 && (<div className="mb-6 p-4 bg-red-50 rounded-lg border border-red-200"><h3 className="font-semibold text-red-800 mb-2 flex items-center"><XCircle className="h-5 w-5 mr-2" />Missing Skills ({missingSkills.length})</h3><div className="flex flex-wrap gap-1">{missingSkills.map((skill, idx) => (<span key={idx} className="bg-red-200 text-red-800 px-2 py-0.5 rounded text-xs">{skill}</span>))}</div></div>)}
          <div className="mt-4 p-3 bg-gray-100 rounded-lg"><p className="text-sm text-gray-600"><span className="font-medium">Status:</span> {candidateAnalysis.status}</p></div>
          <div className="flex justify-end mt-6"><button onClick={() => setShowCandidateDetails(false)} className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">Close</button></div>
        </div>
      </div>
    );
  };
  
  const DeleteConfirmModal = () => {
    if (!jobToDelete) return null;
    
    return (
      <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50">
        <div className="relative top-20 mx-auto p-5 border w-full max-w-md shadow-lg rounded-md bg-white">
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-xl font-bold text-gray-900">Confirm Delete</h2>
            <button onClick={() => { setShowDeleteConfirm(false); setJobToDelete(null); }} className="text-gray-400 hover:text-gray-600"><X className="h-5 w-5" /></button>
          </div>
          <div className="mb-6">
            <p className="text-gray-700 mb-2">Are you sure you want to delete the job posting:</p>
            <p className="font-semibold text-red-600 text-lg">{jobToDelete.title}</p>
            <p className="text-gray-600 mt-2">Company: {jobToDelete.company}</p>
            <div className="mt-4 p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
              <p className="text-sm text-yellow-800">⚠️ This will also delete:</p>
              <ul className="text-sm text-yellow-700 list-disc list-inside mt-1">
                <li>All uploaded resumes for this job</li>
                <li>All candidate records</li>
                <li>Generated CSV files</li>
              </ul>
              <p className="text-sm text-red-600 font-medium mt-2">This action cannot be undone!</p>
            </div>
          </div>
          <div className="flex justify-end space-x-3">
            <button onClick={() => { setShowDeleteConfirm(false); setJobToDelete(null); }} className="px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50">Cancel</button>
            <button onClick={handleDeleteJob} disabled={deleting} className="px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50 flex items-center">
              {deleting ? <Loader className="h-4 w-4 animate-spin mr-2" /> : <Trash2 className="h-4 w-4 mr-2" />}
              {deleting ? 'Deleting...' : 'Delete Permanently'}
            </button>
          </div>
        </div>
      </div>
    );
  };
  
  if (loading) {
    return <div className="text-center py-8"><Loader className="h-8 w-8 animate-spin text-blue-600 mx-auto" /><p className="mt-2 text-gray-600">Loading external jobs...</p></div>;
  }
  
  const filteredCandidates = getFilteredCandidates();
  
  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div><h2 className="text-2xl font-bold text-gray-800">External Job Applications</h2><p className="text-gray-600">Post jobs for external candidates and bulk upload resumes</p></div>
        <button onClick={() => setShowCreateForm(true)} className="bg-blue-600 text-white px-4 py-2 rounded-lg font-semibold hover:bg-blue-700 flex items-center"><Plus className="h-4 w-4 mr-2" />Create External Job</button>
      </div>
      
      {jobs.length === 0 ? (
        <div className="bg-white rounded-lg shadow p-8 text-center"><Briefcase className="h-12 w-12 text-gray-400 mx-auto mb-4" /><h3 className="text-lg font-medium text-gray-800 mb-2">No External Jobs Created</h3><p className="text-gray-500 mb-4">Create your first external job posting to start receiving applications</p><button onClick={() => setShowCreateForm(true)} className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700">Create External Job</button></div>
      ) : (
        <div className="grid gap-4">
          {jobs.map(job => (
            <div key={job.id} className={`bg-white rounded-lg shadow border p-6 cursor-pointer transition-all ${selectedJob?.id === job.id ? 'border-blue-500 shadow-lg' : 'border-gray-200 hover:shadow-md'}`} onClick={() => viewJobDetails(job)}>
              <div className="flex justify-between items-start">
                <div>
                  <h3 className="text-xl font-semibold text-gray-800">{job.title}</h3>
                  <p className="text-gray-600">{job.company}</p>
                  <div className="flex flex-wrap gap-3 mt-2 text-sm text-gray-500">
                    {job.location && <span className="flex items-center"><MapPin className="h-3 w-3 mr-1" />{job.location}</span>}
                    {job.jobType && <span className="flex items-center"><Briefcase className="h-3 w-3 mr-1" />{job.jobType}</span>}
                    {job.salaryRange && <span className="flex items-center"><DollarSign className="h-3 w-3 mr-1" />{job.salaryRange}</span>}
                    <span className="flex items-center"><Users className="h-3 w-3 mr-1" />Need: {job.requiredCandidates} candidates</span>
                  </div>
                </div>
                <div className="flex space-x-2">
                  <button onClick={(e) => { e.stopPropagation(); setSelectedJob(job); setShowUploadModal(true); }} className="px-3 py-1 bg-green-600 text-white rounded text-sm hover:bg-green-700 flex items-center"><Upload className="h-3 w-3 mr-1" /> Upload Resumes</button>
                  <button onClick={(e) => { e.stopPropagation(); setSelectedJob(job); setShowSelectModal(true); }} className="px-3 py-1 bg-purple-600 text-white rounded text-sm hover:bg-purple-700 flex items-center"><Users className="h-3 w-3 mr-1" /> Select Top</button>
                  <button onClick={(e) => { e.stopPropagation(); viewJobResults(job); }} className="px-3 py-1 bg-indigo-600 text-white rounded text-sm hover:bg-indigo-700 flex items-center"><BarChart3 className="h-3 w-3 mr-1" /> View Results</button>
                  <button onClick={(e) => { e.stopPropagation(); setJobToDelete(job); setShowDeleteConfirm(true); }} className="px-3 py-1 bg-red-600 text-white rounded text-sm hover:bg-red-700 flex items-center"><Trash2 className="h-3 w-3 mr-1" /> Delete</button>
                  {shortlistedCandidates.length > 0 && selectedJob?.id === job.id && downloadUrl && (
                    <button onClick={(e) => { e.stopPropagation(); downloadCredentials(); }} className="px-3 py-1 bg-indigo-600 text-white rounded text-sm hover:bg-indigo-700 flex items-center"><FileText className="h-3 w-3 mr-1" /> Download CSV</button>
                  )}
                </div>
              </div>
              
              {selectedJob?.id === job.id && (
                <div className="mt-4 pt-4 border-t border-gray-200">
                  <div className="flex space-x-4 mb-4">
                    <button onClick={() => setViewMode('pending')} className={`px-3 py-1 rounded-full text-sm font-medium ${viewMode === 'pending' ? 'bg-blue-600 text-white' : 'bg-gray-200 text-gray-700 hover:bg-gray-300'}`}>Pending ({allCandidates.filter(c => c.status === 'PENDING').length})</button>
                    <button onClick={() => setViewMode('shortlisted')} className={`px-3 py-1 rounded-full text-sm font-medium ${viewMode === 'shortlisted' ? 'bg-green-600 text-white' : 'bg-gray-200 text-gray-700 hover:bg-gray-300'}`}>Shortlisted ({shortlistedCandidates.length})</button>
                    <button onClick={() => setViewMode('all')} className={`px-3 py-1 rounded-full text-sm font-medium ${viewMode === 'all' ? 'bg-gray-600 text-white' : 'bg-gray-200 text-gray-700 hover:bg-gray-300'}`}>All ({allCandidates.length})</button>
                  </div>
                  
                  <div className="mb-4 relative"><Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-gray-400" /><input type="text" placeholder="Search by name or email..." value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} className="w-full pl-9 pr-3 py-2 border border-gray-300 rounded-lg text-sm" /></div>
                  
                  {loadingCandidates ? (
                    <div className="text-center py-8"><Loader className="h-6 w-6 animate-spin text-blue-600 mx-auto" /><p className="mt-2 text-gray-500">Loading candidates...</p></div>
                  ) : filteredCandidates.length === 0 ? (
                    <div className="text-center py-8"><Users className="h-12 w-12 text-gray-400 mx-auto mb-2" /><p className="text-gray-500">No candidates found</p>{viewMode === 'pending' && <button onClick={() => setShowUploadModal(true)} className="mt-2 text-blue-600 hover:text-blue-800">Upload resumes to get started</button>}</div>
                  ) : (
                    <div className="space-y-3 max-h-96 overflow-y-auto">{filteredCandidates.map(candidate => <CandidateCard key={candidate.id} candidate={candidate} />)}</div>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
      
      {/* Create Job Modal */}
      {showCreateForm && (
        <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50">
          <div className="relative top-20 mx-auto p-5 border w-full max-w-2xl shadow-lg rounded-md bg-white">
            <div className="flex justify-between items-center mb-6"><h2 className="text-2xl font-bold text-gray-900">Create External Job Posting</h2><button onClick={() => setShowCreateForm(false)} className="text-gray-400 hover:text-gray-600"><X className="h-6 w-6" /></button></div>
            <form onSubmit={handleCreateJob} className="space-y-4">
              <div className="grid grid-cols-2 gap-4"><input type="text" placeholder="Job Title *" value={formData.title} onChange={(e) => setFormData({...formData, title: e.target.value})} className="border border-gray-300 rounded-lg px-3 py-2" required /><input type="text" placeholder="Company *" value={formData.company} onChange={(e) => setFormData({...formData, company: e.target.value})} className="border border-gray-300 rounded-lg px-3 py-2" required /></div>
              <textarea placeholder="Job Description *" value={formData.description} onChange={(e) => setFormData({...formData, description: e.target.value})} rows={4} className="border border-gray-300 rounded-lg px-3 py-2 w-full" required />
              <div className="grid grid-cols-2 gap-4">
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Required Skills (JSON array)</label><textarea placeholder='["JavaScript", "React", "Node.js"]' value={formData.requiredSkills} onChange={(e) => setFormData({...formData, requiredSkills: e.target.value})} rows={3} className="border border-gray-300 rounded-lg px-3 py-2 font-mono text-sm w-full" /><p className="text-xs text-gray-500 mt-1">Enter as JSON array, e.g., ["skill1", "skill2"]</p></div>
                <div><label className="block text-sm font-medium text-gray-700 mb-1">Preferred Skills (JSON array)</label><textarea placeholder='["TypeScript", "AWS", "Docker"]' value={formData.preferredSkills} onChange={(e) => setFormData({...formData, preferredSkills: e.target.value})} rows={3} className="border border-gray-300 rounded-lg px-3 py-2 font-mono text-sm w-full" /><p className="text-xs text-gray-500 mt-1">Optional - enter as JSON array</p></div>
              </div>
              <div className="grid grid-cols-3 gap-4"><input type="text" placeholder="Location" value={formData.location} onChange={(e) => setFormData({...formData, location: e.target.value})} className="border border-gray-300 rounded-lg px-3 py-2" /><input type="text" placeholder="Salary Range" value={formData.salaryRange} onChange={(e) => setFormData({...formData, salaryRange: e.target.value})} className="border border-gray-300 rounded-lg px-3 py-2" /><select value={formData.jobType} onChange={(e) => setFormData({...formData, jobType: e.target.value})} className="border border-gray-300 rounded-lg px-3 py-2"><option value="">Job Type</option>{jobTypes.map(type => <option key={type} value={type}>{type}</option>)}</select></div>
              <div className="grid grid-cols-2 gap-4"><select value={formData.experienceLevel} onChange={(e) => setFormData({...formData, experienceLevel: e.target.value})} className="border border-gray-300 rounded-lg px-3 py-2"><option value="">Experience Level</option>{experienceLevels.map(level => <option key={level} value={level}>{level}</option>)}</select><input type="number" placeholder="Candidates Required *" value={formData.requiredCandidates} onChange={(e) => setFormData({...formData, requiredCandidates: parseInt(e.target.value)})} className="border border-gray-300 rounded-lg px-3 py-2" min="1" required /></div>
              <div className="flex justify-end space-x-4 pt-4"><button type="button" onClick={() => setShowCreateForm(false)} className="px-4 py-2 border border-gray-300 rounded-lg">Cancel</button><button type="submit" className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">Create Job</button></div>
            </form>
          </div>
        </div>
      )}
      
      {/* Upload Resumes Modal */}
      {showUploadModal && selectedJob && (
        <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50">
          <div className="relative top-20 mx-auto p-5 border w-full max-w-2xl shadow-lg rounded-md bg-white">
            <div className="flex justify-between items-center mb-6">
              <div>
                <h2 className="text-2xl font-bold text-gray-900">Upload Resumes</h2>
                <p className="text-gray-600">For: {selectedJob.title}</p>
                <p className="text-sm text-blue-600 mt-1">💡 Files will be uploaded in chunks of {CHUNK_SIZE} for better performance.</p>
                <p className="text-xs text-green-600 mt-1">✅ Supports unlimited files! {CHUNK_SIZE} files per chunk.</p>
              </div>
              <button onClick={() => { setShowUploadModal(false); setSelectedFiles([]); setUploadStatus(null); setUploadProgress(0); }} className="text-gray-400 hover:text-gray-600">
                <X className="h-6 w-6" />
              </button>
            </div>
            
            <div className="border-2 border-dashed border-gray-300 rounded-lg p-6 text-center">
              <input type="file" id="resume-upload" multiple accept=".pdf,.doc,.docx,.txt" onChange={(e) => setSelectedFiles(Array.from(e.target.files))} className="hidden" />
              <label htmlFor="resume-upload" className="cursor-pointer">
                <Upload className="h-12 w-12 text-gray-400 mx-auto mb-3" />
                <p className="text-lg font-semibold text-gray-700">Click to upload resumes</p>
                <p className="text-sm text-gray-500">PDF, DOC, DOCX, TXT files (Max 50MB each)</p>
                <p className="text-xs text-gray-400 mt-1">Upload unlimited resumes - processed in chunks of {CHUNK_SIZE}</p>
              </label>
            </div>
            
            {selectedFiles.length > 0 && (
              <div className="mt-4 bg-gray-50 rounded-lg p-4 max-h-40 overflow-y-auto">
                <p className="font-medium mb-2">Selected files ({selectedFiles.length}):</p>
                <div className="grid grid-cols-2 gap-1">
                  {selectedFiles.slice(0, 10).map((file, idx) => (
                    <div key={idx} className="text-sm text-gray-600 truncate">{file.name}</div>
                  ))}
                  {selectedFiles.length > 10 && (
                    <div className="text-sm text-gray-500">... and {selectedFiles.length - 10} more files</div>
                  )}
                </div>
                <p className="text-xs text-gray-500 mt-2">Will be uploaded in {Math.ceil(selectedFiles.length / CHUNK_SIZE)} chunks</p>
              </div>
            )}
            
            {uploading && (
              <div className="text-center py-4">
                <Loader className="h-8 w-8 animate-spin text-blue-600 mx-auto" />
                <p className="text-gray-600 mt-2">{uploadMessage || 'Processing resumes...'}</p>
                {totalChunks > 0 && (
                  <p className="text-sm text-gray-500">Chunk {currentChunk} of {totalChunks}</p>
                )}
                <p className="text-sm text-gray-500">Please don't close this window. Processing in progress...</p>
                <div className="mt-3">
                  <div className="w-full bg-gray-200 rounded-full h-2">
                    <div className="bg-blue-600 h-2 rounded-full transition-all duration-300" style={{ width: `${uploadProgress}%` }}></div>
                  </div>
                  <p className="text-sm text-gray-500 mt-1">{Math.round(uploadProgress)}% completed</p>
                </div>
              </div>
            )}
            
            {uploadStatus === 'completed' && (
              <div className="mt-4 p-3 bg-green-100 border border-green-400 text-green-700 rounded-lg flex items-center">
                <CheckCircle className="h-5 w-5 mr-2" />
                Upload completed successfully! Redirecting...
              </div>
            )}
            
            {uploadStatus === 'failed' && (
              <div className="mt-4 p-3 bg-red-100 border border-red-400 text-red-700 rounded-lg flex items-center">
                <AlertCircle className="h-5 w-5 mr-2" />
                Upload failed. Please check the console for details.
              </div>
            )}
            
            <div className="flex justify-end space-x-4 mt-6">
              <button onClick={() => { setShowUploadModal(false); setSelectedFiles([]); setUploadStatus(null); setUploadProgress(0); }} className="px-4 py-2 border border-gray-300 rounded-lg">Cancel</button>
              <button onClick={handleFileUpload} disabled={uploading || selectedFiles.length === 0} className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50">
                {uploading ? 'Processing...' : `Upload & Analyze (${selectedFiles.length} files in ${Math.ceil(selectedFiles.length / CHUNK_SIZE)} chunks)`}
              </button>
            </div>
          </div>
        </div>
      )}
      
      {/* Select Top Candidates Modal */}
      {showSelectModal && selectedJob && (
        <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50">
          <div className="relative top-20 mx-auto p-5 border w-full max-w-md shadow-lg rounded-md bg-white">
            <div className="flex justify-between items-center mb-6"><h2 className="text-2xl font-bold text-gray-900">Select Top Candidates</h2><button onClick={() => setShowSelectModal(false)} className="text-gray-400 hover:text-gray-600"><X className="h-6 w-6" /></button></div>
            <div className="space-y-4">
              <div><label className="block text-sm font-medium text-gray-700 mb-2">Number of Candidates to Shortlist</label><input type="number" value={numberOfCandidates} onChange={(e) => setNumberOfCandidates(parseInt(e.target.value))} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-lg" min="1" max="1000" /><p className="text-xs text-gray-500 mt-1">The system will analyze all uploaded resumes and select the top candidates based on skill match</p></div>
              <div className="bg-yellow-50 p-4 rounded-lg"><h4 className="font-semibold text-yellow-800 mb-2">What will happen?</h4><ul className="text-sm text-yellow-700 space-y-1"><li>✓ AI will analyze all uploaded resumes</li><li>✓ Top {numberOfCandidates} candidates will be shortlisted</li><li>✓ New users get auto-generated passwords</li><li>✓ Existing users keep their DreamRole credentials</li><li>✓ You can download credentials CSV file</li></ul></div>
              {selecting && (<div className="text-center py-4"><Loader className="h-8 w-8 animate-spin text-purple-600 mx-auto" /><p className="text-gray-600">Selecting top candidates...</p></div>)}
              <div className="flex justify-end space-x-4"><button onClick={() => setShowSelectModal(false)} className="px-4 py-2 border border-gray-300 rounded-lg">Cancel</button><button onClick={handleSelectCandidates} disabled={selecting} className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 disabled:opacity-50">{selecting ? 'Processing...' : 'Select & Generate Credentials'}</button></div>
            </div>
          </div>
        </div>
      )}
      
      {showCandidateDetails && <CandidateDetailsModal />}
      {showDeleteConfirm && <DeleteConfirmModal />}
      {showResultsModal && selectedJobForResults && (
        <ExternalJobResults
          job={selectedJobForResults}
          onClose={() => {
            setShowResultsModal(false);
            setSelectedJobForResults(null);
          }}
        />
      )}
    </div>
  );
};

export default ExternalJobManagement;