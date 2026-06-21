import { ReactNode } from 'react'
import { Link, useLocation } from 'wouter'
import { useAuth } from '@/contexts/auth-context'
import { useLanguage } from '@/lib/i18n/language-context'
import { LanguageSwitcher } from '@/components/language-switcher'
import { Button } from '@/components/ui/button'
import {
  Truck,
  Menu,
  X,
  User as UserIcon,
  LayoutDashboard,
  Package,
  Users,
  TruckIcon,
  Activity,
  Settings,
  LogOut,
  Home,
} from 'lucide-react'
import { useState } from 'react'
import { Sheet, SheetContent, SheetTrigger } from '@/components/ui/sheet'

export function Layout({ children }: { children: ReactNode }) {
  const { user, logout, isAuthenticated } = useAuth()
  const { t } = useLanguage()
  const [location, setLocation] = useLocation()
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false)

  const isFullScreen = location === '/login' || location === '/register'

  const handleLogout = () => {
    logout()
    setLocation('/')
  }

  const navItems = [
    { label: 'Home', href: '/', icon: Home, show: true },
    {
      label: t('nav.dashboard'),
      href: '/dashboard',
      icon: LayoutDashboard,
      show: isAuthenticated,
    },
    { label: t('nav.freight'), href: '/freight', icon: Package, show: true },
    {
      label: t('nav.drivers'),
      href: '/drivers',
      icon: Users,
      show:
        isAuthenticated && (user?.role === 'admin' || user?.role === 'shipper'),
    },
    {
      label: t('nav.vehicles'),
      href: '/vehicles',
      icon: TruckIcon,
      show: isAuthenticated && user?.role === 'driver',
    },
    {
      label: t('nav.admin'),
      href: '/admin',
      icon: Activity,
      show: isAuthenticated && user?.role === 'admin',
    },
  ]

  return (
    <div className='min-h-[100dvh] flex flex-col bg-background'>
      {!isFullScreen && (
        <header className='sticky top-0 z-50 w-full border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60'>
          <div className='container flex h-16 items-center justify-between mx-auto px-4'>
            <div className='flex items-center gap-6'>
              <Link href='/' className='flex items-center space-x-2'>
                <div className='h-8 w-8 rounded-lg bg-primary flex items-center justify-center'>
                  <Truck className='h-4 w-4 text-white' />
                </div>
                <span className='hidden font-bold sm:inline-block text-xl tracking-tight text-foreground'>
                  ETHIO-<span className='text-primary'>FREIGHT</span>
                </span>
              </Link>

              <nav className='hidden md:flex items-center space-x-6 text-sm font-medium'>
                {navItems
                  .filter((item) => item.show)
                  .map((item) => (
                    <Link
                      key={item.href}
                      href={item.href}
                      className={`transition-colors hover:text-primary ${
                        location === item.href ||
                        location.startsWith(item.href + '/')
                          ? 'text-primary'
                          : 'text-muted-foreground'
                      }`}
                    >
                      {item.label}
                    </Link>
                  ))}
              </nav>
            </div>

            <div className='flex items-center space-x-3'>
              <LanguageSwitcher />
              {isAuthenticated ? (
                <div className='hidden md:flex items-center space-x-4'>
                  <Link
                    href='/profile'
                    className='flex items-center space-x-2 text-sm text-muted-foreground hover:text-foreground'
                  >
                    <UserIcon className='h-4 w-4' />
                    <span>{user?.name}</span>
                  </Link>
                  <Button
                    variant='ghost'
                    size='icon'
                    onClick={handleLogout}
                    title={t('nav.logout')}
                  >
                    <LogOut className='h-4 w-4' />
                  </Button>
                </div>
              ) : (
                <div className='hidden md:flex items-center space-x-4'>
                  <Link
                    href='/login'
                    className='text-sm font-medium hover:text-primary'
                  >
                    {t('nav.login')}
                  </Link>
                  <Link href='/register'>
                    <Button
                      size='sm'
                      className='bg-primary text-primary-foreground hover:bg-primary/90 rounded-lg'
                    >
                      {t('nav.getStarted')}
                    </Button>
                  </Link>
                </div>
              )}

              <Sheet open={isMobileMenuOpen} onOpenChange={setIsMobileMenuOpen}>
                <SheetTrigger asChild>
                  <Button variant='ghost' className='md:hidden px-2'>
                    <Menu className='h-5 w-5' />
                  </Button>
                </SheetTrigger>
                <SheetContent side='right' className='w-[300px] sm:w-[400px]'>
                  <nav className='flex flex-col gap-4'>
                    {navItems
                      .filter((item) => item.show)
                      .map((item) => (
                        <Link
                          key={item.href}
                          href={item.href}
                          className='flex items-center gap-2 text-lg font-medium'
                          onClick={() => setIsMobileMenuOpen(false)}
                        >
                          <item.icon className='h-5 w-5' />
                          {item.label}
                        </Link>
                      ))}
                    {isAuthenticated ? (
                      <>
                        <div className='my-4 border-t' />
                        <Link
                          href='/profile'
                          className='flex items-center gap-2 text-lg font-medium'
                          onClick={() => setIsMobileMenuOpen(false)}
                        >
                          <Settings className='h-5 w-5' />
                          Profile
                        </Link>
                        <Button
                          variant='outline'
                          className='justify-start gap-2'
                          onClick={handleLogout}
                        >
                          <LogOut className='h-5 w-5' />
                          Logout
                        </Button>
                      </>
                    ) : (
                      <div className='flex flex-col gap-2 mt-4'>
                        <Link
                          href='/login'
                          onClick={() => setIsMobileMenuOpen(false)}
                        >
                          <Button variant='outline' className='w-full'>
                            Login
                          </Button>
                        </Link>
                        <Link
                          href='/register'
                          onClick={() => setIsMobileMenuOpen(false)}
                        >
                          <Button className='w-full'>Get Started</Button>
                        </Link>
                      </div>
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
