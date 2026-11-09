import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import RejectionExplanationModal from './RejectionExplanationModal';
import { applicationStatusAPI } from '../services/api';

vi.mock('../services/api');

describe('RejectionExplanationModal', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('fetches and shows the explanation for this application', async () => {
    applicationStatusAPI.getRejectionExplanation.mockResolvedValue({
      data: { explanation: 'You matched well but the role needed more Kubernetes experience.' },
    });

    render(<RejectionExplanationModal applicationId={42} onClose={() => {}} />);

    expect(await screen.findByText('You matched well but the role needed more Kubernetes experience.')).toBeInTheDocument();
    expect(applicationStatusAPI.getRejectionExplanation).toHaveBeenCalledWith(42);
  });

  it('shows an error message instead of a blank modal when the request fails', async () => {
    applicationStatusAPI.getRejectionExplanation.mockRejectedValue({
      response: { data: { error: "This application hasn't been rejected - there's nothing to explain yet" } },
    });

    render(<RejectionExplanationModal applicationId={42} onClose={() => {}} />);

    expect(await screen.findByText(/nothing to explain yet/i)).toBeInTheDocument();
  });

  it('calls onClose when the close button is clicked', async () => {
    applicationStatusAPI.getRejectionExplanation.mockResolvedValue({ data: { explanation: 'Some reason.' } });
    const onClose = vi.fn();
    const user = userEvent.setup();

    render(<RejectionExplanationModal applicationId={42} onClose={onClose} />);
    await screen.findByText('Some reason.');
    await user.click(screen.getAllByRole('button')[0]);

    expect(onClose).toHaveBeenCalled();
  });
});
