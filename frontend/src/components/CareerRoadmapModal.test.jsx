import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CareerRoadmapModal from './CareerRoadmapModal';
import api from '../services/api';

vi.mock('../services/api');

const mockJob = {
  id: 20,
  title: 'Cloud DevOps Engineer',
  company: 'CloudScale Inc',
  description: 'Deploy Kubernetes clusters and CI/CD pipelines.',
  matchScore: 60,
  missingSkills: ['Kubernetes', 'Terraform']
};

const mockRoadmapResponse = {
  data: {
    targetJob: 'Cloud DevOps Engineer',
    currentScore: 60,
    projectedScore: 88,
    estimatedWeeks: 4,
    roadmapSummary: 'A 4-week structured curriculum to bridge skills in Kubernetes and Terraform.',
    milestones: [
      {
        week: 1,
        title: 'Core Foundations & Tooling',
        description: 'Master Kubernetes primitives and container architecture.',
        skills: ['Kubernetes'],
        tasks: [
          'Install Minikube and kubectl CLI locally',
          'Deploy single-pod and multi-container workloads',
          'Configure ConfigMaps and Secrets'
        ],
        resources: [
          { name: 'Kubernetes Documentation', url: 'https://kubernetes.io/docs/', type: 'Documentation' }
        ]
      },
      {
        week: 2,
        title: 'Infrastructure as Code with Terraform',
        description: 'Provision cloud resources declaratively.',
        skills: ['Terraform'],
        tasks: [
          'Define VPC and compute instances in HCL',
          'Manage remote state with S3 backend'
        ],
        resources: [
          { name: 'Terraform Tutorials', url: 'https://developer.hashicorp.com/terraform', type: 'Tutorial' }
        ]
      }
    ],
    capstoneProject: {
      title: 'Automated Kubernetes Deployment Pipeline',
      description: 'End-to-end GitOps pipeline provisioning infrastructure and apps.',
      deliverables: ['GitHub Repository', 'Live Cluster URL', 'Architecture Spec']
    }
  }
};

describe('CareerRoadmapModal', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.clear();
  });

  it('renders loading state initially and then shows roadmap details', async () => {
    api.post.mockResolvedValueOnce(mockRoadmapResponse);

    render(
      <CareerRoadmapModal
        job={mockJob}
        userSkills={['Linux', 'Docker']}
        missingSkills={['Kubernetes', 'Terraform']}
        currentMatchScore={60}
        onClose={vi.fn()}
      />
    );

    expect(screen.getByText(/Generating Personalized 4-Week Curriculum/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText(/Candidate AI Career Roadmap/i)).toBeInTheDocument();
      expect(screen.getByText('Cloud DevOps Engineer')).toBeInTheDocument();
      expect(screen.getByText(/Core Foundations & Tooling/i)).toBeInTheDocument();
    });

    expect(api.post).toHaveBeenCalledWith('/resume-matching/career-roadmap', {
      jobTitle: 'Cloud DevOps Engineer',
      jobDescription: 'Deploy Kubernetes clusters and CI/CD pipelines.',
      userSkills: ['Linux', 'Docker'],
      missingSkills: ['Kubernetes', 'Terraform'],
      currentMatchScore: 60
    });
  });

  it('allows toggling task completion and persists to localStorage', async () => {
    api.post.mockResolvedValueOnce(mockRoadmapResponse);

    render(
      <CareerRoadmapModal
        job={mockJob}
        currentMatchScore={60}
        onClose={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByText(/Install Minikube and kubectl CLI locally/i)).toBeInTheDocument();
    });

    const taskText = screen.getByText(/Install Minikube and kubectl CLI locally/i);
    fireEvent.click(taskText);

    // Should update progress
    await waitFor(() => {
      expect(screen.getByText(/1 of 5 tasks/i)).toBeInTheDocument();
    });

    // Check localStorage
    const saved = JSON.parse(localStorage.getItem('dreamrole_roadmap_tasks_20'));
    expect(saved['w1_t0']).toBe(true);
  });

  it('switches between weekly milestone tabs', async () => {
    api.post.mockResolvedValueOnce(mockRoadmapResponse);

    render(
      <CareerRoadmapModal
        job={mockJob}
        currentMatchScore={60}
        onClose={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByText(/Week 1 Milestone/i)).toBeInTheDocument();
    });

    const week2Button = screen.getByText('Week 2');
    fireEvent.click(week2Button);

    await waitFor(() => {
      expect(screen.getByText(/Week 2 Milestone/i)).toBeInTheDocument();
      expect(screen.getByText(/Infrastructure as Code with Terraform/i)).toBeInTheDocument();
    });
  });

  it('renders capstone project details and handles close', async () => {
    const handleClose = vi.fn();
    api.post.mockResolvedValueOnce(mockRoadmapResponse);

    render(
      <CareerRoadmapModal
        job={mockJob}
        currentMatchScore={60}
        onClose={handleClose}
      />
    );

    await waitFor(() => {
      expect(screen.getByText(/Automated Kubernetes Deployment Pipeline/i)).toBeInTheDocument();
      expect(screen.getByText('GitHub Repository')).toBeInTheDocument();
    });

    const doneButton = screen.getByRole('button', { name: /Done/i });
    fireEvent.click(doneButton);

    expect(handleClose).toHaveBeenCalled();
  });
});
