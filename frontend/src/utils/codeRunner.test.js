import { describe, it, expect } from 'vitest';
import { executeCode, CODE_STARTERS } from './codeRunner';

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

  it('executes simulated Python prints', async () => {
    const code = `
print("Hello Python")
print("Line 2")
`;
    const res = await executeCode(code, 'Python');
    expect(res.success).toBe(true);
    expect(res.output[0].text).toBe('Hello Python');
    expect(res.output[1].text).toBe('Line 2');
  });

  it('validates Java main method and prints', async () => {
    const validJava = `
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello Java");
    }
}
`;
    const res = await executeCode(validJava, 'Java');
    expect(res.success).toBe(true);
    expect(res.output[0].text).toBe('Hello Java');

    const invalidJava = `public class Main {}`;
    const resInvalid = await executeCode(invalidJava, 'Java');
    expect(resInvalid.success).toBe(false);
    expect(resInvalid.status).toBe('ERROR');
  });

  it('validates C++ main and cout', async () => {
    const validCpp = `
#include <iostream>
int main() {
    std::cout << "Hello C++" << std::endl;
    return 0;
}
`;
    const res = await executeCode(validCpp, 'C++');
    expect(res.success).toBe(true);
    expect(res.output[0].text).toBe('Hello C++');
  });

  it('executes SQL queries in the sandbox', async () => {
    const sql = `
CREATE TABLE users (id INT, name VARCHAR(50));
SELECT * FROM users;
`;
    const res = await executeCode(sql, 'SQL');
    expect(res.success).toBe(true);
    expect(res.output.some(o => o.text.includes('users'))).toBe(true);
  });
});
