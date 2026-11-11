import React from 'react';
import { X, Star } from 'lucide-react';

const parseSkills = (skillsJson) => {
  try {
    if (!skillsJson) return [];
    if (typeof skillsJson === 'string') {
      const parsed = JSON.parse(skillsJson);
      return Array.isArray(parsed) ? parsed : [];
    }
    return Array.isArray(skillsJson) ? skillsJson : [];
  } catch {
    return [];
  }
};

const Row = ({ label, children }) => (
  <tr className="border-t border-gray-100">
    <td className="py-3 pr-4 text-sm font-medium text-gray-500 whitespace-nowrap align-top">{label}</td>
    {children}
  </tr>
);

/** Side-by-side comparison of 2-3 of the candidate's own applications - salary, required
 *  skills, resume match %, and company rating, all already present on the application/job data
 *  UserApplications already fetched (see ApplicationStatusController.createApplicationResponse),
 *  so this is a pure frontend view with no new endpoint. */
const JobComparisonModal = ({ applications, onClose }) => {
  return (
    <div className="fixed inset-0 bg-gray-600 bg-opacity-50 flex items-center justify-center z-50 p-4 overflow-y-auto">
      <div className="bg-white rounded-lg shadow-lg w-full max-w-4xl max-h-[85vh] overflow-y-auto">
        <div className="flex justify-between items-center p-4 border-b border-gray-200 sticky top-0 bg-white">
          <h2 className="text-lg font-semibold text-gray-900">Compare Applications</h2>
          <button onClick={onClose} aria-label="Close" className="text-gray-400 hover:text-gray-600">
            <X className="h-6 w-6" />
          </button>
        </div>

        <div className="p-4 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr>
                <th className="w-32"></th>
                {applications.map((app) => (
                  <th key={app.id} className="text-left px-4 py-2 min-w-[180px]">
                    <p className="font-semibold text-gray-900">{app.job?.title || 'Unknown Position'}</p>
                    <p className="text-gray-500">{app.job?.company || 'Unknown Company'}</p>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              <Row label="Location">
                {applications.map((app) => (
                  <td key={app.id} className="px-4 py-3">{app.job?.location || '—'}</td>
                ))}
              </Row>
              <Row label="Job Type">
                {applications.map((app) => (
                  <td key={app.id} className="px-4 py-3">{app.job?.jobType || '—'}</td>
                ))}
              </Row>
              <Row label="Experience Level">
                {applications.map((app) => (
                  <td key={app.id} className="px-4 py-3">{app.job?.experienceLevel || '—'}</td>
                ))}
              </Row>
              <Row label="Salary">
                {applications.map((app) => (
                  <td key={app.id} className="px-4 py-3">{app.job?.salaryRange || 'Not specified'}</td>
                ))}
              </Row>
              <Row label="Resume Match">
                {applications.map((app) => (
                  <td key={app.id} className="px-4 py-3">
                    {app.matchPercentage > 0 ? (
                      <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-semibold ${
                        app.matchPercentage >= 80 ? 'bg-green-100 text-green-800' :
                        app.matchPercentage >= 60 ? 'bg-yellow-100 text-yellow-800' : 'bg-red-100 text-red-800'
                      }`}>
                        {Math.round(app.matchPercentage)}%
                      </span>
                    ) : '—'}
                  </td>
                ))}
              </Row>
              <Row label="Company Rating">
                {applications.map((app) => (
                  <td key={app.id} className="px-4 py-3">
                    {app.job?.totalRatings > 0 ? (
                      <span className="flex items-center gap-1">
                        <Star className="h-3.5 w-3.5 text-yellow-400 fill-yellow-400" />
                        {app.job.averageRating} ({app.job.totalRatings})
                      </span>
                    ) : 'No ratings yet'}
                  </td>
                ))}
              </Row>
              <Row label="Required Skills">
                {applications.map((app) => {
                  const skills = parseSkills(app.job?.requiredSkills);
                  return (
                    <td key={app.id} className="px-4 py-3">
                      {skills.length > 0 ? (
                        <div className="flex flex-wrap gap-1">
                          {skills.map((skill, i) => (
                            <span key={i} className="bg-blue-50 text-blue-700 px-2 py-0.5 rounded-full text-xs">
                              {skill}
                            </span>
                          ))}
                        </div>
                      ) : '—'}
                    </td>
                  );
                })}
              </Row>
              <Row label="Status">
                {applications.map((app) => (
                  <td key={app.id} className="px-4 py-3 text-gray-700">
                    {app.status ? app.status.replace(/_/g, ' ') : '—'}
                  </td>
                ))}
              </Row>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default JobComparisonModal;
