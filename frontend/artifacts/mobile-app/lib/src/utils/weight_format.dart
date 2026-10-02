/// 1 metric ton = 10 kuntal (100kg each) — the unit Ethiopian shippers and
/// drivers actually think in. The backend keeps storing and calculating in
/// tons throughout (vehicle capacity checks, AiController's price-prediction
/// formulas, BackhaulService's scoring) — this is purely a presentation-layer
/// conversion at the UI boundary. Applies equally to cargo weight and
/// vehicle capacity, since both are the same kind of figure and showing one
/// in kuntal while the other stayed in tons on the same screen would be
/// inconsistent.
class WeightFormat {
  static double tonsToKuntal(double tons) => tons * 10;

  static double kuntalToTons(double kuntal) => kuntal / 10;

  /// e.g. "50 kuntal" or "37.5 kuntal" — for display (list rows, detail
  /// screens, cards). Drops the decimal when it's a whole number.
  static String display(double tons) {
    final kuntal = tonsToKuntal(tons);
    final rounded = kuntal.toStringAsFixed(kuntal % 1 == 0 ? 0 : 1);
    return '$rounded kuntal';
  }
}
