import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';

/// MapTiler API key. This is a client-embedded key (restricted via allowlist
/// on the MapTiler dashboard, not a server secret) — rotate here if it's
/// ever regenerated.
const String kMapTilerApiKey = 'W7vzVSWrl0ulTMyimxap';

/// Raster tile URL template for flutter_map's TileLayer.urlTemplate.
/// Replaces the previous direct tile.openstreetmap.org hotlink, which
/// OpenStreetMap's usage policy forbids for distributed apps and which was
/// actively blocking requests (403 "Access Blocked").
const String kMapTileUrlTemplate =
    'https://api.maptiler.com/maps/streets-v2/{z}/{x}/{y}.png?key=$kMapTilerApiKey';

/// Required MapTiler + OpenStreetMap attribution. Add as the last child of
/// every FlutterMap that uses [kMapTileUrlTemplate].
const Widget kMapAttribution = SimpleAttributionWidget(
  source: Text('MapTiler © OpenStreetMap contributors'),
  alignment: Alignment.bottomLeft,
);
