type FeedEvent = {
  id: string | number;
  icon?: React.ReactNode;
  title: string;
  detail?: string;
  timestamp: string;
  accentColor?: string;
};

type Props = {
  events: FeedEvent[];
  emptyMessage?: string;
};

export function ActivityFeed({ events, emptyMessage = 'No recent activity' }: Props) {
  if (events.length === 0) {
    return (
      <div className="py-10 text-center">
        <p className="text-sm" style={{ color: '#8FA893' }}>
          {emptyMessage}
        </p>
      </div>
    );
  }

  return (
    <ol className="relative">
      {events.map((evt, idx) => (
        <li key={evt.id} className="flex gap-3 pb-5 relative">
          {/* Vertical connector line */}
          {idx < events.length - 1 && (
            <div
              className="absolute left-4 top-8 bottom-0 w-px"
              style={{ background: '#E2E8E2' }}
            />
          )}

          {/* Dot / icon */}
          <div
            className="flex items-center justify-center rounded-full flex-shrink-0 z-10"
            style={{
              width: 32,
              height: 32,
              background: evt.accentColor ? evt.accentColor + '20' : '#F8FAF8',
              border: `1px solid ${evt.accentColor ?? '#E2E8E2'}`,
              color: evt.accentColor ?? '#8FA893',
            }}
          >
            {evt.icon ?? (
              <div
                className="rounded-full"
                style={{
                  width: 8,
                  height: 8,
                  background: evt.accentColor ?? '#8FA893',
                }}
              />
            )}
          </div>

          {/* Content */}
          <div className="flex-1 min-w-0 pt-1">
            <p className="text-sm font-medium leading-snug" style={{ color: '#0D1F12' }}>
              {evt.title}
            </p>
            {evt.detail && (
              <p className="text-xs mt-0.5 truncate" style={{ color: '#8FA893' }}>
                {evt.detail}
              </p>
            )}
            <p className="text-xs mt-1" style={{ color: '#B4C4B4' }}>
              {evt.timestamp}
            </p>
          </div>
        </li>
      ))}
    </ol>
  );
}
