import 'package:flutter/material.dart';

import 'remote_tile.dart';
import 'tv_theme.dart';

enum FileSort {
  name('이름'),
  size('크기'),
  modified('수정일');

  const FileSort(this.label);
  final String label;

  FileSort get next => values[(index + 1) % values.length];

  List<Map> ordered(List? source) => List<Map>.from(source ?? [])..sort((a, b) {
    if (a['directory'] != b['directory']) return a['directory'] == true ? -1 : 1;
    return switch (this) {
      FileSort.name => (a['name'] as String).toLowerCase().compareTo((b['name'] as String).toLowerCase()),
      FileSort.size => (b['bytes'] as num? ?? 0).compareTo(a['bytes'] as num? ?? 0),
      FileSort.modified => (b['modified'] as num? ?? 0).compareTo(a['modified'] as num? ?? 0),
    };
  });
}

class FileEntries extends StatelessWidget {
  const FileEntries({
    super.key,
    required this.entries,
    required this.emptyMessage,
    required this.node,
    required this.onFocus,
    required this.onOpen,
    required this.onOptions,
  });

  final List<Map> entries;
  final String? emptyMessage;
  final FocusNode Function(String) node;
  final ValueChanged<String> onFocus;
  final ValueChanged<Map> onOpen, onOptions;

  @override
  Widget build(BuildContext context) {
    if (entries.isEmpty) {
      return Center(child: emptyMessage == null ? null : Text(emptyMessage!, style: const TextStyle(color: muted)));
    }
    return SingleChildScrollView(
      key: const PageStorageKey('files'),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [for (final entry in entries) _row(entry)],
      ),
    );
  }

  Widget _row(Map entry) {
    final path = entry['path'];
    final file = 'file:$path';
    final options = 'more:$path';
    return Row(
      children: [
        Expanded(
          child: RemoteTile(
            entry['name'] as String,
            key: ValueKey(file),
            focusNode: node(file),
            subtitle: entry['detail'] as String?,
            icon: entry['directory'] == true ? Icons.folder_outlined : Icons.insert_drive_file_outlined,
            onFocus: () => onFocus(file),
            onTap: () => onOpen(entry),
          ),
        ),
        const SizedBox(width: 8),
        Focus(
          canRequestFocus: false,
          onFocusChange: (focused) {
            if (focused) onFocus(options);
          },
          child: IconButton(
            focusNode: node(options),
            tooltip: '파일 작업',
            onPressed: () => onOptions(entry),
            icon: const Icon(Icons.more_horiz_rounded),
          ),
        ),
      ],
    );
  }
}
