// src/pages/CompanyProfile.jsx
import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { Building, Globe, Users, Briefcase, MapPin, Clock, ArrowLeft } from 'lucide-react';
import api from '../services/api';

const CompanyProfile = () => {
  const { recruiterId } = useParams();
  const [company, setCompany] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    (async () => {
      setIsLoading(true);
      setError('');
      try {
        const response = await api.get(`/companies/${recruiterId}`);
        setCompany(response.data);
      } catch (err) {
        console.error('Error fetching company:', err);
        setError(err.response?.status === 404 ? 'Company not found' : 'Failed to load company');
      } finally {
        setIsLoading(false);
      }
    })();
  }, [recruiterId]);

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
            <h1 className="text-2xl font-bold text-gray-800">{company.companyName}</h1>
            <div className="flex flex-wrap gap-4 mt-2 text-sm text-gray-500">
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
        <div className="text-center py-12 bg-white rounded-lg shadow">
          <Briefcase className="mx-auto h-12 w-12 text-gray-300 mb-3" />
          <p className="text-gray-500">No open positions right now. Check back later.</p>
        </div>
      ) : (
        <div className="space-y-4">
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
    </div>
  );
};

export default CompanyProfile;
