import { ChevronRight } from 'lucide-react';

type Crumb = { label: string; href?: string };

type Props = {
  title: string;
  breadcrumbs?: Crumb[];
  actions?: React.ReactNode;
};

export function PageHeader({ title, breadcrumbs, actions }: Props) {
  return (
    <div className="flex items-start justify-between mb-6">
      <div>
        {breadcrumbs && breadcrumbs.length > 0 && (
          <nav className="flex items-center gap-1 mb-1.5">
            {breadcrumbs.map((crumb, idx) => (
              <span key={idx} className="flex items-center gap-1">
                {idx > 0 && <ChevronRight size={12} color="#B4C4B4" />}
                <span
                  className="text-xs font-medium"
                  style={{ color: idx === breadcrumbs.length - 1 ? '#0F3D1A' : '#8FA893' }}
                >
                  {crumb.label}
                </span>
              </span>
            ))}
          </nav>
        )}
        <h1 className="text-xl font-bold" style={{ color: '#0D1F12' }}>
          {title}
        </h1>
      </div>

      {actions && <div className="flex items-center gap-2 flex-shrink-0">{actions}</div>}
    </div>
  );
}
