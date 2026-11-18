import 'package:flutter/material.dart';

import 'tv_focus.dart';
import 'tv_theme.dart';

/// On-screen PIN entry for remotes with only a D-pad and an OK button.
class PinPanel extends StatefulWidget {
  const PinPanel({
    super.key,
    required this.title,
    required this.onSubmit,
    this.onCancel,
    this.minimum = 4,
    this.maximum = 8,
  });

  final String title;
  final Future<bool> Function(String) onSubmit;
  final VoidCallback? onCancel;
  final int minimum;
  final int maximum;

  @override
  State<PinPanel> createState() => _PinPanelState();
}

class _PinPanelState extends State<PinPanel> {
  String pin = '';
  bool busy = false;
  String? error;

  void edit(String digit) {
    if (busy) return;
    setState(() {
      error = null;
      if (digit.isEmpty) {
        if (pin.isNotEmpty) pin = pin.substring(0, pin.length - 1);
      } else if (pin.length < widget.maximum) {
        pin += digit;
      }
    });
  }

  Future<void> submit() async {
    if (busy || pin.length < widget.minimum) return;
    setState(() => busy = true);
    var accepted = false;
    try {
      accepted = await widget.onSubmit(pin);
    } catch (_) {
      if (mounted) setState(() => error = 'PIN을 확인할 수 없습니다.');
    }
    if (!mounted) return;
    setState(() {
      busy = false;
      if (!accepted) {
        pin = '';
        error ??= 'PIN이 일치하지 않습니다.';
      }
    });
  }

  Widget digit(String value) => OutlinedButton(
    autofocus: value == '1',
    onPressed: () => edit(value),
    child: Text(value),
  );

  @override
  Widget build(BuildContext context) => Center(
    child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 340),
      child: Material(
        color: panel,
        borderRadius: BorderRadius.circular(18),
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: FocusTraversalGroup(
            policy: TvFocusPolicy(),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(widget.title, textAlign: TextAlign.center, style: Theme.of(context).textTheme.titleLarge),
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: Text(
                    error ?? (pin.isEmpty ? 'PIN 입력' : '●' * pin.length),
                    textAlign: TextAlign.center,
                    style: TextStyle(color: error == null ? ink : Theme.of(context).colorScheme.error),
                  ),
                ),
                for (final row in ['123', '456', '789'])
                  Row(
                    children: [
                      for (final value in row.split(''))
                        Expanded(child: Padding(padding: const EdgeInsets.all(2), child: digit(value))),
                    ],
                  ),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () => edit(''),
                        child: const Icon(Icons.backspace_outlined, semanticLabel: '한 자리 지우기'),
                      ),
                    ),
                    Expanded(child: digit('0')),
                    Expanded(child: FilledButton(onPressed: submit, child: const Text('확인'))),
                  ],
                ),
                if (widget.onCancel != null)
                  TextButton(onPressed: widget.onCancel, child: const Text('취소')),
              ],
            ),
          ),
        ),
      ),
    ),
  );
}
