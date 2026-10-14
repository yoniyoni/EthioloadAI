import 'package:flutter/material.dart';
import '../shared/widgets/shared_widgets.dart';
import '../shared/widgets/unsupported_feature_notice.dart';

class ForgotPasswordScreen extends StatelessWidget {
  const ForgotPasswordScreen({super.key});

  @override
  Widget build(BuildContext context) => const Scaffold(
        appBar: EthioAppBar(title: 'Reset Password'),
        body: UnsupportedFeatureNotice(
          title: 'Password recovery unavailable',
          message:
              'Password recovery is not supported by the Spring-backed demo. '
              'No reset code can be requested.',
          icon: Icons.lock_reset,
        ),
      );
}
