import 'package:flutter/material.dart';
import 'remote_tile.dart';
import 'tv_theme.dart';

enum TvTab {
  bluetooth('블루투스', Icons.bluetooth_rounded),
  apps('앱 관리', Icons.apps_rounded),
  shortcuts('바로가기', Icons.open_in_new_rounded),
  files('파일탐색기', Icons.folder_outlined),
  storage('저장공간', Icons.storage_rounded),
  mapping('버튼 맵핑', Icons.gamepad_outlined),
  startup('시작 앱', Icons.power_settings_new_rounded),
  settings('설정', Icons.settings_outlined);

  final String title;
  final IconData icon;
  const TvTab(this.title, this.icon);
}

class ShellFrame extends StatelessWidget {
  final TvTab tab;
  final String version;
  final String? contextLabel, error;
  final Widget child;
  final bool compact;
  final FocusScopeNode sidebarScope, contentScope;
  final FocusNode Function(String) node;
  final ValueChanged<String> onFocus, onSelect;
  const ShellFrame({
    super.key,
    required this.tab,
    required this.version,
    this.contextLabel,
    this.error,
    required this.child,
    required this.compact,
    required this.node,
    required this.sidebarScope,
    required this.contentScope,
    required this.onFocus,
    required this.onSelect,
  });

  static double gutter(bool compact) => compact ? 10 : 14;
  static double railWidth(bool compact) => compact ? 146 : 174;
  static double outerHorizontal(bool compact) => compact ? 18 : 24;

  @override
  Widget build(BuildContext context) {
    final gap = gutter(compact);
    return ColoredBox(
      color: canvas,
      child: Padding(
        padding: EdgeInsets.symmetric(
          horizontal: outerHorizontal(compact),
          vertical: compact ? 14 : 18,
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Container(
              width: railWidth(compact),
              padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 8),
              decoration: BoxDecoration(color: railSurface, borderRadius: BorderRadius.circular(railRadius)),
              child: FocusScope(
                node: sidebarScope,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const Padding(
                      padding: EdgeInsets.fromLTRB(9, 2, 0, 9),
                      child: Text(
                        'GenieBreaker',
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700, letterSpacing: -.6),
                      ),
                    ),
                    Expanded(
                      child: SingleChildScrollView(
                        child: Column(
                          children: [
                            for (final item in TvTab.values)
                              RemoteTile(
                                item.title,
                                key: ValueKey('nav:${item.name}'),
                                navigation: true,
                                focusNode: node('nav:${item.name}'),
                                selected: tab == item,
                                icon: item.icon,
                                onFocus: () => onFocus('nav:${item.name}'),
                                onTap: () => onSelect(item.name),
                              ),
                          ],
                        ),
                      ),
                    ),
                    if (version.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.fromLTRB(9, 6, 0, 0),
                        child: Text('v$version', style: const TextStyle(color: muted, fontSize: 12)),
                      ),
                  ],
                ),
              ),
            ),
            SizedBox(width: gap),
            Expanded(
              child: FocusScope(
                node: contentScope,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Semantics(
                            header: true,
                            child: Text(
                              tab.title,
                              style: TextStyle(
                                fontSize: compact ? 24 : 27,
                                height: 1.2,
                                letterSpacing: -.6,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ),
                        ),
                        if (contextLabel != null)
                          Text(
                            contextLabel!,
                            style: const TextStyle(
                              color: accentTone,
                              fontWeight: FontWeight.w600,
                              fontSize: 15,
                            ),
                          ),
                      ],
                    ),
                    const SizedBox(height: 7),
                    const Divider(height: 1, color: divider),
                    SizedBox(height: compact ? 7 : 10),
                    if (error != null)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 8),
                        child: Text(
                          error!,
                          maxLines: 3,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(color: ink, fontWeight: FontWeight.w600),
                        ),
                      ),
                    Expanded(child: child),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
