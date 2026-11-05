import { ChevronRight } from 'lucide-react';

type Crumb = { label: string; href?: string };

type Props = {
  title: string;
  breadcrumbs?: Crumb[];
  actions?: React.ReactNode;
};

export function PageHeader({ title, breadcrumbs, actions }: Props) {
  return (
    <header className="mb-6 flex flex-wrap items-start justify-between gap-4">
      <div className="min-w-0">
        {breadcrumbs && breadcrumbs.length > 0 && (
          <nav aria-label="Breadcrumb" className="mb-1.5 flex flex-wrap items-center gap-1">
            {breadcrumbs.map((crumb, idx) => (
              <span key={idx} className="flex items-center gap-1">
                {idx > 0 && (
                  <ChevronRight
                    aria-hidden="true"
                    size={12}
                    className="text-muted-foreground/60"
                  />
                )}
                <span
                  aria-current={idx === breadcrumbs.length - 1 ? "page" : undefined}
                  className={
                    idx === breadcrumbs.length - 1
                      ? "text-caption font-label text-foreground"
                      : "text-caption font-medium text-muted-foreground"
                  }
                >
                  {crumb.label}
                </span>
              </span>
            ))}
          </nav>
        )}
        <h1 className="text-page-title font-heading tracking-tight text-foreground">
          {title}
        </h1>
      </div>

      {actions && (
        <div className="flex max-w-full shrink-0 flex-wrap items-center gap-2">
          {actions}
        </div>
      )}
    </header>
  );
}
