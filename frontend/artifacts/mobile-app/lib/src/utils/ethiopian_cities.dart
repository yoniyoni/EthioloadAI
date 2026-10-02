import 'package:latlong2/latlong.dart';

const Map<String, LatLng> kEthiopianCities = {
  'Addis Ababa':  LatLng(9.0320,  38.7469),
  'Gondar':       LatLng(12.6030, 37.4521),
  'Bahir Dar':    LatLng(11.5931, 37.3911),
  'Mekele':       LatLng(13.4967, 39.4697),
  'Hawassa':      LatLng(7.0504,  38.4955),
  'Dire Dawa':    LatLng(9.5931,  41.8661),
  'Jimma':        LatLng(7.6731,  36.8346),
  'Adama':        LatLng(8.5400,  39.2700),
  'Dessie':       LatLng(11.1333, 39.6333),
  'Debre Markos': LatLng(10.3333, 37.7167),
  'Debre Tabor':  LatLng(11.8500, 38.0167),
  'Humera':       LatLng(14.3000, 36.6000),
  'Metema':       LatLng(12.8500, 36.2000),
  'Shire':        LatLng(14.1000, 38.2833),
  'Axum':         LatLng(14.1200, 38.7200),
  'Adigrat':      LatLng(14.2667, 39.4500),
  'Lalibela':     LatLng(12.0333, 39.0500),
  'Woldia':       LatLng(11.8333, 39.6000),
  'Kombolcha':    LatLng(11.0833, 39.7333),
  'Addis Zemen':  LatLng(12.1333, 37.7833),
  'Woreta':       LatLng(11.9167, 37.7000),
  'Injibara':     LatLng(10.9833, 36.9833),
  'Bure':         LatLng(10.7000, 37.0667),
  'Arba Minch':   LatLng(6.0333,  37.5500),
  'Shashamane':   LatLng(7.2000,  38.5833),
  'Shashemene':   LatLng(7.2000,  38.5833),
  'Dilla':        LatLng(6.4167,  38.3167),
  'Asela':        LatLng(7.9500,  39.1333),
  'Asella':       LatLng(7.9500,  39.1333),
  'Jijiga':       LatLng(9.3500,  42.8000),
  'Harar':        LatLng(9.3167,  42.1333),
  'Nekemte':      LatLng(9.0833,  36.5500),
  'Ambo':         LatLng(8.9833,  37.8500),
  'Hosaena':      LatLng(7.5500,  37.8500),
  'Butajira':     LatLng(8.1167,  38.3667),
  'Debre Birhan': LatLng(9.6833,  39.5333),
  'Gambela':      LatLng(8.2500,  34.5833),
  'Assosa':       LatLng(10.0667, 34.5333),
  'Gimbi':        LatLng(9.1667,  35.8167),
  'Welkite':      LatLng(8.2833,  37.7833),
  'Motta':        LatLng(11.0833, 37.8667),
  'Finote Selam': LatLng(10.7000, 37.2667),
  'Goba':         LatLng(7.0000,  39.9833),
  'Robe':         LatLng(7.1167,  40.0000),
  'Bale Robe':    LatLng(7.1167,  40.0000),
  'Yirgalem':     LatLng(6.7500,  38.4000),
  'Adwa':         LatLng(14.1667, 38.9000),
  'Bishoftu':     LatLng(8.7500,  38.9833),
  'Moyale':       LatLng(3.5333,  39.0500),
  'Kebri Dahar':  LatLng(6.7500,  44.2833),
  'Sodo':         LatLng(6.8500,  37.7500),
};

LatLng? cityCoordinates(String cityName) {
  final trimmed = cityName.trim();
  if (trimmed.isEmpty) return null;

  // 1. Exact match
  if (kEthiopianCities.containsKey(trimmed)) return kEthiopianCities[trimmed];

  // 2. Strip after " / " or "," (e.g. "Adama / Nazret" → "Adama")
  final short = trimmed.split(RegExp(r'\s*[/,]\s*')).first.trim();
  if (short != trimmed && kEthiopianCities.containsKey(short)) {
    return kEthiopianCities[short];
  }

  // 3. Case-insensitive substring fallback
  final lc = trimmed.toLowerCase();
  for (final entry in kEthiopianCities.entries) {
    final k = entry.key.toLowerCase();
    if (k.contains(lc) || lc.contains(k)) return entry.value;
  }

  return null;
}
