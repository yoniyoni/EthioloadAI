import math
from ..schemas.route_optimizer import RouteOptimizerRequest, RouteOptimizerResponse

# Approximate Latitude / Longitude for Ethiopian cities
CITY_COORDINATES = {
    'addis ababa': (9.0300, 38.7400),
    'adama': (8.5400, 39.2700),
    'hawassa': (7.0500, 38.4800),
    'bahir dar': (11.5900, 37.3900),
    'gondar': (12.6000, 37.4600),
    'mekele': (13.4900, 39.4700),
    'dire dawa': (9.6000, 41.8600),
    'debre markos': (10.3300, 37.7200),
    'metema': (12.9600, 36.1600), # Corridor city
    'gorgora': (12.2400, 37.3000),
}

def normalize_city(name: str) -> str:
    return name.strip().lower()

def haversine_distance(coord1, coord2):
    # Very basic approximation: Euclidean * 111 for km
    lat1, lon1 = coord1
    lat2, lon2 = coord2
    return math.sqrt((lat2 - lat1)**2 + (lon2 - lon1)**2) * 111.0

def get_coord(city_name: str):
    normalized = normalize_city(city_name)
    # Default to Addis Ababa if unknown
    return CITY_COORDINATES.get(normalized, CITY_COORDINATES['addis ababa'])

def optimize_route(request: RouteOptimizerRequest) -> RouteOptimizerResponse:
    unvisited = request.waypoints.copy()
    current_location = request.origin
    
    optimized_waypoints = []
    total_distance = 0.0
    
    # Nearest Neighbor Algorithm
    while unvisited:
        curr_coord = get_coord(current_location)
        
        # Find closest waypoint
        closest_wp = None
        min_dist = float('inf')
        
        for wp in unvisited:
            wp_coord = get_coord(wp)
            dist = haversine_distance(curr_coord, wp_coord)
            if dist < min_dist:
                min_dist = dist
                closest_wp = wp
                
        # Move to closest
        optimized_waypoints.append(closest_wp)
        total_distance += min_dist
        current_location = closest_wp
        unvisited.remove(closest_wp)
        
    # Finally go to destination
    last_wp_coord = get_coord(current_location)
    dest_coord = get_coord(request.destination)
    total_distance += haversine_distance(last_wp_coord, dest_coord)
    
    final_route = [request.origin] + optimized_waypoints + [request.destination]
    
    # Estimate time (assuming average speed of 50 km/h due to terrain)
    estimated_time_min = (total_distance / 50.0) * 60
    
    return RouteOptimizerResponse(
        optimized_route=final_route,
        total_distance_km=round(total_distance, 2),
        estimated_time_min=round(estimated_time_min, 0)
    )

