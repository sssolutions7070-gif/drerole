import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import DiversityInfoPanel from './DiversityInfoPanel';
import api from '../services/api';

vi.mock('../services/api');

describe('DiversityInfoPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('pre-fills the form from the existing answers', async () => {
    api.get.mockResolvedValue({
      data: { genderIdentity: 'Woman', ethnicity: '', veteranStatus: '', disabilityStatus: '' },
    });

    render(<DiversityInfoPanel userId={1} />);

    expect(await screen.findByDisplayValue('Woman')).toBeInTheDocument();
  });

  it('leaves the form blank when the request fails, without crashing', async () => {
    api.get.mockRejectedValue(new Error('network error'));

    render(<DiversityInfoPanel userId={1} />);

    expect(await screen.findByText('Diversity & Inclusion')).toBeInTheDocument();
  });

  it('saves the selected answers', async () => {
    api.get.mockResolvedValue({ data: {} });
    api.put.mockResolvedValue({ data: {} });
    const user = userEvent.setup();

    render(<DiversityInfoPanel userId={1} />);
    await screen.findByText('Diversity & Inclusion');

    await user.selectOptions(screen.getByLabelText('Gender Identity'), 'Non-binary');
    await user.click(screen.getByRole('button', { name: /save/i }));

    expect(api.put).toHaveBeenCalledWith('/users/1/diversity-info', expect.objectContaining({
      genderIdentity: 'Non-binary',
    }));
    expect(await screen.findByText(/thank you for sharing/i)).toBeInTheDocument();
  });

  it('allows leaving every question blank', async () => {
    api.get.mockResolvedValue({ data: {} });
    api.put.mockResolvedValue({ data: {} });
    const user = userEvent.setup();

    render(<DiversityInfoPanel userId={1} />);
    await user.click(await screen.findByRole('button', { name: /save/i }));

    expect(api.put).toHaveBeenCalledWith('/users/1/diversity-info', {
      genderIdentity: '', ethnicity: '', veteranStatus: '', disabilityStatus: '',
    });
  });

  it('shows an error when saving fails', async () => {
    api.get.mockResolvedValue({ data: {} });
    api.put.mockRejectedValue({ response: { data: { error: 'boom' } } });
    const user = userEvent.setup();

    render(<DiversityInfoPanel userId={1} />);
    await user.click(await screen.findByRole('button', { name: /save/i }));

    expect(await screen.findByText('boom')).toBeInTheDocument();
  });
});
