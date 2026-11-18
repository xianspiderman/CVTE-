import { useEffect, useRef } from "react";
import * as echarts from "echarts/core";
import { LineChart, BarChart } from "echarts/charts";
import { GridComponent, TooltipComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
import { businessLabels, riskLabels, type Charts } from "../api/business";
echarts.use([
  LineChart,
  BarChart,
  GridComponent,
  TooltipComponent,
  CanvasRenderer,
]);
export default function BusinessCharts({
  data,
  onSelect,
}: {
  data: Charts;
  onSelect: (key: string, value: string) => void;
}) {
  return (
    <div className="chart-grid">
      <Plot
        title="当前结果的创建日期分布"
        entries={data.trend.map((x) => [x.date, x.count])}
        line
        onClick={(x) => onSelect("createdDate", x)}
      />
      <Plot
        title="业务类型分布"
        entries={Object.entries(data.business)}
        labels={businessLabels}
        onClick={(x) => onSelect("businessType", x)}
      />
      <Plot
        title="风险等级分布"
        entries={Object.entries(data.risk)}
        labels={riskLabels}
        onClick={(x) => onSelect("riskLevel", x)}
      />
    </div>
  );
}
function Plot({
  title,
  entries,
  line = false,
  labels = {},
  onClick,
}: {
  title: string;
  entries: [string, number][];
  line?: boolean;
  labels?: Record<string, string>;
  onClick: (x: string) => void;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const chart = echarts.init(ref.current!);
    chart.setOption({
      tooltip: { trigger: "axis" },
      grid: { top: 20, bottom: 45, left: 35, right: 15 },
      xAxis: {
        type: "category",
        data: entries.map((x) => x[0]),
        axisLabel: { formatter: (x: string) => labels[x] || x.slice(5) },
      },
      yAxis: { type: "value", minInterval: 1 },
      series: [
        {
          type: line ? "line" : "bar",
          data: entries.map((x) => x[1]),
          smooth: false,
          itemStyle: { color: "#4368df" },
        },
      ],
    });
    chart.on("click", (p) => onClick(String(p.name)));
    const o = new ResizeObserver(() => chart.resize());
    o.observe(ref.current!);
    return () => {
      o.disconnect();
      chart.dispose();
    };
  }, [entries, line, labels, onClick]);
  return (
    <section>
      <h3>{title}</h3>
      <div ref={ref} style={{ height: 220 }} />
      <div className="chart-links">
        {entries.map(([key, count]) => (
          <button key={key} onClick={() => onClick(key)}>
            {labels[key] || key}: {count}
          </button>
        ))}
      </div>
    </section>
  );
}
