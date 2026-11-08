import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import Jobs from './Jobs';
import api from '../services/api';

vi.mock('../services/api');

const allJobs = [
  { id: 1, title: 'Backend Engineer', company: 'Acme', description: 'Build APIs', jobType: 'Full-time', postedDate: '2026-01-01T00:00:00', isActive: true },
  { id: 2, title: 'Product Manager', company: 'Acme', description: 'Own the roadmap', jobType: 'Full-time', postedDate: '2026-01-02T00:00:00', isActive: true },
];

// Already ranked by the backend (title match ahead of a description-only match) - the frontend
// must use this order as-is rather than re-deriving its own.
const rankedSearchResults = [
  { id: 1, title: 'Backend Engineer', company: 'Acme', description: 'Build APIs', jobType: 'Full-time', postedDate: '2026-01-01T00:00:00', isActive: true },
];

const renderJobs = () => render(
  <MemoryRouter>
    <Jobs />
  </MemoryRouter>
);

describe('Jobs page search', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.clear();
    api.get.mockImplementation((url) => {
      if (url === '/jobs') return Promise.resolve({ data: allJobs });
      if (url === '/jobs/search') return Promise.resolve({ data: rankedSearchResults });
      return Promise.resolve({ data: {} });
    });
  });

  it('shows the full list before any search term is typed', async () => {
    renderJobs();

    expect(await screen.findByText('Backend Engineer')).toBeInTheDocument();
    expect(screen.getByText('Product Manager')).toBeInTheDocument();
  });

  it('calls the backend search endpoint with the typed keyword and renders its ranked results', async () => {
    const user = userEvent.setup();
    renderJobs();
    await screen.findByText('Backend Engineer');

    await user.type(screen.getByPlaceholderText(/Search jobs by title, company, or keyword/i), 'Engineer');

    await waitFor(() => expect(api.get).toHaveBeenCalledWith('/jobs/search', { params: { keyword: 'Engineer' } }));
    await waitFor(() => expect(screen.queryByText('Product Manager')).not.toBeInTheDocument());
    expect(screen.getByText('Backend Engineer')).toBeInTheDocument();
  });

  it('reverts to the full list once the search box is cleared', async () => {
    const user = userEvent.setup();
    renderJobs();
    await screen.findByText('Backend Engineer');

    const searchBox = screen.getByPlaceholderText(/Search jobs by title, company, or keyword/i);
    await user.type(searchBox, 'Engineer');
    await waitFor(() => expect(screen.queryByText('Product Manager')).not.toBeInTheDocument());

    await user.clear(searchBox);
    await waitFor(() => expect(screen.getByText('Product Manager')).toBeInTheDocument());
  });
});
