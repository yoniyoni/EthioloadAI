import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:easy_localization/easy_localization.dart';
import '../../../data/models/models.dart';
import '../../../data/repositories/repositories.dart';

/// Debounced autocomplete field backed by Nominatim (via Laravel proxy).
/// When the user picks a result, [onSelected] is called with the chosen place.
/// Falls back gracefully to plain text entry if the search fails.
class PlaceSearchField extends ConsumerStatefulWidget {
  final String label;
  final String? initialValue;
  final void Function(PlaceResult place) onSelected;
  final TextEditingController? controller;
  final String? Function(String?)? validator;

  const PlaceSearchField({
    super.key,
    required this.label,
    required this.onSelected,
    this.initialValue,
    this.controller,
    this.validator,
  });

  @override
  ConsumerState<PlaceSearchField> createState() => _PlaceSearchFieldState();
}

class _PlaceSearchFieldState extends ConsumerState<PlaceSearchField> {
  late TextEditingController _ctrl;
  final FocusNode _focus      = FocusNode();
  final LayerLink _layerLink  = LayerLink();
  OverlayEntry? _overlay;

  List<PlaceResult> _results   = [];
  bool _loading                = false;
  Timer? _debounce;

  static const _green  = Color(0xFF0F3D1A);
  static const _border = Color(0xFFE5E7EB);

  @override
  void initState() {
    super.initState();
    _ctrl = widget.controller ?? TextEditingController(text: widget.initialValue);
    _focus.addListener(_onFocusChange);
  }

  @override
  void dispose() {
    if (widget.controller == null) _ctrl.dispose();
    _focus.removeListener(_onFocusChange);
    _focus.dispose();
    _debounce?.cancel();
    _removeOverlay();
    super.dispose();
  }

  void _onFocusChange() {
    if (!_focus.hasFocus) _removeOverlay();
  }

  void _onChanged(String value) {
    _debounce?.cancel();
    if (value.trim().length < 2) {
      _removeOverlay();
      return;
    }
    _debounce = Timer(const Duration(milliseconds: 400), () => _search(value.trim()));
  }

  Future<void> _search(String query) async {
    setState(() => _loading = true);
    try {
      final repo    = ref.read(routingRepositoryProvider);
      final results = await repo.searchPlace(query);
      if (!mounted) return;
      setState(() => _results = results);
      if (results.isNotEmpty) _showOverlay();
    } catch (_) {
      // Silent fallback — user can still type manually
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _select(PlaceResult place) {
    _ctrl.text = place.shortName;
    widget.onSelected(place);
    _removeOverlay();
    _focus.unfocus();
  }

  void _removeOverlay() {
    _overlay?.remove();
    _overlay = null;
    if (mounted) setState(() => _results = []);
  }

  void _showOverlay() {
    _removeOverlay();
    final renderBox = context.findRenderObject() as RenderBox?;
    if (renderBox == null) return;
    final size = renderBox.size;

    _overlay = OverlayEntry(
      builder: (_) => Positioned(
        width: size.width,
        child: CompositedTransformFollower(
          link: _layerLink,
          showWhenUnlinked: false,
          offset: Offset(0, size.height + 2),
          child: Material(
            elevation: 4,
            borderRadius: BorderRadius.circular(8),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: _results.map((p) => InkWell(
                onTap: () => _select(p),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                  child: Row(children: [
                    const Icon(Icons.place_outlined, size: 16, color: _green),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        p.shortName,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(fontSize: 13),
                      ),
                    ),
                  ]),
                ),
              )).toList(),
            ),
          ),
        ),
      ),
    );

    Overlay.of(context).insert(_overlay!);
  }

  @override
  Widget build(BuildContext context) {
    return CompositedTransformTarget(
      link: _layerLink,
      child: TextFormField(
        controller:   _ctrl,
        focusNode:    _focus,
        onChanged:    _onChanged,
        validator:    widget.validator,
        decoration: InputDecoration(
          labelText: widget.label,
          hintText: 'navigation.search_place'.tr(),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(10),
            borderSide: const BorderSide(color: _border),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(10),
            borderSide: const BorderSide(color: _green, width: 1.5),
          ),
          suffixIcon: _loading
              ? const Padding(
                  padding: EdgeInsets.all(12),
                  child: SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(strokeWidth: 2, color: _green),
                  ),
                )
              : const Icon(Icons.search_rounded, color: Colors.grey),
          isDense: true,
          contentPadding:
              const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        ),
      ),
    );
  }
}
