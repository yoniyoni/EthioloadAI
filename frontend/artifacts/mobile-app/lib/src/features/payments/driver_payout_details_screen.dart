import 'package:flutter/material.dart';
import '../shared/widgets/unsupported_feature_notice.dart';

class DriverPayoutDetailsScreen extends StatelessWidget {
  const DriverPayoutDetailsScreen({super.key});

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('Payout Bank Account')),
        body: const UnsupportedFeatureNotice(
          title: 'Payout details unavailable',
          message:
              'Bank selection and payout-account updates are not supported '
              'by the Spring-backed demo. Do not enter or submit bank details.',
          icon: Icons.account_balance_outlined,
        ),
      );
}
