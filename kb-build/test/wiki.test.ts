import { describe, expect, it, vi } from 'vitest';
import { bucket, fetchRevisions, USER_AGENT, type FetchLike } from '../src/wiki.js';

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
}

function errorResponse(code: string, info: string): Response {
  return jsonResponse({ error: { code, info } });
}

function httpErrorResponse(status: number): Response {
  return new Response('', { status });
}

function revisionsBody(titles: string[]): unknown {
  return {
    query: {
      pages: titles.map((title) => ({
        title,
        revisions: [
          {
            timestamp: `2026-01-01T00:00:00Z-${title}`,
            slots: { main: { content: `content of ${title}` } },
          },
        ],
      })),
    },
  };
}

describe('fetchRevisions', () => {
  it('batches 120 titles into 50/50/20 requests and maps results', async () => {
    const titles = Array.from({ length: 120 }, (_, i) => `Page ${i + 1}`);
    const calls: string[] = [];
    const fake: FetchLike = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input));
      calls.push(url.searchParams.get('titles')!);
      expect(init?.headers).toMatchObject({ 'User-Agent': USER_AGENT });
      const batchTitles = url.searchParams.get('titles')!.split('|');
      return jsonResponse(revisionsBody(batchTitles));
    });

    const result = await fetchRevisions(titles, fake);

    expect(fake).toHaveBeenCalledTimes(3);
    expect(calls[0]!.split('|')).toHaveLength(50);
    expect(calls[1]!.split('|')).toHaveLength(50);
    expect(calls[2]!.split('|')).toHaveLength(20);
    expect(result.size).toBe(120);
    expect(result.get('Page 1')).toEqual({
      content: 'content of Page 1',
      timestamp: '2026-01-01T00:00:00Z-Page 1',
    });
    expect(result.get('Page 120')).toEqual({
      content: 'content of Page 120',
      timestamp: '2026-01-01T00:00:00Z-Page 120',
    });
  });

  it('throws with the title list when a page is missing', async () => {
    const fake: FetchLike = vi.fn(async () =>
      jsonResponse({
        query: {
          pages: [{ title: 'Present page', missing: true }],
        },
      }),
    );

    await expect(fetchRevisions(['Present page'], fake)).rejects.toThrow(/Present page/);
  });

  it('throws naming the error code on a MediaWiki error response (e.g. maxlag)', async () => {
    const fake: FetchLike = vi.fn(async () =>
      errorResponse('maxlag', 'Waiting for a database server: 5 seconds lagged'),
    );

    await expect(fetchRevisions(['X'], fake)).rejects.toThrow(/maxlag/);
  });

  it('throws on a non-2xx HTTP status', async () => {
    const fake: FetchLike = vi.fn(async () => httpErrorResponse(503));

    await expect(fetchRevisions(['X'], fake)).rejects.toThrow(/503/);
  });

  it('keys the result by the requested title when the API normalizes it', async () => {
    const fake: FetchLike = vi.fn(async () =>
      jsonResponse({
        query: {
          normalized: [{ from: 'song_of_the_elves', to: 'Song of the Elves' }],
          pages: [
            {
              title: 'Song of the Elves',
              revisions: [
                { timestamp: '2026-01-01T00:00:00Z', slots: { main: { content: 'content' } } },
              ],
            },
          ],
        },
      }),
    );

    const result = await fetchRevisions(['song_of_the_elves'], fake);

    expect(result.get('song_of_the_elves')).toEqual({
      content: 'content',
      timestamp: '2026-01-01T00:00:00Z',
    });
    expect(result.has('Song of the Elves')).toBe(false);
  });

  it('keys the result by the requested title when the API resolves a redirect', async () => {
    const fake: FetchLike = vi.fn(async () =>
      jsonResponse({
        query: {
          redirects: [{ from: 'SOTE', to: 'Song of the Elves' }],
          pages: [
            {
              title: 'Song of the Elves',
              revisions: [
                { timestamp: '2026-01-01T00:00:00Z', slots: { main: { content: 'content' } } },
              ],
            },
          ],
        },
      }),
    );

    const result = await fetchRevisions(['SOTE'], fake);

    expect(result.get('SOTE')).toEqual({
      content: 'content',
      timestamp: '2026-01-01T00:00:00Z',
    });
    expect(result.has('Song of the Elves')).toBe(false);
  });
});

describe('bucket', () => {
  it('pages 5000 then 12 rows into 2 requests with offsets 0 and 5000', async () => {
    const offsets: number[] = [];
    const fake: FetchLike = vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input));
      const query = url.searchParams.get('query')!;
      expect(query).toContain('.limit(5000)');
      const offsetMatch = query.match(/\.offset\((\d+)\)/);
      const offset = Number(offsetMatch![1]);
      offsets.push(offset);
      const rows = offset === 0 ? 5000 : 12;
      return jsonResponse({ bucket: Array.from({ length: rows }, (_, i) => ({ id: offset + i })) });
    });

    const rows = await bucket('bucket("test")', fake);

    expect(fake).toHaveBeenCalledTimes(2);
    expect(offsets).toEqual([0, 5000]);
    expect(rows).toHaveLength(5012);
  });

  it('stops after a request returning exactly 5000 then 0 rows (2 requests)', async () => {
    const fake: FetchLike = vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input));
      const query = url.searchParams.get('query')!;
      const offset = Number(query.match(/\.offset\((\d+)\)/)![1]);
      const rows = offset === 0 ? 5000 : 0;
      return jsonResponse({ bucket: Array.from({ length: rows }, (_, i) => ({ id: offset + i })) });
    });

    const rows = await bucket('bucket("test")', fake);

    expect(fake).toHaveBeenCalledTimes(2);
    expect(rows).toHaveLength(5000);
  });

  it('sends the User-Agent header', async () => {
    const fake: FetchLike = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      expect(init?.headers).toMatchObject({ 'User-Agent': USER_AGENT });
      return jsonResponse({ bucket: [] });
    });

    await bucket('bucket("test")', fake);
  });

  it('throws naming the error code on a MediaWiki error response (e.g. maxlag)', async () => {
    const fake: FetchLike = vi.fn(async () =>
      errorResponse('maxlag', 'Waiting for a database server: 5 seconds lagged'),
    );

    await expect(bucket('bucket("test")', fake)).rejects.toThrow(/maxlag/);
  });

  it('throws on a non-2xx HTTP status', async () => {
    const fake: FetchLike = vi.fn(async () => httpErrorResponse(503));

    await expect(bucket('bucket("test")', fake)).rejects.toThrow(/503/);
  });
});
