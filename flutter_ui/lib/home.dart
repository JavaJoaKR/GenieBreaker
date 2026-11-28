import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'app_shelf.dart';
import 'file_entries.dart';
import 'platform_bridge.dart';
import 'popup_panel.dart';
import 'remote_tile.dart';
import 'shell_frame.dart';
import 'storage_summary.dart';
import 'tv_dialogs.dart';
import 'tv_controls.dart';
import 'tv_focus.dart';
import 'tv_theme.dart';

part 'home_device_views.dart';
part 'home_storage_views.dart';

typedef _Folder = ({String path, String name});

class _HomeFocusNode extends FocusNode {
  _HomeFocusNode(this.id) : super(debugLabel: id);

  final String id;
}

/// Stable focus identities and explicit arrows keep async device/app updates from stealing focus.
class AppHome extends StatefulWidget {
  final Map<String, dynamic> data;
  final bool obscured;
  const AppHome({super.key, required this.data, this.obscured = false});
  @override
  State<AppHome> createState() => AppHomeState();
}

class AppHomeState extends State<AppHome> with WidgetsBindingObserver {
  static final tabs = TvTab.values.map((item) => item.name).toList(growable: false);
  final nodes = <String, FocusNode>{};
  final remembered = <String, String>{};
  List<_Folder> _trail = [];
  String? fileToolbarFocus;
  String? listingError;
  String fileQuery = '';
  final folderFocus = <String, String>{};
  final contentScope = FocusScopeNode(debugLabel: 'content-scope');
  final sidebarScope = FocusScopeNode(debugLabel: 'sidebar-scope');
  String tab = 'home';
  final launcherScroll = ScrollController();
  Map<String, int> _launcherRowById = {};
  double _launcherRowExtent = 0;
  String? _pendingLauncherFocus;
  bool _enterLauncherWhenReady = false;
  String get location => _trail.lastOrNull?.path ?? '';
  Map<String, dynamic> files = {};
  Map<String, dynamic> storage = {};
  bool busy = false;
  String? error;
  FileSort sort = FileSort.name;
  int columns = 3;
  int fileRequest = 0;
  String? renderedTab;
  String? enteringTab;
  String? lastSurfaceFocus;
  String? modalOpener;
  final pendingKeys = <LogicalKeyboardKey>[];
  List<List<String>> rows = [];
  FocusNode node(String id) => nodes.putIfAbsent(id, () => _HomeFocusNode(id));
  String? get focused {
    // Flutter omits debugLabel in release builds. Navigation needs a runtime ID.
    final current = FocusManager.instance.primaryFocus;
    return current is _HomeFocusNode && identical(nodes[current.id], current) ? current.id : null;
  }
  bool get onSidebar => focused?.startsWith('nav:') == true;
  bool get acceptsFocus => mounted && !widget.obscured && ModalRoute.of(context)?.isCurrent == true;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    FocusManager.instance.addEarlyKeyEventHandler(rescueFocus);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      focus('nav:$tab');
      if (widget.data['homeEntry'] == true) showLauncher();
    });
  }

  @override
  void didUpdateWidget(covariant AppHome oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (_enterLauncherWhenReady) _focusLauncherWhenReady();
    // The shell bridge restores the exact opener. A second recovery callback
    // here would override that pending focus with the first content control.
    if (oldWidget.obscured && !widget.obscured) return;
    if (!widget.obscured)
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!acceptsFocus) return;
        final current = FocusManager.instance.primaryFocus;
        if (current == null ||
            current == contentScope ||
            current == sidebarScope ||
            (current.context == null && !onSidebar))
          enter();
      });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && acceptsFocus) {
      restore();
      call('refreshShell');
      if (tab == 'files') refreshFiles();
      if (tab == 'storage') loadStorage();
    }
  }

  void focus(String id) {
    _pendingLauncherFocus = null;
    final target = nodes[id];
    if (target == null || target.context == null) {
      _scrollToLauncherItem(id);
      return;
    }
    if (!target.canRequestFocus) return;
    target.requestFocus();
    lastSurfaceFocus = id;
    if (!id.startsWith('nav:')) remembered[tab] = id;
    final context = target.context;
    if (context != null) Scrollable.ensureVisible(context, alignment: .5, duration: Duration.zero);
  }

  void _scrollToLauncherItem(String id) {
    final row = _launcherRowById[id];
    if (tab != 'home' || row == null || !launcherScroll.hasClients) return;
    final position = launcherScroll.position;
    if (!position.hasContentDimensions) return;
    final offset = (row * _launcherRowExtent).clamp(0.0, position.maxScrollExtent).toDouble();
    _pendingLauncherFocus = id;
    launcherScroll.jumpTo(offset);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || _pendingLauncherFocus != id) return;
      _pendingLauncherFocus = null;
      if (acceptsFocus && tab == 'home' && nodes[id]?.context != null) focus(id);
    });
    WidgetsBinding.instance.ensureVisualUpdate();
  }

  void showLauncher() {
    select('home');
    _enterLauncherWhenReady = true;
    _focusLauncherWhenReady();
  }

  void _focusLauncherWhenReady() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_enterLauncherWhenReady || !acceptsFocus || tab != 'home') return;
      if (rows.isEmpty && (widget.data['launcher'] as Map?)?['loading'] == true) return;
      _enterLauncherWhenReady = false;
      enter();
      FocusManager.instance.applyFocusChangesIfNeeded();
    });
    WidgetsBinding.instance.ensureVisualUpdate();
  }

  void saveModalOpener() {
    modalOpener = focused;
  }

  void restoreModalOpener() {
    final opener = modalOpener;
    modalOpener = null;
    restore(target: opener);
  }

  void restore({String? target}) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!acceptsFocus) return;
      // Read the latest selection after rendering, including arrows received
      // between the Android focus callback and this frame.
      final wanted = target ?? lastSurfaceFocus ?? remembered[tab];
      if (wanted != null && nodes[wanted]?.context != null) {
        focus(wanted);
      } else {
        focus('nav:$tab');
      }
      FocusManager.instance.applyFocusChangesIfNeeded();
    });
    WidgetsBinding.instance.ensureVisualUpdate();
  }

  void enter() {
    final ids = rows.expand((r) => r).toList();
    final id = remembered[tab];
    focus(id != null && ids.contains(id) ? id : ids.firstOrNull ?? 'nav:$tab');
  }

  Future<dynamic> call(String method, [dynamic argument]) async {
    final origin = tab;
    try {
      return await bridge.invokeMethod(method, argument);
    } catch (e) {
      if (mounted && tab == origin) setState(() => error = requestError(e));
      return null;
    }
  }

  Future<void> configureLock() async {
    if (widget.data['lockEnabled'] == true) {
      final accepted = await confirmAction(context, '앱 잠금 해제', '잠금을 끄면 앱을 바로 열 수 있습니다.', '잠금 끄기');
      if (mounted && accepted) await call('disableLock');
      return;
    }
    await call('enableLock');
  }

  void select(String next, {bool enterContent = false}) {
    if (tab != next) {
      _enterLauncherWhenReady = false;
      _pendingLauncherFocus = null;
      // Keep focus in the rail while the content subtree switches.
      if (focused != 'nav:$next') focus('nav:$next');
      enteringTab = null;
      pendingKeys.clear();
      setState(() {
        tab = next;
        error = null;
      });
      if (next == 'files') refreshFiles();
      if (next == 'storage') loadStorage();
      if (next == 'apps') call('refreshShell');
    }
    if (!enterContent || enteringTab == next) return;
    if (renderedTab == next) {
      if (next == 'home' && rows.isEmpty) _enterLauncherWhenReady = true;
      enter();
      return;
    }
    enteringTab = next;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || tab != next || enteringTab != next) return;
      enteringTab = null;
      final queued = List<LogicalKeyboardKey>.from(pendingKeys);
      pendingKeys.clear();
      if (!acceptsFocus) return;
      enter();
      FocusManager.instance.applyFocusChangesIfNeeded();
      for (final key in queued) {
        navigate(key);
        FocusManager.instance.applyFocusChangesIfNeeded();
      }
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
        setState(
          () => listingError = e is PlatformException && e.message != null
              ? e.message
              : '폴더를 열 수 없습니다. 목록을 새로고침하세요. [GB-UI-0001]',
        );
      }
      return;
    }
    if (!mounted || request != fileRequest) return;
    setState(() {
      files = next;
      listingError = null;
      if (destination != null) {
        _trail = destination;
        fileQuery = '';
        fileToolbarFocus = null;
      }
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || request != fileRequest || tab != 'files' || widget.obscured || onSidebar) return;
      final candidates = rows.expand((r) => r);
      // A refresh must not move focus back to the control that started it if the
      // user has already moved elsewhere while the provider was answering.
      if (destination == null && !restoreSelection && candidates.contains(focused)) return;
      final saved = folderFocus[path];
      if (saved != null && candidates.contains(saved)) {
        focus(saved);
      } else if (previous != null && candidates.contains(previous)) {
        focus(previous);
      } else {
        focus(candidates.firstWhere((id) => id.startsWith('file:'), orElse: () => 'files:refresh'));
      }
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
    if (!onSidebar) {
      focus('nav:$tab');
      return;
    }
    call('exit');
  }

  KeyEventResult rescueFocus(KeyEvent event) {
    if (!acceptsFocus || !isTvNavigationPress(event)) return KeyEventResult.ignored;
    return recoverFocus() ? navigate(event.logicalKey) : KeyEventResult.ignored;
  }

  bool recoverFocus() {
    if (!acceptsFocus) return false;
    final current = nodes[focused];
    if (current?.context != null && current!.canRequestFocus) return false;
    final saved = nodes[lastSurfaceFocus];
    focus(saved?.context != null && saved!.canRequestFocus ? lastSurfaceFocus! : 'nav:$tab');
    FocusManager.instance.applyFocusChangesIfNeeded();
    return true;
  }

  KeyEventResult key(FocusNode _, KeyEvent event) {
    if (event is! KeyDownEvent && event is! KeyRepeatEvent) return KeyEventResult.ignored;
    return navigate(event.logicalKey);
  }

  KeyEventResult navigate(LogicalKeyboardKey key) {
    if (!acceptsFocus) return KeyEventResult.ignored;
    final id = focused;
    if (enteringTab != null) {
      if (key == LogicalKeyboardKey.escape || key == LogicalKeyboardKey.goBack) {
        enteringTab = null;
        pendingKeys.clear();
        focus('nav:$tab');
        return KeyEventResult.handled;
      }
      if (tvArrowKeys.contains(key)) {
        if (pendingKeys.length < 8) pendingKeys.add(key);
        return KeyEventResult.handled;
      }
    }
    if (key == LogicalKeyboardKey.escape || key == LogicalKeyboardKey.goBack) {
      back();
      return KeyEventResult.handled;
    }
    if (!tvArrowKeys.contains(key)) return KeyEventResult.ignored;
    if (id == null || (!onSidebar && !rows.any((r) => r.contains(id)))) {
      focus('nav:$tab');
      return KeyEventResult.handled;
    }
    if (onSidebar) {
      final index = tabs.indexOf(id.substring(4));
      if (key == LogicalKeyboardKey.arrowUp || key == LogicalKeyboardKey.arrowDown) {
        final next = index + (key == LogicalKeyboardKey.arrowDown ? 1 : -1);
        select(tabs[next.clamp(0, tabs.length - 1)]);
      } else if (key == LogicalKeyboardKey.arrowRight) {
        select(tabs[index], enterContent: true);
      }
      return KeyEventResult.handled;
    }
    if (tab == 'bluetooth' &&
        id.startsWith('bt:') &&
        !['bt:scan', 'bt:power', 'bt:permission'].contains(id)) {
      final devices = (widget.data['devices'] as List? ?? []).cast<Map>();
      final deviceId = id.substring(3);
      final ownDevice = devices.where((d) => d['id'] == deviceId).firstOrNull;
      if (ownDevice == null) {
        enter();
        return KeyEventResult.handled;
      }
      final bonded = ownDevice['bonded'] == true;
      final own = devices.where((d) => (d['bonded'] == true) == bonded).toList();
      final index = own.indexWhere((d) => d['id'] == deviceId);
      if (key == LogicalKeyboardKey.arrowUp) {
        if (index == 0) {
          focus('bt:scan');
        } else {
          focus('bt:${own[index - 1]['id']}');
        }
      } else if (key == LogicalKeyboardKey.arrowDown) {
        if (index + 1 < own.length) focus('bt:${own[index + 1]['id']}');
      } else if (key == LogicalKeyboardKey.arrowLeft && bonded) {
        focus('nav:$tab');
      } else if ((key == LogicalKeyboardKey.arrowLeft && !bonded) ||
          (key == LogicalKeyboardKey.arrowRight && bonded)) {
        final other = devices.where((d) => (d['bonded'] == true) != bonded).toList();
        if (index < other.length) {
          focus('bt:${other[index]['id']}');
        } else if (key == LogicalKeyboardKey.arrowLeft && other.isEmpty) {
          focus('nav:$tab');
        }
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
      if (col == 0) {
        focus('nav:$tab');
      } else {
        focus(rows[row][col - 1]);
      }
    } else if (key == LogicalKeyboardKey.arrowRight) {
      if (col + 1 < rows[row].length) focus(rows[row][col + 1]);
    } else {
      final next = row + (key == LogicalKeyboardKey.arrowDown ? 1 : -1);
      if (next >= 0 && next < rows.length && (col < rows[next].length || next == 0))
        focus(rows[next][col.clamp(0, rows[next].length - 1)]);
    }
    return KeyEventResult.handled;
  }

  void rememberContent(String id) {
    lastSurfaceFocus = id;
    remembered[tab] = id;
    if (tab == 'files' && id.startsWith('file:')) folderFocus[location] = id;
  }

  Widget tile(
    String id,
    String title,
    VoidCallback action, {
    String? subtitle,
    IconData icon = Icons.chevron_right_rounded,
    Widget? image,
  }) => RemoteTile(
    title,
    key: ValueKey(id),
    focusNode: node(id),
    subtitle: subtitle,
    icon: icon,
    leading: image,
    onFocus: () => rememberContent(id),
    onTap: action,
  );
  Widget button(String id, String title, VoidCallback action) => OutlinedButton(
    focusNode: node(id),
    onFocusChange: (value) {
      if (value) rememberContent(id);
    },
    onPressed: action,
    child: Text(title),
  );
  Widget toolbar(List<Widget> children, {String? status}) {
    final actions = SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: [
          for (final child in children) Padding(padding: const EdgeInsets.only(right: 6), child: child),
        ],
      ),
    );
    return Padding(
      padding: const EdgeInsets.only(bottom: 9),
      child: status == null || status.isEmpty
          ? actions
          : LayoutBuilder(
              builder: (context, constraints) {
                final note = Text(status, maxLines: 2, overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: muted));
                if (constraints.maxWidth < 540) {
                  return Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [actions, const SizedBox(height: 4), note],
                  );
                }
                return Row(
                  children: [
                    Expanded(flex: 3, child: actions),
                    const SizedBox(width: 6),
                    Expanded(flex: 2, child: Align(alignment: Alignment.centerRight, child: note)),
                  ],
                );
              },
            ),
    );
  }
  Widget scroll(List<Widget> children) => SingleChildScrollView(
    key: PageStorageKey(tab),
    child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: children),
  );
  Future<void> dialogAction(
    String title,
    String message,
    String label,
    Future<void> Function() action,
  ) async {
    final opener = focused;
    final yes = await confirmAction(context, title, message, label);
    if (!mounted) return;
    if (yes) await action();
    if (mounted && opener != null) restore(target: opener);
  }

  Future<void> fileAction(String action, {String? path, String? name}) async {
    if (busy) return;
    setState(() => busy = true);
    try {
      final response = await call('files.action', {
        'action': action,
        'path': path ?? location,
        'parent': location,
        if (name != null) 'name': name,
      });
      if (!mounted || response is! Map) return;
      final selected = response['selected'] as String? ?? '';
      if (selected.isNotEmpty) {
        folderFocus[location] = 'file:$selected';
        setState(() => fileQuery = '');
      }
      await _loadFiles(restoreSelection: true);
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> fileMenu(Map entry) async {
    final opener = focused;
    final path = entry['path'] as String;
    final canWrite = entry['writable'] == true && entry['root'] != true;
    final actions = [
      '열기',
      if (entry['root'] != true) '복사',
      if (canWrite) ...['이동', '이름 변경', '삭제'],
      '상세 정보',
    ];
    final action = await showTvDialog<String>(
      context,
      (ctx) => PopupPanel(
        title: entry['name'] as String,
        onBack: () => Navigator.pop(ctx),
        onClose: () => Navigator.pop(ctx),
        body: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              for (var i = 0; i < actions.length; i++)
                RemoteTile(actions[i], autofocus: i == 0, onTap: () => Navigator.pop(ctx, actions[i])),
            ],
          ),
        ),
      ),
    );
    if (!mounted) return;
    if (action == '열기') {
      if (entry['directory'] == true) {
        await openFolder(entry, opener: opener);
      } else {
        await call('open', path);
      }
    }
    if (action == '복사' || action == '이동') await fileAction(action == '복사' ? 'copy' : 'cut', path: path);
    if (action == '이름 변경') {
      final name = await promptName(context, '이름 변경', entry['name'] as String);
      if (mounted && name != null) await fileAction('rename', path: path, name: name);
    }
    if (action == '삭제') {
      final warning = entry['directory'] == true ? '폴더 안의 파일도 함께 삭제됩니다.' : '삭제한 파일은 복원할 수 없습니다.';
      await dialogAction(
        '삭제 확인',
        "${entry['name']}\n$warning",
        '삭제',
        () => fileAction('delete', path: path),
      );
    }
    if (action == '상세 정보') await showFileDetails(context, entry);
    if (mounted && action != '열기')
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          if (opener != null && nodes[opener]?.context != null) {
            focus(opener);
          } else {
            enter();
          }
        }
      });
  }

  Map<String, dynamic> get _shellData => widget.data;

  void _setFileQuery(String query) {
    setState(() => fileQuery = query);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && tab == 'files' && !widget.obscured) focus('files:search');
    });
  }

  void _cycleFileSort() => setState(() => sort = sort.next);

  @override
  Widget build(BuildContext context) => PopScope(
    canPop: false,
    onPopInvokedWithResult: (didPop, _) {
      if (!didPop && !widget.obscured) back();
    },
    child: Focus(
      onKeyEvent: key,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final compact = constraints.maxWidth < 840 || constraints.maxHeight < 460;
          final contentWidth = constraints.maxWidth - ShellFrame.railWidth(compact) -
              ShellFrame.gutter(compact) - ShellFrame.outerHorizontal(compact) * 2;
          columns = (contentWidth / 200).floor().clamp(1, 6);
          Widget content;
          switch (tab) {
            case 'home':
              content = _HomeDeviceViews(this).launcher(contentWidth, compact);
            case 'bluetooth':
              content = _HomeDeviceViews(this).bluetooth();
            case 'apps':
              content = _HomeDeviceViews(this).apps();
            case 'files':
              content = _HomeStorageViews(this).explorer();
            case 'storage':
              content = _HomeStorageViews(this).storagePage();
            case 'mapping':
              final state = widget.data['controls'] as Map? ?? {};
              final actions = mappingActions(state);
              rows = [
                for (final action in actions) [action.id],
              ];
              content = scroll([
                for (final action in actions)
                  tile(
                    action.id,
                    action.title,
                    () => call(action.method, action.keyCode),
                    subtitle: action.detail,
                    icon: action.icon,
                  ),
                const SizedBox(height: 8),
                const Text('TV가 앱에 전달하는 버튼을 지정할 수 있습니다.', style: TextStyle(color: muted)),
              ]);
            case 'shortcuts':
              rows = [
                ['shortcut:apps'],
                ['shortcut:accessibility'],
                ['shortcut:home'],
                ['shortcut:accounts'],
                ['shortcut:tv-update'],
                ['shortcut:bluetooth'],
                ['shortcut:advanced'],
              ];
              content = scroll([
                tile(
                  'shortcut:apps',
                  '앱 목록',
                  () => call('appSettings'),
                  subtitle: 'Android TV의 앱 관리 설정을 엽니다',
                  icon: Icons.apps_rounded,
                ),
                tile(
                  'shortcut:accessibility',
                  '접근성',
                  () => call('accessibility'),
                  subtitle: 'Android TV 접근성 설정을 엽니다',
                  icon: Icons.accessibility_new_rounded,
                ),
                tile(
                  'shortcut:home',
                  '기본 홈 앱 설정',
                  () => call('homeSettings'),
                  subtitle: widget.data['controls']?['defaultHome'] == true ? '현재 기본 홈 · GenieBreaker' : null,
                  icon: Icons.home_outlined,
                ),
                tile(
                  'shortcut:accounts',
                  '기기 계정 목록',
                  () => call('accountSettings'),
                  icon: Icons.manage_accounts_outlined,
                ),
                tile(
                  'shortcut:tv-update',
                  'TV 업데이트 설정',
                  () => call('tvUpdateSettings'),
                  icon: Icons.system_update_alt_rounded,
                ),
                tile(
                  'shortcut:bluetooth',
                  '블루투스 설정',
                  () => call('bluetoothSettings'),
                  icon: Icons.bluetooth_rounded,
                ),
                tile(
                  'shortcut:advanced',
                  '고급 바로가기',
                  () => call('advancedShortcuts'),
                  icon: Icons.settings_outlined,
                ),
              ]);
            default:
              rows = [
                ['settings:update'],
                ['settings:diagnostics'],
                ['settings:lock'],
                ['settings:home-shortcut'],
                ['settings:permission-review'],
              ];
              content = scroll([
                tile(
                  'settings:update',
                  widget.data['update'] as String? ?? '업데이트 확인',
                  () => call('update'),
                  subtitle: '현재 버전  ${widget.data['version'] ?? '—'}',
                  icon: Icons.system_update_rounded,
                ),
                tile(
                  'settings:diagnostics',
                  '문제 진단',
                  () => call('diagnostics'),
                  subtitle: '최근 실패 원인과 오류 기록을 확인합니다',
                  icon: Icons.info_outline_rounded,
                ),
                tile(
                  'settings:lock',
                  '앱 잠금',
                  configureLock,
                  subtitle: widget.data['lockEnabled'] == true ? '켜짐' : '꺼짐',
                  icon: Icons.lock_outline_rounded,
                ),
                tile(
                  'settings:home-shortcut',
                  '단축 호출',
                  () => call('toggleHomeShortcut'),
                  subtitle: switch (widget.data['controls']?['homeShortcutRequired']) {
                    true => '켜짐 · GProject가 기본 홈인 동안 끌 수 없습니다',
                    _ => widget.data['controls']?['homeShortcutEnabled'] == false
                        ? '꺼짐'
                        : '켜짐 · 홈 버튼을 5초간 눌러 GenieBreaker 열기',
                  },
                  icon: Icons.shortcut_rounded,
                ),
                tile(
                  'settings:permission-review',
                  '앱 권한 검사',
                  () => call('permissionReview'),
                  subtitle: '위치, 마이크 등 허용된 권한을 확인합니다',
                  icon: Icons.security_outlined,
                ),
              ]);
          }
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (!mounted) return;
            final active = {...rows.expand((row) => row), ...tabs.map((name) => 'nav:$name')};
            final stale = nodes.keys
                .where((id) => !active.contains(id) && nodes[id]?.context == null)
                .toList();
            for (final id in stale) {
              nodes.remove(id)?.dispose();
            }
          });
          renderedTab = tab;
          final section = TvTab.values.byName(tab);
          final contextLabel = switch (section) {
            TvTab.bluetooth when widget.data['searching'] == true => '검색 중',
            TvTab.mapping => '${(widget.data['controls']?['bindings'] as List? ?? []).length}개 등록',
            TvTab.apps => '${(widget.data['apps']?['items'] as List? ?? []).length}개 앱',
            TvTab.files when files.isNotEmpty => '${(files['entries'] as List? ?? []).length}개 항목',
            _ => null,
          };
          return ShellFrame(
            tab: section,
            version: widget.data['version'] as String? ?? '',
            compact: compact,
            node: node,
            sidebarScope: sidebarScope,
            contentScope: contentScope,
            contextLabel: contextLabel,
            error: error,
            onSelect: (next) => select(next, enterContent: next == 'home'),
            onFocus: (id) {
              if (!acceptsFocus) return;
              lastSurfaceFocus = id;
              select(id.substring(4));
            },
            child: content,
          );
        },
      ),
    ),
  );
  void filesChanged() {
    if (tab == 'files') refreshFiles();
  }

  @override
  void dispose() {
    FocusManager.instance.removeEarlyKeyEventHandler(rescueFocus);
    WidgetsBinding.instance.removeObserver(this);
    for (final n in nodes.values) {
      n.dispose();
    }
    launcherScroll.dispose();
    contentScope.dispose();
    sidebarScope.dispose();
    super.dispose();
  }
}
