import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import VideoIntroPanel from './VideoIntroPanel';
import api from '../services/api';

vi.mock('../services/api');

const makeFile = (name, type, sizeBytes) => {
  const file = new File(['x'], name, { type });
  Object.defineProperty(file, 'size', { value: sizeBytes });
  return file;
};

describe('VideoIntroPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows an upload prompt when no video exists yet', () => {
    render(<VideoIntroPanel userId={1} videoIntroUrl={null} onChange={vi.fn()} />);

    expect(screen.getByText(/click to upload a video/i)).toBeInTheDocument();
    expect(screen.queryByTestId('video-intro-player')).not.toBeInTheDocument();
  });

  it('shows the video player and Replace/Remove actions once a video exists', () => {
    render(<VideoIntroPanel userId={1} videoIntroUrl="/uploads/video-intros/1_abc.mp4" onChange={vi.fn()} />);

    expect(screen.getByTestId('video-intro-player')).toBeInTheDocument();
    expect(screen.getByText('Replace')).toBeInTheDocument();
    expect(screen.getByText('Remove')).toBeInTheDocument();
  });

  it('uploads a valid video and reports the new URL', async () => {
    api.post.mockResolvedValue({ data: { videoIntroUrl: '/uploads/video-intros/1_new.mp4' } });
    const onChange = vi.fn();
    const user = userEvent.setup();

    render(<VideoIntroPanel userId={1} videoIntroUrl={null} onChange={onChange} />);
    const input = document.querySelector('input[type="file"]');
    const file = makeFile('intro.mp4', 'video/mp4', 1024);
    await user.upload(input, file);

    expect(api.post).toHaveBeenCalledWith('/users/1/video-intro', expect.any(FormData), expect.any(Object));
    await waitFor(() => expect(onChange).toHaveBeenCalledWith('/uploads/video-intros/1_new.mp4'));
  });

  it('rejects an oversized video without calling the API', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();

    render(<VideoIntroPanel userId={1} videoIntroUrl={null} onChange={onChange} />);
    const input = document.querySelector('input[type="file"]');
    const file = makeFile('intro.mp4', 'video/mp4', 60 * 1024 * 1024);
    await user.upload(input, file);

    expect(await screen.findByText(/under 50MB/i)).toBeInTheDocument();
    expect(api.post).not.toHaveBeenCalled();
    expect(onChange).not.toHaveBeenCalled();
  });

  it('rejects an unsupported file type without calling the API', async () => {
    const user = userEvent.setup();

    render(<VideoIntroPanel userId={1} videoIntroUrl={null} onChange={vi.fn()} />);
    const input = document.querySelector('input[type="file"]');
    const file = makeFile('intro.exe', 'application/octet-stream', 1024);
    await user.upload(input, file);

    expect(await screen.findByText(/mp4, webm, or mov/i)).toBeInTheDocument();
    expect(api.post).not.toHaveBeenCalled();
  });

  it('removes the video when Remove is clicked', async () => {
    api.delete.mockResolvedValue({ data: {} });
    const onChange = vi.fn();
    const user = userEvent.setup();

    render(<VideoIntroPanel userId={1} videoIntroUrl="/uploads/video-intros/1_abc.mp4" onChange={onChange} />);
    await user.click(screen.getByText('Remove'));

    expect(api.delete).toHaveBeenCalledWith('/users/1/video-intro');
    expect(onChange).toHaveBeenCalledWith(null);
  });

  it('shows an error if removal fails', async () => {
    api.delete.mockRejectedValue({ response: { data: { error: 'Could not remove video' } } });
    const user = userEvent.setup();

    render(<VideoIntroPanel userId={1} videoIntroUrl="/uploads/video-intros/1_abc.mp4" onChange={vi.fn()} />);
    await user.click(screen.getByText('Remove'));

    expect(await screen.findByText('Could not remove video')).toBeInTheDocument();
  });
});
