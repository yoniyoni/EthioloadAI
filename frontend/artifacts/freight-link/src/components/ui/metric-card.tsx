import { TrendingUp, TrendingDown, Minus } from 'lucide-react';

type Trend = 'up' | 'down' | 'neutral';
type TrendColors = {
  color: string;
  backgroundColor: string;
};

type Props = {
  label: string;
  value: string | number;
  icon: React.ReactNode;
  accentColor?: string;
  trend?: Trend;
  trendLabel?: string;
  sublabel?: string;
};

const TREND_COLORS: Record<Trend, TrendColors> = {
  up: {
    color: 'hsl(var(--success))',
    backgroundColor: 'hsl(var(--success-subtle))',
  },
  down: {
    color: 'hsl(var(--error))',
    backgroundColor: 'hsl(var(--error-subtle))',
  },
  neutral: {
    color: 'hsl(var(--muted-foreground))',
    backgroundColor: 'hsl(var(--muted))',
  },
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
  accentColor = 'hsl(var(--brand-primary))',
  trend,
  trendLabel,
  sublabel,
}: Props) {
  return (
    <div
      className="relative flex flex-col gap-3 overflow-hidden rounded-surface border border-card-border border-l-4 bg-card p-5 text-card-foreground shadow-card"
      style={{ borderLeftColor: accentColor }}
    >
      <div className="flex items-start justify-between">
        <div
          className="flex h-10 w-10 items-center justify-center rounded-lg"
          style={{
            backgroundColor: `color-mix(in srgb, ${accentColor} 12%, transparent)`,
            color: accentColor,
          }}
        >
          {icon}
        </div>
        {trend && (
          <span
            className="flex items-center gap-1 text-xs font-semibold rounded-full px-2 py-0.5"
            style={{
              color: TREND_COLORS[trend].color,
              backgroundColor: TREND_COLORS[trend].backgroundColor,
            }}
          >
            {TREND_ICONS[trend]}
            {trendLabel}
          </span>
        )}
      </div>

      <div>
        <p className="text-2xl font-heading leading-none text-foreground">
          {value}
        </p>
        <p className="mt-1 text-sm text-muted-foreground">
          {label}
        </p>
        {sublabel && (
          <p className="mt-0.5 text-caption text-muted-foreground">
            {sublabel}
          </p>
        )}
      </div>
    </div>
  );
}
