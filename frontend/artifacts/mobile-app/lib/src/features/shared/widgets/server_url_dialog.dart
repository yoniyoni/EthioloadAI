import 'package:flutter/material.dart';

class ServerUrlDialog extends StatelessWidget {
  const ServerUrlDialog({super.key});

  @override
  Widget build(BuildContext context) => AlertDialog(
        title: const Text('Server URL'),
        content: const Text(
          'Changing the server URL is unavailable in this demo. The API '
          'address is selected when the app is built.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Close'),
          ),
        ],
      );
}
