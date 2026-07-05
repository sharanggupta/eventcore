// Hand-rolled SVG charts - no chart library, matching the custom-component ethos.

type Segment = { label: string; value: number; color: string };

export function Donut({ segments, centerLabel }: { segments: Segment[]; centerLabel: string }) {
  const total = segments.reduce((sum, segment) => sum + segment.value, 0);
  const radius = 60;
  const circumference = 2 * Math.PI * radius;
  let offset = 0;

  return (
    <div className="flex items-center gap-6" data-testid="donut-chart">
      <svg viewBox="0 0 160 160" className="h-36 w-36 -rotate-90">
        <circle cx="80" cy="80" r={radius} fill="none" stroke="rgba(255,255,255,0.06)" strokeWidth="16" />
        {total > 0 && segments.map((segment) => {
          const length = (segment.value / total) * circumference;
          const circle = (
            <circle
              key={segment.label}
              cx="80" cy="80" r={radius} fill="none"
              stroke={segment.color} strokeWidth="16" strokeLinecap="butt"
              strokeDasharray={`${length} ${circumference - length}`}
              strokeDashoffset={-offset}
            />
          );
          offset += length;
          return circle;
        })}
      </svg>
      <div className="space-y-2">
        <div className="text-2xl font-bold text-white">{centerLabel}</div>
        {segments.map((segment) => (
          <div key={segment.label} className="flex items-center gap-2 text-sm text-slate-300">
            <span className="h-2.5 w-2.5 rounded-full" style={{ background: segment.color }} />
            {segment.label}
            <span className="mono text-slate-400">{segment.value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

export function Bars({ data, unit }: { data: { label: string; value: number }[]; unit: string }) {
  const max = Math.max(1, ...data.map((point) => point.value));
  const barWidth = 100 / data.length;

  return (
    <div data-testid="bar-chart">
      <svg viewBox="0 0 100 42" preserveAspectRatio="none" className="h-36 w-full">
        {data.map((point, index) => {
          const height = (point.value / max) * 36;
          return (
            <rect
              key={point.label}
              x={index * barWidth + barWidth * 0.15}
              y={40 - height}
              width={barWidth * 0.7}
              height={Math.max(height, point.value > 0 ? 0.8 : 0)}
              rx="0.8"
              fill="url(#barGradient)"
            />
          );
        })}
        <defs>
          <linearGradient id="barGradient" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#67e8f9" />
            <stop offset="100%" stopColor="#818cf8" />
          </linearGradient>
        </defs>
      </svg>
      <div className="mt-1 flex justify-between text-[10px] text-slate-500">
        <span>{data[0]?.label}</span>
        <span>{unit}</span>
        <span>{data[data.length - 1]?.label}</span>
      </div>
    </div>
  );
}
