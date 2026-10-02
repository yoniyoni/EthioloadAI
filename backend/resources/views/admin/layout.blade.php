<!DOCTYPE html>
<html lang="en" data-theme="light">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="{{ csrf_token() }}">
    <meta name="api-token" content="{{ session('admin_api_token') }}">
    <title>EthioLoadAI Admin Panel - @yield('title', 'Dashboard')</title>
    
    <!-- Bootstrap 5.3 -->
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <!-- Font Awesome 6 -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <!-- Leaflet CSS -->
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
    <!-- Google Fonts -->
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
    <!-- Chart.js -->
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <!-- DataTables -->
    <link rel="stylesheet" href="https://cdn.datatables.net/1.13.6/css/dataTables.bootstrap5.min.css">
    <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
    <script src="https://cdn.datatables.net/1.13.6/js/jquery.dataTables.min.js"></script>
    <script src="https://cdn.datatables.net/1.13.6/js/dataTables.bootstrap5.min.js"></script>
    <!-- Leaflet JS -->
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
    <!-- Pusher & Laravel Echo -->
    <script src="https://js.pusher.com/8.2.0/pusher.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/laravel-echo@1.16.1/dist/echo.iife.js"></script>
    
    @stack('styles')
    
    <style>
        :root {
            --primary: #4361ee;
            --primary-dark: #3a0ca3;
            --secondary: #7209b7;
            --success: #10b981;
            --danger: #ef4444;
            --warning: #f59e0b;
            --info: #3b82f6;
            --dark: #111827;
            --light: #f9fafb;
            --gradient: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        }

        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: 'Inter', sans-serif;
            background: #f3f4f6;
            overflow-x: hidden;
        }

        /* Sidebar Styles */
        .sidebar {
            width: 280px;
            background: var(--gradient);
            position: fixed;
            height: 100vh;
            left: 0;
            top: 0;
            z-index: 1000;
            transition: all 0.3s ease;
            box-shadow: 2px 0 20px rgba(0,0,0,0.1);
        }

        .sidebar-header {
            padding: 25px 20px;
            border-bottom: 1px solid rgba(255,255,255,0.1);
            margin-bottom: 20px;
        }

        .logo {
            display: flex;
            align-items: center;
            gap: 12px;
            text-decoration: none;
        }

        .logo-icon {
            width: 45px;
            height: 45px;
            background: rgba(255,255,255,0.2);
            border-radius: 12px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 24px;
            color: white;
        }

        .logo-text {
            font-size: 22px;
            font-weight: 800;
            color: white;
        }

        .logo-text span {
            background: linear-gradient(135deg, #ffd89b, #c7e9fb);
            -webkit-background-clip: text;
            background-clip: text;
            color: transparent;
        }

        .sidebar-nav {
            padding: 0 15px;
        }

        .nav-item {
            margin-bottom: 8px;
        }

        .nav-link-custom {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 12px 15px;
            color: rgba(255,255,255,0.85);
            text-decoration: none;
            border-radius: 12px;
            transition: all 0.3s;
            font-weight: 500;
            cursor: pointer;
        }

        .nav-link-custom:hover {
            background: rgba(255,255,255,0.1);
            color: white;
            transform: translateX(5px);
        }

        .nav-link-custom.active {
            background: rgba(255,255,255,0.15);
            color: white;
            box-shadow: 0 4px 15px rgba(0,0,0,0.1);
        }

        .nav-link-custom i {
            width: 24px;
            font-size: 18px;
        }

        .nav-badge {
            margin-left: auto;
            background: rgba(255,255,255,0.2);
            padding: 2px 8px;
            border-radius: 20px;
            font-size: 10px;
            font-weight: 600;
        }

        .sidebar-footer {
            position: absolute;
            bottom: 0;
            left: 0;
            right: 0;
            padding: 20px;
            border-top: 1px solid rgba(255,255,255,0.1);
        }

        /* Main Content */
        .main-content {
            margin-left: 280px;
            min-height: 100vh;
        }

        /* Top Header */
        .top-header {
            background: white;
            padding: 0 30px;
            height: 70px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            box-shadow: 0 1px 3px rgba(0,0,0,0.05);
            position: sticky;
            top: 0;
            z-index: 999;
        }

        .page-title h1 {
            font-size: 24px;
            font-weight: 600;
            margin: 0;
            color: var(--dark);
        }

        .page-title p {
            font-size: 13px;
            color: #6b7280;
            margin: 0;
        }

        .header-actions {
            display: flex;
            align-items: center;
            gap: 20px;
        }

        .theme-toggle {
            cursor: pointer;
            width: 40px;
            height: 40px;
            display: flex;
            align-items: center;
            justify-content: center;
            border-radius: 10px;
            background: #f3f4f6;
            transition: all 0.3s;
        }

        .notifications {
            position: relative;
            cursor: pointer;
        }

        .notification-icon {
            width: 40px;
            height: 40px;
            background: #f3f4f6;
            border-radius: 10px;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: all 0.3s;
        }

        .notification-badge {
            position: absolute;
            top: -5px;
            right: -5px;
            background: var(--danger);
            color: white;
            font-size: 10px;
            padding: 2px 6px;
            border-radius: 50%;
        }

        .admin-dropdown {
            display: flex;
            align-items: center;
            gap: 12px;
            cursor: pointer;
            padding: 5px 15px;
            border-radius: 12px;
            background: #f3f4f6;
        }

        .admin-avatar {
            width: 40px;
            height: 40px;
            background: var(--gradient);
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            color: white;
        }

        /* Content Area */
        .content-wrapper {
            padding: 30px;
        }

        /* Stat Cards */
        .stat-card {
            background: white;
            border-radius: 16px;
            padding: 20px;
            transition: all 0.3s;
            border: none;
            box-shadow: 0 1px 3px rgba(0,0,0,0.05);
        }

        .stat-card:hover {
            transform: translateY(-5px);
            box-shadow: 0 8px 25px rgba(0,0,0,0.1);
        }

        .stat-icon {
            width: 55px;
            height: 55px;
            border-radius: 14px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 24px;
            background: var(--gradient);
            color: white;
        }

        .stat-value {
            font-size: 32px;
            font-weight: 700;
            margin: 10px 0 5px;
            color: var(--dark);
        }

        .stat-label {
            color: #6b7280;
            font-size: 14px;
            font-weight: 500;
        }

        .stat-change {
            font-size: 12px;
            margin-top: 8px;
        }

        .trend-up { color: var(--success); }
        .trend-down { color: var(--danger); }

        /* Tables */
        .data-table {
            background: white;
            border-radius: 16px;
            overflow: hidden;
            box-shadow: 0 1px 3px rgba(0,0,0,0.05);
        }

        .data-table thead th {
            background: #f9fafb;
            padding: 15px;
            font-weight: 600;
            border-bottom: 2px solid #e5e7eb;
        }

        .data-table tbody td {
            padding: 12px 15px;
            vertical-align: middle;
        }

        /* Badges */
        .badge-custom {
            padding: 5px 12px;
            border-radius: 20px;
            font-size: 11px;
            font-weight: 600;
            display: inline-block;
        }

        .badge-active { background: #dbeafe; color: #1e40af; }
        .badge-pending { background: #fed7aa; color: #92400e; }
        .badge-completed { background: #d1fae5; color: #065f46; }
        .badge-cancelled { background: #fee2e2; color: #991b1b; }
        .badge-verified { background: #d1fae5; color: #065f46; }
        
        /* Buttons */
        .btn-gradient {
            background: var(--gradient);
            border: none;
            color: white;
            padding: 8px 20px;
            border-radius: 10px;
            font-weight: 500;
            transition: all 0.3s;
        }

        .btn-gradient:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 15px rgba(102,126,234,0.4);
            color: white;
        }

        /* Modal */
        .modal-content {
            border-radius: 20px;
            border: none;
        }

        /* Loading */
        .loading-overlay {
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background: rgba(0,0,0,0.5);
            display: none;
            align-items: center;
            justify-content: center;
            z-index: 9999;
        }

        .spinner-border-custom {
            width: 50px;
            height: 50px;
            border: 3px solid #f3f3f3;
            border-top: 3px solid #667eea;
            border-radius: 50%;
            animation: spin 1s linear infinite;
        }

        @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
        }

        /* Mobile Menu Button */
        .mobile-menu-toggle {
            display: none;
            background: none;
            border: none;
            font-size: 24px;
            cursor: pointer;
            margin-right: 15px;
        }

        /* Responsive */
        @media (max-width: 768px) {
            .sidebar {
                transform: translateX(-100%);
                position: fixed;
                z-index: 1050;
            }
            .sidebar.mobile-open {
                transform: translateX(0);
            }
            .main-content {
                margin-left: 0;
            }
            .content-wrapper {
                padding: 20px 15px;
            }
            .stat-value {
                font-size: 24px;
            }
            .mobile-menu-toggle {
                display: block;
            }
            .top-header {
                padding: 0 15px;
            }
        }

        /* Dark Mode */
        [data-theme="dark"] body {
            background: #111827;
        }

        [data-theme="dark"] .top-header,
        [data-theme="dark"] .stat-card,
        [data-theme="dark"] .data-table,
        [data-theme="dark"] .modal-content,
        [data-theme="dark"] .admin-dropdown,
        [data-theme="dark"] .theme-toggle,
        [data-theme="dark"] .notification-icon {
            background: #1f2937;
            color: #e5e7eb;
        }

        [data-theme="dark"] .page-title h1,
        [data-theme="dark"] .stat-value {
            color: white;
        }

        [data-theme="dark"] .data-table thead th {
            background: #111827;
            color: #e5e7eb;
        }

        [data-theme="dark"] .table {
            color: #e5e7eb;
        }

        /* Animations */
        @keyframes fadeInUp {
            from {
                opacity: 0;
                transform: translateY(20px);
            }
            to {
                opacity: 1;
                transform: translateY(0);
            }
        }

        .fade-in-up {
            animation: fadeInUp 0.5s ease;
        }
    </style>
</head>
<body>
    <div id="loadingOverlay" class="loading-overlay">
        <div class="spinner-border-custom"></div>
    </div>

    <!-- Sidebar -->
    <aside class="sidebar" id="sidebar">
        <div class="sidebar-header">
            <a href="#" class="logo" onclick="loadSection('dashboard'); return false;">
                <div class="logo-icon">
                    <i class="fas fa-truck-fast"></i>
                </div>
                <div class="logo-text">
                    EthioLoad<span>AI</span>
                </div>
            </a>
        </div>
        
        <div class="sidebar-nav">
            <div class="nav-item">
                <a href="#" class="nav-link-custom" data-section="dashboard">
                    <i class="fas fa-chart-line"></i>
                    <span>Dashboard</span>
                    <span class="nav-badge">Live</span>
                </a>
            </div>
            <div class="nav-item">
                <a href="#" class="nav-link-custom" data-section="users">
                    <i class="fas fa-users"></i>
                    <span>Users</span>
                </a>
            </div>
            <div class="nav-item">
                <a href="#" class="nav-link-custom" data-section="vehicles">
                    <i class="fas fa-truck"></i>
                    <span>Vehicles</span>
                </a>
            </div>
            <div class="nav-item">
                <a href="#" class="nav-link-custom" data-section="cargo">
                    <i class="fas fa-boxes"></i>
                    <span>Cargo Requests</span>
                    <span class="nav-badge">AI</span>
                </a>
            </div>
            <div class="nav-item">
                <a href="#" class="nav-link-custom" data-section="bookings">
                    <i class="fas fa-bookmark"></i>
                    <span>Bookings</span>
                </a>
            </div>
            <div class="nav-item">
                <a href="#" class="nav-link-custom" data-section="payments">
                    <i class="fas fa-credit-card"></i>
                    <span>Payments</span>
                </a>
            </div>
            <div class="nav-item">
                <a href="#" class="nav-link-custom" data-section="trips">
                    <i class="fas fa-map-marked-alt"></i>
                    <span>Trips</span>
                </a>
            </div>
            <div class="nav-item">
                <a href="#" class="nav-link-custom" data-section="livetracking">
                    <i class="fas fa-satellite-dish"></i>
                    <span>Live Tracking</span>
                </a>
            </div>
            <div class="nav-item">
                <a href="#" class="nav-link-custom" data-section="backhaul">
                    <i class="fas fa-exchange-alt"></i>
                    <span>Backhaul AI</span>
                    <span class="nav-badge">AI</span>
                </a>
            </div>
            <div class="nav-item">
                <a href="#" class="nav-link-custom" data-section="ratings">
                    <i class="fas fa-star"></i>
                    <span>Ratings</span>
                </a>
            </div>
        </div>

        <div class="sidebar-footer">
            <button id="logoutBtn" class="btn btn-gradient w-100">
                <i class="fas fa-sign-out-alt me-2"></i> Logout
            </button>
        </div>
    </aside>

    <!-- Main Content -->
    <main class="main-content">
        <button class="mobile-menu-toggle" id="mobileMenuToggle">
            <i class="fas fa-bars"></i>
        </button>
        
        <header class="top-header">
            <div class="page-title">
                <h1 id="pageTitle">Dashboard</h1>
                <p id="pageSubtitle">AI-powered logistics command center</p>
            </div>
            <div class="header-actions">
                <div class="theme-toggle" id="themeToggle">
                    <i class="fas fa-moon"></i>
                </div>
                <div class="notifications">
                    <div class="notification-icon">
                        <i class="fas fa-bell"></i>
                        <span class="notification-badge">3</span>
                    </div>
                </div>
                <div class="admin-dropdown" data-bs-toggle="dropdown">
                    <div class="admin-avatar">
                        <i class="fas fa-user-shield"></i>
                    </div>
                    <div>
                        <div class="fw-bold">Admin User</div>
                        <small class="text-muted">Administrator</small>
                    </div>
                </div>
            </div>
        </header>

        <div class="content-wrapper" id="adminContent">
            @yield('content')
        </div>
        <div class="modal fade" id="recordModal" tabindex="-1" aria-labelledby="recordModalLabel" aria-hidden="true">
            <div class="modal-dialog modal-lg modal-dialog-centered">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title" id="recordModalLabel">Record Details</h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                    </div>
                    <div class="modal-body" id="recordModalBody"></div>
                    <div class="modal-footer" id="recordModalFooter">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
                        <button type="button" class="btn btn-primary" id="recordModalSaveButton">Save changes</button>
                    </div>
                </div>
            </div>
        </div>
    </main>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    
    <script>
        // Mobile menu toggle
        const mobileMenuToggle = document.getElementById('mobileMenuToggle');
        const sidebar = document.getElementById('sidebar');
        
        mobileMenuToggle.addEventListener('click', () => {
            sidebar.classList.toggle('mobile-open');
        });

        // Close sidebar when clicking outside on mobile
        document.addEventListener('click', (e) => {
            if (window.innerWidth <= 768) {
                if (!sidebar.contains(e.target) && !mobileMenuToggle.contains(e.target)) {
                    sidebar.classList.remove('mobile-open');
                }
            }
        });

        // Theme Toggle
        const themeToggle = document.getElementById('themeToggle');
        const html = document.documentElement;
        
        themeToggle.addEventListener('click', () => {
            const currentTheme = html.getAttribute('data-theme');
            const newTheme = currentTheme === 'light' ? 'dark' : 'light';
            html.setAttribute('data-theme', newTheme);
            localStorage.setItem('theme', newTheme);
            
            const icon = themeToggle.querySelector('i');
            icon.className = newTheme === 'light' ? 'fas fa-moon' : 'fas fa-sun';
        });

        // Load saved theme
        const savedTheme = localStorage.getItem('theme') || 'light';
        html.setAttribute('data-theme', savedTheme);
        themeToggle.querySelector('i').className = savedTheme === 'light' ? 'fas fa-moon' : 'fas fa-sun';

        // Navigation
        document.querySelectorAll('.nav-link-custom').forEach(link => {
            link.addEventListener('click', function(e) {
                e.preventDefault();
                const section = this.dataset.section;
                
                // Update active state
                document.querySelectorAll('.nav-link-custom').forEach(l => l.classList.remove('active'));
                this.classList.add('active');
                
                // Load content
                loadSection(section);
            });
        });

        const csrfToken = $('meta[name="csrf-token"]').attr('content');
        const apiToken = $('meta[name="api-token"]').attr('content');
        const adminBaseUrl = '/admin';

        // Ensure every AJAX request includes CSRF and marks it as XHR
        $.ajaxSetup({
            headers: {
                'X-CSRF-TOKEN': csrfToken,
                'X-Requested-With': 'XMLHttpRequest'
            }
        });

        // Global AJAX error handler: detect session expiry (419) and prompt re-login
        $(document).ajaxError(function(event, jqxhr) {
            if (jqxhr && jqxhr.status === 419) {
                alert('Session expired. Please log in again.');
                window.location.href = '/admin/login';
            }
        });

        function adminRequest(method, endpoint, payload = null) {
            const url = (endpoint.startsWith('/admin') || endpoint.startsWith('/api')) ? endpoint : adminBaseUrl + endpoint;
            const options = {
                url,
                type: method,
                headers: {
                    'X-CSRF-TOKEN': csrfToken,
                    'Authorization': 'Bearer ' + apiToken,
                    'Accept': 'application/json',
                },
                dataType: 'json'
            };

            if (payload !== null) {
                options.data = JSON.stringify(payload);
                options.contentType = 'application/json';
            }

            return $.ajax(options);
        }

        function loadSection(section) {
            $('#loadingOverlay').fadeIn();
            let request;

            switch(section) {
                case 'dashboard':
                    request = loadDashboard();
                    break;
                case 'users':
                    request = loadUsers();
                    break;
                case 'vehicles':
                    request = loadVehicles();
                    break;
                case 'cargo':
                    request = loadCargoRequests();
                    break;
                case 'bookings':
                    request = loadBookings();
                    break;
                case 'payments':
                    request = loadPayments();
                    break;
                case 'trips':
                    request = loadTrips();
                    break;
                case 'livetracking':
                    request = loadLiveTracking();
                    break;
                case 'backhaul':
                    request = loadBackhaul();
                    break;
                case 'ratings':
                    request = loadRatings();
                    break;
                default:
                    request = loadDashboard();
            }

            $('#pageTitle').text(getTitle(section));
            $('#pageSubtitle').text(getSubtitle(section));

            request.always(() => {
                $('#loadingOverlay').fadeOut();
            });
        }

        function getTitle(section) {
            const titles = {
                dashboard: 'Dashboard',
                users: 'User Management',
                vehicles: 'Fleet Management',
                cargo: 'Cargo Requests',
                bookings: 'Booking Management',
                payments: 'Payment Transactions',
                trips: 'Trip Tracking',
                backhaul: 'AI Backhaul Optimization',
                ratings: 'Ratings & Reviews'
            };
            return titles[section] || 'Dashboard';
        }

        function getSubtitle(section) {
            const subtitles = {
                dashboard: 'Real-time analytics and AI insights',
                users: 'Manage shippers, drivers, and administrators',
                vehicles: 'Track and manage fleet operations',
                cargo: 'AI-powered cargo matching',
                bookings: 'Monitor and manage bookings',
                payments: 'Track payments and commissions',
                trips: 'Live GPS tracking and trip status',
                backhaul: 'AI-optimized return trips for Ethiopia corridor',
                ratings: 'User feedback and ratings'
            };
            return subtitles[section] || 'Management dashboard';
        }

        function loadDashboard() {
            return adminRequest('GET', '/dashboard')
                .done(data => {
                    const counts = data.counts || {};
                    const recent = data.recent || {};
                    const content = `
                        <div class="fade-in-up">
                            <div class="row g-4 mb-4">
                                ${renderStatCard('Total Users', counts.users, 'fas fa-users', '1%', 'trend-up')}
                                ${renderStatCard('Active Vehicles', counts.vehicles, 'fas fa-truck', '1%', 'trend-up')}
                                ${renderStatCard('Cargo Requests', counts.cargo_requests, 'fas fa-box', '1%', 'trend-up')}
                                ${renderStatCard('Bookings', counts.bookings, 'fas fa-chart-line', '1%', 'trend-up')}
                            </div>
                            <div class="row g-4 mb-4">
                                <div class="col-md-8">
                                    <div class="stat-card">
                                        <div class="d-flex justify-content-between align-items-center mb-3">
                                            <h5 class="fw-bold mb-0">Freight Trends - Ethiopia Corridor</h5>
                                            <select class="form-select form-select-sm w-auto" id="trendPeriod">
                                                <option value="7">Last 7 days</option>
                                                <option value="30" selected>Last 30 days</option>
                                                <option value="90">Last 90 days</option>
                                            </select>
                                        </div>
                                        <canvas id="freightChart" height="300"></canvas>
                                    </div>
                                </div>
                                <div class="col-md-4">
                                    <div class="stat-card">
                                        <h5 class="fw-bold mb-3">AI Matching Success</h5>
                                        <canvas id="matchingChart" height="250"></canvas>
                                        <div class="text-center mt-3">
                                            <div class="display-6 fw-bold">%</div>
                                            <small class="text-muted">Success Rate</small>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div class="row g-4">
                                <div class="col-md-6">
                                    <div class="stat-card">
                                        <div class="d-flex justify-content-between align-items-center mb-3">
                                            <h5 class="fw-bold mb-0">Recent Users</h5>
                                            <button class="btn btn-sm btn-outline-primary" onclick="loadSection('users');">See all</button>
                                        </div>
                                        <ul class="list-group">
                                            ${recent.users?.map(user => `<li class="list-group-item d-flex justify-content-between align-items-center">${user.full_name}<span class="badge bg-primary rounded-pill">${user.role}</span></li>`).join('') || '<li class="list-group-item">No users yet</li>'}
                                        </ul>
                                    </div>
                                </div>
                                <div class="col-md-6">
                                    <div class="stat-card">
                                        <div class="d-flex justify-content-between align-items-center mb-3">
                                            <h5 class="fw-bold mb-0">Recent Bookings</h5>
                                            <button class="btn btn-sm btn-outline-primary" onclick="loadSection('bookings');">See all</button>
                                        </div>
                                        <ul class="list-group">
                                            ${recent.bookings?.map(booking => `<li class="list-group-item d-flex justify-content-between align-items-center">Booking #${booking.id}<span class="badge bg-success rounded-pill">${booking.booking_status}</span></li>`).join('') || '<li class="list-group-item">No bookings yet</li>'}
                                        </ul>
                                    </div>
                                </div>
                            </div>
                        </div>
                    `;
                    $('#adminContent').html(content);
                    initializeCharts(data.trends, data.success_rates);
                })
                .fail(() => {
                    $('#adminContent').html('<div class="alert alert-danger">Unable to load dashboard data.</div>');
                });
        }

        function renderStatCard(label, value, icon, change, trendClass) {
            return `
                <div class="col-md-3">
                    <div class="stat-card">
                        <div class="d-flex justify-content-between align-items-start">
                            <div class="stat-icon"><i class="${icon}"></i></div>
                            <div class="stat-change ${trendClass}"><i class="fas fa-arrow-up"></i> ${change}</div>
                        </div>
                        <div class="stat-value">${value ?? 0}</div>
                        <div class="stat-label">${label}</div>
                        <small class="text-muted">Updated now</small>
                    </div>
                </div>
            `;
        }

        function loadUsers() {
            return adminRequest('GET', '/users')
                .done(data => {
                    const rows = (data.users || []).map(user => `
                        <tr>
                            <td>${user.id}</td>
                            <td>${user.full_name}</td>
                            <td>${user.phone}</td>
                            <td>${user.role}</td>
                            <td>${user.email}</td>
                            <td>${new Date(user.created_at).toLocaleDateString()}</td>
                            <td>
                                <button class="btn btn-sm btn-outline-primary me-1" onclick="viewRecord('users', ${user.id})"><i class="fas fa-eye"></i></button>
                                <button class="btn btn-sm btn-outline-danger" onclick="deleteRecord('/users', ${user.id})"><i class="fas fa-trash"></i></button>
                            </td>
                        </tr>
                    `).join('');
                    const content = `
                        <div class="stat-card">
                            <div class="d-flex justify-content-between align-items-center mb-4">
                                <h4 class="fw-bold mb-0">User Management</h4>
                                <div>
                                    <button class="btn btn-sm btn-outline-secondary me-2" onclick="loadSection('users');">Refresh</button>
                                    <button class="btn btn-sm btn-primary" onclick="openCreateModal('users')">Add User</button>
                                </div>
                            </div>
                            <div class="table-responsive">
                                <table class="table data-table">
                                    <thead><tr><th>ID</th><th>Name</th><th>Phone</th><th>Role</th><th>Email</th><th>Joined</th><th>Actions</th></tr></thead>
                                    <tbody>${rows}</tbody>
                                </table>
                            </div>
                        </div>
                    `;
                    $('#adminContent').html(content);
                    initializeDataTable();
                })
                .fail(() => {
                    $('#adminContent').html('<div class="alert alert-danger">Unable to load users.</div>');
                });
        }

        function loadVehicles() {
            return adminRequest('GET', '/vehicles')
                .done(data => {
                    const rows = (data.vehicles || []).map(vehicle => `
                        <tr>
                            <td>${vehicle.plate_number}</td>
                            <td>${vehicle.user_id}</td>
                            <td>${vehicle.truck_type}</td>
                            <td>${vehicle.capacity}</td>
                            <td>${vehicle.availability_status}</td>
                            <td>${vehicle.current_city}</td>
                            <td>
                                <button class="btn btn-sm btn-outline-primary me-1" onclick="viewRecord('vehicles', ${vehicle.id})"><i class="fas fa-eye"></i></button>
                                <button class="btn btn-sm btn-outline-danger" onclick="deleteRecord('/vehicles', ${vehicle.id})"><i class="fas fa-trash"></i></button>
                            </td>
                        </tr>
                    `).join('');
                    const content = `
                        <div class="stat-card">
                            <div class="d-flex justify-content-between align-items-center mb-4">
                                <h4 class="fw-bold mb-0">Fleet Management</h4>
                                <div>
                                    <button class="btn btn-sm btn-outline-secondary me-2" onclick="loadSection('vehicles');">Refresh</button>
                                    <button class="btn btn-sm btn-primary" onclick="openCreateModal('vehicles')">Add Vehicle</button>
                                </div>
                            </div>
                            <div class="table-responsive">
                                <table class="table data-table">
                                    <thead><tr><th>Plate Number</th><th>Owner ID</th><th>Type</th><th>Capacity</th><th>Status</th><th>Location</th><th>Actions</th></tr></thead>
                                    <tbody>${rows}</tbody>
                                </table>
                            </div>
                        </div>
                    `;
                    $('#adminContent').html(content);
                    initializeDataTable();
                })
                .fail(() => {
                    $('#adminContent').html('<div class="alert alert-danger">Unable to load vehicles.</div>');
                });
        }

        function loadCargoRequests() {
            return adminRequest('GET', '/cargo-requests')
                .done(data => {
                    const rows = (data.cargo_requests || []).map(cargo => `
                        <tr>
                            <td>${cargo.id}</td>
                            <td>${cargo.user_id}</td>
                            <td>${cargo.pickup_location}</td>
                            <td>${cargo.destination}</td>
                            <td>${cargo.weight}</td>
                            <td>${cargo.status}</td>
                            <td>
                                <button class="btn btn-sm btn-outline-primary me-1" onclick="viewRecord('cargo-requests', ${cargo.id})"><i class="fas fa-eye"></i></button>
                                <button class="btn btn-sm btn-outline-danger" onclick="deleteRecord('/cargo-requests', ${cargo.id})"><i class="fas fa-trash"></i></button>
                            </td>
                        </tr>
                    `).join('');
                    const content = `
                        <div class="stat-card">
                            <div class="d-flex justify-content-between align-items-center mb-4">
                                <h4 class="fw-bold mb-0">Cargo Requests</h4>
                            </div>
                            <div class="table-responsive">
                                <table class="table data-table">
                                    <thead><tr><th>ID</th><th>Shipper ID</th><th>Pickup</th><th>Destination</th><th>Weight</th><th>Status</th><th>Actions</th></tr></thead>
                                    <tbody>${rows}</tbody>
                                </table>
                            </div>
                        </div>
                    `;
                    $('#adminContent').html(content);
                    initializeDataTable();
                })
                .fail(() => {
                    $('#adminContent').html('<div class="alert alert-danger">Unable to load cargo requests.</div>');
                });
        }

        function loadBookings() {
            return adminRequest('GET', '/bookings')
                .done(data => {
                    const rows = (data.bookings || []).map(booking => `
                        <tr>
                            <td>${booking.id}</td>
                            <td>${booking.cargo_id}</td>
                            <td>${booking.driver_id}</td>
                            <td>${booking.booking_status}</td>
                            <td>${booking.estimated_price}</td>
                            <td>
                                <button class="btn btn-sm btn-outline-primary me-1" onclick="viewRecord('bookings', ${booking.id})"><i class="fas fa-eye"></i></button>
                                <button class="btn btn-sm btn-outline-danger" onclick="deleteRecord('/bookings', ${booking.id})"><i class="fas fa-trash"></i></button>
                            </td>
                        </tr>
                    `).join('');
                    const content = `
                        <div class="stat-card">
                            <div class="d-flex justify-content-between align-items-center mb-4">
                                <h4 class="fw-bold mb-0">Booking Management</h4>
                            </div>
                            <div class="table-responsive">
                                <table class="table data-table">
                                    <thead><tr><th>ID</th><th>Cargo ID</th><th>Driver ID</th><th>Status</th><th>Price</th><th>Actions</th></tr></thead>
                                    <tbody>${rows}</tbody>
                                </table>
                            </div>
                        </div>
                    `;
                    $('#adminContent').html(content);
                    initializeDataTable();
                })
                .fail(() => {
                    $('#adminContent').html('<div class="alert alert-danger">Unable to load bookings.</div>');
                });
        }

        function loadPayments() {
            return adminRequest('GET', '/payments')
                .done(data => {
                    const rows = (data.payments || []).map(payment => `
                        <tr>
                            <td>${payment.id}</td>
                            <td>${payment.booking_id}</td>
                            <td>${payment.amount}</td>
                            <td>${payment.payment_method ?? 'N/A'}</td>
                            <td>${payment.status ?? 'N/A'}</td>
                            <td>
                                <button class="btn btn-sm btn-outline-primary me-1" onclick="viewRecord('payments', ${payment.id})"><i class="fas fa-eye"></i></button>
                                <button class="btn btn-sm btn-outline-danger" onclick="deleteRecord('/payments', ${payment.id})"><i class="fas fa-trash"></i></button>
                            </td>
                        </tr>
                    `).join('');
                    const content = `
                        <div class="stat-card">
                            <div class="d-flex justify-content-between align-items-center mb-4">
                                <h4 class="fw-bold mb-0">Payment Transactions</h4>
                            </div>
                            <div class="table-responsive">
                                <table class="table data-table">
                                    <thead><tr><th>ID</th><th>Booking ID</th><th>Amount</th><th>Method</th><th>Status</th><th>Actions</th></tr></thead>
                                    <tbody>${rows}</tbody>
                                </table>
                            </div>
                        </div>
                    `;
                    $('#adminContent').html(content);
                    initializeDataTable();
                })
                .fail(() => {
                    $('#adminContent').html('<div class="alert alert-danger">Unable to load payments.</div>');
                });
        }

        function loadTrips() {
            return adminRequest('GET', '/trips')
                .done(data => {
                    const rows = (data.trips || []).map(trip => `
                        <tr>
                            <td>${trip.id}</td>
                            <td>${trip.driver_id}</td>
                            <td>${trip.booking_id}</td>
                            <td>${trip.status}</td>
                            <td>${trip.current_location ?? 'N/A'}</td>
                            <td>
                                <button class="btn btn-sm btn-outline-primary me-1" onclick="viewRecord('trips', ${trip.id})"><i class="fas fa-eye"></i></button>
                                <button class="btn btn-sm btn-outline-danger" onclick="deleteRecord('/trips', ${trip.id})"><i class="fas fa-trash"></i></button>
                            </td>
                        </tr>
                    `).join('');
                    const content = `
                        <div class="stat-card">
                            <div class="d-flex justify-content-between align-items-center mb-4">
                                <h4 class="fw-bold mb-0">Trip Tracking</h4>
                            </div>
                            <div class="table-responsive">
                                <table class="table data-table">
                                    <thead><tr><th>ID</th><th>Driver ID</th><th>Booking ID</th><th>Status</th><th>Current Location</th><th>Actions</th></tr></thead>
                                    <tbody>${rows}</tbody>
                                </table>
                            </div>
                        </div>
                    `;
                    $('#adminContent').html(content);
                    initializeDataTable();
                })
                .fail(() => {
                    $('#adminContent').html('<div class="alert alert-danger">Unable to load trips.</div>');
                });
        }

        function loadBackhaul() {
            const content = `
                <div class="row g-4">
                    <div class="col-md-12">
                        <div class="stat-card">
                            <div class="d-flex justify-content-between align-items-center mb-4">
                                <h4 class="fw-bold mb-0"><i class="fas fa-brain me-2"></i>AI Backhaul Optimization</h4>
                                <span class="badge bg-success">AI Active</span>
                            </div>
                            <div class="alert alert-info">
                                <i class="fas fa-info-circle me-2"></i> AI is analyzing return trips on the Ethiopia corridor to reduce empty runs.
                            </div>
                        </div>
                    </div>
                    <div class="col-md-6">
                        <div class="stat-card">
                            <h5 class="fw-bold mb-3">Recommended Return Loads</h5>
                            <div class="list-group" id="backhaulList"></div>
                        </div>
                    </div>
                    <div class="col-md-6">
                        <div class="stat-card">
                            <h5 class="fw-bold mb-3">Savings Statistics</h5>
                            <canvas id="savingsChart" height="200"></canvas>
                            <div class="text-center mt-3">
                                <h3 class="text-success">342</h3>
                                <small>Empty trips saved this month</small>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            $('#adminContent').html(content);
            
            $('#backhaulList').html('<div class="text-center p-3"><i class="fas fa-spinner fa-spin"></i> Loading AI matches...</div>');
            
            adminRequest('POST', '/api/ai/backhaul-opportunities', {
                truck_id: 1,
                current_location: 'Gondar',
                destination: 'Addis Ababa'
            }).done(data => {
                const ops = data.opportunities || [];
                if (ops.length === 0) {
                    $('#backhaulList').html('<div class="alert alert-warning text-center m-3">No return loads available from DB.</div>');
                } else {
                    const rows = ops.map(opt => `
                        <div class="list-group-item border-0 mb-2 rounded-3 shadow-sm">
                            <div class="d-flex justify-content-between align-items-center">
                                <div><i class="fas fa-box text-primary me-2"></i><strong>${opt.pickup_location} &rarr; ${opt.destination}</strong><br><small>${opt.weight} tons (ID: ${opt.cargo_id})</small></div>
                                <div><span class="badge-custom badge-active">${(opt.score * 100).toFixed(0)}% Match</span><br><small class="text-success">${opt.price} ETB</small></div>
                            </div>
                        </div>
                    `).join('');
                    $('#backhaulList').html(rows);
                }
            }).fail(() => {
                $('#backhaulList').html('<div class="alert alert-danger m-3">Failed to load AI data.</div>');
            });
            initializeCharts();
            return $.Deferred().resolve();
        }

        let liveMap = null;
        let truckMarkers = {};

        function loadLiveTracking() {
            const content = `
                <div class="stat-card">
                    <div class="d-flex justify-content-between align-items-center mb-4">
                        <h4 class="fw-bold mb-0">Live GPS Tracking</h4>
                        <div>
                            <input type="text" id="trackTripId" class="form-control form-control-sm d-inline-block w-auto" placeholder="Trip ID">
                            <button class="btn btn-sm btn-primary" onclick="startTracking()">Track Trip</button>
                        </div>
                    </div>
                    <div id="liveMapContainer" style="height: 500px; border-radius: 8px; border: 1px solid #ddd;"></div>
                </div>
            `;
            $('#adminContent').html(content);

            // Initialize Leaflet Map
            if (liveMap !== null) {
                liveMap.remove();
            }
            liveMap = L.map('liveMapContainer').setView([9.0192, 38.7525], 6); // Centered on Ethiopia
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '&copy; OpenStreetMap contributors'
            }).addTo(liveMap);

            return $.Deferred().resolve();
        }

        window.startTracking = function() {
            const tripId = $('#trackTripId').val();
            if (!tripId) return alert('Enter a Trip ID');

            if (!window.Echo) {
                alert("WebSocket not configured. Please refresh.");
                return;
            }

            // Fetch initial trip data to plot current location
            adminRequest('GET', `/trips/${tripId}`)
                .done(data => {
                    let gps = { lat: 9.0192, lng: 38.7525 };
                    try {
                        const parsed = JSON.parse(data.trip.gps_tracking_data);
                        if (parsed && parsed.lat && parsed.lng) gps = parsed;
                    } catch (e) {}

                    if (truckMarkers[tripId]) liveMap.removeLayer(truckMarkers[tripId]);
                    
                    truckMarkers[tripId] = L.marker([gps.lat, gps.lng]).addTo(liveMap)
                        .bindPopup(`<b>Trip ${tripId}</b><br>Status: ${data.trip.trip_status}`).openPopup();
                    
                    liveMap.flyTo([gps.lat, gps.lng], 14);

                    // Subscribe to the private trip channel
                    window.Echo.private(`trip.${tripId}`)
                        .listen('TripLocationUpdated', (e) => {
                            console.log('Location update received:', e);
                            const newLatLng = [e.lat, e.lng];
                            truckMarkers[tripId].setLatLng(newLatLng);
                            liveMap.panTo(newLatLng);
                        });
                    
                    alert(`Started live tracking for Trip ${tripId}`);
                })
                .fail(() => alert('Trip not found or access denied.'));
        };

        function loadRatings() {
            return adminRequest('GET', '/ratings')
                .done(data => {
                    const rows = (data.ratings || []).map(rating => `
                        <tr>
                            <td>${rating.id}</td>
                            <td>${rating.booking_id}</td>
                            <td>${rating.user_id}</td>
                            <td>${rating.score}</td>
                            <td>${rating.comment ?? ''}</td>
                            <td>
                                <button class="btn btn-sm btn-outline-primary me-1" onclick="viewRecord('ratings', ${rating.id})"><i class="fas fa-eye"></i></button>
                                <button class="btn btn-sm btn-outline-danger" onclick="deleteRecord('/ratings', ${rating.id})"><i class="fas fa-trash"></i></button>
                            </td>
                        </tr>
                    `).join('');
                    const content = `
                        <div class="stat-card">
                            <div class="d-flex justify-content-between align-items-center mb-4">
                                <h4 class="fw-bold mb-0">Ratings & Reviews</h4>
                            </div>
                            <div class="table-responsive">
                                <table class="table data-table">
                                    <thead><tr><th>ID</th><th>Booking ID</th><th>User ID</th><th>Score</th><th>Comment</th><th>Actions</th></tr></thead>
                                    <tbody>${rows}</tbody>
                                </table>
                            </div>
                        </div>
                    `;
                    $('#adminContent').html(content);
                    initializeDataTable();
                })
                .fail(() => {
                    $('#adminContent').html('<div class="alert alert-danger">Unable to load ratings.</div>');
                });
        }

        function viewRecord(type, id) {
            fetchRecord(type, id).done(response => {
                const record = extractRecord(type, response);
                const title = `${capitalize(type)} #${id}`;
                const body = renderRecordForm(type, record);
                $('#recordModalLabel').text(title);
                $('#recordModalBody').html(body);
                $('#recordModalSaveButton').off('click').on('click', () => saveRecord(type, id));
                const modal = new bootstrap.Modal(document.getElementById('recordModal'));
                modal.show();
            }).fail(() => {
                alert('Unable to load record details.');
            });
        }

        function deleteRecord(endpoint, id) {
            if (!confirm('Delete this record?')) {
                return;
            }
            adminRequest('DELETE', `${endpoint}/${id}`)
                .done(() => {
                    loadSection(getCurrentSection());
                })
                .fail(() => {
                    alert('Unable to delete record.');
                });
        }

        function fetchRecord(type, id) {
            return adminRequest('GET', `/${type}/${id}`);
        }

        function saveRecord(type, id) {
            const form = document.getElementById('recordEditForm');
            const payload = {};
            $(form).serializeArray().forEach(field => {
                payload[field.name] = field.value;
            });

            adminRequest('PUT', `/${type}/${id}`, payload)
                .done(() => {
                    const modal = bootstrap.Modal.getInstance(document.getElementById('recordModal'));
                    modal.hide();
                    loadSection(getCurrentSection());
                })
                .fail(() => {
                    alert('Unable to save changes.');
                });
        }

        function openCreateModal(type) {
            const title = type === 'users' ? 'Add User' : (type === 'vehicles' ? 'Add Vehicle' : 'Create Record');
            $('#recordModalLabel').text(title);
            $('#recordModalBody').html(renderCreateForm(type));
            $('#recordModalSaveButton').off('click').on('click', () => createRecord(type));
            const modal = new bootstrap.Modal(document.getElementById('recordModal'));
            modal.show();
        }

        function createRecord(type) {
            const form = document.getElementById('recordEditForm');
            const payload = {};
            $(form).serializeArray().forEach(field => {
                payload[field.name] = field.value;
            });

            adminRequest('POST', `/${type}`, payload)
                .done(() => {
                    const modal = bootstrap.Modal.getInstance(document.getElementById('recordModal'));
                    modal.hide();
                    loadSection(getCurrentSection());
                })
                .fail((jqxhr) => {
                    const msg = jqxhr.responseJSON?.message || 'Unable to create record.';
                    alert(msg);
                });
        }

        function renderCreateForm(type) {
            if (type === 'users') {
                return `
                    <form id="recordEditForm">
                        <div class="mb-3">
                            <label class="form-label">Full name</label>
                            <input name="full_name" class="form-control" />
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Phone</label>
                            <input name="phone" class="form-control" />
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Role</label>
                            <select name="role" class="form-select" required>
                                <option value="">Select Role</option>
                                <option value="shipper">Shipper</option>
                                <option value="driver">Driver</option>
                                <option value="admin">Admin</option>
                            </select>
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Email</label>
                            <input name="email" class="form-control" type="email" />
                        </div>
                    </form>
                `;
            }

            if (type === 'vehicles') {
                return `
                    <form id="recordEditForm">
                        <div class="mb-3">
                            <label class="form-label">Plate Number</label>
                            <input name="plate_number" class="form-control" />
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Owner User ID</label>
                            <input name="user_id" class="form-control" />
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Truck Type</label>
                            <select name="truck_type" class="form-select" required>
                                <option value="">Select Truck Type</option>
                                <option value="Small">Small</option>
                                <option value="Medium">Medium</option>
                                <option value="Large">Large</option>
                                <option value="Long Vehicle (Very Large)">Long Vehicle (Very Large)</option>
                            </select>
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Capacity</label>
                            <input name="capacity" class="form-control" />
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Availability Status</label>
                            <input name="availability_status" class="form-control" value="available" />
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Current City</label>
                            <input name="current_city" class="form-control" />
                        </div>
                    </form>
                `;
            }

            return `<form id="recordEditForm"><div class="mb-3"><input name="name" class="form-control" /></div></form>`;
        }

        function renderRecordForm(type, record) {
            const readOnly = ['id', 'created_at', 'updated_at'];
            const editableKeys = Object.keys(record).filter(key => !readOnly.includes(key) && (record[key] === null || typeof record[key] !== 'object'));
            const fields = editableKeys.map(key => {
                const value = record[key] ?? '';
                return `
                    <div class="mb-3">
                        <label class="form-label text-capitalize" for="${key}">${key.replace(/_/g, ' ')}</label>
                        <input type="text" class="form-control" name="${key}" id="${key}" value="${value}">
                    </div>
                `;
            }).join('');
            return `
                <form id="recordEditForm">
                    ${fields}
                </form>
            `;
        }

        function extractRecord(type, response) {
            const singular = type.endsWith('s') ? type.slice(0, -1) : type;
            const underscored = singular.replace(/-/g, '_');
            return response[singular] || response[underscored] || response;
        }

        function capitalize(value) {
            return value.charAt(0).toUpperCase() + value.slice(1).replace(/-/g, ' ');
        }

        function getCurrentSection() {
            return document.querySelector('.nav-link-custom.active')?.dataset.section || 'dashboard';
        }

        function initializeDataTable() {
            const table = $('#adminContent').find('table.data-table');
            if (!table.length) {
                return;
            }

            if ($.fn.DataTable.isDataTable(table)) {
                table.DataTable().destroy();
            }

            table.DataTable({
                pageLength: 10,
                responsive: true,
                destroy: true,
                autoWidth: false,
            });
        }

        function initializeCharts(trends = null, successRates = null) {
            const ctx1 = document.getElementById('freightChart')?.getContext('2d');
            const ctx2 = document.getElementById('matchingChart')?.getContext('2d');
            const ctx3 = document.getElementById('savingsChart')?.getContext('2d');
            
            if (ctx1) {
                new Chart(ctx1, {
                    type: 'line',
                    data: {
                        labels: trends ? trends.labels : ['Week 1', 'Week 2', 'Week 3', 'Week 4'],
                        datasets: [{
                            label: 'Bookings',
                            data: trends ? trends.data : [65, 72, 88, 95],
                            borderColor: '#4361ee',
                            backgroundColor: 'rgba(67,97,238,0.1)',
                            tension: 0.4,
                            fill: true
                        }]
                    },
                    options: { responsive: true, maintainAspectRatio: true }
                });
            }
            
            if (ctx2) {
                new Chart(ctx2, {
                    type: 'doughnut',
                    data: {
                        labels: successRates ? successRates.labels : ['AI Matched', 'Manual'],
                        datasets: [{ data: successRates ? successRates.data : [86, 14], backgroundColor: ['#4361ee', '#e5e7eb'] }]
                    },
                    options: { responsive: true }
                });
            }
            
            if (ctx3) {
                new Chart(ctx3, {
                    type: 'bar',
                    data: {
                        labels: ['Jan', 'Feb', 'Mar'],
                        datasets: [{ label: 'Trips Saved', data: [45, 67, 89], backgroundColor: '#10b981' }]
                    },
                    options: { responsive: true }
                });
            }
        }

        // Load initial dashboard
        loadSection('dashboard');

        // Logout
        $('#logoutBtn').click(function() {
            if (!confirm('Are you sure you want to logout?')) {
                return;
            }
            $.ajax({
                url: '/admin/logout',
                type: 'POST',
                headers: {
                    'X-CSRF-TOKEN': csrfToken,
                },
            }).done(() => {
                window.location.href = '/admin/login';
            }).fail(() => {
                alert('Logout failed. Please try again.');
            });
        });
    </script>
    
    @stack('scripts')
    <script>
        document.addEventListener('DOMContentLoaded', () => {
            if (typeof Pusher !== 'undefined' && typeof Echo !== 'undefined') {
                window.Pusher = Pusher;
                const apiToken = document.querySelector('meta[name="api-token"]')?.getAttribute('content');
                window.Echo = new Echo({
                    broadcaster: 'reverb',
                    key: '{{ env("REVERB_APP_KEY", "ethioloadai_key") }}',
                    wsHost: window.location.hostname,
                    wsPort: {{ env('REVERB_PORT', 8080) }},
                    wssPort: {{ env('REVERB_PORT', 8080) }},
                    forceTLS: false,
                    enabledTransports: ['ws', 'wss'],
                    auth: {
                        headers: {
                            Authorization: 'Bearer ' + apiToken
                        }
                    }
                });
            }
        });
    </script>
</body>
</html>