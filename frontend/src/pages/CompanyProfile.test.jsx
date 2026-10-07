import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import CompanyProfile from './CompanyProfile';
import api from '../services/api';

vi.mock('../services/api');

const company = { companyName: 'Acme', industry: 'Software', companySize: '100-500', companyWebsite: null, verified: false };

const reviewsWithReply = {
  averageRating: 4,
  totalReviews: 1,
  reviews: [
    { id: 1, rating: 4, comment: 'Good place', createdAt: '2026-09-01T00:00:00', recruiterReply: 'Thanks for the kind words!', recruiterReplyAt: '2026-09-02T00:00:00' },
  ],
};

const reviewsWithoutReply = {
  averageRating: 4,
  totalReviews: 1,
  reviews: [
    { id: 1, rating: 4, comment: 'Good place', createdAt: '2026-09-01T00:00:00', recruiterReply: null, recruiterReplyAt: null },
  ],
};

const experiences = { averageDifficulty: 0, totalExperiences: 0, experiences: [] };

function mockApiFor({ reviews, role, recruiterProfileId }) {
  localStorage.setItem('user', JSON.stringify({ id: 99, role }));
  api.get.mockImplementation((url) => {
    if (url === '/companies/5') return Promise.resolve({ data: company });
    if (url === '/companies/5/reviews') return Promise.resolve({ data: reviews });
    if (url === '/companies/5/interview-experiences') return Promise.resolve({ data: experiences });
    if (url.includes('/company-reviews/5')) return Promise.reject({ response: { status: 404 } });
    if (url.includes('/interview-experiences/5')) return Promise.reject({ response: { status: 404 } });
    if (url === '/recruiters/user/99') return Promise.resolve({ data: { recruiter: { id: recruiterProfileId } } });
    return Promise.resolve({ data: {} });
  });
}

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/companies/5']}>
      <Routes>
        <Route path="/companies/:recruiterId" element={<CompanyProfile />} />
      </Routes>
    </MemoryRouter>
  );

describe('CompanyProfile - recruiter replies to reviews', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.clear();
  });

  it('shows a published reply to any visitor', async () => {
    mockApiFor({ reviews: reviewsWithReply, role: 'ROLE_USER' });

    renderPage();

    expect(await screen.findByText('Thanks for the kind words!')).toBeInTheDocument();
    expect(screen.getByText(/Response from Acme/i)).toBeInTheDocument();
  });

  it('lets the owning recruiter reply to a review with no reply yet', async () => {
    const user = userEvent.setup();
    mockApiFor({ reviews: reviewsWithoutReply, role: 'ROLE_RECRUITER', recruiterProfileId: 5 });
    api.post.mockResolvedValue({ data: { message: 'Reply posted' } });

    renderPage();

    const input = await screen.findByPlaceholderText(/Reply as this company/i);
    await user.type(input, 'Thanks for the feedback!');
    await user.click(screen.getByRole('button', { name: /^Reply$/i }));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/recruiters/5/company-reviews/1/reply', { reply: 'Thanks for the feedback!' });
    });
  });

  it('does not show a reply form to a candidate viewer', async () => {
    mockApiFor({ reviews: reviewsWithoutReply, role: 'ROLE_USER' });

    renderPage();

    await screen.findByText('Good place');
    expect(screen.queryByPlaceholderText(/Reply as this company/i)).not.toBeInTheDocument();
  });

  it('does not show a reply form to a recruiter who owns a different company', async () => {
    mockApiFor({ reviews: reviewsWithoutReply, role: 'ROLE_RECRUITER', recruiterProfileId: 999 });

    renderPage();

    await screen.findByText('Good place');
    expect(screen.queryByPlaceholderText(/Reply as this company/i)).not.toBeInTheDocument();
  });
});
