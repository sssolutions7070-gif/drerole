import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ApplicationsList from './ApplicationsList';
import api from '../services/api';

vi.mock('../services/api');

const mockJob = {
  id: 10,
  title: 'Full Stack Engineer',
  company: 'Acme Corp',
  recruiter: { id: 5 }
};

const mockApplications = [
  {
    id: 101,
    applicantName: 'Bob Candidate',
    applicantEmail: 'bob@example.com',
    status: 'APPLICATION_SUBMITTED',
    matchPercentage: 85,
    applicantSkills: JSON.stringify(['React', 'Node.js']),
    applicationDate: '2026-09-20T10:00:00',
    hasResume: false
  },
  {
    id: 102,
    applicantName: 'Charlie Candidate',
    applicantEmail: 'charlie@example.com',
    status: 'APPLICATION_SUBMITTED',
    matchPercentage: 75,
    applicantSkills: JSON.stringify(['Java', 'Spring Boot']),
    applicationDate: '2026-09-21T11:00:00',
    hasResume: false
  }
];

describe('ApplicationsList bulk actions', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.setItem('user', JSON.stringify({ id: 1, role: 'ROLE_RECRUITER', recruiterId: 5 }));
    window.confirm = vi.fn(() => true);
    api.get.mockImplementation((url) => {
      if (url.includes('/resume-matching/job/') || url.includes('/applications/job/')) {
        return Promise.resolve({ data: { applications: mockApplications } });
      }
      if (url.includes('/recruiters/user/')) {
        return Promise.resolve({ data: { recruiter: { id: 5 } } });
      }
      if (url.includes('/aptitude-tests/recruiter')) {
        return Promise.resolve({ data: [] });
      }
      if (url.includes('/dream-role-tests/recruiter')) {
        return Promise.resolve({ data: [] });
      }
      return Promise.resolve({ data: {} });
    });
    api.put.mockResolvedValue({ data: { success: true } });
  });

  it('shows a Compare button once 2-4 candidates are selected and renders a comparison table', async () => {
    const user = userEvent.setup();
    render(<ApplicationsList job={mockJob} onClose={vi.fn()} />);

    await screen.findByText('Bob Candidate');

    const bobCheckbox = screen.getByLabelText(/Select Bob Candidate/i);
    expect(screen.queryByRole('button', { name: /Compare \(/i })).not.toBeInTheDocument();
    await user.click(bobCheckbox);
    expect(screen.queryByRole('button', { name: /Compare \(/i })).not.toBeInTheDocument();

    const charlieCheckbox = screen.getByLabelText(/Select Charlie Candidate/i);
    await user.click(charlieCheckbox);

    const compareBtn = screen.getByRole('button', { name: /Compare \(2\)/i });
    await user.click(compareBtn);

    expect(screen.getByText(/Comparing 2 candidates/i)).toBeInTheDocument();
    expect(screen.getAllByText('Bob Candidate').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Charlie Candidate').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('85%').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('75%').length).toBeGreaterThanOrEqual(1);
  });

  it('exports the applicant list as a CSV file', async () => {
    const user = userEvent.setup();
    const clickSpy = vi.fn();
    const originalCreateElement = document.createElement.bind(document);
    vi.spyOn(document, 'createElement').mockImplementation((tag) => {
      const el = originalCreateElement(tag);
      if (tag === 'a') el.click = clickSpy;
      return el;
    });
    api.get.mockImplementation((url) => {
      if (url.includes('/applications/export')) {
        return Promise.resolve({ data: new Blob(['Name,Email\nBob,bob@example.com']) });
      }
      if (url.includes('/resume-matching/job/') || url.includes('/applications/job/')) {
        return Promise.resolve({ data: { applications: mockApplications } });
      }
      if (url.includes('/recruiters/user/')) {
        return Promise.resolve({ data: { recruiter: { id: 5 } } });
      }
      return Promise.resolve({ data: [] });
    });

    render(<ApplicationsList job={mockJob} onClose={vi.fn()} />);
    await screen.findByText('Bob Candidate');

    await user.click(screen.getByRole('button', { name: /Export CSV/i }));

    await waitFor(() => {
      expect(api.get).toHaveBeenCalledWith(
        expect.stringContaining('/recruiters/jobs/10/applications/export?recruiterId=5'),
        expect.objectContaining({ responseType: 'blob' })
      );
      expect(clickSpy).toHaveBeenCalled();
    });

    document.createElement.mockRestore();
  });

  it('renders application list with bulk action controls', async () => {
    render(<ApplicationsList job={mockJob} onClose={vi.fn()} />);

    expect(await screen.findByText('Bob Candidate')).toBeInTheDocument();
    expect(screen.getByText('Charlie Candidate')).toBeInTheDocument();
    expect(screen.getByText(/Select All \(2\)/i)).toBeInTheDocument();
  });

  it('toggles individual checkboxes and select-all', async () => {
    const user = userEvent.setup();
    render(<ApplicationsList job={mockJob} onClose={vi.fn()} />);

    await screen.findByText('Bob Candidate');

    const selectAllCheckbox = screen.getByLabelText(/Select all applications/i);
    const bobCheckbox = screen.getByLabelText(/Select Bob Candidate/i);
    const charlieCheckbox = screen.getByLabelText(/Select Charlie Candidate/i);

    expect(selectAllCheckbox).not.toBeChecked();
    expect(bobCheckbox).not.toBeChecked();

    // Select Bob
    await user.click(bobCheckbox);
    expect(bobCheckbox).toBeChecked();
    expect(screen.getByText(/1 selected/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Shortlist \(1\)/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Reject \(1\)/i })).toBeInTheDocument();

    // Select all
    await user.click(selectAllCheckbox);
    expect(bobCheckbox).toBeChecked();
    expect(charlieCheckbox).toBeChecked();
    expect(screen.getByText(/2 selected/i)).toBeInTheDocument();
  });

  it('executes bulk shortlist on selected applications', async () => {
    const user = userEvent.setup();
    render(<ApplicationsList job={mockJob} onClose={vi.fn()} />);

    await screen.findByText('Bob Candidate');

    const selectAllCheckbox = screen.getByLabelText(/Select all applications/i);
    await user.click(selectAllCheckbox);

    const shortlistBtn = screen.getByRole('button', { name: /Shortlist \(2\)/i });
    await user.click(shortlistBtn);

    expect(window.confirm).toHaveBeenCalledWith('Shortlist 2 selected applications?');
    await waitFor(() => {
      expect(api.put).toHaveBeenCalledWith(
        expect.stringContaining('/applications/101/shortlist'),
        expect.objectContaining({ notes: 'Bulk shortlisted' })
      );
      expect(api.put).toHaveBeenCalledWith(
        expect.stringContaining('/applications/102/shortlist'),
        expect.objectContaining({ notes: 'Bulk shortlisted' })
      );
    });
  });
});

describe('ApplicationsList Kanban Board View', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.setItem('user', JSON.stringify({ id: 1, role: 'ROLE_RECRUITER', recruiterId: 5 }));
    window.confirm = vi.fn(() => true);
    api.get.mockImplementation((url) => {
      if (url.includes('/resume-matching/job/') || url.includes('/applications/job/')) {
        return Promise.resolve({
          data: {
            applications: [
              ...mockApplications,
              {
                id: 103,
                applicantName: 'Dana Shortlisted',
                applicantEmail: 'dana@example.com',
                status: 'SHORTLISTED',
                matchPercentage: 92,
                applicantSkills: JSON.stringify(['Python', 'AWS']),
                applicationDate: '2026-09-22T09:00:00',
                hasResume: true,
              },
            ],
          },
        });
      }
      if (url.includes('/recruiters/user/')) {
        return Promise.resolve({ data: { recruiter: { id: 5 } } });
      }
      if (url.includes('/aptitude-tests/recruiter')) {
        return Promise.resolve({ data: [] });
      }
      if (url.includes('/dream-role-tests/recruiter')) {
        return Promise.resolve({ data: [] });
      }
      return Promise.resolve({ data: {} });
    });
    api.put.mockResolvedValue({ data: { success: true } });
  });

  it('switches between List View and Kanban Board View', async () => {
    const user = userEvent.setup();
    render(<ApplicationsList job={mockJob} onClose={vi.fn()} />);

    await screen.findByText('Bob Candidate');

    const kanbanTab = screen.getByRole('button', { name: /Kanban Board/i });
    await user.click(kanbanTab);

    // Columns should be visible
    expect(screen.getByTestId('kanban-column-applied')).toBeInTheDocument();
    expect(screen.getByTestId('kanban-column-shortlisted')).toBeInTheDocument();
    expect(screen.getByTestId('kanban-column-assessment')).toBeInTheDocument();
    expect(screen.getByTestId('kanban-column-interview')).toBeInTheDocument();
    expect(screen.getByTestId('kanban-column-offer')).toBeInTheDocument();
    expect(screen.getByTestId('kanban-column-rejected')).toBeInTheDocument();

    // Candidate cards should appear in their respective columns
    expect(screen.getByTestId('kanban-card-101')).toBeInTheDocument();
    expect(screen.getByTestId('kanban-card-103')).toBeInTheDocument();
  });

  it('filters candidates across Kanban columns using search', async () => {
    const user = userEvent.setup();
    render(<ApplicationsList job={mockJob} onClose={vi.fn()} />);

    await screen.findByText('Bob Candidate');
    await user.click(screen.getByRole('button', { name: /Kanban Board/i }));

    const searchInput = screen.getByPlaceholderText(/Search candidates or skills.../i);
    await user.type(searchInput, 'Dana');

    expect(screen.queryByTestId('kanban-card-101')).not.toBeInTheDocument();
    expect(screen.getByTestId('kanban-card-103')).toBeInTheDocument();
  });

  it('handles dropping a candidate card into Shortlisted column', async () => {
    const user = userEvent.setup();
    render(<ApplicationsList job={mockJob} onClose={vi.fn()} />);

    await screen.findByText('Bob Candidate');
    await user.click(screen.getByRole('button', { name: /Kanban Board/i }));

    const bobCard = screen.getByTestId('kanban-card-101');
    const shortlistedCol = screen.getByTestId('kanban-column-shortlisted');

    // Simulate drag start on Bob's card
    const dataTransfer = {
      setData: vi.fn(),
      getData: vi.fn(() => '101'),
    };
    fireEvent.dragStart(bobCard, { dataTransfer });

    // Simulate drag over and drop on Shortlisted column
    fireEvent.dragOver(shortlistedCol);
    fireEvent.drop(shortlistedCol);

    await waitFor(() => {
      expect(api.put).toHaveBeenCalledWith(
        expect.stringContaining('/applications/101/shortlist'),
        expect.objectContaining({ notes: 'Shortlisted via Kanban' })
      );
    });
  });

  describe('Blind / Bias-Free Screening Mode', () => {
    it('masks candidate names, emails, and contact info when Blind Screening is enabled', async () => {
      const user = userEvent.setup();
      render(<ApplicationsList job={mockJob} onClose={vi.fn()} />);

      await screen.findByText('Bob Candidate');
      expect(screen.getAllByText('bob@example.com').length).toBeGreaterThanOrEqual(1);

      const blindToggle = screen.getByRole('button', { name: /Enable Blind Screening/i });
      expect(blindToggle).toBeInTheDocument();
      await user.click(blindToggle);

      // Names should now be masked as Candidate #ID
      expect(await screen.findByText('Candidate #101')).toBeInTheDocument();
      expect(screen.getByText('Candidate #102')).toBeInTheDocument();
      expect(screen.queryByText('Bob Candidate')).not.toBeInTheDocument();
      expect(screen.queryByText('Charlie Candidate')).not.toBeInTheDocument();

      // Emails and contact info should be hidden
      expect(screen.queryByText('bob@example.com')).not.toBeInTheDocument();
      expect(screen.getAllByText(/hidden for blind screening/i).length).toBeGreaterThanOrEqual(1);

      // Active banner should be visible
      expect(screen.getByText(/Blind Screening Active:/i)).toBeInTheDocument();

      // Toggling off restores original names
      const turnOffBtn = screen.getByRole('button', { name: /turn off/i });
      await user.click(turnOffBtn);

      expect(await screen.findByText('Bob Candidate')).toBeInTheDocument();
      expect(screen.getAllByText('bob@example.com').length).toBeGreaterThanOrEqual(1);
      expect(screen.queryByText('Candidate #101')).not.toBeInTheDocument();
    });

    it('anonymizes candidate cards in Kanban Board view when Blind Screening is enabled', async () => {
      const user = userEvent.setup();
      render(<ApplicationsList job={mockJob} onClose={vi.fn()} />);

      await screen.findByText('Bob Candidate');

      // Enable Blind Screening
      await user.click(screen.getByRole('button', { name: /Enable Blind Screening/i }));
      expect(await screen.findByText('Candidate #101')).toBeInTheDocument();

      // Switch to Kanban View
      await user.click(screen.getByRole('button', { name: /Kanban Board/i }));

      // Kanban cards should render masked candidate IDs
      expect(screen.getByTestId('kanban-card-101')).toHaveTextContent('Candidate #101');
      expect(screen.getByTestId('kanban-card-101')).not.toHaveTextContent('Bob Candidate');
      expect(screen.getByTestId('kanban-card-101')).toHaveTextContent(/hidden for blind screening/i);
    });
  });
});


