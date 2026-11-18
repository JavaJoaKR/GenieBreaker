import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'tv_focus.dart';
import 'tv_theme.dart';

/// Numeric PIN entry via the TV keyboard, including physical remote digits.
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
  final input = TextEditingController();
  bool busy = false;
  String? error;

  @override
  void dispose() {
    input.dispose();
    super.dispose();
  }

  Future<void> submit() async {
    final pin = input.text;
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
        input.clear();
        error ??= 'PIN이 일치하지 않습니다.';
      }
    });
  }

  @override
  Widget build(BuildContext context) => Center(
    child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 340),
      child: Material(
        color: panel,
        borderRadius: BorderRadius.circular(modalRadius),
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(12),
          child: FocusTraversalGroup(
            policy: TvFocusPolicy(),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(widget.title, textAlign: TextAlign.center, style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 8),
                Actions(
                  actions: {
                    ActivateIntent: CallbackAction<ActivateIntent>(onInvoke: (_) {
                      submit();
                      return null;
                    }),
                  },
                  child: TextField(
                    controller: input,
                    autofocus: true,
                    obscureText: true,
                    keyboardType: TextInputType.number,
                    textInputAction: TextInputAction.done,
                    enableSuggestions: false,
                    autocorrect: false,
                    inputFormatters: [
                      FilteringTextInputFormatter.digitsOnly,
                      LengthLimitingTextInputFormatter(widget.maximum),
                    ],
                    decoration: InputDecoration(labelText: 'PIN', errorText: error),
                    onChanged: (_) {
                      if (error != null) setState(() => error = null);
                    },
                    onSubmitted: (_) => submit(),
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    if (widget.onCancel != null)
                      TextButton(onPressed: widget.onCancel, child: const Text('취소')),
                    FilledButton(onPressed: submit, child: const Text('확인')),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    ),
  );
}
