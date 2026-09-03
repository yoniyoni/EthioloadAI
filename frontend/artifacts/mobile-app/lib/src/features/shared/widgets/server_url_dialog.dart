import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../../../data/api/api_client.dart';

/// Lets the backend address be changed from inside the app — for a local/
/// test server whose LAN IP changes with DHCP, this replaces "rebuild the
/// whole APK" with "edit one field and restart the app". Reachable from
/// both the login screen (works even when not authenticated — the whole
/// point is fixing an unreachable server, which is also why login itself
/// would otherwise be impossible) and Profile → Developer for a returning,
/// already-logged-in user.
class ServerUrlDialog extends StatefulWidget {
  const ServerUrlDialog({super.key});

  @override
  State<ServerUrlDialog> createState() => _ServerUrlDialogState();
}

class _ServerUrlDialogState extends State<ServerUrlDialog> {
  late final TextEditingController _ctrl;
  static const _storage = FlutterSecureStorage();

  @override
  void initState() {
    super.initState();
    _ctrl = TextEditingController(text: kBaseUrl);
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final url = _ctrl.text.trim();
    if (url.isEmpty || !(url.startsWith('http://') || url.startsWith('https://'))) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('Enter a full URL starting with http:// or https://'),
        backgroundColor: Colors.red,
      ));
      return;
    }
    await ServerConfig.save(_storage, url);
    if (!mounted) return;
    Navigator.pop(context);
    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
      content: Text('Saved. Close and reopen the app for it to take effect.'),
      backgroundColor: Colors.green,
      duration: Duration(seconds: 4),
    ));
  }

  Future<void> _resetToDefault() async {
    await ServerConfig.save(_storage, null);
    if (!mounted) return;
    Navigator.pop(context);
    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
      content: Text('Reset to default. Close and reopen the app for it to take effect.'),
      backgroundColor: Colors.green,
      duration: Duration(seconds: 4),
    ));
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Server URL',
          style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'For pointing this app at a local/test backend — e.g. '
            'http://192.168.1.5:8000/api. Update this whenever your '
            "computer's address changes, no rebuild needed.",
            style: TextStyle(fontSize: 12, color: Colors.grey),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _ctrl,
            keyboardType: TextInputType.url,
            autocorrect: false,
            decoration: const InputDecoration(
              labelText: 'Base URL',
              hintText: 'http://192.168.1.5:8000/api',
              border: OutlineInputBorder(),
            ),
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: _resetToDefault,
          child: const Text('Reset to Default'),
        ),
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        ElevatedButton(
          onPressed: _save,
          child: const Text('Save'),
        ),
      ],
    );
  }
}
