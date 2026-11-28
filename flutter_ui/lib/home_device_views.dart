part of 'home.dart';

extension _HomeDeviceViews on AppHomeState {
  Widget launcher(double width, bool compact) {
    final data = _shellData['launcher'] as Map? ?? {};
    final items = (data['items'] as List? ?? []).cast<Map>();
    if (items.isEmpty) {
      final loading = data['loading'] == true;
      _launcherRowById = {};
      rows = loading ? [] : [['launcher:refresh']];
      return Center(
        child: loading
            ? const Text('앱 목록 불러오는 중', style: TextStyle(color: muted))
            : Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(data['error'] as String? ?? '실행할 앱이 없습니다', style: const TextStyle(color: muted)),
                  const SizedBox(height: 10),
                  button('launcher:refresh', '새로고침', () => call('refreshLauncher')),
                ],
              ),
      );
    }

    final count = (width / (compact ? 128 : 148)).floor().clamp(1, 8);
    final shelf = appRows(items, count);
    rows = [
      for (final row in shelf) [for (final app in row) "launcher:${app['id']}"],
    ];
    _launcherRowById = {
      for (final (index, row) in rows.indexed)
        for (final id in row) id: index,
    };
    _launcherRowExtent = (compact ? 88.0 : 100.0) +
        MediaQuery.textScalerOf(context).scale(15) * 2.5;

    return LauncherShelf(
      rows: shelf,
      columns: count,
      rowExtent: _launcherRowExtent,
      controller: launcherScroll,
      node: node,
      onFocus: rememberContent,
      onOpen: (id) => call('launchHomeApp', id),
    );
  }

  Widget bluetooth() {
    if (_shellData['supported'] == false) {
      rows = [];
      return const Center(
        child: Text('이 기기는 블루투스를 지원하지 않습니다', style: TextStyle(color: muted)),
      );
    }
    final devices = (_shellData['devices'] as List? ?? []).cast<Map>();
    final registered = devices.where((d) => d['bonded'] == true).toList();
    final nearby = devices.where((d) => d['bonded'] != true).toList();
    final actions = <String>[
      'bt:scan',
      if (_shellData['permission'] != true) 'bt:permission',
      if (_shellData['enabled'] != true) 'bt:power',
    ];
    rows = [actions];
    final length = registered.length > nearby.length ? registered.length : nearby.length;
    for (var i = 0; i < length; i++) {
      rows.add([
        if (i < registered.length) 'bt:${registered[i]['id']}',
        if (i < nearby.length) 'bt:${nearby[i]['id']}',
      ]);
    }
    // Explicit column adjacency avoids jumping to the other list when one ends.
    Widget device(Map d) => tile(
      'bt:${d['id']}',
      d['name'] as String,
      () => call('device', d['id']),
      subtitle: d['detail'] as String?,
      icon: d['kind'] == 'input'
          ? Icons.keyboard_outlined
          : d['kind'] == 'audio'
          ? Icons.headphones_outlined
          : Icons.bluetooth_rounded,
    );
    Widget group(String title, List<Map> list) => Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(color: panel, borderRadius: BorderRadius.circular(panelRadius)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('$title  ·  ${list.length}', style: const TextStyle(fontSize: 18)),
          const SizedBox(height: 8),
          Expanded(
            child: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  if (list.isEmpty)
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 6),
                      child: Text('검색 결과가 없습니다', style: TextStyle(color: muted)),
                    ),
                  ...list.map(device),
                ],
              ),
            ),
          ),
        ],
      ),
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        toolbar([
          button('bt:scan', _shellData['searching'] == true ? '검색 중지' : '기기 검색', () => call('scan')),
          if (actions.contains('bt:permission'))
            button('bt:permission', '권한 허용', () => call('permission')),
          if (actions.contains('bt:power')) button('bt:power', '블루투스 켜기', () => call('power')),
        ], status: _shellData['status'] as String?),
        Expanded(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Expanded(child: group('내 기기', registered)),
              const SizedBox(width: 8),
              Expanded(child: group('새 기기', nearby)),
            ],
          ),
        ),
      ],
    );
  }

  Widget apps() {
    final appData = _shellData['apps'] as Map? ?? {};
    final items = (appData['items'] as List? ?? []).cast<Map>();
    final status = appData['status'] as String?;
    final shelf = appRows(items, columns);
    rows = [
      ['apps:filter', 'apps:view', 'apps:startup'],
      for (final row in shelf) [for (final a in row) "app:${a['id']}"],
    ];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        toolbar([
          button('apps:filter', appData['duals'] == true ? '모든 앱' : '내 듀얼앱', () => call('appFilter')),
          button('apps:view', '보기 설정', () => call('appView')),
          button('apps:startup', '시작 앱', () => call('startupSettings')),
        ], status: status == '듀얼앱을 만들 원본 앱을 선택하세요' ? null : status),
        Expanded(
          child: items.isEmpty
              ? Center(
                  child: Text(
                    appData['loading'] == true ? '앱 목록 불러오는 중' : '표시할 앱이 없습니다. 보기 설정에서 목록 범위를 확인하세요.',
                    style: const TextStyle(color: muted),
                  ),
                )
              : AppShelf(
                  rows: shelf,
                  columns: columns,
                  node: node,
                  onFocus: rememberContent,
                  onOpen: (id) => call('app', id),
                ),
        ),
      ],
    );
  }
}
