part of 'home.dart';

extension _HomeStorageViews on AppHomeState {
  Widget explorer() {
    final query = fileQuery.toLowerCase();
    final entries = sort.ordered(files['entries'] as List?).where((entry) {
      return (entry['name'] as String).toLowerCase().contains(query);
    }).toList();
    final clipboard = files['clipboard'] as String? ?? '';
    final writable = files['writable'] == true;
    final canPaste = writable && clipboard.isNotEmpty;
    rows = [
      [
        if (_trail.isNotEmpty) 'files:up',
        if (_trail.length > 1) 'files:roots',
        'files:refresh',
        'files:sort',
        'files:pick',
        if (writable) 'files:new',
        if (canPaste) 'files:paste',
        if (_trail.isNotEmpty) 'files:path',
        'files:search',
        if (fileQuery.isNotEmpty) 'files:clear-search',
        if (clipboard.isNotEmpty) 'files:clear-clipboard',
      ],
      for (final e in entries) ['file:${e['path']}', 'more:${e['path']}'],
    ];
    final emptyMessage = fileQuery.isNotEmpty
        ? '일치하는 항목이 없습니다'
        : files.isEmpty
        ? '저장소 목록 불러오는 중'
        : _trail.isEmpty
        ? '연결된 저장소가 없습니다'
        : '폴더가 비어 있습니다';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Align(
          alignment: Alignment.centerLeft,
          child: SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            reverse: true,
            child: Text(
              _trail.isEmpty ? '내 파일 · 연결한 저장소' : _trail.map((folder) => folder.name).join('  ›  '),
              maxLines: 1,
              style: const TextStyle(color: muted),
            ),
          ),
        ),
        const SizedBox(height: 12),
        toolbar([
          if (_trail.isNotEmpty) button('files:up', '상위 폴더', back),
          if (_trail.length > 1) button('files:roots', '저장소 목록', () => _browseTo([])),
          button('files:refresh', '새로고침', refreshFiles),
          button('files:sort', '정렬: ${sort.label}', _cycleFileSort),
          button('files:pick', '저장소 연결', () => call('pick')),
          if (writable)
            button('files:new', '새 폴더', () async {
              final name = await promptName(context, '새 폴더');
              if (mounted && name != null) await fileAction('mkdir', name: name);
            }),
          if (canPaste)
            button('files:paste', '붙여넣기', () => fileAction('paste')),
          if (_trail.isNotEmpty) button('files:path', '경로 이동', _showFilePath),
          button('files:search', '검색', _searchFiles),
          if (fileQuery.isNotEmpty) button('files:clear-search', '검색 해제', () => _setFileQuery('')),
          if (clipboard.isNotEmpty)
            button('files:clear-clipboard', '복사·이동 취소', () => fileAction('clearClipboard')),
        ]),
        if (fileQuery.isNotEmpty)
          Text('검색어 · $fileQuery', style: const TextStyle(color: accentTone)),
        if (clipboard.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(
              '${files['cut'] == true ? '이동' : '복사'}: $clipboard',
              style: const TextStyle(color: accentTone),
            ),
          ),
        if (listingError != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(listingError!, style: const TextStyle(color: ink, fontWeight: FontWeight.w600)),
          ),
        if (busy) const Padding(padding: EdgeInsets.only(bottom: 8), child: LinearProgressIndicator()),
        if (location.isEmpty)
          const Padding(
            padding: EdgeInsets.only(bottom: 12),
            child: Text(
              '내부 저장소나 USB의 폴더는 ‘저장소 연결’에서 접근을 허용하세요.',
              style: TextStyle(color: muted, fontSize: 12),
            ),
          ),
        Expanded(
          child: FileEntries(
            entries: entries,
            emptyMessage: listingError == null ? emptyMessage : null,
            node: node,
            onFocus: rememberContent,
            onOpen: (entry) {
              if (entry['directory'] == true) {
                openFolder(entry);
              } else {
                if (!busy) call('open', entry['path']);
              }
            },
            onOptions: fileMenu,
          ),
        ),
      ],
    );
  }

  Future<void> _searchFiles() async {
    final query = await promptText(context, '현재 목록 검색', initial: fileQuery,
        fieldLabel: '파일이나 폴더 이름', actionLabel: '검색');
    if (mounted && query != null) _setFileQuery(query);
  }

  Future<void> _showFilePath() async {
    final trail = List<_Folder>.of(_trail);
    final level = await showTvDialog<int>(context, (route) => PopupPanel(
      title: '경로 이동',
      onBack: () => Navigator.pop(route),
      onClose: () => Navigator.pop(route),
      body: SingleChildScrollView(child: Column(children: [
        RemoteTile('저장소 목록', autofocus: true, onTap: () => Navigator.pop(route, 0)),
        for (var i = 0; i < trail.length - 1; i++)
          RemoteTile(trail[i].name, onTap: () => Navigator.pop(route, i + 1)),
      ])),
    ));
    if (mounted && level != null) await _browseTo(trail.take(level).toList());
  }

  Widget storagePage() {
    rows = [
      ['storage:refresh'],
      ['storage:apks'],
      ['storage:updates'],
      ['storage:cache'],
    ];
    return scroll([
      toolbar([button('storage:refresh', '새로고침', loadStorage)]),
      StorageSummary(storage),
      const SizedBox(height: 10),
      for (final item in [
        ('apks', '듀얼앱 설치 파일', 'apkSize'),
        ('updates', '다운로드한 업데이트', 'updateSize'),
        ('cache', '캐시', 'cacheSize'),
      ])
        tile(
          'storage:${item.$1}',
          '${item.$2}  ·  ${storage[item.$3] ?? '—'}',
          () => dialogAction(
            '${item.$2} 정리',
            'GenieBreaker에 저장된 ${item.$2} 파일을 삭제합니다. 설치된 앱은 유지됩니다.',
            '정리',
            () async {
              await call('clean', item.$1);
              await loadStorage();
            },
          ),
          icon: Icons.cleaning_services_outlined,
        ),
      const SizedBox(height: 12),
      const Text('정리 작업은 이 앱이 만든 설치 파일과 캐시에만 적용됩니다.', style: TextStyle(color: muted)),
    ]);
  }
}
