import 'package:ethiopian_datetime/ethiopian_datetime.dart';

/// Converts a standard (Gregorian) DateTime to its Ethiopian-calendar
/// display string. The backend stores and sends plain Gregorian
/// dates/timestamps throughout (CargoRequest.bid_deadline/preferred_date,
/// Carbon-based) — this is purely how the mobile app *shows* them to
/// shippers and drivers. Mobile app only — the admin panel stays Gregorian,
/// standard practice for an ops-facing tool.
class EthiopianDate {
  static String display(DateTime date) =>
      ETDateFormat('dd MMMM yyyy').format(date.convertToEthiopian());

  static String displayWithTime(DateTime date) =>
      ETDateFormat('dd MMMM yyyy, hh:mm a').format(date.convertToEthiopian());
}
