import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'remote_tile.dart';
import 'tv_focus.dart';
import 'tv_theme.dart';

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
                        leading: switch (app['icon']) {
                          final Uint8List icon when icon.isNotEmpty => _appIcon(icon, 28),
                          _ => null,
                        },
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

class LauncherShelf extends StatelessWidget {
  final List<List<Map>> rows;
  final int columns;
  final double rowExtent;
  final ScrollController controller;
  final FocusNode Function(String) node;
  final ValueChanged<String> onFocus, onOpen;

  const LauncherShelf({
    super.key,
    required this.rows,
    required this.columns,
    required this.rowExtent,
    required this.controller,
    required this.node,
    required this.onFocus,
    required this.onOpen,
  });

  @override
  Widget build(BuildContext context) => ListView.builder(
    key: const PageStorageKey('launcher'),
    controller: controller,
    padding: EdgeInsets.zero,
    itemCount: rows.length,
    itemExtent: rowExtent,
    itemBuilder: (context, index) {
      final row = rows[index];
      return Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (var column = 0; column < columns; column++)
            Expanded(
              child: column < row.length ? _tile(row[column]) : const SizedBox.shrink(),
            ),
        ],
      );
    },
  );

  Widget _tile(Map app) {
    final package = app['id'] as String;
    final id = 'launcher:$package';
    final label = app['name'] as String;
    return Padding(
      padding: const EdgeInsets.fromLTRB(3, 2, 3, 6),
      child: FocusableActionDetector(
        key: ValueKey(id),
        focusNode: node(id),
        shortcuts: tvActivationShortcuts,
        actions: {
          ActivateIntent: CallbackAction<ActivateIntent>(
            onInvoke: (_) {
              onOpen(package);
              return null;
            },
          ),
        },
        onFocusChange: (focused) {
          if (!focused) return;
          onFocus(id);
          final targetContext = node(id).context;
          if (targetContext != null) {
            Scrollable.ensureVisible(targetContext, alignment: .5, duration: Duration.zero);
          }
        },
        child: Builder(
          builder: (context) {
            final focused = Focus.of(context).hasFocus;
            final foreground = focused ? Colors.black87 : Theme.of(context).colorScheme.onSurface;
            return Semantics(
              button: true,
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: () => onOpen(package),
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: focused ? ink : panel,
                    borderRadius: BorderRadius.circular(tileRadius),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 6),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        _appIcon(app['icon'], 54),
                        const SizedBox(height: 8),
                        Text(
                          label,
                          maxLines: 2,
                          textAlign: TextAlign.center,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(color: foreground, fontSize: 15, height: 1.2),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}

Widget _appIcon(Object? icon, double extent) {
  const fallback = Icon(Icons.android_rounded);
  if (icon is! Uint8List || icon.isEmpty) {
    return SizedBox.square(dimension: extent, child: Center(child: fallback));
  }
  return Image.memory(
    icon,
    width: extent,
    height: extent,
    fit: BoxFit.contain,
    gaplessPlayback: true,
    errorBuilder: (_, _, _) => SizedBox.square(dimension: extent, child: const Center(child: fallback)),
  );
}
