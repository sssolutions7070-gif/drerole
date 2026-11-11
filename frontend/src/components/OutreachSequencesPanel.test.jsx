import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import OutreachSequencesPanel from './OutreachSequencesPanel';
import { outreachSequenceAPI } from '../services/api';

vi.mock('../services/api');

describe('OutreachSequencesPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows an empty state when there are no sequences', async () => {
    outreachSequenceAPI.list.mockResolvedValue({ data: [] });

    render(<OutreachSequencesPanel recruiterId={100} />);

    expect(await screen.findByText(/no outreach sequences yet/i)).toBeInTheDocument();
  });

  it('lists a sequence with its status and step progress', async () => {
    outreachSequenceAPI.list.mockResolvedValue({
      data: [{ id: 1, candidateName: 'Alice Anderson', status: 'ACTIVE', currentStep: 1, totalSteps: 3 }],
    });

    render(<OutreachSequencesPanel recruiterId={100} />);

    expect(await screen.findByText('Alice Anderson')).toBeInTheDocument();
    expect(screen.getByText('Step 1 of 3')).toBeInTheDocument();
    expect(screen.getByText('ACTIVE')).toBeInTheDocument();
  });

  it('only shows the stop action for active sequences', async () => {
    outreachSequenceAPI.list.mockResolvedValue({
      data: [
        { id: 1, candidateName: 'Alice', status: 'ACTIVE', currentStep: 1, totalSteps: 2 },
        { id: 2, candidateName: 'Bob', status: 'COMPLETED', currentStep: 2, totalSteps: 2 },
      ],
    });

    render(<OutreachSequencesPanel recruiterId={100} />);

    await screen.findByText('Alice');
    expect(screen.getByLabelText('Stop sequence for Alice')).toBeInTheDocument();
    expect(screen.queryByLabelText('Stop sequence for Bob')).not.toBeInTheDocument();
  });

  it('stops a sequence', async () => {
    outreachSequenceAPI.list.mockResolvedValue({
      data: [{ id: 1, candidateName: 'Alice', status: 'ACTIVE', currentStep: 1, totalSteps: 2 }],
    });
    outreachSequenceAPI.stop.mockResolvedValue({});
    const user = userEvent.setup();

    render(<OutreachSequencesPanel recruiterId={100} />);
    await user.click(await screen.findByLabelText('Stop sequence for Alice'));

    expect(outreachSequenceAPI.stop).toHaveBeenCalledWith(100, 1);
    expect(await screen.findByText('STOPPED')).toBeInTheDocument();
  });

  it('shows an error when loading fails', async () => {
    outreachSequenceAPI.list.mockRejectedValue({ response: { data: { error: 'boom' } } });

    render(<OutreachSequencesPanel recruiterId={100} />);

    expect(await screen.findByText('boom')).toBeInTheDocument();
  });

  it('refetches when refreshKey changes', async () => {
    outreachSequenceAPI.list.mockResolvedValue({ data: [] });

    const { rerender } = render(<OutreachSequencesPanel recruiterId={100} refreshKey={0} />);
    await screen.findByText(/no outreach sequences yet/i);

    rerender(<OutreachSequencesPanel recruiterId={100} refreshKey={1} />);

    expect(outreachSequenceAPI.list).toHaveBeenCalledTimes(2);
  });
});
