import 'package:dio/dio.dart';
import 'package:file_selector/file_selector.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../data/api/api_client.dart';

const companyStatusLabels = {
  'DRAFT': 'Draft',
  'PENDING': 'Under review',
  'APPROVED': 'Approved',
  'REJECTED': 'Rejected',
  'MORE_INFORMATION_REQUIRED': 'Changes requested',
};
const _statusHelp = {
  'DRAFT': 'Complete your company details and upload all three documents, then submit for review. You can save and return later.',
  'PENDING': 'An admin is reviewing your company and documents. Your application is locked during review. Refresh to check for updates.',
  'APPROVED': 'Your company registration is approved. You can now invite fleet managers and dispatchers.',
  'REJECTED': 'This application was rejected and cannot be edited. Read the admin feedback below.',
  'MORE_INFORMATION_REQUIRED': 'Update your details or replace documents as requested, then submit again.',
};
const _fields = {
  'companyName': 'Company name',
  'legalName': 'Legal name (optional)',
  'tinNumber': 'TIN number',
  'businessRegistrationNumber': 'Business registration number',
  'phone': 'Company phone',
  'email': 'Company email',
  'businessAddress': 'Business address',
  'city': 'City',
};
const _limits = {'companyName': 255, 'legalName': 255, 'tinNumber': 50, 'businessRegistrationNumber': 100, 'phone': 16, 'email': 255, 'businessAddress': 1000, 'city': 100};
const _documents = {
  'BUSINESS_LICENSE': 'Business license',
  'BUSINESS_REGISTRATION': 'Business registration certificate',
  'TIN_CERTIFICATE': 'TIN certificate',
};

class CompanyRegistrationScreen extends ConsumerStatefulWidget {
  final String? companyId;
  const CompanyRegistrationScreen({super.key, this.companyId});

  @override
  ConsumerState<CompanyRegistrationScreen> createState() => _CompanyRegistrationState();
}

class _CompanyRegistrationState extends ConsumerState<CompanyRegistrationScreen> {
  final _formKey = GlobalKey<FormState>();
  final _controllers = {for (final key in _fields.keys) key: TextEditingController()};
  final _email = TextEditingController();
  final _inviteFormKey = GlobalKey<FormState>();
  String _inviteRole = 'FLEET_MANAGER';
  Map<String, dynamic>? _company;
  List<Map<String, dynamic>> _companies = [];
  List<Map<String, dynamic>> _docs = [];
  bool _loading = true, _busy = false, _last = true;
  String? _error, _notice;
  int _page = 0, _step = 1;
  bool get _isNew => widget.companyId == 'new';
  bool get _isList => widget.companyId == null;
  bool get _editable => _isNew || ['DRAFT', 'MORE_INFORMATION_REQUIRED'].contains(_company?['verificationStatus']);
  ApiClient get _api => ref.read(apiClientProvider);

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void didUpdateWidget(covariant CompanyRegistrationScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.companyId != widget.companyId) {
      _company = null; _docs = []; _error = null; _notice = null;
      for (final controller in _controllers.values) { controller.clear(); }
      _load();
    }
  }

  @override
  void dispose() {
    for (final controller in _controllers.values) { controller.dispose(); }
    _email.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    try {
      if (_isList) {
        final result = await _api.get<Map<String, dynamic>>('/carrier-companies/me', queryParameters: {'page': _page, 'size': 10});
        if (!mounted) return;
        _companies = (result['content'] as List).map((e) => Map<String, dynamic>.from(e as Map)).toList();
        _last = result['last'] == true;
      } else if (!_isNew) {
        final company = await _api.get<Map<String, dynamic>>('/carrier-companies/${widget.companyId}');
        final docs = await _api.get<List<dynamic>>('/carrier-companies/${widget.companyId}/documents');
        if (!mounted) return;
        _company = company;
        _docs = docs.map((e) => Map<String, dynamic>.from(e as Map)).toList();
        for (final key in _fields.keys) { _controllers[key]!.text = company[key]?.toString() ?? ''; }
        _step = 2;
      } else {
        _step = 1;
      }
    } catch (error) {
      if (mounted) _error = error.toString();
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _run(Future<void> Function() action) async {
    setState(() { _busy = true; _error = null; _notice = null; });
    try { await action(); }
    catch (error) {
      if (mounted) {
        setState(() => _error = error.toString());
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.toString())));
      }
    }
    finally { if (mounted) setState(() => _busy = false); }
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    await _run(() async {
      final data = {for (final entry in _controllers.entries) entry.key: entry.value.text.trim().isEmpty ? null : entry.value.text.trim()};
      final company = _isNew
          ? await _api.post<Map<String, dynamic>>('/carrier-companies', data: data)
          : await _api.put<Map<String, dynamic>>('/carrier-companies/${widget.companyId}', data: data);
      if (!mounted) return;
      if (_isNew) {
        context.go('/fleet/companies/${company['id']}');
      } else {
        setState(() { _company = company; _step = 2; _notice = 'Company details saved. Upload your documents next.'; });
      }
    });
  }

  Future<void> _upload(String type) async {
    await _run(() async {
      const group = XTypeGroup(label: 'Company documents', extensions: ['pdf', 'jpg', 'jpeg', 'png'], mimeTypes: ['application/pdf', 'image/jpeg', 'image/png'], uniformTypeIdentifiers: ['com.adobe.pdf', 'public.jpeg', 'public.png']);
      final file = await openFile(acceptedTypeGroups: [group]);
      if (file == null || !mounted) return;
      if (await file.length() > 5 * 1024 * 1024) throw ApiException(message: 'Maximum file size is 5 MB.');
      final extension = file.name.split('.').last.toLowerCase();
      final mime = {'pdf': 'application/pdf', 'jpg': 'image/jpeg', 'jpeg': 'image/jpeg', 'png': 'image/png'}[extension];
      if (mime == null) throw ApiException(message: 'Choose a PDF, JPEG or PNG file.');
      final data = FormData.fromMap({'documentType': type, 'file': MultipartFile.fromBytes(await file.readAsBytes(), filename: file.name, contentType: DioMediaType.parse(mime))});
      final uploaded = await _api.upload('/carrier-companies/${widget.companyId}/documents', data);
      if (!mounted) return;
      setState(() { _docs.removeWhere((doc) => doc['documentType'] == type); _docs.add(uploaded); _notice = 'Document saved.'; });
    });
  }

  Future<void> _submit() => _run(() async {
    final result = await _api.post<Map<String, dynamic>>('/carrier-companies/${widget.companyId}/submit');
    if (mounted) setState(() { _company = result; _notice = 'Application submitted. An admin will review your company and documents.'; });
  });

  String? _validate(String key, String? input) {
    final value = input?.trim() ?? '';
    if (value.isEmpty) return key == 'companyName' ? 'Enter a company name.' : null;
    if (key == 'phone' && !RegExp(r'^\+?[0-9]{7,15}$').hasMatch(value)) return 'Use 7–15 digits, optionally starting with +.';
    if (key == 'email' && !RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$').hasMatch(value)) return 'Enter a valid email address.';
    if ((key == 'tinNumber' || key == 'businessRegistrationNumber') && !RegExp(r'^[A-Za-z0-9][A-Za-z0-9/-]+$').hasMatch(value)) return 'Use at least two letters or digits; slashes and hyphens are allowed.';
    return null;
  }

  Widget _card(String title, List<Widget> children) => Card(child: Padding(padding: const EdgeInsets.all(16), child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [Text(title, style: Theme.of(context).textTheme.titleMedium), const SizedBox(height: 12), ...children])));

  @override
  Widget build(BuildContext context) {
    final status = _company?['verificationStatus']?.toString() ?? 'DRAFT';
    final completeDetails = _fields.keys.where((key) => key != 'legalName').every((key) => (_company?[key]?.toString().trim() ?? '').isNotEmpty);
    final completeDocs = _documents.keys.every((type) => _docs.any((doc) => doc['documentType'] == type));
    return Scaffold(
      appBar: AppBar(title: Text(_isList ? 'My companies' : _isNew ? 'Register company' : 'Company application'), leading: IconButton(tooltip: _isList ? 'Fleet dashboard' : 'My companies', icon: const Icon(Icons.arrow_back), onPressed: _busy ? null : () => context.go(_isList ? '/fleet' : '/fleet/companies')), actions: [IconButton(tooltip: 'Refresh status', onPressed: _busy || _loading ? null : _load, icon: const Icon(Icons.refresh))]),
      body: _loading ? const Center(child: CircularProgressIndicator()) : Center(child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 720), child: ListView(padding: const EdgeInsets.all(16), children: [
        if (_error != null) _card('Unable to continue', [Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)), if (_company == null && !_isNew) TextButton(onPressed: _load, child: const Text('Retry'))]),
        if (_notice != null) Padding(padding: const EdgeInsets.all(12), child: Semantics(liveRegion: true, child: Text(_notice!))),
        if (_busy) const LinearProgressIndicator(),
        if (_isList) ...[
          const Text('Your owner account and your company application are separate. Register a company, upload its documents and submit for admin review.'),
          const SizedBox(height: 16),
          FilledButton.icon(onPressed: () => context.go('/fleet/companies/new'), icon: const Icon(Icons.add_business), label: const Text('Register a company')),
          if (_companies.isEmpty && _error == null) const Padding(padding: EdgeInsets.all(20), child: Text('No company applications yet. Register your first company to get started.')),
          ..._companies.map((company) => Card(child: ListTile(isThreeLine: true, title: Text(company['companyName'].toString()), subtitle: Text('${companyStatusLabels[company['verificationStatus']]}\n${company['city'] ?? 'Details incomplete'}'), trailing: const Icon(Icons.chevron_right), onTap: () => context.go('/fleet/companies/${company['id']}')))),
          Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [TextButton(onPressed: _page == 0 ? null : () { _page--; _load(); }, child: const Text('Previous')), Text('Page ${_page + 1}'), TextButton(onPressed: _last ? null : () { _page++; _load(); }, child: const Text('Next'))]),
        ] else if (_isNew || _company != null) ...[
          Text('Step ${_editable ? _step : 3} of 3: ${_editable ? (_step == 1 ? 'Company details' : 'Documents') : 'Admin review'}', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          LinearProgressIndicator(value: (_editable ? _step : 3) / 3),
          if (_company != null) _card(companyStatusLabels[status] ?? status, [Text(_statusHelp[status] ?? ''), if (_company!['reviewMessage'] != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text('Admin feedback: ${_company!['reviewMessage']}'))]),
          if (_editable && _step == 1) _card('1. Company details', [
            const Text('Save a draft with a company name. All fields except legal name are required before submission.'),
            const SizedBox(height: 16),
            Form(key: _formKey, child: Column(children: [
              ..._fields.entries.map((field) => Padding(padding: const EdgeInsets.only(bottom: 16), child: TextFormField(controller: _controllers[field.key], enabled: !_busy, maxLength: _limits[field.key], decoration: InputDecoration(labelText: field.value, border: const OutlineInputBorder(), counterText: ''), keyboardType: field.key == 'email' ? TextInputType.emailAddress : field.key == 'phone' ? TextInputType.phone : TextInputType.text, validator: (value) => _validate(field.key, value)))),
              SizedBox(width: double.infinity, child: FilledButton(onPressed: _busy ? null : _save, child: const Text('Save and continue'))),
            ])),
          ]) else if (_company != null) _card('Company details', [
            ..._fields.entries.map((field) => Padding(padding: const EdgeInsets.only(bottom: 12), child: Text('${field.value}\n${_company![field.key] ?? 'Not provided'}'))),
            if (_editable) OutlinedButton(onPressed: _busy ? null : () => setState(() => _step = 1), child: const Text('Edit company details')),
          ]),
          if (_company != null && (!_editable || _step == 2)) _card('2. Company documents', [
            const Text('Upload all three documents. PDF, JPEG or PNG, up to 5 MB each. These files are private to you and the admin.'),
            ..._documents.entries.map((entry) {
              final matches = _docs.where((doc) => doc['documentType'] == entry.key);
              final document = matches.isEmpty ? null : matches.first;
              return Padding(padding: const EdgeInsets.symmetric(vertical: 12), child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                Row(children: [Icon(document == null ? Icons.upload_file : Icons.check_circle_outline, size: 20), const SizedBox(width: 8), Expanded(child: Text(entry.value, style: const TextStyle(fontWeight: FontWeight.w600)))]),
                const SizedBox(height: 6), Text(document?['originalName']?.toString() ?? 'Required before submission'),
                if (_editable) OutlinedButton(onPressed: _busy ? null : () => _upload(entry.key), child: Text(document == null ? 'Upload document' : 'Replace document')),
              ]));
            }),
            if (_editable) ...[
              Text(completeDetails ? 'Company details complete.' : 'Complete the missing company details before submitting.'),
              Text(completeDocs ? 'All required documents uploaded.' : 'Upload all three required documents.'),
              const SizedBox(height: 12),
              FilledButton(onPressed: _busy || !completeDetails || !completeDocs ? null : _submit, child: Text(status == 'MORE_INFORMATION_REQUIRED' ? 'Resubmit for review' : 'Submit for review')),
              const SizedBox(height: 8), const Text('Submitting locks your application until the admin reviews it or requests changes.'),
            ],
          ]),
          if (status == 'APPROVED') _card('Invite your team', [
            const Text('The employee must accept using the invited email address.'), const SizedBox(height: 12),
            Form(key: _inviteFormKey, child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              TextFormField(controller: _email, enabled: !_busy, keyboardType: TextInputType.emailAddress, decoration: const InputDecoration(labelText: 'Employee email', border: OutlineInputBorder()), validator: (value) => (value?.trim().isEmpty ?? true) ? 'Enter an email address.' : _validate('email', value)),
              const SizedBox(height: 12), DropdownButtonFormField<String>(initialValue: _inviteRole, decoration: const InputDecoration(labelText: 'Company role'), items: const [DropdownMenuItem(value: 'FLEET_MANAGER', child: Text('Fleet manager')), DropdownMenuItem(value: 'DISPATCHER', child: Text('Dispatcher'))], onChanged: _busy ? null : (value) => setState(() => _inviteRole = value!)),
              const SizedBox(height: 12), FilledButton(onPressed: _busy ? null : () { if (_inviteFormKey.currentState!.validate()) { _run(() async { await _api.post<Map<String, dynamic>>('/carrier-companies/${widget.companyId}/team/invitations', data: {'email': _email.text.trim(), 'role': _inviteRole}); if (mounted) setState(() { _email.clear(); _notice = 'Invitation submitted to the email provider. Ask the recipient to check their inbox and spam folder.'; }); }); } }, child: const Text('Send invitation')),
            ])),
          ]),
        ],
        const SizedBox(height: 24),
      ]))),
    );
  }
}
