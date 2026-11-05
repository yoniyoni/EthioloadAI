import { useState } from 'react'
import { useLocation } from 'wouter'
import { ChevronDown, LogOut, User } from 'lucide-react'
import { useAuth } from '@/contexts/auth-context'
import { NotificationBell } from '@/components/notification-bell'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

type Props = {
  title: string
  subtitle?: string
}

export function EthioTopbar({ title, subtitle }: Props) {
  const { user, logout } = useAuth()
  const [, setLocation] = useLocation()
  const [avatarOpen, setAvatarOpen] = useState(false)

  const handleLogout = () => {
    logout()
    setLocation('/')
  }

  return (
    <header className='fixed left-0 right-0 top-0 z-30 flex h-[60px] min-w-0 items-center gap-2 border-b border-border bg-background/95 pl-16 pr-3 backdrop-blur supports-[backdrop-filter]:bg-background/80 lg:left-[240px] lg:gap-4 lg:px-6'>
      <div className='min-w-0 flex-1'>
        <h1 className='truncate text-base font-semibold leading-tight text-foreground'>{title}</h1>
        {subtitle && <p className='mt-0.5 truncate text-xs text-muted-foreground'>{subtitle}</p>}
      </div>

      <div className='flex shrink-0 items-center gap-1 sm:gap-2'>
        <NotificationBell />

        <DropdownMenu open={avatarOpen} onOpenChange={setAvatarOpen}>
          <DropdownMenuTrigger asChild>
            <button
              type='button'
              aria-label={`Open account menu for ${user?.name ?? 'Admin'}`}
              className='flex min-h-10 max-w-48 cursor-pointer items-center gap-2 rounded-lg border border-border px-2 py-1.5 transition-colors hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2'
            >
              <span className='flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-admin-sidebar text-xs font-bold text-white'>
                {user?.name?.charAt(0).toUpperCase() ?? 'A'}
              </span>
              <span className='hidden truncate text-sm font-medium text-foreground sm:block'>
                {user?.name?.split(' ')[0] ?? 'Admin'}
              </span>
              <ChevronDown className='h-3.5 w-3.5 shrink-0 text-muted-foreground' aria-hidden='true' />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align='end' className='w-56'>
            <DropdownMenuLabel className='min-w-0'>
              <span className='block truncate'>{user?.name ?? 'Admin'}</span>
              {user?.email && <span className='block truncate text-xs font-normal text-muted-foreground'>{user.email}</span>}
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem onSelect={() => setLocation('/profile')}>
              <User className='h-4 w-4' aria-hidden='true' />
              Profile
            </DropdownMenuItem>
            <DropdownMenuItem onSelect={handleLogout}>
              <LogOut className='h-4 w-4' aria-hidden='true' />
              Sign out
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  )
}
