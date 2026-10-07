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
