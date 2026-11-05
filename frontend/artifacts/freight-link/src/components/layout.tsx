import { ReactNode } from 'react'
import { Link, useLocation } from 'wouter'
import { useAuth } from '@/contexts/auth-context'
import { useLanguage } from '@/lib/i18n/language-context'
import { LanguageSwitcher } from '@/components/language-switcher'
import { NotificationBell } from '@/components/notification-bell'
import { Button } from '@/components/ui/button'
import {
  Truck,
  Menu,
  User as UserIcon,
  LayoutDashboard,
  Package,
  Users,
  Building2,
  TruckIcon,
  Activity,
  Bell,
  History as HistoryIcon,
  Settings,
  LogOut,
  Home,
  Navigation,
  ClipboardList,
  ChevronDown,
  MoreHorizontal,
  type LucideIcon,
} from 'lucide-react'
import { useState } from 'react'
import { Sheet, SheetContent, SheetDescription, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { FleetOwnerShell } from '@/components/fleet-owner-shell'

type NavItem = {
  label: string
  href: string
  icon: LucideIcon
}

type NavSection = {
  label: string
  items: NavItem[]
}

function isActiveRoute(location: string, href: string) {
  if (href === '/admin?tab=drivers') return location === '/drivers'
  return location === href || (href !== '/' && location.startsWith(`${href}/`))
}

function isFleetOwnerWorkspaceRoute(location: string) {
  return location === '/dashboard' ||
    location === '/bookings' ||
    location === '/fleet' ||
    location.startsWith('/fleet/') ||
    /^\/company\/\d+$/.test(location) ||
    /^\/carrier-offers\/company\/\d+(?:\/freight\/\d+)?$/.test(location) ||
    location === '/carrier-dispatch' ||
    /^\/carrier-dispatch\/company\/\d+$/.test(location) ||
    /^\/carrier\/\d+\/settlements(?:\/\d+)?$/.test(location) ||
    location === '/history' ||
    location === '/notifications' ||
    location === '/vehicles'
}

export function Layout({ children }: { children: ReactNode }) {
  const { user, logout, isAuthenticated, memberships, isLoading } = useAuth()
  const { t } = useLanguage()
  const [location, setLocation] = useLocation()
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false)

  const isFullScreen = location === '/login' || location === '/register'
  const isAdminWorkspace = user?.role === 'admin' &&
    (location === '/admin' || location === '/admin/companies' || /^\/(?:admin\/)?drivers\/\d+$/.test(location))
  const isAdmin = isAuthenticated && user?.role === 'admin'
  const isDriver = isAuthenticated && user?.role === 'driver'
  const isShipper = isAuthenticated && user?.role === 'shipper'
  const isFleetOwner = isAuthenticated && user?.role === 'fleet_owner'
  const isFleetOwnerWorkspace = user?.role === 'fleet_owner' &&
    isFleetOwnerWorkspaceRoute(location)

  const handleLogout = () => {
    logout()
    setLocation('/')
  }

  const marketplaceItem: NavItem | undefined = isAuthenticated
    ? { label: t('nav.marketplace'), href: '/freight', icon: Package }
    : undefined

  const workspaceItems: NavItem[] = []
  if (isAdmin) {
    workspaceItems.push(
      { label: 'Admin overview', href: '/admin', icon: LayoutDashboard },
      { label: t('nav.drivers'), href: '/admin?tab=drivers', icon: Users },
      { label: 'Company reviews', href: '/admin/companies', icon: Building2 },
    )
  } else if (isAuthenticated) {
    workspaceItems.push({ label: t('nav.dashboard'), href: '/dashboard', icon: LayoutDashboard })
    if (isFleetOwner) {
      workspaceItems.push(
        { label: 'Fleet overview', href: '/fleet/overview', icon: TruckIcon },
        { label: 'My companies', href: '/fleet/companies', icon: Building2 },
      )
      if (!isLoading) {
        workspaceItems.push(...memberships.map((membership) => ({
          label: membership.role === 'DISPATCHER'
            ? 'Dispatch workspace'
            : membership.role === 'FLEET_MANAGER'
              ? 'Fleet workspace'
              : 'Company workspace',
          href: `/company/${membership.carrierCompanyId}`,
          icon: Users,
        })))
        workspaceItems.push(...memberships
          .filter((membership) =>
            membership.status === 'ACTIVE' &&
            ['CARRIER_OWNER', 'FLEET_MANAGER', 'DISPATCHER'].includes(membership.role))
          .map((membership) => ({
            label: `Company offers · ${membership.companyName || `#${membership.carrierCompanyId}`}`,
            href: `/carrier-offers/company/${membership.carrierCompanyId}`,
            icon: Package,
          })))
      }
    }
  }

  const operationsItems: NavItem[] = []
  if (isDriver) {
    operationsItems.push(
      { label: 'Bids', href: '/bids', icon: Activity },
      { label: 'My assignments', href: '/driver/assignments', icon: ClipboardList },
      { label: 'Bookings', href: '/bookings', icon: Package },
      { label: 'Documents', href: '/documents', icon: Settings },
      { label: t('nav.vehicles'), href: '/vehicles', icon: TruckIcon },
      { label: 'History', href: '/history', icon: HistoryIcon },
      { label: 'Track shipments', href: '/tracking', icon: Navigation },
      { label: 'Notifications', href: '/notifications', icon: Bell },
    )
  } else if (isShipper) {
    operationsItems.push(
      { label: 'Bookings', href: '/bookings', icon: Package },
      { label: 'History', href: '/history', icon: HistoryIcon },
      { label: 'Track shipments', href: '/tracking', icon: Navigation },
      { label: 'Notifications', href: '/notifications', icon: Bell },
    )
  } else if (isFleetOwner) {
    operationsItems.push(
      { label: 'Bookings', href: '/bookings', icon: Package },
      { label: t('nav.vehicles'), href: '/fleet/vehicles', icon: TruckIcon },
      { label: 'History', href: '/history', icon: HistoryIcon },
      { label: 'Notifications', href: '/notifications', icon: Bell },
    )
  }

  const navSections: NavSection[] = [
    {
      label: t('nav.section.explore'),
      items: [
        { label: 'Home', href: '/', icon: Home },
        ...(marketplaceItem ? [marketplaceItem] : []),
      ],
    },
    { label: isAdmin ? t('nav.section.administration') : t('nav.section.workspace'), items: workspaceItems },
    { label: t('nav.section.operations'), items: operationsItems },
  ].filter((section) => section.items.length > 0)
  const visibleNavItems = navSections.flatMap((section) => section.items)
  const desktopNavItems = visibleNavItems.filter((item) => item.href !== '/')
  const primaryDesktopItems = desktopNavItems.slice(0, isShipper ? 2 : 3)
  const overflowDesktopItems = desktopNavItems.slice(3)
  const responsiveOverflowItem = primaryDesktopItems[2]
  const hasDesktopOverflow = overflowDesktopItems.length > 0 || primaryDesktopItems.length > 2

  if (isFleetOwnerWorkspace) {
    return <FleetOwnerShell location={location}>{children}</FleetOwnerShell>
  }

  return (
    <div className='min-h-[100dvh] flex flex-col bg-background'>
      {!isFullScreen && !isAdminWorkspace && (
        <header className={`sticky top-0 z-50 w-full border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60 ${location === '/' ? 'absolute left-0 top-0 border-white/10 bg-transparent text-white backdrop-blur-sm supports-[backdrop-filter]:bg-black/10' : ''}`}>
          <div className='container mx-auto flex h-16 min-w-0 items-center gap-2 px-3 sm:px-4'>
            <Link
              href='/'
              aria-current={location === '/' ? 'page' : undefined}
              className='flex min-w-0 shrink items-center space-x-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2'
            >
              <span className='flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary'>
                <Truck className='h-4 w-4 text-white' aria-hidden='true' />
              </span>
              <span className={`truncate text-base font-bold tracking-tight sm:text-xl ${location === '/' ? 'text-white' : 'text-foreground'}`}>
                ETHIO-<span className={location === '/' ? 'text-emerald-200' : 'text-brand-secondary'}>FREIGHT</span>
              </span>
            </Link>

            <nav aria-label='Main navigation' className='hidden min-w-0 flex-1 items-center justify-center gap-1 lg:flex'>
              {primaryDesktopItems.map((item, index) => {
                const active = isActiveRoute(location, item.href)
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    aria-current={active ? 'page' : undefined}
                    className={`${
                      index === 2 ? 'hidden xl:flex' : 'flex'
                    } min-h-10 max-w-48 items-center gap-2 truncate rounded-lg px-3 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${
                      active && location === '/'
                        ? 'bg-white/10 text-white'
                        : active
                          ? 'bg-primary/10 text-primary'
                          : location === '/'
                            ? 'text-white/80 hover:bg-white/10 hover:text-white'
                            : 'text-muted-foreground hover:bg-muted hover:text-foreground'
                    }`}
                  >
                    <item.icon className='h-4 w-4 shrink-0' aria-hidden='true' />
                    <span className='truncate'>{item.label}</span>
                  </Link>
                )
              })}
              {hasDesktopOverflow && (
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button
                      type='button'
                      variant='ghost'
                      className={`min-h-10 gap-2 px-3 ${location === '/' ? 'text-white hover:bg-white/10 hover:text-white' : ''}`}
                    >
                      <MoreHorizontal className='h-4 w-4' aria-hidden='true' />
                      More
                      <ChevronDown className='h-3.5 w-3.5' aria-hidden='true' />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align='center' className='w-64'>
                    {responsiveOverflowItem && (
                      <DropdownMenuItem
                        aria-current={isActiveRoute(location, responsiveOverflowItem.href) ? 'page' : undefined}
                        onSelect={() => setLocation(responsiveOverflowItem.href)}
                        className={`xl:hidden ${isActiveRoute(location, responsiveOverflowItem.href) ? 'bg-accent text-accent-foreground' : ''}`}
                      >
                        <responsiveOverflowItem.icon className='h-4 w-4' aria-hidden='true' />
                        <span>{responsiveOverflowItem.label}</span>
                      </DropdownMenuItem>
                    )}
                    {overflowDesktopItems.map((item) => {
                      const active = isActiveRoute(location, item.href)
                      return (
                        <DropdownMenuItem
                          key={item.href}
                          aria-current={active ? 'page' : undefined}
                          onSelect={() => setLocation(item.href)}
                          className={active ? 'bg-accent text-accent-foreground' : ''}
                        >
                          <item.icon className='h-4 w-4' aria-hidden='true' />
                          <span>{item.label}</span>
                          {active && <span className='sr-only'>Current page</span>}
                        </DropdownMenuItem>
                      )
                    })}
                  </DropdownMenuContent>
                </DropdownMenu>
              )}
            </nav>

            <div className='ml-auto flex shrink-0 items-center gap-1 sm:gap-2'>
              <LanguageSwitcher />
              <NotificationBell />

              {isAuthenticated ? (
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button
                      type='button'
                      variant='ghost'
                      className='hidden min-h-10 max-w-44 gap-2 px-2 lg:inline-flex'
                      aria-label={`Account menu for ${user?.name || 'Profile'}`}
                    >
                      <UserIcon className='h-4 w-4 shrink-0' aria-hidden='true' />
                      <span className='hidden truncate xl:inline'>{user?.name || 'Profile'}</span>
                      <ChevronDown className='h-3.5 w-3.5 shrink-0' aria-hidden='true' />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align='end' className='w-56'>
                    <DropdownMenuLabel className='truncate'>{user?.name || 'Profile'}</DropdownMenuLabel>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem onSelect={() => setLocation('/profile')}>
                      <UserIcon className='h-4 w-4' aria-hidden='true' />
                      {t('nav.profile')}
                    </DropdownMenuItem>
                    <DropdownMenuItem onSelect={handleLogout}>
                      <LogOut className='h-4 w-4' aria-hidden='true' />
                      {t('nav.logout')}
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              ) : (
                <div className='hidden items-center gap-2 lg:flex'>
                  <Link
                    href='/login'
                    className={`rounded-md px-3 py-2 text-sm font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${
                      location === '/' ? 'text-white hover:bg-white/10' : 'text-foreground hover:bg-muted'
                    }`}
                  >
                    {t('nav.login')}
                  </Link>
                  <Link href='/register'>
                    <Button
                      type='button'
                      className={`min-h-10 ${location === '/' ? 'bg-brand-secondary text-white hover:bg-brand-secondary/90' : ''}`}
                    >
                      {t('nav.getStarted')}
                    </Button>
                  </Link>
                </div>
              )}

              <Sheet open={isMobileMenuOpen} onOpenChange={setIsMobileMenuOpen}>
                <SheetTrigger asChild>
                  <Button
                    type='button'
                    variant='ghost'
                    className='h-11 w-11 p-0 lg:hidden'
                    aria-label='Open navigation menu'
                  >
                    <Menu className='h-5 w-5' aria-hidden='true' />
                  </Button>
                </SheetTrigger>
                <SheetContent side='right' className='flex w-[min(22rem,calc(100vw-1rem))] flex-col gap-0 overflow-y-auto p-0'>
                  <SheetTitle className='sr-only'>Navigation</SheetTitle>
                  <SheetDescription className='sr-only'>Choose a destination or account action.</SheetDescription>
                  <div className='border-b px-5 py-5 pr-14'>
                    <p className='text-sm font-semibold'>ETHIO-FREIGHT</p>
                    <p className='text-xs text-muted-foreground'>
                      {isAuthenticated ? user?.name : 'Freight marketplace'}
                    </p>
                  </div>
                  <nav aria-label='Main navigation' className='flex-1 space-y-5 overflow-y-auto px-3 py-5'>
                    {navSections.map((section) => (
                      <section key={section.label} aria-label={section.label}>
                        <h2 className='mb-2 px-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground'>
                          {section.label}
                        </h2>
                        <div className='space-y-1'>
                          {section.items.map((item) => {
                            const active = isActiveRoute(location, item.href)
                            return (
                              <Link
                                key={`${item.href}-${item.label}`}
                                href={item.href}
                                aria-current={active ? 'page' : undefined}
                                onClick={() => setIsMobileMenuOpen(false)}
                                className={`flex min-h-11 min-w-0 items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${
                                  active ? 'bg-primary/10 text-primary' : 'text-foreground hover:bg-muted'
                                }`}
                              >
                                <item.icon className='h-4 w-4 shrink-0' aria-hidden='true' />
                                <span className='min-w-0 break-words'>{item.label}</span>
                              </Link>
                            )
                          })}
                        </div>
                      </section>
                    ))}
                    {isAuthenticated && (
                      <section aria-label={t('nav.section.account')}>
                        <h2 className='mb-2 px-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground'>
                          {t('nav.section.account')}
                        </h2>
                        <div className='space-y-1'>
                          <Link
                            href='/profile'
                            aria-current={location === '/profile' ? 'page' : undefined}
                            onClick={() => setIsMobileMenuOpen(false)}
                            className={`flex min-h-11 min-w-0 items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${
                              location === '/profile' ? 'bg-primary/10 text-primary' : 'text-foreground hover:bg-muted'
                            }`}
                          >
                            <UserIcon className='h-4 w-4 shrink-0' aria-hidden='true' />
                            <span className='min-w-0 break-words'>{user?.name || t('nav.profile')}</span>
                          </Link>
                          <Button
                            type='button'
                            variant='outline'
                            className='min-h-11 w-full justify-start gap-3'
                            onClick={() => {
                              setIsMobileMenuOpen(false)
                              handleLogout()
                            }}
                          >
                            <LogOut className='h-4 w-4 shrink-0' aria-hidden='true' />
                            {t('nav.logout')}
                          </Button>
                        </div>
                      </section>
                    )}
                    {!isAuthenticated && (
                      <section aria-label={t('nav.section.account')} className='border-t pt-4'>
                        <h2 className='sr-only'>Account</h2>
                        <div className='flex flex-col gap-2'>
                          <Link href='/login' onClick={() => setIsMobileMenuOpen(false)}>
                            <Button type='button' variant='outline' className='min-h-11 w-full'>
                              {t('nav.login')}
                            </Button>
                          </Link>
                          <Link href='/register' onClick={() => setIsMobileMenuOpen(false)}>
                            <Button type='button' className='min-h-11 w-full'>{t('nav.getStarted')}</Button>
                          </Link>
                        </div>
                      </section>
                    )}
                  </nav>
                </SheetContent>
              </Sheet>
            </div>
          </div>
        </header>
      )}
      <main className='flex-1 flex flex-col'>{children}</main>
    </div>
  )
}
