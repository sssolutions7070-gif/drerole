import React from 'react';
import { BadgeCheck } from 'lucide-react';

/** A small trust signal for a company whose business registration an admin has reviewed and
 *  approved (RecruiterVerificationService) - shown wherever a company's name appears publicly. */
const VerifiedBadge = ({ className = '' }) => (
  <span
    title="Verified Employer - this company's business registration has been reviewed"
    className={`inline-flex items-center gap-1 text-xs font-medium text-blue-700 bg-blue-50 px-2 py-0.5 rounded-full ${className}`}
  >
    <BadgeCheck className="h-3.5 w-3.5" />
    Verified
  </span>
);

export default VerifiedBadge;
