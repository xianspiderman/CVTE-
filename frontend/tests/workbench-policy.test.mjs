import { test } from "node:test";
import assert from "node:assert/strict";
import {
  refreshGroups,
  navigationTree,
  metricSource,
} from "../src/state/workbenchPolicy.ts";
test("each visible card retains its own refresh interval and time window", () => {
  const groups = refreshGroups([
    { key: "a", visible: true, days: 7, refreshSeconds: 10 },
    { key: "b", visible: true, days: 30, refreshSeconds: 300 },
    { key: "c", visible: false, days: 30, refreshSeconds: 10 },
    { key: "d", visible: true, days: 30, refreshSeconds: 300 },
  ]);
  assert.deepEqual(
    groups.map((g) => [g.days, g.seconds, g.cards.length]),
    [
      [7, 10, 1],
      [30, 300, 2],
    ],
  );
});
test("parent hidden state propagates and hierarchy controls order", () => {
  const rows = [
    { key: "child", parent: "parent", visible: true, order: 0 },
    { key: "grandchild", parent: "child", visible: true, order: 0 },
    { key: "hidden", visible: false, order: 0 },
    { key: "hiddenChild", parent: "hidden", visible: true, order: 0 },
    { key: "parent", visible: true, order: 2 },
  ];
  assert.deepEqual(
    navigationTree(rows).map((x) => [x.key, x.depth]),
    [
      ["parent", 0],
      ["child", 1],
      ["grandchild", 2],
    ],
  );
});
test("changing metric source leaves stable card identity intact", () => {
  assert.equal(metricSource({ key: "TOTAL", metric: "OVERDUE" }), "OVERDUE");
  assert.equal(metricSource({ key: "TOTAL" }), "TOTAL");
});
