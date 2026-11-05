import { Link, useLocation, useSearch } from 'wouter'
import { useState } from 'react'
import {
  LayoutDashboard,
  Truck,
  Users,
  CreditCard,
  AlertTriangle,
  LogOut,
  ChevronRight,
  Building2,
  Lock,
  FileText,
  TrendingUp,
  Banknote,
  Package,
  Bell,
  History,
  Menu,
} from 'lucide-react'
import { useAuth } from '@/contexts/auth-context'
import { Sheet, SheetContent, SheetDescription, SheetTitle, SheetTrigger } from '@/components/ui/sheet'

type NavItem = {
  label: string
  icon: React.ReactNode
  tab: string
}

type NavGroup = {
  label: string
  items: NavItem[]
}

const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Overview',
    items: [{ label: 'Overview', icon: <LayoutDashboard size={18} />, tab: 'overview' }],
  },
  {
    label: 'Freight & people',
    items: [
      { label: 'Cargo & Bids', icon: <Package size={18} />, tab: 'cargo' },
      { label: 'Drivers', icon: <Truck size={18} />, tab: 'drivers' },
      { label: 'Fleet Owners', icon: <Building2 size={18} />, tab: 'fleet' },
      { label: 'Company reviews', icon: <Building2 size={18} />, tab: 'companies' },
      { label: 'Users', icon: <Users size={18} />, tab: 'users' },
    ],
  },
  {
    label: 'Finance',
    items: [
      { label: 'Payments', icon: <CreditCard size={18} />, tab: 'payments' },
      { label: 'Escrow', icon: <Banknote size={18} />, tab: 'escrow' },
      { label: 'Disputes', icon: <AlertTriangle size={18} />, tab: 'disputes' },
    ],
  },
  {
    label: 'Platform',
    items: [
      { label: 'Documents', icon: <FileText size={18} />, tab: 'documents' },
      { label: 'Vehicles', icon: <Truck size={18} />, tab: 'vehicles' },
      { label: 'Analytics', icon: <TrendingUp size={18} />, tab: 'analytics' },
      { label: 'Notifications', icon: <Bell size={18} />, tab: 'notifications' },
      { label: 'Activity', icon: <History size={18} />, tab: 'activity' },
      { label: 'Settings', icon: <Lock size={18} />, tab: 'settings' },
    ],
  },
]

export function EthioSidebar() {
  const [location, navigate] = useLocation()
  const rawSearch = useSearch()
  const { user, logout } = useAuth()
  const [mobileOpen, setMobileOpen] = useState(false)

  const searchStr = rawSearch.startsWith('?') ? rawSearch.slice(1) : rawSearch
  const queryTab = new URLSearchParams(searchStr).get('tab')
  const currentTab =
    location === '/admin/companies'
      ? 'companies'
      : location === '/drivers' || location.startsWith('/drivers/') || location.startsWith('/admin/drivers/')
        ? 'drivers'
        : queryTab || 'overview'

  const handleNav = (tab: string) => {
    setMobileOpen(false)
    navigate(tab === 'companies' ? '/admin/companies' : tab === 'overview' ? '/admin' : `/admin?tab=${tab}`)
  }

  const renderSidebarContent = (onNavigate?: () => void) => (
    <>
      <Link
        href='/admin'
        onClick={onNavigate}
        className='flex min-h-16 items-center gap-3 border-b border-white/10 px-5 py-4 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-admin-accent'
        aria-label='EthioLoad Admin overview'
      >
        <span className='flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-admin-accent text-admin-sidebar'>
          <Truck size={20} aria-hidden='true' />
        </span>
        <span className='min-w-0'>
          <span className='block truncate text-sm font-bold text-white'>EthioLoad</span>
          <span className='block truncate text-xs font-semibold text-admin-accent'>AI Admin</span>
        </span>
      </Link>

      <nav aria-label='Admin navigation' className='flex-1 space-y-5 overflow-y-auto px-3 py-5'>
        {NAV_GROUPS.map((group) => (
          <section key={group.label} aria-label={group.label}>
            <h2 className='mb-2 px-2 text-[10px] font-semibold uppercase tracking-widest text-white/55'>
              {group.label}
            </h2>
            <ul className='space-y-1'>
              {group.items.map((item) => {
                const active = currentTab === item.tab
                return (
                  <li key={item.tab}>
                    <button
                      type='button'
                      onClick={() => {
                        onNavigate?.()
                        handleNav(item.tab)
                      }}
                      aria-current={active ? 'page' : undefined}
                      className={`group relative flex min-h-11 w-full cursor-pointer items-center gap-3 rounded-lg border-l-2 px-3 py-2.5 text-left text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-admin-accent ${
                        active
                          ? 'border-admin-accent bg-white/10 text-white'
                          : 'border-transparent text-white/75 hover:bg-white/10 hover:text-white'
                      }`}
                    >
                      <span className={active ? 'text-admin-accent' : 'text-white/65'} aria-hidden='true'>
                        {item.icon}
                      </span>
                      <span className='min-w-0 flex-1'>{item.label}</span>
                      {active && <ChevronRight size={14} className='shrink-0 text-admin-accent' aria-hidden='true' />}
                    </button>
                  </li>
                )
              })}
            </ul>
          </section>
        ))}
      </nav>

      <div className='border-t border-white/10 p-4'>
        {user && (
          <div className='mb-3 flex min-w-0 items-center gap-3'>
            <span className='flex h-[34px] w-[34px] shrink-0 items-center justify-center rounded-full bg-admin-accent text-xs font-bold text-admin-sidebar'>
              {user.name?.charAt(0).toUpperCase() ?? 'A'}
            </span>
            <span className='min-w-0'>
              <span className='block truncate text-sm font-semibold text-white'>{user.name}</span>
              <span className='block truncate text-xs text-white/55'>{user.role}</span>
            </span>
          </div>
        )}
        <button
          type='button'
          onClick={() => logout()}
          className='flex min-h-11 w-full cursor-pointer items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-white/70 transition-colors hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-admin-accent'
        >
          <LogOut size={16} aria-hidden='true' />
          Sign out
        </button>
      </div>
    </>
  )

  return (
    <>
      <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
        <SheetTrigger asChild>
          <button
            type='button'
            aria-label='Open admin navigation'
            aria-expanded={mobileOpen}
            className='fixed left-3 top-2.5 z-40 flex h-10 w-10 cursor-pointer items-center justify-center rounded-lg bg-admin-sidebar text-white shadow-md transition-colors hover:bg-admin-sidebar/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-admin-accent focus-visible:ring-offset-2 lg:hidden'
          >
            <Menu size={20} aria-hidden='true' />
          </button>
        </SheetTrigger>
        <SheetContent side='left' className='flex w-60 flex-col gap-0 border-white/10 bg-admin-sidebar p-0 text-white'>
          <SheetTitle className='sr-only'>Admin navigation</SheetTitle>
          <SheetDescription className='sr-only'>Navigate to platform administration sections.</SheetDescription>
          {renderSidebarContent()}
        </SheetContent>
      </Sheet>

      <aside
        aria-label='Admin sidebar'
        className='fixed left-0 top-0 z-30 hidden h-dvh w-[240px] shrink-0 flex-col bg-admin-sidebar text-white lg:flex'
      >
        {renderSidebarContent()}
      </aside>
    </>
  )
}
