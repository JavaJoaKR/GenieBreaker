import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'main.dart';
import 'tv_focus.dart';

typedef _Folder = ({String path, String name});

/// Stable focus identities and explicit arrows keep async device/app updates from stealing focus.
class AppHome extends StatefulWidget {
  final Map<String, dynamic> data;
  final bool obscured;
  const AppHome({super.key, required this.data, this.obscured = false});
  @override
  State<AppHome> createState() => AppHomeState();
}
class AppHomeState extends State<AppHome> with WidgetsBindingObserver {
  static const tabs = ['bluetooth', 'apps', 'shortcuts', 'files', 'storage', 'settings'];
  static const titles = ['블루투스', '앱 관리', '바로가기', '파일탐색기', '저장공간', '설정'];
  static const icons = [Icons.bluetooth_rounded, Icons.apps_rounded, Icons.open_in_new_rounded, Icons.folder_outlined, Icons.storage_rounded, Icons.settings_outlined];
  final nodes = <String, FocusNode>{};
  final remembered = <String, String>{};
  List<_Folder> _trail = [];
  String? fileToolbarFocus;
  String? listingError;
  final folderFocus = <String, String>{};
  final contentScope = FocusScopeNode(debugLabel: 'content-scope');
  final sidebarScope = FocusScopeNode(debugLabel: 'sidebar-scope');
  String tab = 'bluetooth';
  String get location => _trail.lastOrNull?.path ?? '';
  Map<String, dynamic> files = {};
  Map<String, dynamic> storage = {};
  bool busy = false;
  String? error;
  String sort = 'name';
  int columns = 3;
  int fileRequest = 0;
  String? enteringTab;
  String? lastSurfaceFocus;
  String? modalOpener;
  final pendingKeys = <KeyEvent>[];
  List<List<String>> rows = [];
  FocusNode node(String id) => nodes.putIfAbsent(id, () => FocusNode(debugLabel: id));
  String? get focused => FocusManager.instance.primaryFocus?.debugLabel;
  bool get onSidebar => focused?.startsWith('nav:') == true;
  @override
  void initState() {
    super.initState(); WidgetsBinding.instance.addObserver(this);
    FocusManager.instance.addEarlyKeyEventHandler(rescueFocus);
    WidgetsBinding.instance.addPostFrameCallback((_) => focus('nav:$tab'));
  }
  @override
  void didUpdateWidget(covariant AppHome oldWidget) {
    super.didUpdateWidget(oldWidget);
    // The shell bridge restores the exact opener. A second recovery callback
    // here would override that pending focus with the first content control.
    if (oldWidget.obscured && !widget.obscured) return;
    if (!widget.obscured) WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || widget.obscured) return;
      final current = FocusManager.instance.primaryFocus;
      if (current == null || current == contentScope || current == sidebarScope || (current.context == null && !onSidebar)) enter();
    });
  }
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && !widget.obscured) {
      restore(); call('refreshShell');
      if (tab == 'files') refreshFiles();
      if (tab == 'storage') loadStorage();
    }
  }
  void focus(String id) {
    final target = nodes[id];
    if (target?.context == null || !target!.canRequestFocus) return;
    target.requestFocus();
    lastSurfaceFocus = id;
    if (!id.startsWith('nav:')) remembered[tab] = id;
    final context = target.context;
    if (context != null) Scrollable.ensureVisible(context, alignment: .5, duration: Duration.zero);
  }
  void saveModalOpener() { modalOpener = focused; }
  void restoreModalOpener() { final opener = modalOpener; modalOpener = null; restore(target: opener); }
  void restore({String? target}) {
    final wanted = target ?? lastSurfaceFocus ?? remembered[tab];
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || widget.obscured) return;
      if (wanted != null && nodes[wanted]?.context != null) { focus(wanted); } else { focus('nav:$tab'); }
      FocusManager.instance.applyFocusChangesIfNeeded();
    });
  }
  void enter() {
    final ids = rows.expand((r) => r).toList();
    final id = remembered[tab];
    focus(id != null && ids.contains(id) ? id : ids.firstOrNull ?? 'nav:$tab');
  }
  Future<dynamic> call(String method, [dynamic argument]) async {
    try { return await bridge.invokeMethod(method, argument); }
    catch (e) { if (mounted) setState(() => error = e is PlatformException ? e.message : '요청 실패 [GB-UI-0001]'); return null; }
  }
  void select(String next, {bool enterContent = false}) {
    if (tab == next) { if (enterContent) enter(); return; }
    // Move focus before removing the old subtree; no detached focus target survives a tab switch.
    focus('nav:$next');
    enteringTab = enterContent ? next : null; pendingKeys.clear();
    setState(() { tab = next; error = null; });
    if (next == 'files') refreshFiles();
    if (next == 'storage') loadStorage();
    if (next == 'apps') call('refreshShell');
    if (enterContent) WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || tab != next || enteringTab != next) return;
      enteringTab = null;
      final queued = List<KeyEvent>.from(pendingKeys); pendingKeys.clear();
      if (widget.obscured) return;
      enter();
      // Focus changes normally settle in a microtask. Settle them before replaying
      // keys that arrived while the new tab's first frame was being built.
      FocusManager.instance.applyFocusChangesIfNeeded();
      for (final event in queued) { key(contentScope, event); FocusManager.instance.applyFocusChangesIfNeeded(); }
    });
  }
  Future<void> loadStorage() async {
    final next = await call('storage.list');
    if (mounted && next is Map) setState(() => storage = Map<String, dynamic>.from(next));
  }
  void refreshFiles() {
    if (!busy) _loadFiles();
  }

  Future<void> _loadFiles({List<_Folder>? destination, bool restoreSelection = false}) async {
    final request = ++fileRequest;
    final previous = focused;
    final path = destination == null ? location : destination.lastOrNull?.path ?? '';
    late final Map<String, dynamic> next;
    try {
      final response = await bridge.invokeMapMethod<String, dynamic>('files.list', path);
      if (response == null) throw StateError('Missing listing');
      next = response;
    } catch (e) {
      if (mounted && request == fileRequest) {
        setState(() => listingError = e is PlatformException && e.message != null
            ? e.message
            : '폴더를 읽지 못했습니다. 새로고침해 주세요. [GB-UI-0001]');
      }
      return;
    }
    if (!mounted || request != fileRequest) return;
    setState(() {
      files = next;
      listingError = null;
      if (destination != null) _trail = destination;
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || request != fileRequest || tab != 'files' || widget.obscured || onSidebar) return;
      final candidates = rows.expand((r) => r);
      // A refresh must not move focus back to the control that started it if the
      // user has already moved elsewhere while the provider was answering.
      if (destination == null && !restoreSelection && candidates.contains(focused)) return;
      final saved = folderFocus[path];
      if (saved != null && candidates.contains(saved)) { focus(saved); }
      else if (previous != null && candidates.contains(previous)) { focus(previous); }
      else { focus(candidates.firstWhere((id) => id.startsWith('file:'), orElse: () => 'files:refresh')); }
    });
  }

  Future<void> _browseTo(List<_Folder> destination) async {
    if (busy) return;
    setState(() => busy = true);
    try {
      await _loadFiles(destination: destination);
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> openFolder(Map entry, {String? opener}) async {
    if (busy) return;
    final path = entry['path'] as String;
    folderFocus[location] = (opener ?? focused) == 'more:$path' ? 'more:$path' : 'file:$path';
    await _browseTo([..._trail, (path: path, name: entry['name'] as String)]);
  }

  void back() {
    if (busy) return;
    if (tab == 'files' && !onSidebar && _trail.isNotEmpty) {
      _browseTo(_trail.sublist(0, _trail.length - 1));
      return;
    }
    if (!onSidebar) { focus('nav:$tab'); return; }
    call('exit');
  }
  KeyEventResult rescueFocus(KeyEvent event) {
    if (!mounted || widget.obscured || ModalRoute.of(context)?.isCurrent != true || (event is! KeyDownEvent && event is! KeyRepeatEvent)) return KeyEventResult.ignored;
    if (![LogicalKeyboardKey.arrowUp, LogicalKeyboardKey.arrowDown, LogicalKeyboardKey.arrowLeft, LogicalKeyboardKey.arrowRight].contains(event.logicalKey)) return KeyEventResult.ignored;
    final id = focused;
    if (id != null && nodes[id]?.context != null && nodes[id]!.canRequestFocus) return KeyEventResult.ignored;
    // Losing Android surface focus must not consume the first directional press.
    focus('nav:$tab'); FocusManager.instance.applyFocusChangesIfNeeded();
    return key(contentScope, event);
  }
  KeyEventResult key(FocusNode _, KeyEvent event) {
    if (widget.obscured || (event is! KeyDownEvent && event is! KeyRepeatEvent)) return KeyEventResult.ignored;
    final key = event.logicalKey;
    final id = focused;
    if (enteringTab != null) {
      if (key == LogicalKeyboardKey.escape || key == LogicalKeyboardKey.goBack) {
        enteringTab = null; pendingKeys.clear(); focus('nav:$tab'); return KeyEventResult.handled;
      }
      if ([LogicalKeyboardKey.arrowUp, LogicalKeyboardKey.arrowDown, LogicalKeyboardKey.arrowLeft, LogicalKeyboardKey.arrowRight].contains(key)) {
        if (pendingKeys.length < 8) pendingKeys.add(event);
        return KeyEventResult.handled;
      }
    }
    if (key == LogicalKeyboardKey.escape || key == LogicalKeyboardKey.goBack) { back(); return KeyEventResult.handled; }
    if (![LogicalKeyboardKey.arrowUp, LogicalKeyboardKey.arrowDown, LogicalKeyboardKey.arrowLeft, LogicalKeyboardKey.arrowRight].contains(key)) return KeyEventResult.ignored;
    if (id == null || (!onSidebar && !rows.any((r) => r.contains(id)))) { focus('nav:$tab'); return KeyEventResult.handled; }
    if (onSidebar) {
      final index = tabs.indexOf(id.substring(4));
      if (key == LogicalKeyboardKey.arrowUp || key == LogicalKeyboardKey.arrowDown) {
        focus('nav:${tabs[(index + (key == LogicalKeyboardKey.arrowDown ? 1 : -1)).clamp(0, tabs.length - 1)]}');
      } else if (key == LogicalKeyboardKey.arrowRight) { select(tabs[index], enterContent: true); }
      return KeyEventResult.handled;
    }
    if (tab == 'bluetooth' && id.startsWith('bt:') && !['bt:scan', 'bt:power', 'bt:permission'].contains(id)) {
      final devices = (widget.data['devices'] as List? ?? []).cast<Map>();
      final deviceId = id.substring(3);
      final ownDevice = devices.where((d) => d['id'] == deviceId).firstOrNull;
      if (ownDevice == null) { enter(); return KeyEventResult.handled; }
      final bonded = ownDevice['bonded'] == true;
      final own = devices.where((d) => (d['bonded'] == true) == bonded).toList();
      final index = own.indexWhere((d) => d['id'] == deviceId);
      if (key == LogicalKeyboardKey.arrowUp) {
        if (index == 0) { focus('bt:scan'); } else { focus('bt:${own[index - 1]['id']}'); }
      } else if (key == LogicalKeyboardKey.arrowDown) {
        if (index + 1 < own.length) focus('bt:${own[index + 1]['id']}');
      } else if (key == LogicalKeyboardKey.arrowLeft && bonded) { focus('nav:$tab'); }
      else if ((key == LogicalKeyboardKey.arrowLeft && !bonded) || (key == LogicalKeyboardKey.arrowRight && bonded)) {
        final other = devices.where((d) => (d['bonded'] == true) != bonded).toList();
        if (index < other.length) { focus('bt:${other[index]['id']}'); }
        else if (key == LogicalKeyboardKey.arrowLeft && other.isEmpty) { focus('nav:$tab'); }
      }
      return KeyEventResult.handled;
    }
    final row = rows.indexWhere((r) => r.contains(id));
    final col = rows[row].indexOf(id);
    if (tab == 'files' && row == 0 && key == LogicalKeyboardKey.arrowDown && rows.length > 1) {
      fileToolbarFocus = id;
      focus(rows[1].first);
      return KeyEventResult.handled;
    }
    if (tab == 'files' && row == 1 && key == LogicalKeyboardKey.arrowUp) {
      final origin = fileToolbarFocus;
      focus(origin != null && rows.first.contains(origin) ? origin : rows.first.first);
      return KeyEventResult.handled;
    }
    if (key == LogicalKeyboardKey.arrowLeft) {
      if (col == 0) { focus('nav:$tab'); } else { focus(rows[row][col - 1]); }
    } else if (key == LogicalKeyboardKey.arrowRight) {
      if (col + 1 < rows[row].length) focus(rows[row][col + 1]);
    } else {
      final next = row + (key == LogicalKeyboardKey.arrowDown ? 1 : -1);
      if (next >= 0 && next < rows.length && (col < rows[next].length || next == 0)) focus(rows[next][col.clamp(0, rows[next].length - 1)]);
    }
    return KeyEventResult.handled;
  }
  Widget tile(String id, String title, VoidCallback action, {String? subtitle, IconData icon = Icons.chevron_right_rounded, Widget? image}) => RemoteTile(title,
    key: ValueKey(id), focusNode: node(id), subtitle: subtitle, icon: icon, leading: image,
    onFocus: () { lastSurfaceFocus = id; remembered[tab] = id; if (tab == 'files' && id.startsWith('file:')) folderFocus[location] = id; }, onTap: action);
  Widget button(String id, String title, VoidCallback action) => OutlinedButton(focusNode: node(id), onFocusChange: (value) { if (value) { lastSurfaceFocus = id; remembered[tab] = id; } }, onPressed: action, child: Text(title));
  Widget toolbar(List<Widget> children) => Padding(padding: const EdgeInsets.only(bottom: 16), child: SingleChildScrollView(scrollDirection: Axis.horizontal, child: Row(children: [for (final child in children) Padding(padding: const EdgeInsets.only(right: 10), child: child)])));
  Widget scroll(List<Widget> children) => SingleChildScrollView(key: PageStorageKey(tab), child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: children));
  Widget bluetooth() {
    if (widget.data['supported'] == false) { rows = []; return const Center(child: Text('이 기기는 블루투스를 지원하지 않습니다', style: TextStyle(color: muted))); }
    final devices = (widget.data['devices'] as List? ?? []).cast<Map>();
    final registered = devices.where((d) => d['bonded'] == true).toList();
    final nearby = devices.where((d) => d['bonded'] != true).toList();
    final actions = <String>['bt:scan', if (widget.data['permission'] != true) 'bt:permission', if (widget.data['enabled'] != true) 'bt:power'];
    rows = [actions];
    final length = registered.length > nearby.length ? registered.length : nearby.length;
    for (var i = 0; i < length; i++) { rows.add([if (i < registered.length) 'bt:${registered[i]['id']}', if (i < nearby.length) 'bt:${nearby[i]['id']}']); }
    // Explicit column adjacency avoids jumping to the other list when one ends.
    Widget device(Map d) => tile('bt:${d['id']}', d['name'] as String, () => call('device', d['id']), subtitle: d['detail'] as String?,
      icon: d['kind'] == 'input' ? Icons.keyboard_outlined : d['kind'] == 'audio' ? Icons.headphones_outlined : Icons.bluetooth_rounded);
    Widget group(String title, List<Map> list) => Container(padding: const EdgeInsets.all(14), decoration: BoxDecoration(color: panel, borderRadius: BorderRadius.circular(14)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [Text('$title  ·  ${list.length}', style: const TextStyle(fontSize: 18)), const SizedBox(height: 14),
        if (list.isEmpty) const Padding(padding: EdgeInsets.symmetric(vertical: 32), child: Text('표시할 기기가 없습니다', style: TextStyle(color: muted))), ...list.map(device)]));
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Text(widget.data['status'] as String? ?? '', style: const TextStyle(color: muted)), const SizedBox(height: 16),
      toolbar([button('bt:scan', widget.data['searching'] == true ? '검색 중지' : '기기 검색', () => call('scan')),
        if (actions.contains('bt:permission')) button('bt:permission', '권한 허용', () => call('permission')),
        if (actions.contains('bt:power')) button('bt:power', '블루투스 켜기', () => call('power'))]),
      Expanded(child: Row(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Expanded(child: SingleChildScrollView(child: group('내 기기', registered))), const SizedBox(width: 16),
        Expanded(child: SingleChildScrollView(child: group('새 기기', nearby))),
      ])),
    ]);
  }
  Widget apps() {
    final appData = widget.data['apps'] as Map? ?? {};
    final items = (appData['items'] as List? ?? []).cast<Map>();
    rows = [['apps:filter', 'apps:view']];
    for (var i = 0; i < items.length; i += columns) { rows.add(items.skip(i).take(columns).map((a) => 'app:${a['id']}').toList()); }
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      toolbar([button('apps:filter', appData['duals'] == true ? '모든 앱' : '내 듀얼앱', () => call('appFilter')),
        button('apps:view', '보기 설정', () => call('appView'))]),
      Text(appData['status'] as String? ?? '', style: const TextStyle(color: muted)), const SizedBox(height: 14),
      Expanded(child: items.isEmpty ? Center(child: Text(appData['loading'] == true ? '앱 목록을 불러오는 중입니다' : '표시할 앱이 없습니다. 보기 설정을 확인하세요.', style: const TextStyle(color: muted))) : scroll([
        for (var offset = 0; offset < items.length; offset += columns) Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          for (final a in items.skip(offset).take(columns)) Expanded(child: Padding(padding: const EdgeInsets.only(right: 10), child: tile('app:${a['id']}', a['name'] as String,
            () => call('app', a['id']), subtitle: a['detail'] as String?, icon: Icons.android_rounded,
            image: a['icon'] is Uint8List && (a['icon'] as Uint8List).isNotEmpty ? Image.memory(a['icon'] as Uint8List, width: 34, height: 34, gaplessPlayback: true) : null))),
          for (var gap = items.skip(offset).take(columns).length; gap < columns; gap++) const Expanded(child: SizedBox()),
        ]),
      ])),
    ]);
  }
  Future<void> dialogAction(String title, String message, Future<void> Function() action) async {
    final opener = focused;
    final yes = await showDialog<bool>(context: context, builder: (ctx) => Center(child: Padding(padding: const EdgeInsets.all(24), child: FocusTraversalGroup(policy: TvFocusPolicy(), child: PopupPanel(
      title: title, onBack: () => Navigator.pop(ctx, false), onClose: () => Navigator.pop(ctx, false),
      body: Text(message, style: const TextStyle(color: muted, fontSize: 16, height: 1.5)),
      footer: Row(mainAxisAlignment: MainAxisAlignment.end, children: [OutlinedButton(autofocus: true, onPressed: () => Navigator.pop(ctx, false), child: const Text('취소')),
        const SizedBox(width: 12), FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('확인'))]),
    )))));
    if (!mounted) return;
    if (yes == true) await action();
    if (mounted && opener != null) WidgetsBinding.instance.addPostFrameCallback((_) { if (mounted) focus(opener); });
  }
  Future<String?> inputName(String title, [String initial = '']) async {
    final text = TextEditingController(text: initial);
    final name = await showDialog<String>(context: context, builder: (ctx) => Center(child: Padding(padding: const EdgeInsets.all(24), child: PopupPanel(
      title: title, onBack: () => Navigator.pop(ctx), onClose: () => Navigator.pop(ctx),
      body: TextField(controller: text, autofocus: true, maxLength: 120, decoration: const InputDecoration(labelText: '이름'), onSubmitted: (value) => Navigator.pop(ctx, value)),
      footer: FilledButton(onPressed: () => Navigator.pop(ctx, text.text), child: const Text('저장')),
    ))));
    // Dialog route disposal finishes after its reverse transition.
    await Future<void>.delayed(const Duration(milliseconds: 250)); text.dispose();
    return name?.trim().isNotEmpty == true ? name!.trim() : null;
  }
  Future<void> fileAction(String action, {String? path, String? name}) async {
    if (busy) return;
    setState(() => busy = true);
    final response = await call('files.action', {'action': action, 'path': path ?? location, 'parent': location, if (name != null) 'name': name});
    if (response is Map && (response['selected'] as String? ?? '').isNotEmpty) folderFocus[location] = 'file:${response['selected']}';
    await _loadFiles(restoreSelection: true);
    if (mounted) setState(() => busy = false);
  }
  Future<void> fileMenu(Map entry) async {
    final opener = focused;
    final path = entry['path'] as String;
    final canWrite = entry['writable'] == true && entry['root'] != true;
    final actions = ['열기', if (entry['root'] != true) '복사', if (canWrite) ...['이동', '이름 변경', '삭제'], '상세 정보'];
    final action = await showDialog<String>(context: context, builder: (ctx) => Center(child: Padding(padding: const EdgeInsets.all(24), child: FocusTraversalGroup(policy: TvFocusPolicy(), child: PopupPanel(
      title: entry['name'] as String, onBack: () => Navigator.pop(ctx), onClose: () => Navigator.pop(ctx),
      body: SingleChildScrollView(child: Column(mainAxisSize: MainAxisSize.min, children: [for (var i = 0; i < actions.length; i++) RemoteTile(actions[i], autofocus: i == 0, onTap: () => Navigator.pop(ctx, actions[i]))])),
    )))));
    if (!mounted) return;
    if (action == '열기') { if (entry['directory'] == true) { await openFolder(entry, opener: opener); } else { await call('open', path); } }
    if (action == '복사' || action == '이동') await fileAction(action == '복사' ? 'copy' : 'cut', path: path);
    if (action == '이름 변경') { final name = await inputName('이름 변경', entry['name'] as String); if (mounted && name != null) await fileAction('rename', path: path, name: name); }
    if (action == '삭제') await dialogAction('삭제할까요?', '${entry['name']}\n폴더 안의 파일도 삭제되며 되돌릴 수 없습니다.', () => fileAction('delete', path: path));
    if (action == '상세 정보') await dialogAction('상세 정보', '${entry['name']}\n${entry['detail'] ?? ''}\n수정: ${DateTime.fromMillisecondsSinceEpoch((entry['modified'] as num? ?? 0).toInt()).toLocal()}\n${entry['writable'] == true ? '읽기·쓰기' : '읽기 전용'}', () async {});
    if (mounted && action != '열기') WidgetsBinding.instance.addPostFrameCallback((_) { if (mounted) { if (opener != null && nodes[opener]?.context != null) { focus(opener); } else { enter(); } } });
  }
  Widget explorer() {
    final entries = List<Map>.from(files['entries'] as List? ?? []);
    entries.sort((a, b) {
      if (a['directory'] != b['directory']) return a['directory'] == true ? -1 : 1;
      if (sort == 'size') return ((b['bytes'] as num? ?? 0).compareTo(a['bytes'] as num? ?? 0));
      if (sort == 'date') return ((b['modified'] as num? ?? 0).compareTo(a['modified'] as num? ?? 0));
      return (a['name'] as String).toLowerCase().compareTo((b['name'] as String).toLowerCase());
    });
    rows = [[if (_trail.isNotEmpty) 'files:up', if (_trail.length > 1) 'files:roots', 'files:refresh', 'files:sort', 'files:pick', if (files['writable'] == true) 'files:new', if (files['writable'] == true && files['clipboard'] != '') 'files:paste'],
      for (final e in entries) ['file:${e['path']}', 'more:${e['path']}']];
    final emptyMessage = files.isEmpty
        ? '저장소 목록을 불러오는 중입니다'
        : _trail.isEmpty ? '연결된 저장소가 없습니다' : '폴더가 비어 있습니다';
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Align(alignment: Alignment.centerLeft, child: SingleChildScrollView(
        scrollDirection: Axis.horizontal, reverse: true,
        child: Text(_trail.isEmpty ? '내 파일 · 연결한 저장소' : _trail.map((folder) => folder.name).join('  ›  '),
          maxLines: 1, style: const TextStyle(color: muted)),
      )), const SizedBox(height: 12),
      toolbar([if (_trail.isNotEmpty) button('files:up', '상위 폴더', back),
        if (_trail.length > 1) button('files:roots', '저장소 목록', () => _browseTo([])),
        button('files:refresh', '새로고침', refreshFiles),
        button('files:sort', '정렬: ${sort == 'name' ? '이름' : sort == 'size' ? '크기' : '수정일'}', () => setState(() { sort = sort == 'name' ? 'size' : sort == 'size' ? 'date' : 'name'; })),
        button('files:pick', '저장소 연결', () => call('pick')),
        if (files['writable'] == true) button('files:new', '새 폴더', () async { final name = await inputName('새 폴더'); if (mounted && name != null) await fileAction('mkdir', name: name); }),
        if (files['writable'] == true && files['clipboard'] != '') button('files:paste', '붙여넣기', () => fileAction('paste'))]),
      if ((files['clipboard'] as String? ?? '').isNotEmpty) Padding(padding: const EdgeInsets.only(bottom: 8), child: Text('${files['cut'] == true ? '이동' : '복사'}: ${files['clipboard']}', style: const TextStyle(color: blue))),
      if (listingError != null) Padding(padding: const EdgeInsets.only(bottom: 8),
        child: Text(listingError!, style: const TextStyle(color: Colors.orangeAccent))),
      if (busy) const Padding(padding: EdgeInsets.only(bottom: 8), child: LinearProgressIndicator()),
      if (location.isEmpty) const Padding(padding: EdgeInsets.only(bottom: 12), child: Text('내부 저장소·USB는 저장소 연결에서 폴더 접근을 허용하세요.', style: TextStyle(color: muted, fontSize: 12))),
      Expanded(child: entries.isEmpty ? Center(child: listingError == null
          ? Text(emptyMessage, style: const TextStyle(color: muted)) : null) : scroll([
        for (final e in entries) Row(children: [Expanded(child: tile('file:${e['path']}', e['name'] as String, () { if (e['directory'] == true) { openFolder(e); } else { fileMenu(e); } },
          subtitle: e['detail'] as String?, icon: e['directory'] == true ? Icons.folder_outlined : Icons.insert_drive_file_outlined)),
          const SizedBox(width: 8), IconButton(focusNode: node('more:${e['path']}'), tooltip: '파일 작업', onPressed: () => fileMenu(e), icon: const Icon(Icons.more_horiz_rounded))]),
      ])),
    ]);
  }
  Widget storagePage() {
    rows = [['storage:refresh'], ['storage:apks'], ['storage:updates'], ['storage:cache']];
    return scroll([
      toolbar([button('storage:refresh', '새로고침', loadStorage)]),
      Text('전체 ${storage['total'] ?? '—'}  ·  사용 가능 ${storage['free'] ?? '—'}', style: const TextStyle(fontSize: 18)), const SizedBox(height: 14),
      LinearProgressIndicator(value: (storage['usedFraction'] as num?)?.toDouble()), const SizedBox(height: 24),
      for (final item in [('apks', '듀얼앱 설치파일', 'apkSize'), ('updates', '업데이트 다운로드', 'updateSize'), ('cache', '임시 캐시', 'cacheSize')])
        tile('storage:${item.$1}', '${item.$2}  ·  ${storage[item.$3] ?? '—'}', () => dialogAction('${item.$2} 정리', '설치된 앱과 서명 키는 유지됩니다.\n다운로드·설치·파일 작업 중에는 정리할 수 없습니다.', () async { await call('clean', item.$1); await loadStorage(); }), icon: Icons.cleaning_services_outlined),
      const SizedBox(height: 12), const Text('정리 작업은 이 앱이 만든 설치파일과 캐시에만 적용됩니다.', style: TextStyle(color: muted)),
    ]);
  }
  @override
  Widget build(BuildContext context) => PopScope(canPop: false, onPopInvokedWithResult: (didPop, _) { if (!didPop && !widget.obscured) back(); }, child: Focus(onKeyEvent: key,
    child: LayoutBuilder(builder: (context, constraints) {
      columns = ((constraints.maxWidth - 240) / 220).floor().clamp(1, 5);
      Widget content;
      switch (tab) {
        case 'bluetooth': content = bluetooth();
        case 'apps': content = apps();
        case 'files': content = explorer();
        case 'storage': content = storagePage();
        case 'shortcuts': rows = [['shortcut:apps']]; content = scroll([tile('shortcut:apps', '앱 목록 설정', () => call('appSettings'), subtitle: 'TV 설정의 설치된 앱 목록 열기', icon: Icons.apps_rounded)]);
        default: rows = [['settings:update'], ['settings:diagnostics']]; content = scroll([
          tile('settings:update', widget.data['update'] as String? ?? '업데이트 확인', () => call('update'), icon: Icons.system_update_rounded),
          tile('settings:diagnostics', '문제 기록', () => call('diagnostics'), subtitle: '기기 안에 저장된 오류 식별 코드', icon: Icons.info_outline_rounded),
        ]);
      }
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        final active = {...rows.expand((row) => row), ...tabs.map((name) => 'nav:$name')};
        final stale = nodes.keys.where((id) => !active.contains(id) && nodes[id]?.context == null).toList();
        for (final id in stale) { nodes.remove(id)?.dispose(); }
      });
      return ColoredBox(color: canvas, child: Padding(padding: const EdgeInsets.all(28), child: Row(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        SizedBox(width: 176, child: FocusScope(node: sidebarScope, child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const Text('GenieBreaker', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w600)), const SizedBox(height: 6),
          Text('v${widget.data['version'] ?? ''}', style: const TextStyle(color: muted, fontSize: 11)), const SizedBox(height: 24),
          Expanded(child: SingleChildScrollView(child: Column(children: [for (var i = 0; i < tabs.length; i++) RemoteTile(titles[i],
            key: ValueKey('nav:${tabs[i]}'), focusNode: node('nav:${tabs[i]}'), selected: tab == tabs[i], icon: icons[i], onFocus: () => lastSurfaceFocus = 'nav:${tabs[i]}', onTap: () => select(tabs[i]))]))),
        ]))),
        const SizedBox(width: 20), const VerticalDivider(width: 1, color: Color(0xff304057)), const SizedBox(width: 24),
        Expanded(child: FocusScope(node: contentScope, child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Text(titles[tabs.indexOf(tab)], style: const TextStyle(fontSize: 30, fontWeight: FontWeight.w600)), const SizedBox(height: 18),
          if (error != null) Padding(padding: const EdgeInsets.only(bottom: 12), child: Text(error!, style: const TextStyle(color: Colors.orangeAccent))),
          Expanded(child: content),
        ]))),
      ])));
    }),
  ));
  void filesChanged() { if (tab == 'files') refreshFiles(); }
  @override
  void dispose() {
    FocusManager.instance.removeEarlyKeyEventHandler(rescueFocus);
    WidgetsBinding.instance.removeObserver(this);
    for (final n in nodes.values) { n.dispose(); } contentScope.dispose(); sidebarScope.dispose(); super.dispose();
  }
}
