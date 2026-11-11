// src/pages/CompanyProfile.jsx
import React, { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { Building, Globe, Users, Briefcase, MapPin, Clock, ArrowLeft, Star, MessageSquare, Linkedin, Twitter, Calendar, CheckCircle2 } from 'lucide-react';
import api from '../services/api';
import VerifiedBadge from '../components/VerifiedBadge';
import ResponsiveBadge from '../components/ResponsiveBadge';
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

const OUTCOME_LABELS = {
  OFFER: 'Got an offer', REJECTED: 'Rejected', NO_RESPONSE: 'No response', WITHDREW: 'Withdrew',
};

const DifficultyRow = ({ rating }) => (
  <div className="flex items-center">
    {[1, 2, 3, 4, 5].map((n) => (
      <div
        key={n}
        className={`w-2.5 h-2.5 rounded-full mr-0.5 ${n <= Math.round(rating) ? 'bg-orange-500' : 'bg-gray-200'}`}
      />
    ))}
  </div>
);

const StarRow = ({ rating, size = 'h-4 w-4' }) => (
  <div className="flex items-center">
    {[1, 2, 3, 4, 5].map((n) => (
      <Star
        key={n}
        className={`${size} ${n <= Math.round(rating) ? 'text-yellow-400 fill-yellow-400' : 'text-gray-300'}`}
      />
    ))}
  </div>
);

const CompanyProfile = () => {
  const { recruiterId } = useParams();
  const navigate = useNavigate();
  const currentUser = JSON.parse(localStorage.getItem('user'));

  const [company, setCompany] = useState(null);
  const [reviews, setReviews] = useState({ averageRating: 0, totalReviews: 0, reviews: [] });
  const [myReview, setMyReview] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  const [ownRecruiterId, setOwnRecruiterId] = useState(null);
  const [replyDrafts, setReplyDrafts] = useState({});
  const [postingReplyId, setPostingReplyId] = useState(null);

  const [reviewRating, setReviewRating] = useState(0);
  const [reviewComment, setReviewComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitMessage, setSubmitMessage] = useState('');

  const [experiences, setExperiences] = useState({ averageDifficulty: 0, totalExperiences: 0, experiences: [] });
  const [myExperience, setMyExperience] = useState(null);
  const [experienceDifficulty, setExperienceDifficulty] = useState(0);
  const [experienceOutcome, setExperienceOutcome] = useState('');
  const [experienceDescription, setExperienceDescription] = useState('');
  const [submittingExperience, setSubmittingExperience] = useState(false);
  const [experienceSubmitMessage, setExperienceSubmitMessage] = useState('');

  const fetchAll = async () => {
    setIsLoading(true);
    setError('');
    try {
      const [companyRes, reviewsRes, experiencesRes] = await Promise.all([
        api.get(`/companies/${recruiterId}`),
        api.get(`/companies/${recruiterId}/reviews`),
        api.get(`/companies/${recruiterId}/interview-experiences`),
      ]);
      setCompany(companyRes.data);
      setReviews(reviewsRes.data);
      setExperiences(experiencesRes.data);

      if (currentUser?.id) {
        try {
          const mine = await api.get(`/users/${currentUser.id}/company-reviews/${recruiterId}`);
          if (mine.status === 200) {
            setMyReview(mine.data);
            setReviewRating(mine.data.rating);
            setReviewComment(mine.data.comment || '');
          }
        } catch {
          // No existing review - fine, the form just starts blank.
        }
        try {
          const mine = await api.get(`/users/${currentUser.id}/interview-experiences/${recruiterId}`);
          if (mine.status === 200) {
            setMyExperience(mine.data);
            setExperienceDifficulty(mine.data.difficultyRating);
            setExperienceOutcome(mine.data.outcome);
            setExperienceDescription(mine.data.processDescription || '');
          }
        } catch {
          // No existing report - fine, the form just starts blank.
        }
        if (currentUser.role === 'ROLE_RECRUITER') {
          try {
            const profile = await api.get(`/recruiters/user/${currentUser.id}`);
            if (profile.data?.recruiter?.id) {
              setOwnRecruiterId(profile.data.recruiter.id);
            }
          } catch {
            // Not a recruiter, or no recruiter profile yet - the reply form just stays hidden.
          }
        }
      }
    } catch (err) {
      console.error('Error fetching company:', err);
      setError(err.response?.status === 404 ? 'Company not found' : 'Failed to load company');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchAll();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recruiterId]);

  const handleSubmitReview = async (e) => {
    e.preventDefault();
    if (!currentUser) {
      navigate('/login');
      return;
    }
    setSubmitting(true);
    setSubmitMessage('');
    try {
      await api.post(`/users/${currentUser.id}/company-reviews/${recruiterId}`, {
        rating: reviewRating,
        comment: reviewComment,
      });
      setSubmitMessage('Thanks! Your review is awaiting approval before it appears publicly.');
      fetchAll();
    } catch (err) {
      setSubmitMessage(err.response?.data?.error || 'Failed to submit review');
    } finally {
      setSubmitting(false);
    }
  };

  const handleSubmitExperience = async (e) => {
    e.preventDefault();
    if (!currentUser) {
      navigate('/login');
      return;
    }
    setSubmittingExperience(true);
    setExperienceSubmitMessage('');
    try {
      await api.post(`/users/${currentUser.id}/interview-experiences/${recruiterId}`, {
        difficultyRating: experienceDifficulty,
        outcome: experienceOutcome,
        processDescription: experienceDescription,
      });
      setExperienceSubmitMessage('Thanks! Your report is awaiting approval before it appears publicly.');
      fetchAll();
    } catch (err) {
      setExperienceSubmitMessage(err.response?.data?.error || 'Failed to submit your report');
    } finally {
      setSubmittingExperience(false);
    }
  };

  const handlePostReply = async (reviewId) => {
    const reply = (replyDrafts[reviewId] || '').trim();
    if (!reply) return;
    setPostingReplyId(reviewId);
    try {
      await api.post(`/recruiters/${ownRecruiterId}/company-reviews/${reviewId}/reply`, { reply });
      setReplyDrafts((prev) => ({ ...prev, [reviewId]: '' }));
      fetchAll();
    } catch (err) {
      alert(err.response?.data?.error || 'Failed to post reply');
    } finally {
      setPostingReplyId(null);
    }
  };

  const formatDate = (dateString) => {
    if (!dateString) return '';
    return new Date(dateString).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
  };

  if (isLoading) {
    return <div className="max-w-4xl mx-auto py-12 px-4 text-center text-gray-500">Loading...</div>;
  }

  if (error || !company) {
    return (
      <div className="max-w-4xl mx-auto py-12 px-4 text-center">
        <p className="text-gray-500 mb-4">{error || 'Company not found'}</p>
        <Link to="/companies" className="text-blue-600 hover:text-blue-800">Back to companies</Link>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto py-8 px-4">
      <Link to="/companies" className="inline-flex items-center text-sm text-gray-500 hover:text-gray-700 mb-4">
        <ArrowLeft className="h-4 w-4 mr-1" />
        All companies
      </Link>

      <div className="bg-white rounded-lg shadow-lg border border-gray-200 mb-6 overflow-hidden">
        {company.companyBanner ? (
          <img
            src={toAbsoluteUrl(company.companyBanner)}
            alt=""
            className="w-full h-40 object-cover"
          />
        ) : (
          <div className="w-full h-24 bg-gradient-to-r from-blue-500 to-indigo-600" />
        )}

        <div className="p-6">
          <div className="flex items-start space-x-4">
            {company.companyLogo ? (
              <img
                src={toAbsoluteUrl(company.companyLogo)}
                alt={`${company.companyName} logo`}
                className="w-16 h-16 rounded-full object-cover border border-gray-200 flex-shrink-0 bg-white"
              />
            ) : (
              <div className="w-16 h-16 bg-blue-100 rounded-full flex items-center justify-center flex-shrink-0">
                <Building className="h-8 w-8 text-blue-600" />
              </div>
            )}
            <div className="flex-1">
              <h1 className="text-2xl font-bold text-gray-800 flex items-center gap-2">
                {company.companyName}
                {company.verified && <VerifiedBadge />}
                {company.responsiveBadge && <ResponsiveBadge />}
              </h1>
              <div className="flex flex-wrap items-center gap-4 mt-2 text-sm text-gray-500">
                {company.industry && <span>{company.industry}</span>}
                {company.companySize && (
                  <span className="flex items-center">
                    <Users className="h-4 w-4 mr-1" />
                    {company.companySize} employees
                  </span>
                )}
                {company.headquarters && (
                  <span className="flex items-center">
                    <MapPin className="h-4 w-4 mr-1" />
                    {company.headquarters}
                  </span>
                )}
                {company.foundedYear && (
                  <span className="flex items-center">
                    <Calendar className="h-4 w-4 mr-1" />
                    Founded {company.foundedYear}
                  </span>
                )}
                {company.companyWebsite && (
                  <a
                    href={company.companyWebsite}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center text-blue-600 hover:text-blue-800"
                  >
                    <Globe className="h-4 w-4 mr-1" />
                    Website
                  </a>
                )}
                {company.companyLinkedin && (
                  <a
                    href={company.companyLinkedin}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center text-blue-600 hover:text-blue-800"
                  >
                    <Linkedin className="h-4 w-4 mr-1" />
                    LinkedIn
                  </a>
                )}
                {company.companyTwitter && (
                  <a
                    href={company.companyTwitter}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center text-blue-600 hover:text-blue-800"
                  >
                    <Twitter className="h-4 w-4 mr-1" />
                    Twitter
                  </a>
                )}
                {reviews.totalReviews > 0 && (
                  <span className="flex items-center">
                    <StarRow rating={reviews.averageRating} />
                    <span className="ml-1">{reviews.averageRating} ({reviews.totalReviews} {reviews.totalReviews === 1 ? 'review' : 'reviews'})</span>
                  </span>
                )}
                {company.averageInterviewRating != null && (
                  <span className="flex items-center" title="How candidates who were actually interviewed rated the interview process itself">
                    <StarRow rating={company.averageInterviewRating} />
                    <span className="ml-1">
                      {company.averageInterviewRating} interview experience ({company.interviewFeedbackCount} {company.interviewFeedbackCount === 1 ? 'rating' : 'ratings'})
                    </span>
                  </span>
                )}
              </div>
              {company.companyDescription && (
                <p className="mt-4 text-gray-700 whitespace-pre-line">{company.companyDescription}</p>
              )}
              {company.missionStatement && (
                <blockquote className="mt-4 border-l-4 border-blue-200 pl-4 italic text-gray-600">
                  {company.missionStatement}
                </blockquote>
              )}
              {parseBenefits(company.benefits).length > 0 && (
                <div className="mt-4">
                  <p className="text-xs font-semibold text-gray-500 uppercase mb-2">Benefits & Perks</p>
                  <div className="flex flex-wrap gap-2">
                    {parseBenefits(company.benefits).map((benefit, i) => (
                      <span
                        key={i}
                        className="inline-flex items-center gap-1 text-sm bg-green-50 text-green-700 px-3 py-1 rounded-full"
                      >
                        <CheckCircle2 className="h-3.5 w-3.5" />
                        {benefit}
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      <h2 className="text-xl font-semibold text-gray-800 mb-4">
        Open Positions ({company.jobs?.length || 0})
      </h2>

      {!company.jobs || company.jobs.length === 0 ? (
        <div className="text-center py-12 bg-white rounded-lg shadow mb-6">
          <Briefcase className="mx-auto h-12 w-12 text-gray-300 mb-3" />
          <p className="text-gray-500">No open positions right now. Check back later.</p>
        </div>
      ) : (
        <div className="space-y-4 mb-8">
          {company.jobs.map((job) => (
            <Link
              key={job.id}
              to={`/jobs?search=${encodeURIComponent(job.title)}`}
              className="block bg-white rounded-lg shadow border border-gray-200 p-5 hover:shadow-lg transition-shadow"
            >
              <h3 className="text-lg font-semibold text-gray-800">{job.title}</h3>
              <div className="flex flex-wrap gap-4 mt-2 text-sm text-gray-500">
                {job.location && (
                  <span className="flex items-center">
                    <MapPin className="h-4 w-4 mr-1" />
                    {job.location}
                  </span>
                )}
                {job.jobType && <span>{job.jobType}</span>}
                <span className="flex items-center">
                  <Clock className="h-4 w-4 mr-1" />
                  Posted {formatDate(job.postedDate)}
                </span>
              </div>
            </Link>
          ))}
        </div>
      )}

      <h2 className="text-xl font-semibold text-gray-800 mb-4">
        Reviews ({reviews.totalReviews})
      </h2>

      <div className="bg-white rounded-lg shadow border border-gray-200 p-6 mb-6">
        <h3 className="font-semibold text-gray-800 mb-3">
          {myReview ? 'Update your review' : 'Write a review'}
        </h3>
        {myReview?.moderationStatus === 'PENDING' && (
          <p className="text-sm text-yellow-700 bg-yellow-50 rounded px-3 py-2 mb-3">
            Your review is awaiting approval.
          </p>
        )}
        {myReview?.moderationStatus === 'REJECTED' && (
          <p className="text-sm text-red-700 bg-red-50 rounded px-3 py-2 mb-3">
            Your review wasn't approved{myReview.rejectionReason ? `: ${myReview.rejectionReason}` : '.'} You can edit and resubmit it below.
          </p>
        )}
        <form onSubmit={handleSubmitReview} className="space-y-3">
          <div className="flex items-center space-x-1">
            {[1, 2, 3, 4, 5].map((n) => (
              <button
                type="button"
                key={n}
                onClick={() => setReviewRating(n)}
                aria-label={`Rate ${n} star${n > 1 ? 's' : ''}`}
              >
                <Star className={`h-6 w-6 ${n <= reviewRating ? 'text-yellow-400 fill-yellow-400' : 'text-gray-300'}`} />
              </button>
            ))}
          </div>
          <textarea
            value={reviewComment}
            onChange={(e) => setReviewComment(e.target.value)}
            placeholder="Share your experience with this company..."
            rows={3}
            className="w-full border border-gray-300 rounded-lg px-3 py-2"
          />
          {submitMessage && <p className="text-sm text-gray-600">{submitMessage}</p>}
          <button
            type="submit"
            disabled={submitting || reviewRating === 0}
            className="bg-blue-600 text-white px-4 py-2 rounded-lg font-medium hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {submitting ? 'Submitting...' : myReview ? 'Update Review' : 'Submit Review'}
          </button>
        </form>
      </div>

      {reviews.reviews.length === 0 ? (
        <p className="text-gray-500 text-center py-6">No reviews yet. Be the first to share your experience.</p>
      ) : (
        <div className="space-y-3">
          {reviews.reviews.map((review) => (
            <div key={review.id} className="bg-white rounded-lg shadow border border-gray-200 p-4">
              <div className="flex items-center justify-between mb-1">
                <StarRow rating={review.rating} />
                <span className="text-xs text-gray-400">{formatDate(review.createdAt)}</span>
              </div>
              {review.comment && <p className="text-gray-700 text-sm">{review.comment}</p>}

              {review.recruiterReply && (
                <div className="mt-3 ml-4 pl-3 border-l-2 border-blue-200 bg-blue-50/50 rounded-r p-2">
                  <p className="text-xs font-semibold text-blue-800">
                    Response from {company.companyName}
                    <span className="font-normal text-gray-400 ml-2">{formatDate(review.recruiterReplyAt)}</span>
                  </p>
                  <p className="text-sm text-gray-700 mt-1">{review.recruiterReply}</p>
                </div>
              )}

              {!review.recruiterReply && ownRecruiterId === Number(recruiterId) && (
                <div className="mt-3 ml-4 flex gap-2">
                  <input
                    type="text"
                    value={replyDrafts[review.id] || ''}
                    onChange={(e) => setReplyDrafts((prev) => ({ ...prev, [review.id]: e.target.value }))}
                    placeholder="Reply as this company..."
                    className="flex-1 text-sm border border-gray-300 rounded px-2 py-1"
                  />
                  <button
                    type="button"
                    onClick={() => handlePostReply(review.id)}
                    disabled={postingReplyId === review.id || !(replyDrafts[review.id] || '').trim()}
                    className="text-sm bg-blue-600 text-white px-3 py-1 rounded hover:bg-blue-700 disabled:opacity-50"
                  >
                    {postingReplyId === review.id ? 'Posting…' : 'Reply'}
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      <h2 className="text-xl font-semibold text-gray-800 mb-4 mt-8 flex items-center">
        <MessageSquare className="h-5 w-5 mr-2" />
        Interview Experiences ({experiences.totalExperiences})
      </h2>

      <div className="bg-white rounded-lg shadow border border-gray-200 p-6 mb-6">
        <h3 className="font-semibold text-gray-800 mb-3">
          {myExperience ? 'Update your interview report' : 'Share what interviewing here was like'}
        </h3>
        {myExperience?.moderationStatus === 'PENDING' && (
          <p className="text-sm text-yellow-700 bg-yellow-50 rounded px-3 py-2 mb-3">
            Your report is awaiting approval.
          </p>
        )}
        {myExperience?.moderationStatus === 'REJECTED' && (
          <p className="text-sm text-red-700 bg-red-50 rounded px-3 py-2 mb-3">
            Your report wasn't approved{myExperience.rejectionReason ? `: ${myExperience.rejectionReason}` : '.'} You can edit and resubmit it below.
          </p>
        )}
        <form onSubmit={handleSubmitExperience} className="space-y-3">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">How difficult was it?</label>
            <div className="flex items-center space-x-1">
              {[1, 2, 3, 4, 5].map((n) => (
                <button
                  type="button"
                  key={n}
                  onClick={() => setExperienceDifficulty(n)}
                  aria-label={`Difficulty ${n}`}
                  className={`w-8 h-8 rounded-md text-sm font-medium border ${
                    n <= experienceDifficulty ? 'bg-orange-500 text-white border-orange-500' : 'bg-white text-gray-600 border-gray-300'
                  }`}
                >
                  {n}
                </button>
              ))}
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Outcome</label>
            <select
              value={experienceOutcome}
              onChange={(e) => setExperienceOutcome(e.target.value)}
              className="w-full border border-gray-300 rounded-lg px-3 py-2"
            >
              <option value="">Select...</option>
              {Object.entries(OUTCOME_LABELS).map(([value, label]) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </div>
          <textarea
            value={experienceDescription}
            onChange={(e) => setExperienceDescription(e.target.value)}
            placeholder="What was the process like? How many rounds, what kind of questions..."
            rows={3}
            className="w-full border border-gray-300 rounded-lg px-3 py-2"
          />
          {experienceSubmitMessage && <p className="text-sm text-gray-600">{experienceSubmitMessage}</p>}
          <button
            type="submit"
            disabled={submittingExperience || experienceDifficulty === 0 || !experienceOutcome}
            className="bg-blue-600 text-white px-4 py-2 rounded-lg font-medium hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {submittingExperience ? 'Submitting...' : myExperience ? 'Update Report' : 'Submit Report'}
          </button>
        </form>
      </div>

      {experiences.experiences.length === 0 ? (
        <p className="text-gray-500 text-center py-6">No interview experiences shared yet.</p>
      ) : (
        <div className="space-y-3">
          {experiences.experiences.map((experience) => (
            <div key={experience.id} className="bg-white rounded-lg shadow border border-gray-200 p-4">
              <div className="flex items-center justify-between mb-1">
                <div className="flex items-center gap-3">
                  <DifficultyRow rating={experience.difficultyRating} />
                  <span className="text-xs font-medium text-gray-600">{OUTCOME_LABELS[experience.outcome]}</span>
                </div>
                <span className="text-xs text-gray-400">{formatDate(experience.createdAt)}</span>
              </div>
              {experience.processDescription && <p className="text-gray-700 text-sm">{experience.processDescription}</p>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default CompanyProfile;
