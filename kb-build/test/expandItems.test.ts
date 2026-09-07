import { describe, expect, it } from 'vitest';
import { expandMilestoneItemIds, type ItemIdRow, type MilestoneLike } from '../src/expandItems.js';

const itemIdRows: ItemIdRow[] = [
  // The real Bucket table carries one row per id, not one row per page with every id - Dragon
  // defender shows up as three separate rows, each with a single-element `id` array.
  { page_name: 'Dragon defender', id: [12954] },
  { page_name: 'Dragon defender', id: [20463] },
  { page_name: 'Dragon defender', id: [24143] },
  { page_name: 'Dragon defender (t)', id: [19722] },
  { page_name: 'Dragon defender (t)', id: [27008] },
  { page_name: 'Rune defender', id: [8850] },
  { page_name: 'Coins', id: [995] },
];

function milestone(overrides: Partial<MilestoneLike> = {}): MilestoneLike {
  return {
    ownedIf: [],
    recommended: null,
    requirements: { items: [] },
    ...overrides,
  };
}

describe('expandMilestoneItemIds', () => {
  it('adds ids for an ownedIf entry, sorted and including the existing id', () => {
    const { milestones } = expandMilestoneItemIds(
      [milestone({ ownedIf: [{ name: 'Rune defender', id: 8850 }] })],
      itemIdRows,
    );

    expect(milestones[0]?.ownedIf).toEqual([{ name: 'Rune defender', id: 8850, ids: [8850] }]);
  });

  it('folds the "(t)" trimmed page ids into an ownedIf entry only', () => {
    const { milestones } = expandMilestoneItemIds(
      [
        milestone({
          ownedIf: [{ name: 'Dragon defender', id: 12954 }],
          recommended: { gearOwnedAny: [{ name: 'Dragon defender', id: 12954 }] },
          requirements: { items: [{ name: 'Dragon defender', id: 12954 }] },
        }),
      ],
      itemIdRows,
    );

    const m = milestones[0]!;
    expect(m.ownedIf).toEqual([{ name: 'Dragon defender', id: 12954, ids: [12954, 19722, 20463, 24143, 27008] }]);
    expect(m.recommended?.gearOwnedAny).toEqual([{ name: 'Dragon defender', id: 12954, ids: [12954, 20463, 24143] }]);
    expect(m.requirements.items).toEqual([{ name: 'Dragon defender', id: 12954, ids: [12954, 20463, 24143] }]);
  });

  it('keeps ids: [id] and reports the name unresolved when no page matches', () => {
    const { milestones, summary } = expandMilestoneItemIds(
      [milestone({ ownedIf: [{ name: 'Some Untracked Thing', id: 42 }] })],
      itemIdRows,
    );

    expect(milestones[0]?.ownedIf).toEqual([{ name: 'Some Untracked Thing', id: 42, ids: [42] }]);
    expect(summary.unresolved).toEqual(['Some Untracked Thing']);
  });

  it('leaves recommended: null as null', () => {
    const { milestones } = expandMilestoneItemIds([milestone()], itemIdRows);
    expect(milestones[0]?.recommended).toBeNull();
  });

  it('summarizes items processed and names with more than one id', () => {
    const { summary } = expandMilestoneItemIds(
      [
        milestone({
          ownedIf: [
            { name: 'Dragon defender', id: 12954 },
            { name: 'Coins', id: 995 },
          ],
        }),
      ],
      itemIdRows,
    );

    expect(summary.itemsProcessed).toBe(2);
    expect(summary.multiId).toEqual(['Dragon defender']);
    expect(summary.unresolved).toEqual([]);
  });

  it('preserves every other field on the milestone untouched', () => {
    const { milestones } = expandMilestoneItemIds(
      [
        {
          ...milestone({ requirements: { items: [{ name: 'Coins', id: 995 }] } }),
          id: 'milestone:test',
          priority: 9,
        } as unknown as MilestoneLike,
      ],
      itemIdRows,
    );

    expect((milestones[0] as unknown as { id: string; priority: number }).id).toBe('milestone:test');
    expect((milestones[0] as unknown as { id: string; priority: number }).priority).toBe(9);
  });
});
