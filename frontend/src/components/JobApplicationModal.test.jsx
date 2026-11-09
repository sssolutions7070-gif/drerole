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

describe('JobApplicationModal - screening questions', () => {
  const jobWithScreening = {
    id: 7,
    title: 'Backend Engineer',
    company: 'Acme Corp',
    description: 'Build APIs',
    screeningQuestions: JSON.stringify([
      { questionText: 'Authorized to work in India?', expectedAnswer: true, knockout: true },
    ]),
  };

  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Asha Rao' }));
    api.get.mockImplementation((url) => {
      if (url.includes('/resumes/user/')) {
        // educationLevel must exactly match one of the <select>'s listed options - a value that
        // doesn't (e.g. "Bachelor's" instead of "Bachelor's Degree") makes the DOM silently
        // reset to the blank option, which then fails the field's `required` HTML5 validation
        // and blocks submission before React's onSubmit ever runs.
        return Promise.resolve({ data: { id: 50, filePath: '/r.pdf', skills: '["Java"]', experienceYears: 3, educationLevel: "Bachelor's Degree" } });
      }
      return Promise.resolve({ data: {} });
    });
  });

  it('renders the job\'s screening questions with Yes/No options', async () => {
    render(<JobApplicationModal job={jobWithScreening} onClose={() => {}} onSuccess={() => {}} />);

    await waitFor(() => expect(screen.queryByText(/No resume found/i)).not.toBeInTheDocument());

    expect(screen.getByText('Authorized to work in India?')).toBeInTheDocument();
  });

  it('blocks submission with a clear error until every screening question is answered', async () => {
    const user = userEvent.setup();
    render(<JobApplicationModal job={jobWithScreening} onClose={() => {}} onSuccess={() => {}} />);
    await waitFor(() => expect(screen.queryByText(/No resume found/i)).not.toBeInTheDocument());

    await user.click(screen.getByRole('button', { name: /submit application/i }));

    expect(await screen.findByText(/Please answer: "Authorized to work in India\?"/i)).toBeInTheDocument();
    expect(api.post).not.toHaveBeenCalledWith(expect.stringContaining('/apply-with-resume/'), expect.anything());
  });

  it('includes the answered questions in the submitted application', async () => {
    api.post.mockResolvedValue({ data: { applicationId: 1, autoRejected: false } });
    const onSuccess = vi.fn();
    const user = userEvent.setup();
    render(<JobApplicationModal job={jobWithScreening} onClose={() => {}} onSuccess={onSuccess} />);
    await waitFor(() => expect(screen.queryByText(/No resume found/i)).not.toBeInTheDocument());

    const yesRadios = screen.getAllByLabelText('Yes');
    await user.click(yesRadios[yesRadios.length - 1]);
    await user.click(screen.getByRole('button', { name: /submit application/i }));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith(
      expect.stringContaining('/apply-with-resume/'),
      expect.objectContaining({
        screeningAnswers: [{ questionText: 'Authorized to work in India?', answer: true }],
      })
    ));
    expect(onSuccess).toHaveBeenCalled();
  });

  it('alerts with the server\'s explanation when the answer auto-rejects the application', async () => {
    api.post.mockResolvedValue({
      data: { applicationId: 1, autoRejected: true, message: "Didn't meet a required screening question." },
    });
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
    const user = userEvent.setup();
    render(<JobApplicationModal job={jobWithScreening} onClose={() => {}} onSuccess={() => {}} />);
    await waitFor(() => expect(screen.queryByText(/No resume found/i)).not.toBeInTheDocument());

    const noRadios = screen.getAllByLabelText('No');
    await user.click(noRadios[noRadios.length - 1]);
    await user.click(screen.getByRole('button', { name: /submit application/i }));

    await waitFor(() => expect(alertSpy).toHaveBeenCalledWith("Didn't meet a required screening question."));
  });

  it('does not show a screening-questions section when the job has none', async () => {
    render(<JobApplicationModal job={{ id: 7, title: 'Backend Engineer' }} onClose={() => {}} onSuccess={() => {}} />);

    await waitFor(() => expect(screen.queryByText(/No resume found/i)).not.toBeInTheDocument());

    expect(screen.queryByText(/Screening Questions/i)).not.toBeInTheDocument();
  });
});

describe('JobApplicationModal - education level normalization', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Asha Rao' }));
  });

  // A resume's educationLevel is free text and often won't exactly match one of the <select>'s
  // option values ("Bachelor's Degree", etc). Before normalizeEducationLevel existed, a
  // near-miss like "Bachelor's" (no "Degree") made the <select> silently fall back to its blank
  // option, which then failed the field's `required` attribute and blocked submission with no
  // visible error - these tests prove that trap is closed for several realistic near-misses.
  it.each([
    ["Bachelor's", "Bachelor's Degree"],
    ['BS', "Bachelor's Degree"],
    ["Master's", "Master's Degree"],
    ['Ph.D.', 'PhD'],
    ["Associate's", 'Associate Degree'],
    ['Some Unrecognized Credential', "Bachelor's Degree"],
  ])('normalizes resume educationLevel "%s" to a selectable option and still allows submission', async (rawValue, expectedOption) => {
    api.get.mockImplementation((url) => {
      if (url.includes('/resumes/user/')) {
        return Promise.resolve({ data: { id: 50, filePath: '/r.pdf', skills: '["Java"]', experienceYears: 3, educationLevel: rawValue } });
      }
      return Promise.resolve({ data: {} });
    });
    api.post.mockResolvedValue({ data: { applicationId: 1, autoRejected: false } });
    const onSuccess = vi.fn();
    const user = userEvent.setup();

    render(<JobApplicationModal job={job} onClose={() => {}} onSuccess={onSuccess} />);
    await waitFor(() => expect(screen.queryByText(/No resume found/i)).not.toBeInTheDocument());

    expect(screen.getByLabelText(/Education Level/i)).toHaveValue(expectedOption);

    await user.click(screen.getByRole('button', { name: /submit application/i }));

    await waitFor(() => expect(onSuccess).toHaveBeenCalled());
  });
});
