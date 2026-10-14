import { useQuery } from '@tanstack/react-query'
import { useAuth } from '@/contexts/auth-context'
import { useLanguage } from '@/lib/i18n/language-context'
import { api, auth } from '@/lib/api'
import Fleet from '@/pages/fleet'
import CompanyWorkspace from '@/pages/company-workspace'
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
  const { user, memberships } = useAuth()
  const { t } = useLanguage()
  const role = user?.role

  const { data: dashboardData, isLoading: dashboardLoading, isError: dashboardError } = useQuery({
    queryKey: ['dashboard-summary', user?.id],
    queryFn: () => api.get<any>('/dashboard/summary'),
    enabled: role === 'shipper' || role === 'admin',
  })

  const { data: marketplaceData, isLoading: marketplaceLoading } = useQuery({
    queryKey: ['marketplace-freight', 'posted'],
    queryFn: () => api.get<{ content?: any[] }>('/freight?size=20&page=0&status=posted'),
    enabled: role === 'driver',
  })

  const { data: driverData } = useQuery({
    queryKey: ['my-driver'],
    // SB: GET /auth/me — auth client (no /api prefix), replaces Laravel GET /api/me
    queryFn: () => auth.get<any>('/auth/me'),
    enabled: role === 'driver',
  })

  const { data: appsData } = useQuery({
    queryKey: ['my-applications'],
    queryFn: async () => {
      // SB: GET /api/driver/bids returns Page<BidResponse>: { content: [...], ... }
      const resp = await api.get<{ content: any[] } | any[]>('/driver/bids')
      const bids = Array.isArray(resp)
        ? resp
        : Array.isArray((resp as any)?.content)
          ? (resp as any).content
          : []
      return {
        applications: bids.map((b: any) => ({
          id:            b.id,
          proposedPrice: b.amount,
          status:        b.status,
          cargoPickup:   b.cargoPickup,      // populated by SB BidMapper if included
          cargoDestination: b.cargoDestination,
        })),
      }
    },
    enabled: role === 'driver',
  })

  // SB: GET /api/vehicles/my-vehicles — returns List<VehicleResponse> directly (not wrapped)
  const { data: vehiclesData } = useQuery({
    queryKey: ['my-vehicles', user?.id],
    queryFn: () => api.get<any[]>('/vehicles/my-vehicles'),
    enabled: role === 'driver',
  })
  const myVehicles = Array.isArray(vehiclesData) ? vehiclesData : []

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

  // Normalise SB FreightResponse: destination→deliveryLocation, materialType→cargoType
  const freightSource = role === 'driver' ? marketplaceData : dashboardData?.recentFreight
  const freightRows = Array.isArray(freightSource)
    ? freightSource
    : Array.isArray((freightSource as any)?.content)
      ? (freightSource as any).content
      : []
  const freight = freightRows.map((f: any) => ({
    ...f,
    deliveryLocation: f.deliveryLocation ?? f.destination,
    cargoType:        f.cargoType        ?? f.materialType,
  }))
  const bookings = dashboardData?.recentBookings ?? []
  const trips = dashboardData?.recentTrips ?? []
  const activeFreight = (dashboardData?.recentOperationalFreight ?? []).map((f: any) => ({
    ...f,
    deliveryLocation: f.deliveryLocation ?? f.destination,
    cargoType: f.cargoType ?? f.materialType,
  }))
  const shipperDataError = role === 'shipper' && dashboardError
  const apps = appsData?.applications ?? []

  if (memberships.length > 0) return <CompanyWorkspace id={String(memberships[0].carrierCompanyId)} />
  if (role === 'fleet_owner') return <div className='p-6'><Link href='/fleet/companies'>Manage your carrier company application</Link></div>

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
          <div className='flex flex-wrap gap-2'>
            <Link href='/freight/new'><Button className='gap-2 rounded-lg bg-primary hover:bg-primary/90'><Plus className='h-4 w-4' />{t('freight.postNew')}</Button></Link>
            {role === 'shipper' && <><Link href='/tracking'><Button variant='outline' className='gap-2 rounded-lg'><Navigation className='h-4 w-4' />Track Shipments</Button></Link><Link href='/history'><Button variant='outline' className='gap-2 rounded-lg'><Calendar className='h-4 w-4' />History</Button></Link></>}
          </div>
        )}
      </div>

      {shipperDataError && <div role='alert' className='mb-6 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive'>Some dashboard data could not be loaded. Refresh the page to try again.</div>}

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
            />
            <StatCard
              label={t('common.success')}
              value={
                driverData?.successRate != null ? `${driverData.successRate.toFixed(0)}%` : '—'
              }
              icon={TrendingUp}
            />
            <StatCard
              label={t('freight.detail.applications')}
              value={apps.length}
              icon={Package}
            />
          </>
        ) : role === 'shipper' ? (
          <>
            <StatCard label={t('freight.totalPosts')} value={dashboardLoading ? '—' : dashboardData?.totalFreightPosts ?? 0} icon={Package} sub='Your freight postings' />
            <StatCard label='Active shipments' value={dashboardLoading ? '—' : dashboardData?.operationalShipments ?? 0} icon={Clock} sub='Posted, matched, or in transit' />
            <StatCard label='Active bookings' value={dashboardLoading ? '—' : dashboardData?.activeBookings ?? 0} icon={Truck} sub='Spring booking status' />
            <StatCard label='Active trips' value={dashboardLoading ? '—' : dashboardData?.activeTrips ?? 0} icon={Navigation} sub='Authorized trip records' />
          </>
        ) : (
          <>
            <StatCard
              label={t('freight.totalPosts')}
              value={dashboardData?.totalFreightPosts ?? 0}
              icon={Package}
            />
            <StatCard
              label={t('freight.active')}
              value={
                dashboardData?.operationalShipments ?? 0
              }
              icon={Clock}
            />
            <StatCard
              label={t('freight.completed')}
              value={
                dashboardData?.completedFreight ?? 0
              }
              icon={CheckCircle}
            />
            <StatCard
              label={t('freight.cancelled')}
              value={
                dashboardData?.cancelledFreight ?? 0
              }
              icon={XCircle}
            />
          </>
        )}
      </div>

      {role === 'shipper' && (
        <div className='grid gap-6 md:grid-cols-2 mb-6'>
          <Card className='border-border/60'>
            <CardHeader className='flex flex-row items-center justify-between pb-3'><CardTitle className='text-base font-semibold'>Active shipments</CardTitle><Link href='/freight'><Button variant='ghost' size='sm' className='gap-1 text-xs'>{t('common.viewAll')} <ArrowRight className='h-3 w-3' /></Button></Link></CardHeader>
            <CardContent className='space-y-3'>
              {dashboardLoading ? <Skeleton className='h-16 w-full' /> : activeFreight.length === 0 ? <div className='py-8 text-center text-sm text-muted-foreground'><Package className='h-8 w-8 mx-auto mb-2 opacity-50' /><p>No active shipments to track</p><div className='mt-3 flex justify-center gap-2'><Link href='/freight/new'><Button size='sm'>Create Freight</Button></Link><Link href='/history'><Button size='sm' variant='outline'>History</Button></Link></div></div> : activeFreight.map((shipment: any) => <Link key={shipment.id} href={`/tracking/${shipment.id}`}><div className='flex items-center justify-between gap-3 rounded-lg border border-border/60 p-3 hover:bg-muted/40'><div className='min-w-0'><p className='truncate text-sm font-medium'>{shipment.cargoType ?? shipment.materialType ?? `Shipment #${shipment.id}`}</p><p className='truncate text-xs text-muted-foreground'>{shipment.pickupLocation} → {shipment.deliveryLocation ?? shipment.destination}</p></div><Badge variant='outline'>{shipment.status}</Badge></div></Link>)}
            </CardContent>
          </Card>
          <Card className='border-border/60'>
            <CardHeader className='flex flex-row items-center justify-between pb-3'><CardTitle className='text-base font-semibold'>Recent shipment activity</CardTitle><Link href='/history'><Button variant='ghost' size='sm' className='gap-1 text-xs'>History <ArrowRight className='h-3 w-3' /></Button></Link></CardHeader>
            <CardContent className='space-y-3'>
              {dashboardLoading ? <Skeleton className='h-16 w-full' /> : bookings.length === 0 && trips.length === 0 ? <div className='py-8 text-center text-sm text-muted-foreground'><Clock className='h-8 w-8 mx-auto mb-2 opacity-50' />No booking or trip activity yet.</div> : <>{bookings.map((booking: any) => <div key={`booking-${booking.id}`} className='flex items-center justify-between gap-3 rounded-lg border border-border/60 p-3 text-sm'><span>Booking #{booking.id}</span><Badge variant='outline'>{booking.bookingStatus}</Badge></div>)}{trips.map((trip: any) => <div key={`trip-${trip.id}`} className='flex items-center justify-between gap-3 rounded-lg border border-border/60 p-3 text-sm'><span>Trip #{trip.id}</span><Badge variant='outline'>{trip.tripStatus}</Badge></div>)}</>}
            </CardContent>
          </Card>
        </div>
      )}

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
            {(role === 'driver' ? marketplaceLoading : dashboardLoading) ? (
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
