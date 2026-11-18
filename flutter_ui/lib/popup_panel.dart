import 'package:flutter/material.dart';
import 'tv_theme.dart';

/// Shared bounded surface for every menu and confirmation. Background belongs to the caller.
class PopupPanel extends StatelessWidget {
  final String title;
  final List<String> breadcrumbs;
  final Widget body;
  final Widget? footer;
  final String? errorCode;
  final bool wide;
  final VoidCallback onBack;
  final VoidCallback onClose;
  const PopupPanel({
    super.key,
    required this.title,
    this.breadcrumbs = const [],
    required this.body,
    this.footer,
    this.errorCode,
    this.wide = false,
    required this.onBack,
    required this.onClose,
  });
  @override
  Widget build(BuildContext context) => ConstrainedBox(
    constraints: BoxConstraints(
      maxWidth: wide ? 670 : 510,
      maxHeight: MediaQuery.sizeOf(context).height - 32,
    ),
    child: Material(
      key: const ValueKey('popup-surface'),
      color: panel,
      surfaceTintColor: Colors.transparent,
      elevation: 24,
      shadowColor: Colors.black54,
      borderRadius: BorderRadius.circular(modalRadius),
      clipBehavior: Clip.antiAlias,
      child: DecoratedBox(
        decoration: BoxDecoration(
          border: Border.all(color: divider),
          borderRadius: BorderRadius.circular(modalRadius),
        ),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 10),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (breadcrumbs.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(left: 4, bottom: 6),
                  child: Text(
                    breadcrumbs.join('  ›  '),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: muted, fontSize: 12),
                  ),
                ),
              Row(
                children: [
                  if (breadcrumbs.isNotEmpty) ...[
                    IconButton(
                      tooltip: '이전 메뉴',
                      onPressed: onBack,
                      icon: const Icon(Icons.arrow_back_rounded, color: ink, size: 22),
                    ),
                    const SizedBox(width: 6),
                  ],
                  Expanded(
                    child: Text(
                      title,
                      style: const TextStyle(fontSize: 24, height: 1.2, fontWeight: FontWeight.w700),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  const SizedBox(width: 12),
                  IconButton(
                    tooltip: '메뉴 닫기',
                    onPressed: onClose,
                    icon: const Icon(Icons.close_rounded, color: muted, size: 22),
                  ),
                ],
              ),
              if (errorCode != null)
                Padding(
                  padding: const EdgeInsets.only(top: 10),
                  child: Text(
                    '오류 코드  $errorCode',
                    key: const ValueKey('error-code'),
                    style: const TextStyle(color: accentTone, fontSize: 18, fontWeight: FontWeight.w600),
                  ),
                ),
              const SizedBox(height: 8),
              Flexible(fit: FlexFit.loose, child: body),
              if (footer != null) ...[
                const SizedBox(height: 8),
                const Divider(height: 1, color: divider),
                const SizedBox(height: 8),
                footer!,
              ],
            ],
          ),
        ),
      ),
    ),
  );
}
