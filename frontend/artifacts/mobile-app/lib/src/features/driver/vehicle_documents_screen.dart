import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/models/models.dart';
import '../../data/repositories/repositories.dart';

class VehicleDocumentsScreen extends ConsumerStatefulWidget {
  final int vehicleId;
  final String? plateNumber;

  const VehicleDocumentsScreen({
    super.key,
    required this.vehicleId,
    this.plateNumber,
  });

  @override
  ConsumerState<VehicleDocumentsScreen> createState() => _VehicleDocumentsScreenState();
}

class _VehicleDocumentsScreenState extends ConsumerState<VehicleDocumentsScreen> {
  static const List<Map<String, String>> _documentTypes = [
    {'value': 'registration_certificate', 'label': 'Registration Certificate'},
    {'value': 'insurance_certificate', 'label': 'Insurance Certificate'},
    {'value': 'roadworthiness_certificate', 'label': 'Roadworthiness Certificate'},
    {'value': 'ownership_document', 'label': 'Ownership Document'},
  ];

  bool _loading = true;
  String? _error;
  List<VehicleDocument> _documents = <VehicleDocument>[];
  String _selectedType = 'registration_certificate';

  @override
  void initState() {
    super.initState();
    _loadDocuments();
  }

  Future<void> _loadDocuments() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final docs = await ref.read(vehicleDocumentRepositoryProvider).list(widget.vehicleId);
      if (!mounted) return;
      setState(() => _documents = docs);
    } catch (error) {
      if (!mounted) return;
      setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _uploadDocument() async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: const ['pdf', 'jpg', 'jpeg', 'png'],
      withData: true,
    );
    if (result == null || result.files.single.bytes == null) {
      return;
    }

    final file = result.files.single;
    try {
      await ref.read(vehicleDocumentRepositoryProvider).upload(
        vehicleId: widget.vehicleId,
        documentType: _selectedType,
        fileBytes: file.bytes!,
        fileName: file.name,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Vehicle document uploaded.')),
      );
      await _loadDocuments();
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error.toString())),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final title = widget.plateNumber != null && widget.plateNumber!.isNotEmpty
        ? 'Vehicle Documents — ${widget.plateNumber}'
        : 'Vehicle Documents';

    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Icon(Icons.error_outline, size: 42),
                        const SizedBox(height: 12),
                        Text(_error!, textAlign: TextAlign.center),
                        const SizedBox(height: 12),
                        ElevatedButton.icon(
                          onPressed: _loadDocuments,
                          icon: const Icon(Icons.refresh),
                          label: const Text('Retry'),
                        ),
                      ],
                    ),
                  ),
                )
              : RefreshIndicator(
                  onRefresh: _loadDocuments,
                  child: ListView(
                    padding: const EdgeInsets.all(16),
                    children: [
                      Row(
                        children: [
                          const Expanded(
                            child: Text(
                              'Document type',
                              style: TextStyle(fontWeight: FontWeight.w600),
                            ),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: DropdownButtonFormField<String>(
                              value: _selectedType,
                              items: _documentTypes
                                  .map((entry) => DropdownMenuItem<String>(
                                        value: entry['value'],
                                        child: Text(entry['label']!),
                                      ))
                                  .toList(),
                              onChanged: (value) {
                                if (value != null) {
                                  setState(() => _selectedType = value);
                                }
                              },
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      SizedBox(
                        width: double.infinity,
                        child: ElevatedButton.icon(
                          onPressed: _uploadDocument,
                          icon: const Icon(Icons.upload_file),
                          label: const Text('Upload document'),
                        ),
                      ),
                      const SizedBox(height: 20),
                      if (_documents.isEmpty)
                        const Card(
                          child: ListTile(
                            leading: Icon(Icons.description_outlined),
                            title: Text('No vehicle documents uploaded yet.'),
                            subtitle: Text('Upload one of the required document types.'),
                          ),
                        )
                      else
                        ..._documents.map((doc) => Card(
                              margin: const EdgeInsets.only(bottom: 12),
                              child: ListTile(
                                leading: const Icon(Icons.description_outlined),
                                title: Text(doc.documentLabel.isNotEmpty ? doc.documentLabel : doc.documentType),
                                subtitle: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text('File: ${doc.originalName}'),
                                    Text('Status: ${doc.status}'),
                                    if (doc.rejectionReason != null && doc.rejectionReason!.isNotEmpty)
                                      Text('Reason: ${doc.rejectionReason!}'),
                                  ],
                                ),
                                trailing: Icon(
                                  doc.isApproved
                                      ? Icons.check_circle
                                      : doc.isRejected
                                          ? Icons.error_outline
                                          : Icons.pending,
                                  color: doc.isApproved
                                      ? Colors.green
                                      : doc.isRejected
                                          ? Colors.red
                                          : Colors.orange,
                                ),
                              ),
                            )),
                    ],
                  ),
                ),
    );
  }
}
