import 'dart:async';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';
import '../shared/widgets/shared_widgets.dart';

/// Hosts Chapa's checkout page in-app. Detection of "the shipper is done" is
/// purely a URL-prefix match against [returnUrlPrefix] inside the WebView's
/// own navigation callbacks — the app has no deep-link/custom-scheme setup,
/// and this avoids needing any. Popping `true` means only "the shipper
/// finished on Chapa's page" — it is NOT proof of payment; the caller still
/// confirms the real outcome by polling the backend (same contract on both
/// the WebView path and the external-browser fallback below).
class ChapaCheckoutScreen extends StatefulWidget {
  final String checkoutUrl;
  final String returnUrlPrefix;

  const ChapaCheckoutScreen({
    required this.checkoutUrl,
    required this.returnUrlPrefix,
    super.key,
  });

  @override
  State<ChapaCheckoutScreen> createState() => _ChapaCheckoutScreenState();
}

class _ChapaCheckoutScreenState extends State<ChapaCheckoutScreen> {
  WebViewController? _controller;
  bool _loading = true;
  bool _returned = false;
  // Some devices have no usable WebView platform implementation (missing/
  // disabled/outdated Android System WebView, or a build where the plugin
  // failed to register — WebViewController's constructor throws
  // synchronously in that case). Rather than crash the whole payment flow,
  // fall back to opening the checkout page in the device's real browser.
  bool _webViewUnavailable = false;

  @override
  void initState() {
    super.initState();
    try {
      _controller = WebViewController()
        ..setJavaScriptMode(JavaScriptMode.unrestricted)
        ..setNavigationDelegate(
          NavigationDelegate(
            onPageStarted: (_) {
              if (mounted) setState(() => _loading = true);
            },
            onPageFinished: (url) {
              if (!mounted) return;
              setState(() => _loading = false);
              if (!_returned && url.startsWith(widget.returnUrlPrefix)) {
                _returned = true;
                // Let the "payment received / not completed" landing page
                // stay visible for a moment before handing back to the app.
                Future.delayed(const Duration(milliseconds: 1400), () {
                  if (mounted) Navigator.of(context).pop(true);
                });
              }
            },
          ),
        )
        ..loadRequest(Uri.parse(widget.checkoutUrl));
    } catch (_) {
      _webViewUnavailable = true;
    }
  }

  Future<void> _openInBrowser() async {
    try {
      await launchUrl(Uri.parse(widget.checkoutUrl), mode: LaunchMode.externalApplication);
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text("Couldn't open the payment page — no browser available."),
          backgroundColor: Colors.red,
        ));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: true,
      onPopInvokedWithResult: (didPop, _) {},
      child: Scaffold(
        appBar: const EthioAppBar(title: 'Pay with Chapa'),
        body: _webViewUnavailable ? _buildBrowserFallback() : _buildWebView(),
      ),
    );
  }

  Widget _buildWebView() {
    return Stack(
      children: [
        WebViewWidget(controller: _controller!),
        if (_loading) const Center(child: CircularProgressIndicator()),
      ],
    );
  }

  Widget _buildBrowserFallback() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.open_in_browser_rounded, size: 48, color: Colors.grey),
            const SizedBox(height: 16),
            const Text(
              "This device can't show the payment page in-app — "
              "open it in your browser instead, then come back here.",
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 14, color: Colors.black87),
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              icon: const Icon(Icons.open_in_new_rounded),
              label: const Text('Open Payment Page'),
              onPressed: _openInBrowser,
            ),
            const SizedBox(height: 12),
            OutlinedButton(
              onPressed: () => Navigator.of(context).pop(true),
              child: const Text("I've completed the payment"),
            ),
          ],
        ),
      ),
    );
  }
}
