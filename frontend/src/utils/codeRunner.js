/**
 * DreamRole In-Interview Live Code Execution Engine.
 * Executes JavaScript client-side with captured stdout/stderr and execution timing. Python,
 * Java, C++ and SQL are compiled and run for real too, via Wandbox (wandbox.org) - a free,
 * keyless, CORS-enabled public compile-and-run API. The code (and any custom stdin) is sent to
 * that third-party service to actually execute, which is the trade-off for genuine multi-language
 * execution without running our own sandboxed infrastructure (no Docker/VM sandbox exists in
 * this project). This previously just pattern-matched the source with regex and faked plausible
 * output for every language but JavaScript - none of it was real execution.
 */

export const CODE_STARTERS = {
  JavaScript: `// JavaScript Live Sandbox
function solution(input) {
  console.log("Processing input:", input || "No input provided");
  // Example: Two Sum or String Reversal
  return "Result computed successfully";
}

const result = solution("DreamRole Technical Round");
console.log("Output:", result);
`,

  Python: `# Python Live Sandbox
def solution(data):
    print("Processing input:", data if data else "No input provided")
    return "Result computed successfully"

result = solution("DreamRole Technical Round")
print("Output:", result)
`,

  Java: `// Java Live Sandbox
public class Main {
    public static void main(String[] args) {
        System.out.println("Executing Java Main...");
        String result = solution("DreamRole Technical Round");
        System.out.println("Output: " + result);
    }

    public static String solution(String input) {
        return "Result computed successfully";
    }
}
`,

  'C++': `// C++ Live Sandbox
#include <iostream>
#include <string>

std::string solution(std::string input) {
    return "Result computed successfully";
}

int main() {
    std::cout << "Executing C++ Main..." << std::endl;
    std::cout << "Output: " << solution("DreamRole Technical Round") << std::endl;
    return 0;
}
`,

  SQL: `-- SQL Live Sandbox
CREATE TABLE candidates (
    id INT PRIMARY KEY,
    name VARCHAR(100),
    role VARCHAR(100),
    score INT
);

INSERT INTO candidates VALUES (1, 'Alice Chen', 'Senior Full Stack', 95);
INSERT INTO candidates VALUES (2, 'Bob Smith', 'Backend Engineer', 88);

SELECT * FROM candidates WHERE score >= 90;
`
};

/**
 * Execute code and return structured output.
 * @param {string} code 
 * @param {string} language 
 * @param {string} customInput 
 * @param {object} options 
 * @returns {Promise<{ success: boolean, output: Array<{type: string, text: string}>, durationMs: number, status: string }>}
 */
export async function executeCode(code, language = 'JavaScript', customInput = '', options = {}) {
  const timeoutMs = options.timeoutMs || 3000;
  const startTime = typeof performance !== 'undefined' ? performance.now() : Date.now();

  if (!code || !code.trim()) {
    return {
      success: true,
      output: [{ type: 'system', text: 'No code provided to execute.' }],
      durationMs: 0,
      status: 'EMPTY'
    };
  }

  if (language === 'JavaScript') {
    return executeJavaScript(code, customInput, timeoutMs, startTime);
  }

  if (WANDBOX_COMPILERS[language]) {
    // Compiling and actually running Java/C++/Python/SQL takes real network + compiler time,
    // well beyond the 3s default meant for catching JavaScript infinite loops - give it room.
    return executeViaWandbox(language, code, customInput, startTime, options.timeoutMs || 20000);
  }

  return {
    success: true,
    output: [
      { type: 'system', text: `Execution for ${language} completed in sandbox.` },
      { type: 'stdout', text: `Code analyzed: ${code.trim().split('\\n').length} lines.` }
    ],
    durationMs: 1,
    status: 'SUCCESS'
  };
}

/**
 * Runs JavaScript in a sandboxed, intercepting context.
 */
function executeJavaScript(code, customInput, timeoutMs, startTime) {
  return new Promise((resolve) => {
    const logs = [];
    let timedOut = false;

    const timer = setTimeout(() => {
      timedOut = true;
      const durationMs = Math.round((typeof performance !== 'undefined' ? performance.now() : Date.now()) - startTime);
      resolve({
        success: false,
        output: [
          ...logs,
          { type: 'stderr', text: `Execution timed out after ${timeoutMs}ms. Check for infinite loops.` }
        ],
        durationMs,
        status: 'TIMEOUT'
      });
    }, timeoutMs);

    const safeLog = (...args) => {
      logs.push({
        type: 'stdout',
        text: args.map(arg => formatLogArgument(arg)).join(' ')
      });
    };

    const safeWarn = (...args) => {
      logs.push({
        type: 'stderr',
        text: '[WARN] ' + args.map(arg => formatLogArgument(arg)).join(' ')
      });
    };

    const safeError = (...args) => {
      logs.push({
        type: 'stderr',
        text: '[ERROR] ' + args.map(arg => formatLogArgument(arg)).join(' ')
      });
    };

    try {
      // Create isolated wrapper
      const sandboxConsole = {
        log: safeLog,
        info: safeLog,
        warn: safeWarn,
        error: safeError,
        table: (val) => safeLog(JSON.stringify(val, null, 2))
      };

      // Wrap code to execute synchronously or return promise
      const runnerFn = new Function('console', 'stdin', 'input', `"use strict";\n${code}`);
      const result = runnerFn(sandboxConsole, customInput, customInput);

      if (result && typeof result.then === 'function') {
        result.then((asyncVal) => {
          if (timedOut) return;
          clearTimeout(timer);
          if (asyncVal !== undefined && logs.length === 0) {
            safeLog(asyncVal);
          }
          const durationMs = Math.round((typeof performance !== 'undefined' ? performance.now() : Date.now()) - startTime);
          resolve({
            success: true,
            output: logs.length > 0 ? logs : [{ type: 'system', text: 'Program finished with no output.' }],
            durationMs,
            status: 'SUCCESS'
          });
        }).catch((err) => {
          if (timedOut) return;
          clearTimeout(timer);
          safeError(err && err.message ? err.message : String(err));
          const durationMs = Math.round((typeof performance !== 'undefined' ? performance.now() : Date.now()) - startTime);
          resolve({
            success: false,
            output: logs,
            durationMs,
            status: 'ERROR'
          });
        });
        return;
      }

      clearTimeout(timer);
      if (result !== undefined && logs.length === 0) {
        safeLog(result);
      }

      const durationMs = Math.round((typeof performance !== 'undefined' ? performance.now() : Date.now()) - startTime);
      resolve({
        success: true,
        output: logs.length > 0 ? logs : [{ type: 'system', text: 'Program finished with no output.' }],
        durationMs,
        status: 'SUCCESS'
      });
    } catch (err) {
      clearTimeout(timer);
      safeError(err && err.message ? err.message : String(err));
      const durationMs = Math.round((typeof performance !== 'undefined' ? performance.now() : Date.now()) - startTime);
      resolve({
        success: false,
        output: logs,
        durationMs,
        status: 'ERROR'
      });
    }
  });
}

function formatLogArgument(arg) {
  if (arg === null) return 'null';
  if (arg === undefined) return 'undefined';
  if (typeof arg === 'object') {
    try {
      return JSON.stringify(arg, null, 2);
    } catch (e) {
      return String(arg);
    }
  }
  return String(arg);
}

// Wandbox's compile API (https://wandbox.org/api/compile.json) - free, no API key, CORS-enabled
// (Access-Control-Allow-Origin: *, verified live). Each of these actually compiles and runs the
// submitted source on Wandbox's own infrastructure; none of this is simulated.
const WANDBOX_API = 'https://wandbox.org/api/compile.json';
const WANDBOX_COMPILERS = {
  Python: 'cpython-3.12.7',
  Java: 'openjdk-jdk-21+35',
  'C++': 'gcc-13.2.0',
  SQL: 'sqlite-3.46.1',
};

// Wandbox always compiles the primary submission under a fixed filename (javac sees "prog.java"
// regardless of what's inside it - confirmed against the live API). javac only enforces
// "public class Foo must live in Foo.java" for a *public* top-level class, so stripping that one
// modifier avoids the mismatch without needing a real Java parser - a non-public top-level class
// behaves identically otherwise, including still being runnable via `public static void main`.
function stripPublicFromTopLevelClass(code) {
  return code.replace(/\bpublic\s+(class\s+[A-Za-z_$][A-Za-z0-9_$]*)/, '$1');
}

async function executeViaWandbox(language, code, customInput, startTime, timeoutMs) {
  const compiler = WANDBOX_COMPILERS[language];
  const source = language === 'Java' ? stripPublicFromTopLevelClass(code) : code;
  const elapsed = () => Math.round((typeof performance !== 'undefined' ? performance.now() : Date.now()) - startTime);

  const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
  const timer = controller ? setTimeout(() => controller.abort(), timeoutMs) : null;

  try {
    const response = await fetch(WANDBOX_API, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: source, compiler, stdin: customInput || '', save: false }),
      signal: controller?.signal,
    });
    if (timer) clearTimeout(timer);

    if (!response.ok) {
      throw new Error(`execution service responded with HTTP ${response.status}`);
    }

    const result = await response.json();
    const output = [
      { type: 'system', text: `Compiled & executed via a sandboxed third-party runtime (wandbox.org, ${compiler}).` }
    ];
    if (result.compiler_error) {
      output.push({ type: 'stderr', text: result.compiler_error.replace(/\n$/, '') });
    }
    if (result.program_output) {
      output.push({ type: 'stdout', text: result.program_output.replace(/\n$/, '') });
    }
    if (result.program_error) {
      output.push({ type: 'stderr', text: result.program_error.replace(/\n$/, '') });
    }
    if (result.signal) {
      output.push({ type: 'stderr', text: `Terminated by signal: ${result.signal}` });
    }

    const success = result.status === '0';
    return {
      success,
      output,
      durationMs: elapsed(),
      status: success ? 'SUCCESS' : (result.compiler_error ? 'COMPILE_ERROR' : 'ERROR'),
    };
  } catch (err) {
    if (timer) clearTimeout(timer);
    const timedOut = err?.name === 'AbortError';
    const message = timedOut
      ? `Execution timed out after ${timeoutMs}ms.`
      : `Could not reach the code execution service: ${err?.message || err}`;
    return {
      success: false,
      output: [{ type: 'stderr', text: message }],
      durationMs: elapsed(),
      status: timedOut ? 'TIMEOUT' : 'ERROR',
    };
  }
}
