import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import JobPostForm from './JobPostForm';
import api from '../services/api';

vi.mock('../services/api');

const fillRequiredFields = async (user) => {
  await user.type(screen.getByLabelText(/Job Title/i), 'Backend Engineer');
  await user.type(screen.getByLabelText(/^Company/i), 'Acme Corp');
  await user.type(screen.getByLabelText(/Job Description/i), 'Build APIs');
  // requiredSkills carries an HTML5 `required` attribute even though the component's own
  // handleSubmit doesn't check it - leaving it blank silently blocks native form submission
  // before React's onSubmit ever runs.
  // userEvent's keyboard syntax treats [ (but not ]) as a special opener - double it to type a
  // literal [; ] needs no escaping.
  await user.type(screen.getByLabelText(/Required Skills/i), '[["Java"]');
  await user.type(screen.getByLabelText(/^Location/i), 'Remote');
  await user.selectOptions(screen.getByLabelText(/Job Type/i), 'Full-time');
  await user.selectOptions(screen.getByLabelText(/Experience Level/i), 'Mid-level');
};

describe('JobPostForm screening questions', () => {
  it('starts with no screening questions and lets a recruiter add one', async () => {
    const user = userEvent.setup();
    render(<JobPostForm job={null} onSubmit={vi.fn()} onCancel={vi.fn()} />);

    expect(screen.getByText(/No screening questions yet/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Add question/i }));

    expect(screen.queryByText(/No screening questions yet/i)).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText(/Are you authorized to work/i)).toBeInTheDocument();
  });

  it('submits the filled-in questions as a real array, dropping blank rows', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(<JobPostForm job={null} onSubmit={onSubmit} onCancel={vi.fn()} />);
    await fillRequiredFields(user);

    await user.click(screen.getByRole('button', { name: /Add question/i }));
    await user.type(screen.getByPlaceholderText(/Are you authorized to work/i), 'Authorized to work?');
    await user.selectOptions(screen.getByDisplayValue('Yes'), 'no');
    await user.click(screen.getByLabelText(/Auto-reject if answered incorrectly/i));

    // A second, never-filled-in row should be dropped on submit.
    await user.click(screen.getByRole('button', { name: /Add question/i }));

    await user.click(screen.getByRole('button', { name: /Post Job/i }));

    expect(onSubmit).toHaveBeenCalledTimes(1);
    const submitted = onSubmit.mock.calls[0][0];
    expect(submitted.screeningQuestions).toEqual([
      { questionText: 'Authorized to work?', expectedAnswer: false, knockout: true },
    ]);
  });

  it('removes a question row', async () => {
    const user = userEvent.setup();
    render(<JobPostForm job={null} onSubmit={vi.fn()} onCancel={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: /Add question/i }));
    expect(screen.getByPlaceholderText(/Are you authorized to work/i)).toBeInTheDocument();

    await user.click(screen.getByLabelText(/Remove screening question 1/i));

    expect(screen.getByText(/No screening questions yet/i)).toBeInTheDocument();
  });

  it('pre-populates questions when editing a job that already has them', () => {
    const job = {
      title: 'Backend Engineer',
      company: 'Acme',
      description: 'Build APIs',
      location: 'Remote',
      jobType: 'Full-time',
      experienceLevel: 'Mid-level',
      screeningQuestions: JSON.stringify([
        { questionText: 'Authorized to work?', expectedAnswer: true, knockout: true },
      ]),
    };
    render(<JobPostForm job={job} onSubmit={vi.fn()} onCancel={vi.fn()} />);

    expect(screen.getByDisplayValue('Authorized to work?')).toBeInTheDocument();
  });
});

describe('JobPostForm AI-suggested screening questions', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('asks for a title and description before suggesting', async () => {
    const user = userEvent.setup();
    render(<JobPostForm job={null} onSubmit={vi.fn()} onCancel={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: /Suggest with AI/i }));

    expect(await screen.findByText(/add a title and description first/i)).toBeInTheDocument();
    expect(api.post).not.toHaveBeenCalled();
  });

  it('appends AI-suggested questions to the list', async () => {
    api.post.mockResolvedValue({
      data: {
        screeningQuestions: [
          { questionText: 'Are you authorized to work in this country?', expectedAnswer: true, knockout: true },
        ],
      },
    });
    const user = userEvent.setup();
    render(<JobPostForm job={null} onSubmit={vi.fn()} onCancel={vi.fn()} />);

    await user.type(screen.getByLabelText(/Job Title/i), 'Backend Engineer');
    await user.type(screen.getByLabelText(/Job Description/i), 'Build APIs');
    await user.click(screen.getByRole('button', { name: /Suggest with AI/i }));

    expect(api.post).toHaveBeenCalledWith('/recruiters/jobs/suggest-screening-questions', expect.objectContaining({
      title: 'Backend Engineer',
      description: 'Build APIs',
    }));
    expect(await screen.findByDisplayValue('Are you authorized to work in this country?')).toBeInTheDocument();
  });

  it('shows an error when the suggestion request fails', async () => {
    api.post.mockRejectedValue({ response: { data: { error: 'AI service unavailable' } } });
    const user = userEvent.setup();
    render(<JobPostForm job={null} onSubmit={vi.fn()} onCancel={vi.fn()} />);

    await user.type(screen.getByLabelText(/Job Title/i), 'Backend Engineer');
    await user.type(screen.getByLabelText(/Job Description/i), 'Build APIs');
    await user.click(screen.getByRole('button', { name: /Suggest with AI/i }));

    expect(await screen.findByText('AI service unavailable')).toBeInTheDocument();
  });
});
