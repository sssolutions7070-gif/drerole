import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ResumeTailoringModal from './ResumeTailoringModal';
import api from '../services/api';

vi.mock('../services/api');

const mockJob = {
  id: 10,
  title: 'Full Stack Engineer',
  company: 'Acme Corp',
  description: 'Looking for a senior full stack developer with Docker and GraphQL expertise.',
  matchScore: 65,
  missingSkills: ['Docker', 'GraphQL']
};

const mockTailoringResponse = {
  data: {
    targetJob: 'Full Stack Engineer',
    currentScore: 65,
    projectedScore: 88,
    boostSummary: 'Your match for Full Stack Engineer is 65%. Adding concrete experience with Docker and GraphQL would boost your match to 88%.',
    missingSkillRecommendations: [
      {
        skill: 'Docker',
        priority: 'High Priority',
        impactReason: 'Crucial for containerization in CI/CD pipeline.',
        actionTip: 'Highlight real projects deploying multi-container services.'
      },
      {
        skill: 'GraphQL',
        priority: 'Medium Priority',
        impactReason: 'Used across core public APIs.',
        actionTip: 'Mention Apollo Client or GraphQL schema design.'
      }
    ],
    bulletPointSuggestions: [
      {
        category: 'Architecture & Delivery',
        enhancedBullet: 'Architected and deployed scalable containerized microservices using Docker, boosting uptime to 99.98%.',
        skillsHighlighted: ['Docker', 'Microservices'],
        metricImpact: '99.98% uptime'
      }
    ],
    keywordOptimization: ['Docker', 'GraphQL', 'Microservices', 'Kubernetes']
  }
};

describe('ResumeTailoringModal', () => {
  let writeTextMock;

  beforeEach(() => {
    vi.resetAllMocks();
    writeTextMock = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: {
        writeText: writeTextMock,
      },
      configurable: true,
    });
    api.post.mockResolvedValue(mockTailoringResponse);
  });

  it('renders modal, calls tailoring API, and displays boost projections', async () => {
    render(
      <ResumeTailoringModal
        job={mockJob}
        userSkills={['React', 'Node.js']}
        missingSkills={['Docker', 'GraphQL']}
        currentMatchScore={65}
        onClose={vi.fn()}
      />
    );

    expect(screen.getByText(/AI Resume Tailoring Copilot/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/resume-matching/tailor-resume', {
        jobTitle: 'Full Stack Engineer',
        jobDescription: mockJob.description,
        userSkills: ['React', 'Node.js'],
        missingSkills: ['Docker', 'GraphQL'],
        currentMatchScore: 65
      });
    });

    expect(await screen.findByText(/Potential Boost:/i)).toBeInTheDocument();
    expect(screen.getByText(/\+23% Match/i)).toBeInTheDocument();
    expect(screen.getByText(/Your match for Full Stack Engineer is 65%/i)).toBeInTheDocument();
    expect(screen.getAllByText('Docker').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('GraphQL').length).toBeGreaterThanOrEqual(1);
  });

  it('allows copying bullet points and keyword tags to clipboard', async () => {
    render(
      <ResumeTailoringModal
        job={mockJob}
        userSkills={['React', 'Node.js']}
        missingSkills={['Docker', 'GraphQL']}
        currentMatchScore={65}
        onClose={vi.fn()}
      />
    );

    await screen.findByText(/Potential Boost:/i);

    const copyBulletBtn = screen.getByRole('button', { name: /Copy Bullet/i });
    fireEvent.click(copyBulletBtn);

    expect(writeTextMock).toHaveBeenCalledWith(
      expect.stringContaining('Architected and deployed scalable containerized microservices using Docker')
    );
    expect(await screen.findByText(/Copied!/i)).toBeInTheDocument();
  });
});
