import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'remote_tile.dart';

const _monochrome = ColorFilter.matrix([
  0.213, 0.715, 0.072, 0, 0,
  0.213, 0.715, 0.072, 0, 0,
  0.213, 0.715, 0.072, 0, 0,
  0, 0, 0, 1, 0,
]);

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
  Widget build(BuildContext context) {
    final scaler = MediaQuery.textScalerOf(context);
    final rowHeight = math.max(64.0, scaler.scale(17) * 1.3 + scaler.scale(12) * 1.4 + 23);
    const gap = SizedBox(width: 6);
    return SingleChildScrollView(
      key: const PageStorageKey('apps'),
      child: Column(
        children: [
          for (final row in rows)
            SizedBox(
              height: rowHeight,
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  for (final (index, app) in row.indexed) ...[
                    if (index > 0) gap,
                    Expanded(
                      child: RemoteTile(
                        app['name'] as String,
                        key: ValueKey("app:${app['id']}"),
                        focusNode: node("app:${app['id']}"),
                        onFocus: () => onFocus("app:${app['id']}"),
                        icon: Icons.android_rounded,
                        subtitle: app['detail'] as String?,
                        maxLabelLines: 1,
                        leading: app['icon'] is Uint8List && (app['icon'] as Uint8List).isNotEmpty
                            ? ColorFiltered(
                                colorFilter: _monochrome,
                                child: Image.memory(
                                  app['icon'] as Uint8List,
                                  width: 28,
                                  height: 28,
                                  fit: BoxFit.contain,
                                  gaplessPlayback: true,
                                  errorBuilder: (_, _, _) => const Icon(Icons.android_rounded, size: 20),
                                ),
                              )
                            : null,
                        onTap: () => onOpen(app['id'] as String),
                      ),
                    ),
                  ],
                  for (var missing = row.length; missing < columns; missing++) ...[
                    gap,
                    const Expanded(child: SizedBox.shrink()),
                  ],
                ],
              ),
            ),
        ],
      ),
    );
  }
}
