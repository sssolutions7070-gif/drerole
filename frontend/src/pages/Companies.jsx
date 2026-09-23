// src/pages/Companies.jsx
import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Building, Briefcase, Users } from 'lucide-react';
import api from '../services/api';

const Companies = () => {
  const [companies, setCompanies] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const response = await api.get('/companies');
        setCompanies(response.data);
      } catch (err) {
        console.error('Error fetching companies:', err);
        setError('Failed to load companies');
      } finally {
        setIsLoading(false);
      }
    })();
  }, []);

  const filtered = companies.filter((company) =>
    (company.companyName || '').toLowerCase().includes(search.toLowerCase()) ||
    (company.industry || '').toLowerCase().includes(search.toLowerCase())
  );

  if (isLoading) {
    return <div className="max-w-5xl mx-auto py-12 px-4 text-center text-gray-500">Loading companies...</div>;
  }

  return (
    <div className="max-w-5xl mx-auto py-8 px-4">
      <h1 className="text-3xl font-bold text-gray-800 mb-2">Companies Hiring Now</h1>
      <p className="text-gray-600 mb-6">Browse companies with open positions on DreamRole.</p>

      <input
        type="text"
        placeholder="Search by company name or industry..."
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        className="w-full mb-6 px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
      />

      {error && <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-4">{error}</div>}

      {filtered.length === 0 ? (
        <div className="text-center py-12 bg-white rounded-lg shadow">
          <Building className="mx-auto h-16 w-16 text-gray-400 mb-4" />
          <p className="text-gray-500">No companies found.</p>
        </div>
      ) : (
        <div className="grid md:grid-cols-2 gap-4">
          {filtered.map((company) => (
            <Link
              key={company.recruiterId}
              to={`/companies/${company.recruiterId}`}
              className="bg-white rounded-lg shadow border border-gray-200 p-6 hover:shadow-lg transition-shadow"
            >
              <div className="flex items-start space-x-3">
                <div className="w-12 h-12 bg-blue-100 rounded-full flex items-center justify-center flex-shrink-0">
                  <Building className="h-6 w-6 text-blue-600" />
                </div>
                <div className="flex-1">
                  <h3 className="text-lg font-semibold text-gray-800">{company.companyName}</h3>
                  <div className="flex flex-wrap gap-3 mt-1 text-sm text-gray-500">
                    {company.industry && <span>{company.industry}</span>}
                    {company.companySize && (
                      <span className="flex items-center">
                        <Users className="h-3.5 w-3.5 mr-1" />
                        {company.companySize}
                      </span>
                    )}
                  </div>
                  <div className="mt-2 flex items-center text-sm text-blue-600 font-medium">
                    <Briefcase className="h-4 w-4 mr-1" />
                    {company.openJobCount} open {company.openJobCount === 1 ? 'position' : 'positions'}
                  </div>
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
};

export default Companies;
