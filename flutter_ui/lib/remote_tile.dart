import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'tv_theme.dart';

class RemoteTile extends StatefulWidget {
  final String title;
  final String? subtitle;
  final IconData icon;
  final Widget? leading;
  final FocusNode? focusNode;
  final bool autofocus;
  final bool selected;
  final bool navigation;
  final int maxLabelLines;
  final bool? toggle;
  final bool danger;
  final Color? accent;
  final VoidCallback? onFocus;
  final VoidCallback onTap;
  const RemoteTile(
    this.title, {
    super.key,
    this.subtitle,
    this.icon = Icons.chevron_right_rounded,
    this.leading,
    this.focusNode,
    this.autofocus = false,
    this.selected = false,
    this.navigation = false,
    this.maxLabelLines = 2,
    this.toggle,
    this.danger = false,
    this.accent,
    this.onFocus,
    required this.onTap,
  });
  @override
  State<RemoteTile> createState() => _RemoteTileState();
}

class _RemoteTileState extends State<RemoteTile> {
  bool focused = false;
  @override
  Widget build(BuildContext context) => Padding(
    padding: EdgeInsets.only(bottom: widget.navigation ? 2 : 5),
    child: FocusableActionDetector(
      focusNode: widget.focusNode,
      autofocus: widget.autofocus,
      onFocusChange: (value) {
        if (mounted) setState(() => focused = value);
        if (value) {
          widget.onFocus?.call();
          Scrollable.ensureVisible(context, alignment: .5, duration: Duration.zero);
        }
      },
      shortcuts: const {
        SingleActivator(LogicalKeyboardKey.select, includeRepeats: false): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.enter, includeRepeats: false): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.space, includeRepeats: false): ActivateIntent(),
      },
      actions: {
        ActivateIntent: CallbackAction<ActivateIntent>(
          onInvoke: (_) {
            widget.onTap();
            return null;
          },
        ),
      },
      child: Semantics(
        button: true,
        selected: widget.selected,
        toggled: widget.toggle,
        child: GestureDetector(
          onTap: widget.onTap,
          child: Container(
            constraints: BoxConstraints(minHeight: widget.navigation ? 42 : 50),
            padding: EdgeInsets.symmetric(horizontal: widget.navigation ? 7 : 10, vertical: 5),
            decoration: BoxDecoration(
              color: focused
                  ? focusFill
                  : widget.selected
                  ? const Color(0xff203b58)
                  : widget.navigation
                  ? Colors.transparent
                  : raised,
              borderRadius: BorderRadius.circular(tileRadius),
              border: widget.navigation
                  ? null
                  : Border.all(color: focused ? ink : Colors.transparent, width: 2),
            ),
            child: Row(
              children: [
                if (widget.accent != null) ...[
                  Icon(widget.icon, size: 24, color: widget.accent),
                  const SizedBox(width: 8),
                ] else if (widget.navigation) ...[
                  Icon(widget.icon, size: 21, color: widget.selected || focused ? ink : muted),
                  const SizedBox(width: 8),
                ] else if (widget.toggle == null) ...[
                  Container(
                    width: 30,
                    height: 30,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      color: widget.danger ? const Color(0xff40292e) : const Color(0xff1c3453),
                      borderRadius: BorderRadius.circular(4.5),
                    ),
                    child:
                        widget.leading ??
                        Icon(
                          widget.icon,
                          color: widget.danger ? const Color(0xffffa4a4) : blue,
                          size: 20,
                        ),
                  ),
                  const SizedBox(width: 8),
                ],
                Expanded(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        widget.title,
                        maxLines: widget.maxLabelLines,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 17,
                          height: 1.3,
                          fontWeight: FontWeight.w500,
                          color: widget.danger ? const Color(0xffffb6b6) : widget.accent ?? ink,
                        ),
                      ),
                      if (widget.subtitle?.isNotEmpty == true) ...[
                        const SizedBox(height: 2),
                        Text(
                          widget.subtitle!,
                          maxLines: widget.maxLabelLines,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(color: muted, fontSize: 12, height: 1.4),
                        ),
                      ],
                    ],
                  ),
                ),
                if (widget.selected && !widget.navigation && widget.toggle == null) ...[
                  const SizedBox(width: 6),
                  const Icon(Icons.check_rounded, size: 17, color: blue),
                ],
                if (widget.toggle != null) ...[
                  const SizedBox(width: 16),
                  Icon(
                    widget.toggle! ? Icons.toggle_on_rounded : Icons.toggle_off_outlined,
                    size: 36,
                    color: widget.toggle! ? blue : muted,
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    ),
  );
}
