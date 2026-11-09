import React from 'react';
import { Zap } from 'lucide-react';

/** A trust signal for the other side of the application process: this company has a track record
 *  of actually deciding on applications quickly (shortlist/interview/reject/offer), not just
 *  leaving candidates in a black hole. See CompanyService.getCompanyProfile /
 *  ApplicationStatusService.getResponsivenessSummary for how it's earned. */
const ResponsiveBadge = ({ className = '' }) => (
  <span
    title="Responsive Employer - this company typically decides on applications within a few days"
    className={`inline-flex items-center gap-1 text-xs font-medium text-green-700 bg-green-50 px-2 py-0.5 rounded-full ${className}`}
  >
    <Zap className="h-3.5 w-3.5" />
    Responsive
  </span>
);

export default ResponsiveBadge;
