import { useQuery } from '@tanstack/react-query'
import { useAuth } from '@/contexts/auth-context'
import { useLanguage } from '@/lib/i18n/language-context'
import { api } from '@/lib/api'
import { Link } from 'wouter'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Package,
  Truck,
  Star,
  TrendingUp,
  Clock,
  CheckCircle,
  XCircle,
  Loader2,
  Plus,
  ArrowRight,
  Zap,
  MapPin,
  DollarSign,
  Navigation,
  Calendar,
  Phone,
  Wallet,
  MessageSquare,
} from 'lucide-react'

function statusColor(status: string) {
  const map: Record<string, string> = {
    posted: 'bg-blue-50 text-blue-700 border-blue-200',
    matched: 'bg-sky-50 text-sky-700 border-sky-200',
    in_transit: 'bg-purple-50 text-purple-700 border-purple-200',
    delivered: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    completed: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    cancelled: 'bg-red-50 text-red-700 border-red-200',
    pending: 'bg-gray-50 text-gray-700 border-gray-200',
    accepted: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    rejected: 'bg-red-50 text-red-700 border-red-200',
  }
  return map[status] ?? 'bg-gray-50 text-gray-700 border-gray-200'
}

function StatCard({
  label,
  value,
  icon: Icon,
  sub,
  trend,
}: {
  label: string
  value: string | number
  icon: any
  sub?: string
  trend?: string
}) {
  return (
    <Card className='border-border/60 hover:border-primary/30 transition-colors'>
      <CardContent className='pt-6'>
        <div className='flex items-center justify-between'>
          <div>
            <p className='text-sm font-medium text-muted-foreground'>{label}</p>
            <p className='text-3xl font-bold mt-1'>{value}</p>
            {sub && <p className='text-xs text-muted-foreground mt-1'>{sub}</p>}
            {trend && <p className='text-xs text-emerald-600 mt-1'>{trend}</p>}
          </div>
          <div className='h-12 w-12 rounded-xl bg-primary/10 flex items-center justify-center'>
            <Icon className='h-6 w-6 text-primary' />
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

export default function Dashboard() {
  const { user } = useAuth()
  const { t } = useLanguage()
  const role = user?.role

  const { data: freightData, isLoading: freightLoading } = useQuery({
    queryKey: ['my-freight'],
    queryFn: () =>
      api.get<{ freight: any[]; total: number }>('/freight?limit=5'),
  })

  const { data: driverData } = useQuery({
    queryKey: ['my-driver'],
    queryFn: () => api.get<any>('/me'),
    enabled: role === 'driver',
  })

  const { data: appsData } = useQuery({
    queryKey: ['my-applications'],
    queryFn: async () => {
      const resp = await api.get<{ data: any[] }>('/driver/bids')
      const bids = Array.isArray(resp?.data) ? resp.data : []
      return {
        applications: bids.map((b: any) => ({
          id: b.id,
          proposedPrice: b.amount,
          status: b.status,
          cargoPickup: b.cargo_pickup,
          cargoDestination: b.cargo_destination,
        })),
      }
    },
    enabled: role === 'driver',
  })

  const { data: vehiclesData } = useQuery({
    queryKey: ['my-vehicles'],
    queryFn: () => api.get<{ vehicles: any[] }>('/my-vehicles'),
    enabled: role === 'driver',
  })
  const myVehicles = vehiclesData?.vehicles ?? []

  const { data: backhaulData, isLoading: backhaulLoading } = useQuery({
    queryKey: ['backhaul-opportunities', myVehicles[0]?.id],
    queryFn: () =>
      api.post<{ opportunities: any[] }>('/ai/backhaul-opportunities', {
        truck_id: myVehicles[0]?.id,
        current_location: myVehicles[0]?.currentCity ?? 'Addis Ababa',
      }),
    enabled: role === 'driver' && myVehicles.length > 0,
    staleTime: 5 * 60 * 1000,
  })
  const backhaulOpps = backhaulData?.opportunities ?? []

  const freight = freightData?.freight ?? []
  const apps = appsData?.applications ?? []

  return (
    <div className='container mx-auto max-w-6xl px-4 py-8'>
      <div className='flex items-center justify-between mb-8'>
        <div>
          <h1 className='text-3xl font-bold tracking-tight'>
            {t('nav.dashboard')}
          </h1>
          <p className='text-muted-foreground mt-1'>{user?.name}</p>
        </div>
        {(role === 'shipper' || role === 'admin') && (
          <Link href='/freight/new'>
            <Button className='gap-2 rounded-lg bg-primary hover:bg-primary/90'>
              <Plus className='h-4 w-4' />
              {t('freight.postNew')}
            </Button>
          </Link>
        )}
      </div>

      {/* Stats Row */}
      <div className='grid grid-cols-2 md:grid-cols-4 gap-4 mb-8'>
        {role === 'driver' ? (
          <>
            <StatCard
              label={t('drivers.rating')}
              value={driverData?.rating?.toFixed(1) ?? '—'}
              icon={Star}
              sub='/ 5.0'
            />
            <StatCard
              label={t('drivers.deliveries')}
              value={driverData?.totalDeliveries ?? 0}
              icon={CheckCircle}
              trend='+12.4% last week'
            />
            <StatCard
              label={t('common.success')}
              value={
                driverData ? `${driverData.successRate?.toFixed(0)}%` : '—'
              }
              icon={TrendingUp}
            />
            <StatCard
              label={t('freight.detail.applications')}
              value={apps.length}
              icon={Package}
            />
          </>
        ) : (
          <>
            <StatCard
              label={t('freight.totalPosts')}
              value={freightData?.total ?? 0}
              icon={Package}
            />
            <StatCard
              label={t('freight.active')}
              value={
                freight.filter((f: any) =>
                  ['posted', 'matched', 'in_transit'].includes(f.status),
                ).length
              }
              icon={Clock}
            />
            <StatCard
              label={t('freight.completed')}
              value={
                freight.filter((f: any) => f.status === 'completed').length
              }
              icon={CheckCircle}
            />
            <StatCard
              label={t('freight.cancelled')}
              value={
                freight.filter((f: any) => f.status === 'cancelled').length
              }
              icon={XCircle}
            />
          </>
        )}
      </div>

      {/* Driver Online Status */}
      {role === 'driver' && driverData && (
        <Card className='mb-6 border-emerald-200 bg-gradient-to-r from-emerald-50 to-white'>
          <CardContent className='pt-6 flex items-center justify-between'>
            <div className='flex items-center gap-4'>
              <div className='h-12 w-12 rounded-full bg-emerald-100 flex items-center justify-center'>
                <div className='h-3 w-3 rounded-full bg-emerald-500 animate-pulse' />
              </div>
              <div>
                <p className='font-semibold text-foreground'>You are Online</p>
                <p className='text-sm text-muted-foreground'>
                  Ready for matching
                </p>
              </div>
            </div>
            <Button
              variant='outline'
              className='rounded-lg border-emerald-200 text-emerald-700 hover:bg-emerald-50'
            >
              Go Offline
            </Button>
          </CardContent>
        </Card>
      )}

      {/* Return Cargo Optimization - drivers only, live data */}
      {role === 'driver' && (
        <Card className='mb-6 border-border/60 overflow-hidden'>
          <CardContent className='p-0'>
            <div className='bg-gradient-to-r from-[#0c1e4a] to-[#1a3a6e] text-white p-6'>
              <div className='flex items-center justify-between mb-4'>
                <div className='flex items-center gap-2'>
                  <Zap className='h-4 w-4 text-emerald-400' />
                  <span className='text-sm font-semibold'>
                    Return Cargo Optimization
                  </span>
                </div>
                <Badge className='bg-emerald-500/20 text-emerald-300 border-emerald-500/30 hover:bg-emerald-500/20'>
                  <Zap className='h-3 w-3 mr-1' /> AI Suggested
                </Badge>
              </div>

              {backhaulLoading ? (
                <div className='flex items-center gap-2 text-slate-300 text-sm'>
                  <Loader2 className='h-4 w-4 animate-spin' /> Finding return
                  loads near you…
                </div>
              ) : myVehicles.length === 0 ? (
                <p className='text-slate-300 text-sm'>
                  Register a vehicle to get AI-matched return loads.
                </p>
              ) : backhaulOpps.length === 0 ? (
                <p className='text-slate-300 text-sm'>
                  No return loads available in your area right now. Check back
                  after your delivery.
                </p>
              ) : (
                <div className='flex items-center justify-between'>
                  <div>
                    <div className='flex items-center gap-2 mb-1'>
                      <Badge className='bg-emerald-500/20 text-emerald-300 border-emerald-500/30 text-xs'>
                        {Math.round((backhaulOpps[0].score ?? 0) * 100)}% MATCH
                        SCORE
                      </Badge>
                    </div>
                    <h3 className='text-xl font-bold'>
                      {backhaulOpps[0].pickup_location} →{' '}
                      {backhaulOpps[0].destination}
                    </h3>
                    <p className='text-sm text-slate-300 mt-1'>
                      AI-suggested return load to minimize your empty miles.
                    </p>
                    <div className='flex items-center gap-4 mt-3 text-sm'>
                      <div className='flex items-center gap-1'>
                        <DollarSign className='h-3 w-3 text-emerald-400' />
                        <span className='text-emerald-400 font-semibold'>
                          ETB {Number(backhaulOpps[0].price).toLocaleString()}
                        </span>
                      </div>
                      <div className='flex items-center gap-1'>
                        <Package className='h-3 w-3 text-slate-400' />
                        <span className='text-slate-300'>
                          {backhaulOpps[0].weight} tons
                        </span>
                      </div>
                    </div>
                  </div>
                  <Link href={`/freight/${backhaulOpps[0].cargo_id}`}>
                    <Button className='bg-emerald-500 hover:bg-emerald-600 text-white rounded-lg shrink-0 ml-4'>
                      View Load →
                    </Button>
                  </Link>
                </div>
              )}
            </div>
          </CardContent>
        </Card>
      )}

      {/* Main Content Grid */}
      <div className='grid md:grid-cols-2 gap-6'>
        {/* Recent freight / Available Loads */}
        <Card className='border-border/60'>
          <CardHeader className='flex flex-row items-center justify-between pb-3'>
            <CardTitle className='text-base font-semibold'>
              {role === 'driver'
                ? 'Available Loads Near You'
                : t('dashboard.myFreight')}
            </CardTitle>
            <div className='flex items-center gap-2'>
              <Button
                variant='ghost'
                size='sm'
                className='text-xs text-muted-foreground'
              >
                Filter
              </Button>
              <Link href='/freight'>
                <Button variant='ghost' size='sm' className='gap-1 text-xs'>
                  {t('common.viewAll')} <ArrowRight className='h-3 w-3' />
                </Button>
              </Link>
            </div>
          </CardHeader>
          <CardContent className='space-y-3'>
            {freightLoading ? (
              Array.from({ length: 3 }).map((_, i) => (
                <Skeleton key={i} className='h-16 w-full' />
              ))
            ) : freight.length === 0 ? (
              <div className='text-center py-8 text-muted-foreground text-sm'>
                <Package className='h-8 w-8 mx-auto mb-2 opacity-50' />
                {t('freight.noResults')}
              </div>
            ) : (
              freight.slice(0, 5).map((f: any) => (
                <Link key={f.id} href={`/freight/${f.id}`}>
                  <div className='flex items-start justify-between p-3 rounded-xl hover:bg-muted/50 cursor-pointer border border-transparent hover:border-border transition-all'>
                    <div className='min-w-0 flex-1'>
                      <div className='flex items-center gap-2 mb-1'>
                        <p className='font-medium text-sm truncate'>
                          {f.cargoType}
                        </p>
                        <span className='text-xs text-muted-foreground'>
                          ({f.weightTons} {t('freight.tons')})
                        </span>
                      </div>
                      <div className='flex items-center gap-1 text-xs text-muted-foreground'>
                        <MapPin className='h-3 w-3' />
                        <span>
                          {f.pickupLocation} ({f.distanceKm}km)
                        </span>
                      </div>
                      <div className='flex items-center gap-2 mt-1'>
                        <span className='text-xs text-muted-foreground'>
                          <Calendar className='h-3 w-3 inline mr-1' />
                          {new Date(f.deadline).toLocaleDateString()}
                        </span>
                      </div>
                    </div>
                    <div className='text-right shrink-0 ml-2'>
                      <p className='text-sm font-bold text-foreground'>
                        ETB {Number(f.budget).toLocaleString()}
                      </p>
                      <span
                        className={`text-xs font-medium px-2 py-0.5 rounded-full border ${statusColor(f.status)}`}
                      >
                        {t(`freight.${f.status}`) ?? f.status}
                      </span>
                    </div>
                  </div>
                </Link>
              ))
            )}
          </CardContent>
        </Card>

        {/* Applications / Driver Profile */}
        {role === 'driver' && (
          <Card className='border-border/60'>
            <CardHeader className='pb-3'>
              <CardTitle className='text-base font-semibold'>
                {t('dashboard.myApplications')}
              </CardTitle>
            </CardHeader>
            <CardContent className='space-y-3'>
              {apps.length === 0 ? (
                <div className='text-center py-8 text-muted-foreground text-sm'>
                  <Truck className='h-8 w-8 mx-auto mb-2 opacity-50' />
                  {t('dashboard.noApplications')}
                </div>
              ) : (
                apps.slice(0, 5).map((a: any) => (
                  <div
                    key={a.id}
                    className='flex items-center justify-between p-3 rounded-xl border border-border/60'
                  >
                    <div>
                      <p className='font-medium text-sm'>Application #{a.id}</p>
                      <p className='text-xs text-muted-foreground'>
                        {t('common.ETB')}{' '}
                        {Number(a.proposedPrice).toLocaleString()}
                      </p>
                    </div>
                    <span
                      className={`text-xs font-medium px-2 py-0.5 rounded-full border ${statusColor(a.status)}`}
                    >
                      {t(`freight.${a.status}`) ?? a.status}
                    </span>
                  </div>
                ))
              )}
            </CardContent>
          </Card>
        )}

        {role === 'driver' && driverData && (
          <Card className='border-border/60'>
            <CardHeader className='pb-3'>
              <CardTitle className='text-base font-semibold'>
                {t('dashboard.driverProfile')}
              </CardTitle>
            </CardHeader>
            <CardContent className='space-y-3'>
              <div className='flex items-center justify-between'>
                <span className='text-sm text-muted-foreground'>
                  {t('common.status')}
                </span>
                <Badge
                  variant='outline'
                  className={statusColor(driverData.status)}
                >
                  {t(`freight.${driverData.status}`) ?? driverData.status}
                </Badge>
              </div>
              <div className='flex items-center justify-between'>
                <span className='text-sm text-muted-foreground'>
                  {t('drivers.rating')}
                </span>
                <span className='text-sm font-medium flex items-center gap-1'>
                  <Star className='h-3 w-3 text-amber-500 fill-amber-500' />
                  {driverData.rating?.toFixed(1) ?? t('dashboard.noRatings')}
                </span>
              </div>
              <div className='flex items-center justify-between'>
                <span className='text-sm text-muted-foreground'>
                  {t('drivers.experience')}
                </span>
                <span className='text-sm font-medium'>
                  {driverData.yearsExperience ?? 0} {t('dashboard.years')}
                </span>
              </div>
              <div className='flex items-center justify-between'>
                <span className='text-sm text-muted-foreground'>
                  {t('drivers.available')}
                </span>
                <span
                  className={`text-sm font-medium ${driverData.isAvailable ? 'text-emerald-600' : 'text-red-500'}`}
                >
                  {driverData.isAvailable ? t('common.yes') : t('common.no')}
                </span>
              </div>
              <Link href='/vehicles'>
                <Button
                  variant='outline'
                  size='sm'
                  className='w-full mt-2 gap-2 rounded-lg'
                >
                  <Truck className='h-4 w-4' /> {t('nav.vehicles')}
                </Button>
              </Link>
            </CardContent>
          </Card>
        )}

        {/* Recent Deliveries / Earnings */}
        {!role || role === 'shipper' || role === 'admin' ? (
          <Card className='border-border/60'>
            <CardHeader className='pb-3'>
              <CardTitle className='text-base font-semibold'>
                Recent Deliveries
              </CardTitle>
            </CardHeader>
            <CardContent className='space-y-3'>
              {freight.filter((f: any) => f.status === 'completed').length ===
              0 ? (
                <div className='text-center py-8 text-muted-foreground text-sm'>
                  <CheckCircle className='h-8 w-8 mx-auto mb-2 opacity-50' />
                  No completed deliveries yet
                </div>
              ) : (
                freight
                  .filter((f: any) => f.status === 'completed')
                  .slice(0, 3)
                  .map((f: any) => (
                    <div
                      key={f.id}
                      className='flex items-center justify-between p-3 rounded-xl border border-border/60'
                    >
                      <div className='flex items-center gap-3'>
                        <div className='h-10 w-10 rounded-xl bg-emerald-50 flex items-center justify-center'>
                          <CheckCircle className='h-5 w-5 text-emerald-600' />
                        </div>
                        <div>
                          <p className='font-medium text-sm'>
                            {f.pickupLocation} → {f.deliveryLocation}
                          </p>
                          <p className='text-xs text-muted-foreground'>
                            {f.cargoType} • {f.weightTons} {t('freight.tons')}
                          </p>
                        </div>
                      </div>
                      <div className='text-right'>
                        <p className='text-sm font-semibold'>
                          ETB {Number(f.budget).toLocaleString()}
                        </p>
                        <p className='text-xs text-emerald-600'>Completed</p>
                      </div>
                    </div>
                  ))
              )}
            </CardContent>
          </Card>
        ) : null}
      </div>
    </div>
  )
}
