import { TrendingUp, TrendingDown, Minus } from 'lucide-react';

type Trend = 'up' | 'down' | 'neutral';

type Props = {
  label: string;
  value: string | number;
  icon: React.ReactNode;
  accentColor?: string;
  trend?: Trend;
  trendLabel?: string;
  sublabel?: string;
};

const TREND_COLORS: Record<Trend, string> = {
  up: '#16A34A',
  down: '#DC2626',
  neutral: '#8FA893',
};

const TREND_ICONS: Record<Trend, React.ReactNode> = {
  up: <TrendingUp size={12} />,
  down: <TrendingDown size={12} />,
  neutral: <Minus size={12} />,
};

export function MetricCard({
  label,
  value,
  icon,
  accentColor = '#0F3D1A',
  trend,
  trendLabel,
  sublabel,
}: Props) {
  return (
    <div
      className="rounded-xl bg-white flex flex-col gap-3 p-5 relative overflow-hidden"
      style={{ border: '1px solid #E2E8E2', borderLeft: `4px solid ${accentColor}` }}
    >
      {/* Icon bubble */}
      <div className="flex items-start justify-between">
        <div
          className="flex items-center justify-center rounded-lg"
          style={{
            background: accentColor + '18',
            color: accentColor,
            width: 40,
            height: 40,
          }}
        >
          {icon}
        </div>
        {trend && (
          <span
            className="flex items-center gap-1 text-xs font-semibold rounded-full px-2 py-0.5"
            style={{
              color: TREND_COLORS[trend],
              background: TREND_COLORS[trend] + '18',
            }}
          >
            {TREND_ICONS[trend]}
            {trendLabel}
          </span>
        )}
      </div>

      {/* Value + label */}
      <div>
        <p
          className="text-2xl font-bold leading-none"
          style={{ color: '#0D1F12' }}
        >
          {value}
        </p>
        <p className="text-sm mt-1" style={{ color: '#6B7F6B' }}>
          {label}
        </p>
        {sublabel && (
          <p className="text-xs mt-0.5" style={{ color: '#8FA893' }}>
            {sublabel}
          </p>
        )}
      </div>
    </div>
  );
}
