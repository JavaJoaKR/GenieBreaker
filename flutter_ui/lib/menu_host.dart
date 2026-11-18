import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'home.dart';
import 'pin_panel.dart';
import 'platform_bridge.dart';
import 'popup_panel.dart';
import 'remote_tile.dart';
import 'tv_focus.dart';
import 'tv_theme.dart';

class GeniePage extends StatefulWidget {
  const GeniePage({super.key});
  @override
  State<GeniePage> createState() => _GeniePageState();
}

class _GeniePageState extends State<GeniePage> {
  Map<String, dynamic>? data;
  Map<String, dynamic>? shellData;
  final homeKey = GlobalKey<AppHomeState>();
  String? error;
  bool working = false;
  bool advanced = false;
  Map<String, bool> cloneOptions = {};
  final Map<String, FocusNode> focusNodes = {};
  final Map<String, bool> expandedPages = {};
  FocusNode node(String key) => focusNodes.putIfAbsent(key, () => FocusNode(debugLabel: key));
  String get pageId => '${data?['pageId'] ?? 'root'}';
  FocusNode menuNode(String key) => node('$pageId:$key');
  void remember(String key) {
    data?['focusKey'] = key;
    bridge.invokeMethod<void>('focus', key);
  }
  bool get menuActive =>
      mounted && data != null && data?['shell'] != true && ModalRoute.of(context)?.isCurrent == true;
  FocusNode? get rememberedFocus {
    final key = data?['focusKey'] as String?;
    final initial = data?['cloneOptions'] == true
        ? (cloneOptions.containsKey('netflixCompatibility') ? 'netflixCompatibility' : 'renameApp')
        : 'item-${data?['focus'] ?? 0}';
    return focusNodes['$pageId:${key?.isNotEmpty == true ? key : initial}'];
  }
  void restoreFocus() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!menuActive) return;
      final target = rememberedFocus;
      if (target?.context != null && target!.canRequestFocus) target.requestFocus();
    });
    WidgetsBinding.instance.ensureVisualUpdate();
  }

  KeyEventResult recoverMenuFocus(KeyEvent event) {
    if (menuActive && isTvNavigationPress(event)) recoverPopupFocus();
    return KeyEventResult.ignored;
  }

  void recoverPopupFocus() {
    final current = FocusManager.instance.primaryFocus;
    if (current != null &&
        current is! FocusScopeNode &&
        current.context != null &&
        current.canRequestFocus &&
        current.ancestors.contains(menuScope)) {
      return;
    }
    final target = rememberedFocus;
    if (target?.context != null && target!.canRequestFocus && target.ancestors.contains(menuScope)) {
      target.requestFocus();
      FocusManager.instance.applyFocusChangesIfNeeded();
    }
  }

  void handleRemoteNavigation(String command) {
    final direction = switch (command) {
      'up' => (LogicalKeyboardKey.arrowUp, TraversalDirection.up),
      'down' => (LogicalKeyboardKey.arrowDown, TraversalDirection.down),
      'left' => (LogicalKeyboardKey.arrowLeft, TraversalDirection.left),
      'right' => (LogicalKeyboardKey.arrowRight, TraversalDirection.right),
      _ => null,
    };
    if (direction == null && command != 'activate') return;
    final focusManager = FocusManager.instance;
    focusManager.applyFocusChangesIfNeeded();
    final home = homeKey.currentState;
    if (data?['shell'] == true && home != null && home.acceptsFocus) {
      home.recoverFocus();
      if (direction != null) {
        home.navigate(direction.$1);
        return;
      }
    } else if (menuActive) {
      recoverPopupFocus();
    }
    final target = focusManager.primaryFocus;
    final targetContext = target?.context;
    // A page from Android may be pending its first frame. Never act on the
    // previous screen while the new page or dialog takes focus.
    if (targetContext == null ||
        ModalRoute.of(targetContext)?.isCurrent != true ||
        (menuActive && !target!.ancestors.contains(menuScope))) return;
    if (direction != null) {
      Actions.maybeInvoke(targetContext, DirectionalFocusIntent(direction.$2));
    } else {
      Actions.maybeInvoke(targetContext, const ActivateIntent());
    }
  }

  final menuScope = FocusScopeNode(debugLabel: 'menu-scope');

  @override
  void initState() {
    super.initState();
    FocusManager.instance.addEarlyKeyEventHandler(recoverMenuFocus);
    bridge.setMethodCallHandler((call) async {
      if (!mounted) return;
      if (call.method == 'shell') {
        final next = Map<String, dynamic>.from(call.arguments as Map);
        setState(() {
          shellData = next;
          if (data?['shell'] == true) data = next;
        });
      }
      if (call.method == 'dismiss') {
        expandedPages.clear();
        setState(() => data = shellData);
        homeKey.currentState?.restoreModalOpener();
      }
      if (call.method == 'session') {
        Navigator.of(context).popUntil((route) => route.isFirst);
        expandedPages.clear();
        receive(Map<String, dynamic>.from(call.arguments as Map));
      }
      if (call.method == 'filesChanged') homeKey.currentState?.filesChanged();
      if (call.method == 'back') back();
      if (call.method == 'remoteNavigation' && call.arguments is String) {
        handleRemoteNavigation(call.arguments as String);
      }
      if (call.method == 'enter') {
        if (data?['shell'] == true) {
          homeKey.currentState?.restore();
        } else {
          restoreFocus();
        }
      }
      if (call.method == 'page') receive(Map<String, dynamic>.from(call.arguments as Map));
    });
    load();
  }

  @override
  void dispose() {
    bridge.setMethodCallHandler(null);
    FocusManager.instance.removeEarlyKeyEventHandler(recoverMenuFocus);
    menuScope.dispose();
    for (final node in focusNodes.values) {
      node.dispose();
    }
    super.dispose();
  }

  void receive(Map<String, dynamic> next) {
    if (!mounted) return;
    if (next['shell'] != true && data?['shell'] == true) homeKey.currentState?.saveModalOpener();
    setState(() {
      final menu = next['shell'] == true ? next['menu'] : null;
      final page = menu is Map ? Map<String, dynamic>.from(menu) : next;
      final changed = page['pageId'] != data?['pageId'];
      if (next['shell'] == true) shellData = next;
      data = page;
      error = null;
      if (changed) {
        advanced = expandedPages[pageId] ?? false;
        working = false;
      }
      cloneOptions = page['cloneOptions'] == true ? Map<String, bool>.from(page['options'] as Map) : {};
    });
    restoreFocus();
  }

  Future<void> load() async {
    try {
      final next = Map<String, dynamic>.from(await bridge.invokeMethod('ready') as Map);
      if (mounted) receive(next);
    } catch (e) {
      if (mounted) setState(() => error = requestError(e));
    }
  }

  Future<void> perform(String method, [dynamic argument]) async {
    if (working) return;
    setState(() => working = true);
    try {
      final message = await bridge.invokeMethod(method, argument);
      if (mounted && message is String)
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
    } catch (e) {
      if (mounted) setState(() => error = requestError(e));
    } finally {
      if (mounted) setState(() => working = false);
    }
  }

  void back() {
    perform('back');
  }

  void toggleOption(String key) {
    setState(() => cloneOptions[key] = !(cloneOptions[key] ?? true));
    bridge.invokeMethod<void>('options', cloneOptions);
  }

  Widget messageBox(String message) => Container(
    margin: const EdgeInsets.only(bottom: 8),
    padding: const EdgeInsets.all(10),
    decoration: BoxDecoration(color: canvas, borderRadius: BorderRadius.circular(insetRadius)),
    constraints: const BoxConstraints(maxHeight: 180),
    child: SingleChildScrollView(
      child: Text(message, style: const TextStyle(color: muted, fontSize: 15, height: 1.5)),
    ),
  );
  Widget menuBody() {
    final configure = data?['cloneOptions'] == true;
    final items = (data?['items'] as List? ?? []).cast<String>();
    final confirmation = isConfirmation;
    const labels = {
      'renameApp': ['앱 이름 변경', '앱 이름에 듀얼 표시'],
      'renameLaunchers': ['런처 이름 변경', 'TV 홈 화면의 실행 이름 변경'],
      'renameResources': ['리소스 패키지 변경', '끄면 원본 리소스 패키지명 유지'],
      'isolateTasks': ['실행 작업 분리', '끄면 원본 작업 그룹 유지'],
      'extractLibraries': ['네이티브 라이브러리 추출', '끄면 원본 추출 설정 유지'],
    };
    return SingleChildScrollView(
      key: ValueKey('body-${data?['pageId'] ?? 'root'}'),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if ((data?['message'] as String? ?? '').isNotEmpty)
            configure
                ? Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: Text(
                      data!['message'] as String,
                      style: const TextStyle(color: muted, fontSize: 14),
                    ),
                  )
                : messageBox(data!['message'] as String),
          if (error != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Text(error!, style: const TextStyle(color: ink, fontWeight: FontWeight.w600)),
            ),
          if (configure) ...[
            if (cloneOptions.containsKey('netflixCompatibility'))
              RemoteTile(
                '넷플릭스 전용 패치',
                icon: Icons.extension_outlined,
                accent: accentTone,
                key: const ValueKey('netflixCompatibility'),
                focusNode: menuNode('netflixCompatibility'),
                onFocus: () => remember('netflixCompatibility'),
                subtitle: '이 듀얼앱에 실행 호환성 설정 적용',
                toggle: cloneOptions['netflixCompatibility'] ?? false,
                onTap: () => toggleOption('netflixCompatibility'),
              ),
            for (final key in ['renameApp', 'renameLaunchers'])
              RemoteTile(
                labels[key]![0],
                key: ValueKey(key),
                focusNode: menuNode(key),
                onFocus: () => remember(key),
                autofocus: key == 'renameApp',
                subtitle: labels[key]![1],
                toggle: cloneOptions[key] ?? true,
                onTap: () => toggleOption(key),
              ),
            RemoteTile(
              '호환성 옵션',
              focusNode: menuNode('advanced'),
              onFocus: () => remember('advanced'),
              subtitle: advanced ? '접기' : '필요할 때만 변경',
              icon: advanced ? Icons.expand_less : Icons.tune_rounded,
              onTap: () => setState(() {
                advanced = !advanced;
                expandedPages[pageId] = advanced;
              }),
            ),
            if (advanced) ...[
              const Padding(
                padding: EdgeInsets.fromLTRB(4, 6, 4, 12),
                child: Text(
                  '패키지·공급자 주소·충돌 권한·서명은 별도 설치를 위해 변경됩니다. 호환성 옵션은 앱 실행을 보장하지 않습니다.',
                  style: TextStyle(color: muted, fontSize: 12, height: 1.5),
                ),
              ),
              if (cloneOptions['netflixCompatibility'] == true)
                const Padding(
                  padding: EdgeInsets.all(4),
                  child: Text(
                    '넷플릭스 프로필: 원본 리소스·작업 그룹 유지, 라이브러리 추출을 적용합니다.',
                    style: TextStyle(color: muted, fontSize: 12),
                  ),
                ),
              if (cloneOptions['netflixCompatibility'] != true)
                for (final key in ['renameResources', 'isolateTasks', 'extractLibraries'])
                  RemoteTile(
                    labels[key]![0],
                    key: ValueKey(key),
                    focusNode: menuNode(key),
                    onFocus: () => remember(key),
                    subtitle: labels[key]![1],
                    toggle: cloneOptions[key] ?? true,
                    onTap: () => toggleOption(key),
                  ),
              TextButton(
                onPressed: () {
                  setState(
                    () => cloneOptions.updateAll(
                      (key, value) => key == 'netflixCompatibility' ? value : true,
                    ),
                  );
                  bridge.invokeMethod<void>('options', cloneOptions);
                },
                child: const Text('기본값 복원'),
              ),
            ],
          ] else if (!confirmation) ...[
            for (var i = 0; i < items.length; i++)
              RemoteTile(
                items[i],
                key: ValueKey('${data?['pageId'] ?? 'root'}-$i'),
                focusNode: menuNode('item-$i'),
                autofocus: i == (data?['focus'] ?? 0),
                selected: data?['selected'] == i,
                danger: RegExp('제거|삭제|지우기').hasMatch(items[i]),
                icon: data?['selected'] != null && data?['selected'] != -1
                    ? Icons.radio_button_unchecked
                    : actionIcon(items[i]),
                onFocus: () => remember('item-$i'),
                onTap: () => perform('choose', i),
              ),
          ],
        ],
      ),
    );
  }

  bool get isConfirmation {
    final items = (data?['items'] as List? ?? []).cast<String>();
    return items.length == 2 &&
        ['취소', '나중에'].contains(items.first) &&
        (data?['message'] as String? ?? '').isNotEmpty;
  }

  IconData actionIcon(String label) {
    if (RegExp('제거|삭제|지우기').hasMatch(label)) return Icons.delete_outline_rounded;
    if (label.contains('실행')) return Icons.play_arrow_rounded;
    if (label.contains('만들기')) return Icons.copy_all_rounded;
    if (label.contains('업데이트') || label.contains('설치')) return Icons.system_update_rounded;
    if (label.contains('연결 해제')) return Icons.link_off_rounded;
    if (label.contains('연결') || label.contains('페어링')) return Icons.bluetooth_rounded;
    if (label.contains('저장')) return Icons.save_alt_rounded;
    if (label.contains('복사')) return Icons.content_copy_rounded;
    if (label.contains('보기')) return Icons.more_horiz_rounded;
    if (label.contains('기록') || label.contains('정보')) return Icons.info_outline_rounded;
    return Icons.chevron_right_rounded;
  }

  Widget? menuFooter(bool configure) {
    if (configure)
      return Row(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          OutlinedButton(
            focusNode: menuNode('cancel'),
            onFocusChange: (value) { if (value) remember('cancel'); },
            onPressed: back,
            child: const Text('취소'),
          ),
          const SizedBox(width: 12),
          FilledButton(
            focusNode: menuNode('build'),
            onFocusChange: (value) { if (value) remember('build'); },
            onPressed: working ? null : () => perform('build', cloneOptions),
            child: const Text('듀얼앱 만들기'),
          ),
        ],
      );
    if (!isConfirmation) return null;
    final items = (data!['items'] as List).cast<String>();
    final danger = RegExp('제거|삭제|지우기').hasMatch(items[1]);
    return Row(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        OutlinedButton(
          focusNode: menuNode('item-0'),
          onFocusChange: (value) { if (value) remember('item-0'); },
          autofocus: true,
          onPressed: () => perform('choose', 0),
          child: Text(items[0]),
        ),
        const SizedBox(width: 12),
        FilledButton(
          focusNode: menuNode('item-1'),
          onFocusChange: (value) { if (value) remember('item-1'); },
          style: danger ? FilledButton.styleFrom(backgroundColor: ink) : null,
          onPressed: working ? null : () => perform('choose', 1),
          child: Text(items[1]),
        ),
      ],
    );
  }

  Widget menuOverlay() {
    final configure = data?['cloneOptions'] == true;
    return Stack(
      children: [
        Positioned.fill(
          child: GestureDetector(
            key: const ValueKey('popup-barrier'),
            onTap: () => perform('close'),
            child: const ColoredBox(color: Color(0x99000000)),
          ),
        ),
        SafeArea(
          child: Center(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: FocusScope(
                node: menuScope,
                key: ValueKey(data == null ? 'loading' : data?['pageId'] ?? 'root'),
                child: FocusTraversalGroup(
                  policy: TvFocusPolicy(),
                  child: PopupPanel(
                    title: data?['title'] as String? ?? '불러오는 중',
                    errorCode: (data?['title'] as String? ?? '').startsWith('문제 기록')
                        ? null
                        : RegExp(
                            r'GB-[A-Z]{2}-[0-9A-F]{4}',
                          ).firstMatch(data?['message'] as String? ?? error ?? '')?.group(0),
                    wide: configure || (data?['message'] as String? ?? '').length > 180,
                    breadcrumbs: (data?['breadcrumbs'] as List? ?? []).cast<String>(),
                    onBack: back,
                    onClose: () => perform('close'),
                    body: data == null
                        ? (error == null
                              ? const SizedBox(
                                  height: 60,
                                  child: Center(child: CircularProgressIndicator()),
                                )
                              : RemoteTile('다시 시도', autofocus: true, subtitle: error, onTap: load))
                        : menuBody(),
                    footer: menuFooter(configure),
                  ),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final detached = focusNodes.keys.where((id) => focusNodes[id]?.context == null).toList();
      for (final id in detached) {
        focusNodes.remove(id)?.dispose();
      }
    });
    if (data?['locked'] == true) {
      return Scaffold(
        backgroundColor: canvas,
        body: PinPanel(
          title: 'GenieBreaker 잠금',
          minimum: 1,
          maximum: 12,
          onSubmit: (pin) async => await bridge.invokeMethod<bool>('unlockPin', pin) == true,
        ),
      );
    }
    final home = shellData;
    final menu = data?['shell'] != true;
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && menu) back();
      },
      child: Scaffold(
        backgroundColor: home == null ? Colors.transparent : canvas,
        body: Stack(
          children: [
            if (home != null)
              ExcludeFocus(
                excluding: menu,
                child: ExcludeSemantics(
                  excluding: menu,
                  child: AppHome(key: homeKey, data: home, obscured: menu),
                ),
              ),
            if (menu) Positioned.fill(child: menuOverlay()),
          ],
        ),
      ),
    );
  }
}
