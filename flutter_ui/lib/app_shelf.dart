import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'remote_tile.dart';

/// Partition the visible packages once for both layout and directional navigation.
List<List<Map>> appRows(List apps, int columns) => [
  for (var i = 0; i < apps.length; i += columns)
    apps.sublist(i, (i + columns).clamp(0, apps.length)).cast<Map>(),
];

class AppShelf extends StatelessWidget {
  final List<List<Map>> rows;
  final int columns;
  final FocusNode Function(String) node;
  final ValueChanged<String> onFocus, onOpen;

  const AppShelf({
    super.key,
    required this.rows,
    required this.columns,
    required this.node,
    required this.onFocus,
    required this.onOpen,
  });

  @override
  Widget build(BuildContext context) => SingleChildScrollView(
    key: const PageStorageKey('apps'),
    child: Column(
      children: [
        for (final row in rows)
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              for (final (index, app) in row.indexed)
                Expanded(
                  child: Padding(
                    padding: EdgeInsets.only(right: index + 1 < columns ? 5 : 0),
                    child: RemoteTile(
                      app['name'] as String,
                      key: ValueKey("app:${app['id']}"),
                      focusNode: node("app:${app['id']}"),
                      onFocus: () => onFocus("app:${app['id']}"),
                      icon: Icons.android_rounded,
                      subtitle: app['detail'] as String?,
                      leading: app['icon'] is Uint8List && (app['icon'] as Uint8List).isNotEmpty
                          ? Image.memory(
                              app['icon'] as Uint8List,
                              width: 28,
                              height: 28,
                              gaplessPlayback: true,
                            )
                          : null,
                      onTap: () => onOpen(app['id'] as String),
                    ),
                  ),
                ),
              for (var missing = row.length; missing < columns; missing++)
                const Expanded(child: SizedBox()),
            ],
          ),
      ],
    ),
  );
}
