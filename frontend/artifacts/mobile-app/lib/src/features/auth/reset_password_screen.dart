import 'package:flutter/material.dart';
import '../shared/widgets/shared_widgets.dart';
import '../shared/widgets/unsupported_feature_notice.dart';

class ResetPasswordScreen extends StatelessWidget {
  final String? identifier;

  const ResetPasswordScreen({super.key, this.identifier});

  @override
  Widget build(BuildContext context) => const Scaffold(
        appBar: EthioAppBar(title: 'Reset Password'),
        body: UnsupportedFeatureNotice(
          title: 'Password reset unavailable',
          message:
              'Password reset is not supported by the Spring-backed demo. '
              'No reset code or new password will be submitted.',
          icon: Icons.lock_reset,
        ),
      );
}
