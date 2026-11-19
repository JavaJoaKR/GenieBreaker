import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'tv_focus.dart';
import 'tv_theme.dart';

class LockCodePanel extends StatefulWidget {
  const LockCodePanel({super.key, required this.onSubmit});

  final Future<bool> Function(String) onSubmit;

  @override
  State<LockCodePanel> createState() => _LockCodePanelState();
}

class _LockCodePanelState extends State<LockCodePanel> {
  final input = TextEditingController();
  bool busy = false;
  String? error;

  @override
  void dispose() {
    input.dispose();
    super.dispose();
  }

  Future<void> submit() async {
    final code = input.text;
    if (busy || code.length != 6) return;
    setState(() {
      busy = true;
      error = null;
    });
    var accepted = false;
    try {
      accepted = await widget.onSubmit(code);
    } catch (_) {
      if (mounted) setState(() => error = '잠금 코드를 확인할 수 없습니다.');
    }
    if (!mounted) return;
    setState(() {
      busy = false;
      if (!accepted) {
        input.clear();
        error ??= '잠금 코드가 일치하지 않습니다.';
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
                Text('GenieBreaker 잠금', textAlign: TextAlign.center, style: Theme.of(context).textTheme.titleLarge),
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
                    keyboardType: TextInputType.visiblePassword,
                    textInputAction: TextInputAction.done,
                    enableSuggestions: false,
                    autocorrect: false,
                    inputFormatters: [
                      FilteringTextInputFormatter.allow(RegExp('[0-9a-fA-F]')),
                      LengthLimitingTextInputFormatter(6),
                    ],
                    decoration: InputDecoration(labelText: '빌드 커밋 앞 6자리', errorText: error),
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
