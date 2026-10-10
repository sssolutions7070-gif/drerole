import { describe, it, expect, vi, afterEach } from 'vitest';
import { executeCode, CODE_STARTERS } from './codeRunner';

// Python/Java/C++/SQL execute for real against wandbox.org's free compile API rather than being
// simulated - mocking fetch here keeps these tests fast, offline and deterministic while still
// proving the request is built correctly and the response is parsed correctly, exactly the way
// an integration test for a third-party API call should (never depend on a live network call
// from a unit test). The exact response shape mocked below was verified against the live API.
const wandboxResponse = (overrides = {}) => ({
  status: '0',
  signal: '',
  compiler_output: '',
  compiler_error: '',
  compiler_message: '',
  program_output: '',
  program_error: '',
  program_message: '',
  permlink: '',
  url: '',
  ...overrides,
});

const mockFetchOnce = (body, ok = true, status = 200) => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok,
    status,
    json: () => Promise.resolve(body),
  }));
};

describe('codeRunner', () => {
  it('has starter templates for all supported languages', () => {
    expect(CODE_STARTERS.JavaScript).toBeDefined();
    expect(CODE_STARTERS.Python).toBeDefined();
    expect(CODE_STARTERS.Java).toBeDefined();
    expect(CODE_STARTERS['C++']).toBeDefined();
    expect(CODE_STARTERS.SQL).toBeDefined();
  });

  it('handles empty code cleanly', async () => {
    const res = await executeCode('', 'JavaScript');
    expect(res.success).toBe(true);
    expect(res.status).toBe('EMPTY');
  });

  it('executes JavaScript console.log correctly', async () => {
    const code = `
      console.log("Hello from test");
      console.log("Second line", 42);
    `;
    const res = await executeCode(code, 'JavaScript');
    expect(res.success).toBe(true);
    expect(res.output).toHaveLength(2);
    expect(res.output[0].text).toBe('Hello from test');
    expect(res.output[1].text).toBe('Second line 42');
  });

  it('captures JavaScript runtime errors cleanly in stderr', async () => {
    const code = `
      const obj = null;
      obj.doSomething();
    `;
    const res = await executeCode(code, 'JavaScript');
    expect(res.success).toBe(false);
    expect(res.status).toBe('ERROR');
    expect(res.output.some(o => o.type === 'stderr')).toBe(true);
  });

  it('supports custom input (stdin) in JavaScript', async () => {
    const code = `
      console.log("Received:", input);
    `;
    const res = await executeCode(code, 'JavaScript', 'CustomData123');
    expect(res.success).toBe(true);
    expect(res.output[0].text).toContain('CustomData123');
  });

  it('handles asynchronous JavaScript code', async () => {
    const code = `
      return new Promise((resolve) => {
        console.log("Async step");
        resolve("Async result");
      });
    `;
    const res = await executeCode(code, 'JavaScript');
    expect(res.success).toBe(true);
    expect(res.output[0].text).toBe('Async step');
  });

  describe('Python/Java/C++/SQL execute for real via the Wandbox API (fetch mocked)', () => {
    afterEach(() => {
      vi.unstubAllGlobals();
    });

    it('runs real Python output back from the response, not a regex guess', async () => {
      mockFetchOnce(wandboxResponse({ program_output: 'Hello Python\nLine 2\n' }));

      const res = await executeCode('print("Hello Python")\nprint("Line 2")', 'Python');

      expect(res.success).toBe(true);
      expect(res.status).toBe('SUCCESS');
      expect(res.output.some(o => o.type === 'stdout' && o.text === 'Hello Python\nLine 2')).toBe(true);
      expect(fetch).toHaveBeenCalledWith('https://wandbox.org/api/compile.json', expect.objectContaining({
        method: 'POST',
      }));
      const sentBody = JSON.parse(fetch.mock.calls[0][1].body);
      expect(sentBody.compiler).toBe('cpython-3.12.7');
    });

    it('strips "public" from the top-level Java class before sending, so Wandbox\'s fixed filename doesn\'t reject it', async () => {
      mockFetchOnce(wandboxResponse({ program_output: 'Hello Java\n' }));

      const res = await executeCode(
        'public class Main { public static void main(String[] args) { System.out.println("Hello Java"); } }',
        'Java'
      );

      expect(res.success).toBe(true);
      expect(res.output.some(o => o.text === 'Hello Java')).toBe(true);
      const sentBody = JSON.parse(fetch.mock.calls[0][1].body);
      expect(sentBody.code).not.toMatch(/\bpublic\s+class\s+Main\b/);
      expect(sentBody.code).toMatch(/\bclass\s+Main\b/);
    });

    it('reports a real Java compile error instead of always succeeding', async () => {
      mockFetchOnce(wandboxResponse({
        status: '1',
        compiler_error: 'prog.java:1: error: reached end of file while parsing\n1 error\n',
      }));

      const res = await executeCode('public class Main {', 'Java');

      expect(res.success).toBe(false);
      expect(res.status).toBe('COMPILE_ERROR');
      expect(res.output.some(o => o.type === 'stderr' && o.text.includes('reached end of file'))).toBe(true);
    });

    it('runs real C++ output back from the response', async () => {
      mockFetchOnce(wandboxResponse({ program_output: 'Hello C++\n' }));

      const res = await executeCode('#include <iostream>\nint main() { std::cout << "Hello C++"; return 0; }', 'C++');

      expect(res.success).toBe(true);
      expect(res.output.some(o => o.text === 'Hello C++')).toBe(true);
      const sentBody = JSON.parse(fetch.mock.calls[0][1].body);
      expect(sentBody.compiler).toBe('gcc-13.2.0');
    });

    it('runs real SQL against an actual SQLite engine and returns real rows, not a fixed fixture row', async () => {
      mockFetchOnce(wandboxResponse({ program_output: '1|Alice\n2|Bob\n' }));

      const res = await executeCode(
        "CREATE TABLE users (id INT, name TEXT); INSERT INTO users VALUES (1,'Alice'); INSERT INTO users VALUES (2,'Bob'); SELECT * FROM users;",
        'SQL'
      );

      expect(res.success).toBe(true);
      expect(res.output.some(o => o.text === '1|Alice\n2|Bob')).toBe(true);
      // Never the old hardcoded fixture row that showed up regardless of what was queried.
      expect(res.output.some(o => o.text.includes('Alice Chen'))).toBe(false);
    });

    it('reports a runtime error (e.g. a Python traceback) distinctly from a compile error', async () => {
      mockFetchOnce(wandboxResponse({
        status: '1',
        program_error: 'Traceback (most recent call last):\n  File "prog.py", line 1\nZeroDivisionError: division by zero\n',
      }));

      const res = await executeCode('print(1/0)', 'Python');

      expect(res.success).toBe(false);
      expect(res.status).toBe('ERROR');
      expect(res.output.some(o => o.type === 'stderr' && o.text.includes('ZeroDivisionError'))).toBe(true);
    });

    it('surfaces a clear message when the execution service cannot be reached, instead of silently faking success', async () => {
      vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network down')));

      const res = await executeCode('print("hi")', 'Python');

      expect(res.success).toBe(false);
      expect(res.status).toBe('ERROR');
      expect(res.output[0].text).toContain('Could not reach the code execution service');
    });

    it('passes custom stdin through to the execution request', async () => {
      mockFetchOnce(wandboxResponse({ program_output: 'Hello, World\n' }));

      await executeCode('name = input()\nprint("Hello, " + name)', 'Python', 'World');

      const sentBody = JSON.parse(fetch.mock.calls[0][1].body);
      expect(sentBody.stdin).toBe('World');
    });
  });
});
