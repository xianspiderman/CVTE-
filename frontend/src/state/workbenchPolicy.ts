import type { Item } from "../api/business";
export const iconGlyph = (icon?: string) =>
  icon === "chart" ? "▥" : icon === "link" ? "↗" : "◇";
export const metricSource = (card: Item) => card.metric || card.key;
export function refreshGroups(cards: Item[]) {
  const groups = new Map<
    string,
    { days: number; seconds: number; cards: Item[] }
  >();
  for (const card of cards.filter((c) => c.visible)) {
    const days = card.days || 30,
      seconds = card.refreshSeconds || 60,
      key = `${days}:${seconds}`;
    if (!groups.has(key)) groups.set(key, { days, seconds, cards: [] });
    groups.get(key)!.cards.push(card);
  }
  return [...groups.values()];
}
export function navigationTree(items: Item[]) {
  const result: (Item & { depth: number })[] = [];
  const seen = new Set<string>();
  function visit(parent: string, depth: number) {
    for (const item of items
      .filter((n) => n.visible && (n.parent || "") === parent)
      .sort((a, b) => a.order - b.order)) {
      if (seen.has(item.key)) continue;
      seen.add(item.key);
      result.push({ ...item, depth });
      visit(item.key, depth + 1);
    }
  }
  visit("", 0);
  return result;
}
