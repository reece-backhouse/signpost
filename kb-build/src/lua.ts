export type LuaValue = string | number | boolean | null | LuaTable;

export interface LuaTable {
  [key: string]: LuaValue;
}

type Token =
  | { type: 'string'; value: string; line: number; column: number }
  | { type: 'number'; value: number; line: number; column: number }
  | { type: 'ident'; value: string; line: number; column: number }
  | { type: 'punct'; value: string; line: number; column: number }
  | { type: 'eof'; line: number; column: number };

class LuaParseError extends Error {
  constructor(message: string, line: number, column: number) {
    super(`${message} at line ${line}, column ${column}`);
    this.name = 'LuaParseError';
  }
}

const PUNCTUATION = new Set(['{', '}', '[', ']', '=', ',', ';', '/']);

function tokenize(text: string): Token[] {
  const tokens: Token[] = [];
  let i = 0;
  let line = 1;
  let column = 1;

  function advance(count = 1): void {
    for (let n = 0; n < count; n++) {
      if (text[i] === '\n') {
        line++;
        column = 1;
      } else {
        column++;
      }
      i++;
    }
  }

  while (i < text.length) {
    const ch = text[i]!;

    if (ch === ' ' || ch === '\t' || ch === '\r' || ch === '\n') {
      advance();
      continue;
    }

    if (ch === '-' && text[i + 1] === '-') {
      if (text[i + 2] === '[' && text[i + 3] === '[') {
        const end = text.indexOf(']]', i + 4);
        advance(end === -1 ? text.length - i : end + 2 - i);
      } else {
        const end = text.indexOf('\n', i);
        advance(end === -1 ? text.length - i : end - i);
      }
      continue;
    }

    if (ch === "'" || ch === '"') {
      const startLine = line;
      const startColumn = column;
      const quote = ch;
      let value = '';
      advance();
      while (i < text.length && text[i] !== quote) {
        if (text[i] === '\\' && i + 1 < text.length) {
          value += text[i + 1];
          advance(2);
        } else {
          value += text[i];
          advance();
        }
      }
      if (text[i] !== quote) {
        throw new LuaParseError('Unterminated string', startLine, startColumn);
      }
      advance();
      tokens.push({ type: 'string', value, line: startLine, column: startColumn });
      continue;
    }

    if (/[0-9]/.test(ch) || (ch === '-' && /[0-9]/.test(text[i + 1] ?? ''))) {
      const startLine = line;
      const startColumn = column;
      let raw = '';
      if (ch === '-') {
        raw += ch;
        advance();
      }
      while (i < text.length && /[0-9.]/.test(text[i]!)) {
        raw += text[i];
        advance();
      }
      tokens.push({ type: 'number', value: Number(raw), line: startLine, column: startColumn });
      continue;
    }

    if (/[A-Za-z_]/.test(ch)) {
      const startLine = line;
      const startColumn = column;
      let raw = '';
      while (i < text.length && /[A-Za-z0-9_]/.test(text[i]!)) {
        raw += text[i];
        advance();
      }
      tokens.push({ type: 'ident', value: raw, line: startLine, column: startColumn });
      continue;
    }

    if (PUNCTUATION.has(ch)) {
      tokens.push({ type: 'punct', value: ch, line, column });
      advance();
      continue;
    }

    throw new LuaParseError(`Unexpected character '${ch}'`, line, column);
  }

  tokens.push({ type: 'eof', line, column });
  return tokens;
}

class Parser {
  private pos = 0;
  constructor(private tokens: Token[]) {}

  peek(offset = 0): Token {
    return this.tokens[Math.min(this.pos + offset, this.tokens.length - 1)]!;
  }

  next(): Token {
    return this.tokens[Math.min(this.pos++, this.tokens.length - 1)]!;
  }

  private isPunct(token: Token, value: string): boolean {
    return token.type === 'punct' && token.value === value;
  }

  expectPunct(value: string): void {
    const token = this.next();
    if (!this.isPunct(token, value)) {
      throw new LuaParseError(`Expected '${value}'`, token.line, token.column);
    }
  }

  parseValue(): LuaValue {
    const token = this.peek();
    switch (token.type) {
      case 'string':
        this.next();
        return token.value;
      case 'number': {
        this.next();
        // ponytail: only a single a/b numeric division is supported (the one real
        // case is `quantity = 1/140` in Module:Skill calc/Smithing); a general
        // arithmetic expression evaluator would be needed for anything richer.
        if (this.isPunct(this.peek(), '/')) {
          this.next();
          const divisor = this.next();
          if (divisor.type !== 'number') {
            throw new LuaParseError('Expected a number after /', divisor.line, divisor.column);
          }
          return token.value / divisor.value;
        }
        return token.value;
      }
      case 'ident':
        this.next();
        if (token.value === 'true') return true;
        if (token.value === 'false') return false;
        if (token.value === 'nil') return null;
        throw new LuaParseError(`Unexpected identifier '${token.value}'`, token.line, token.column);
      case 'punct':
        if (token.value === '{') return this.parseTable();
        throw new LuaParseError(`Unexpected token '${token.value}'`, token.line, token.column);
      default:
        throw new LuaParseError('Unexpected end of input', token.line, token.column);
    }
  }

  parseTable(): LuaTable {
    this.expectPunct('{');
    const table: LuaTable = {};
    let index = 1;

    while (!this.isPunct(this.peek(), '}')) {
      let key: string;
      if (this.isPunct(this.peek(), '[')) {
        this.next();
        const keyToken = this.next();
        if (keyToken.type !== 'string' && keyToken.type !== 'number') {
          throw new LuaParseError('Expected string or number table key', keyToken.line, keyToken.column);
        }
        key = String(keyToken.value);
        this.expectPunct(']');
        this.expectPunct('=');
        table[key] = this.parseValue();
      } else if (this.peek().type === 'ident' && this.isPunct(this.peek(1), '=')) {
        const keyToken = this.next();
        key = keyToken.type === 'ident' ? keyToken.value : '';
        this.expectPunct('=');
        table[key] = this.parseValue();
      } else {
        key = String(index++);
        table[key] = this.parseValue();
      }

      if (this.isPunct(this.peek(), ',') || this.isPunct(this.peek(), ';')) {
        this.next();
      } else if (!this.isPunct(this.peek(), '}')) {
        const token = this.peek();
        throw new LuaParseError("Expected ',' or '}'", token.line, token.column);
      }
    }

    this.expectPunct('}');
    return table;
  }
}

/** Parses the subset of Lua used by OSRS wiki `Module:*` data tables. */
export function parseLua(text: string): LuaValue {
  const parser = new Parser(tokenize(text));

  const first = parser.peek();
  if (first.type === 'ident' && first.value === 'local') {
    parser.next();
    parser.next();
    parser.expectPunct('=');
    const result = parser.parseValue();
    const afterValue = parser.peek();
    if (afterValue.type === 'ident' && afterValue.value === 'return') {
      parser.next();
      parser.next();
    }
    return result;
  }

  if (first.type === 'ident' && first.value === 'return') {
    parser.next();
  }

  return parser.parseValue();
}
