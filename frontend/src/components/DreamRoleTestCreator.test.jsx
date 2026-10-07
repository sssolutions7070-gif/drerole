import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import DreamRoleTestCreator from './DreamRoleTestCreator';
import api from '../services/api';

vi.mock('../services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('DreamRoleTestCreator', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    api.get.mockImplementation((url) => {
      if (url.includes('/branches/stats')) {
        return Promise.resolve({
          data: {
            branches: ['Computer Science & Engineering', 'Information Technology'],
            branchCounts: {
              'Computer Science & Engineering': 50,
              'Information Technology': 45
            }
          }
        });
      }
      if (url.includes('/recruiters/jobs/recruiter/')) {
        return Promise.resolve({
          data: [
            { id: 101, title: 'React Frontend Engineer', description: 'Build responsive apps', location: 'Remote', skills: 'React, TypeScript' }
          ]
        });
      }
      return Promise.resolve({ data: {} });
    });
  });

  it('renders Step 1 and loads branches and recruiter jobs', async () => {
    render(<DreamRoleTestCreator recruiterId="1" onClose={vi.fn()} onSuccess={vi.fn()} />);

    expect(await screen.findByText('Create DreamRole Test')).toBeInTheDocument();
    expect(screen.getByText(/Auto-Fill Test Details from Posted Job/i)).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: /auto-fill from posted job/i })).toBeInTheDocument();
  });

  it('auto-fills test info when a posted job is selected', async () => {
    const user = userEvent.setup();
    render(<DreamRoleTestCreator recruiterId="1" onClose={vi.fn()} onSuccess={vi.fn()} />);

    await screen.findByText('Create DreamRole Test');
    const jobSelect = screen.getByRole('combobox', { name: /auto-fill from posted job/i });
    await user.selectOptions(jobSelect, '101');

    const testNameInput = screen.getByPlaceholderText(/e\.g\., Software Developer DreamRole Test/i);
    expect(testNameInput.value).toBe('React Frontend Engineer Technical Assessment');
  });

  it('progresses to Step 2 and generates 10 questions with AI in 1 click', async () => {
    const user = userEvent.setup();
    api.post.mockImplementation((url) => {
      if (url.includes('/dream-role-tests/create')) {
        return Promise.resolve({ data: { test: { id: 501 } } });
      }
      if (url.includes('/generate-questions')) {
        return Promise.resolve({
          data: {
            questions: [
              {
                question: 'What is React JSX?',
                optionA: 'XML syntax extension',
                optionB: 'Database schema',
                optionC: 'CSS preprocessor',
                optionD: 'Network protocol',
                correctAnswer: 'A',
                explanation: 'JSX allows writing HTML-like syntax in JS.',
                difficultyLevel: 'Easy'
              }
            ]
          }
        });
      }
      return Promise.resolve({ data: {} });
    });

    // Mock window.alert
    vi.spyOn(window, 'alert').mockImplementation(() => {});

    render(<DreamRoleTestCreator recruiterId="1" onClose={vi.fn()} onSuccess={vi.fn()} />);

    await screen.findByText('Create DreamRole Test');
    const jobSelect = screen.getByRole('combobox', { name: /auto-fill from posted job/i });
    await user.selectOptions(jobSelect, '101');

    const createBtn = screen.getByRole('button', { name: /create test & continue/i });
    await user.click(createBtn);

    // Now in Step 2: verify AI generator banner
    expect(await screen.findByText('Claude AI Assessment Generator')).toBeInTheDocument();
    const generateAiBtn = screen.getByRole('button', { name: /generate 10 questions with ai/i });
    expect(generateAiBtn).toBeInTheDocument();

    await user.click(generateAiBtn);

    // Verify AI preview modal appears with generated question
    expect(await screen.findByText(/Generated Questions/i)).toBeInTheDocument();
    expect(screen.getByText(/What is React JSX\?/i)).toBeInTheDocument();

    // Click Apply All
    const applyBtn = screen.getByRole('button', { name: /apply ai questions/i });
    await user.click(applyBtn);

    // Verified questions added to list
    expect(await screen.findByText(/What is React JSX\?/i)).toBeInTheDocument();
    expect(screen.getByText(/Successfully applied 10 AI-generated technical questions!/i)).toBeInTheDocument();
  });
});
