from ..schemas.route_optimizer import RouteOptimizerRequest, RouteOptimizerResponse
from ._geo import road_distance_km, CITY_COORDS, normalize


def _coord(city: str):
    """Return (lat, lng) for a city, defaulting to Addis Ababa."""
    return CITY_COORDS.get(normalize(city), CITY_COORDS['addis ababa'])


def optimize_route(request: RouteOptimizerRequest) -> RouteOptimizerResponse:
    unvisited = request.waypoints.copy()
    current_location = request.origin

    optimized_waypoints = []
    total_distance = 0.0

    # Nearest-neighbour greedy algorithm
    while unvisited:
        closest_wp = min(
            unvisited,
            key=lambda wp: road_distance_km(current_location, wp),
        )
        leg_km = road_distance_km(current_location, closest_wp)
        total_distance += leg_km
        optimized_waypoints.append(closest_wp)
        current_location = closest_wp
        unvisited.remove(closest_wp)

    total_distance += road_distance_km(current_location, request.destination)

    final_route = [request.origin] + optimized_waypoints + [request.destination]
    estimated_time_min = round((total_distance / 50.0) * 60)   # 50 km/h avg

    return RouteOptimizerResponse(
        optimized_route=final_route,
        total_distance_km=round(total_distance, 2),
        estimated_time_min=estimated_time_min,
    )
