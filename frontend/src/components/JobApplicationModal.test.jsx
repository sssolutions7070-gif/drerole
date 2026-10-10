import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import JobApplicationModal from './JobApplicationModal';
import api from '../services/api';

vi.mock('../services/api');

const job = { id: 7, title: 'Backend Engineer', company: 'Acme Corp', description: 'Build APIs' };

describe('JobApplicationModal - AI cover letter generation', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Asha Rao' }));
    api.get.mockImplementation((url) => {
      if (url.includes('/resumes/user/')) {
        return Promise.resolve({ data: { id: 50, filePath: '/r.pdf', skills: '["Java", "Spring"]', experienceYears: 3, educationLevel: "Bachelor's" } });
      }
      return Promise.resolve({ data: {} });
    });
  });

  it('fills the cover letter textarea with the AI-generated text, built from the job and the resume-derived skills', async () => {
    api.post.mockResolvedValue({ data: { coverLetter: 'Dear Hiring Manager, ... Sincerely, Asha' } });
    const user = userEvent.setup();
    render(<JobApplicationModal job={job} onClose={() => {}} onSuccess={() => {}} />);

    await waitFor(() => expect(screen.queryByText(/No resume found/i)).not.toBeInTheDocument());

    await user.click(screen.getByRole('button', { name: /generate with ai/i }));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/resume-matching/generate-cover-letter', expect.objectContaining({
      candidateName: 'Asha Rao',
      jobTitle: 'Backend Engineer',
      company: 'Acme Corp',
      jobDescription: 'Build APIs',
      userSkills: ['Java', 'Spring'],
    })));

    expect(await screen.findByDisplayValue('Dear Hiring Manager, ... Sincerely, Asha')).toBeInTheDocument();
  });

  it('shows an error message instead of a blank letter when generation fails', async () => {
    api.post.mockRejectedValue({ response: { data: { error: 'AI service unavailable' } } });
    const user = userEvent.setup();
    render(<JobApplicationModal job={job} onClose={() => {}} onSuccess={() => {}} />);

    await waitFor(() => expect(screen.queryByText(/No resume found/i)).not.toBeInTheDocument());

    await user.click(screen.getByRole('button', { name: /generate with ai/i }));

    expect(await screen.findByText('AI service unavailable')).toBeInTheDocument();
  });

  it('disables the button and shows a generating state while the request is in flight', async () => {
    let resolveRequest;
    api.post.mockReturnValue(new Promise((resolve) => { resolveRequest = resolve; }));
    const user = userEvent.setup();
    render(<JobApplicationModal job={job} onClose={() => {}} onSuccess={() => {}} />);

    await waitFor(() => expect(screen.queryByText(/No resume found/i)).not.toBeInTheDocument());

    await user.click(screen.getByRole('button', { name: /generate with ai/i }));

    expect(await screen.findByRole('button', { name: /generating/i })).toBeDisabled();
    resolveRequest({ data: { coverLetter: 'Letter text' } });
  });
});
