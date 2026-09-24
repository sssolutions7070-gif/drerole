// src/pages/CompanyProfile.jsx
import React, { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { Building, Globe, Users, Briefcase, MapPin, Clock, ArrowLeft, Star, MessageSquare } from 'lucide-react';
import api from '../services/api';
import VerifiedBadge from '../components/VerifiedBadge';

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

      <div className="bg-white rounded-lg shadow-lg border border-gray-200 p-6 mb-6">
        <div className="flex items-start space-x-4">
          <div className="w-16 h-16 bg-blue-100 rounded-full flex items-center justify-center flex-shrink-0">
            <Building className="h-8 w-8 text-blue-600" />
          </div>
          <div className="flex-1">
            <h1 className="text-2xl font-bold text-gray-800 flex items-center gap-2">
              {company.companyName}
              {company.verified && <VerifiedBadge />}
            </h1>
            <div className="flex flex-wrap items-center gap-4 mt-2 text-sm text-gray-500">
              {company.industry && <span>{company.industry}</span>}
              {company.companySize && (
                <span className="flex items-center">
                  <Users className="h-4 w-4 mr-1" />
                  {company.companySize} employees
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
              {reviews.totalReviews > 0 && (
                <span className="flex items-center">
                  <StarRow rating={reviews.averageRating} />
                  <span className="ml-1">{reviews.averageRating} ({reviews.totalReviews} {reviews.totalReviews === 1 ? 'review' : 'reviews'})</span>
                </span>
              )}
            </div>
            {company.companyDescription && (
              <p className="mt-4 text-gray-700 whitespace-pre-line">{company.companyDescription}</p>
            )}
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
