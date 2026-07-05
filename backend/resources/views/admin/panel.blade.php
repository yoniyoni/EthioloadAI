@extends('admin.layout')

@section('title', 'Dashboard')

@section('content')
<!-- Content is dynamically loaded by JavaScript -->
<div id="dynamicContent"></div>

@push('scripts')
<script>
    // The main layout already handles all the dynamic content loading
    // This file just extends the layout
    console.log('Admin panel loaded');
</script>
@endpush
@endsection