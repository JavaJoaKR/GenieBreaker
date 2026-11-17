import 'package:flutter/material.dart';
import 'popup_panel.dart';
import 'tv_focus.dart';
import 'tv_theme.dart';

Future<T?> showTvDialog<T>(BuildContext context, Widget Function(BuildContext) panel) => showDialog<T>(
  context: context,
  builder: (route) => Center(
    child: Padding(
      padding: const EdgeInsets.all(20),
      child: FocusTraversalGroup(policy: TvFocusPolicy(), child: panel(route)),
    ),
  ),
);

Future<bool> confirmAction(BuildContext context, String title, String message, String label) async {
  final accepted = await showTvDialog<bool>(
    context,
    (route) => PopupPanel(
      title: title,
      onBack: () => Navigator.pop(route, false),
      onClose: () => Navigator.pop(route, false),
      body: SingleChildScrollView(
        child: Text(message, style: const TextStyle(color: muted, fontSize: 16, height: 1.5)),
      ),
      footer: Wrap(
        alignment: WrapAlignment.end,
        spacing: 12,
        children: [
          OutlinedButton(
            autofocus: true,
            onPressed: () => Navigator.pop(route, false),
            child: const Text('취소'),
          ),
          FilledButton(onPressed: () => Navigator.pop(route, true), child: Text(label)),
        ],
      ),
    ),
  );
  return accepted == true;
}

Future<void> showFileDetails(BuildContext context, Map entry) async {
  final stamp = (entry['modified'] as num?)?.toInt() ?? 0;
  final date = DateTime.fromMillisecondsSinceEpoch(stamp);
  final local = MaterialLocalizations.of(context);
  final lines = [
    if ((entry['detail'] as String? ?? '').isNotEmpty) entry['detail'],
    if (stamp > 0)
      '수정일  ${date.year}.${date.month}.${date.day}  ${local.formatTimeOfDay(TimeOfDay.fromDateTime(date), alwaysUse24HourFormat: true)}',
    entry['writable'] == true ? '읽기·쓰기' : '읽기 전용',
  ];
  await showTvDialog<void>(
    context,
    (route) => PopupPanel(
      title: entry['name'] as String,
      onBack: () => Navigator.pop(route),
      onClose: () => Navigator.pop(route),
      body: SingleChildScrollView(
        child: Text(lines.join('\n'), style: const TextStyle(fontSize: 16, height: 1.6)),
      ),
      footer: Align(
        alignment: Alignment.centerRight,
        child: OutlinedButton(
          autofocus: true,
          onPressed: () => Navigator.pop(route),
          child: const Text('닫기'),
        ),
      ),
    ),
  );
}

Future<String?> promptName(BuildContext context, String title, [String initial = '']) {
  var name = initial;
  return showTvDialog<String>(context, (route) {
    void save(String value) {
      Navigator.pop(route, value.trim().isEmpty ? null : value.trim());
    }

    return PopupPanel(
      title: title,
      onBack: () => Navigator.pop(route),
      onClose: () => Navigator.pop(route),
      body: SingleChildScrollView(
        child: TextFormField(
          initialValue: initial,
          autofocus: true,
          maxLength: 120,
          decoration: const InputDecoration(labelText: '이름'),
          onChanged: (value) => name = value,
          onFieldSubmitted: save,
        ),
      ),
      footer: FilledButton(onPressed: () => save(name), child: const Text('저장')),
    );
  });
}
