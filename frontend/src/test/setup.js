import '@testing-library/jest-dom/vitest';

// jsdom doesn't implement scrollIntoView - stub it so components that call it
// (e.g. auto-scrolling a chat thread to the latest message) don't crash tests.
if (typeof Element !== 'undefined' && !Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {};
}

// jsdom doesn't implement the Blob URL registry - stub it so components that trigger a client-side
// file download (e.g. exporting a CSV) don't crash tests.
if (typeof window !== 'undefined' && window.URL && !window.URL.createObjectURL) {
  window.URL.createObjectURL = () => 'blob:mock-url';
  window.URL.revokeObjectURL = () => {};
}

// jsdom doesn't implement the Web Speech API - stub SpeechSynthesisUtterance so components that
// speak responses aloud (e.g. the voice assistant) don't crash tests that exercise that path.
// window.speechSynthesis itself is stubbed per-test (its mock needs vi.fn() spies), but the
// Utterance constructor has no reason to vary between tests, so it lives here once.
if (typeof window !== 'undefined' && !window.SpeechSynthesisUtterance) {
  window.SpeechSynthesisUtterance = class SpeechSynthesisUtterance {
    constructor(text) {
      this.text = text;
      this.lang = '';
      this.voice = null;
      this.rate = 1;
      this.onstart = null;
      this.onend = null;
      this.onerror = null;
    }
  };
}
