import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'tv_focus.dart';
import 'home.dart';

const bridge = MethodChannel('genie/ui');
const ink = Color(0xfff3f6fc);
const muted = Color(0xffaab7c9);
const blue = Color(0xff9dc5ff);
const canvas = Color(0xff0d1420);
const panel = Color(0xff172233);
void main() => runApp(const GenieUI());

class GenieUI extends StatelessWidget {
  const GenieUI({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: ThemeData.dark(useMaterial3: true).copyWith(
      scaffoldBackgroundColor: Colors.transparent,
      colorScheme: const ColorScheme.dark(primary: blue, surface: panel, surfaceTint: Colors.transparent),
      textTheme: ThemeData.dark().textTheme.apply(bodyColor: ink, displayColor: ink),
      iconButtonTheme: IconButtonThemeData(style: ButtonStyle(
        backgroundColor: WidgetStateProperty.resolveWith((states) => states.contains(WidgetState.focused) ? const Color(0xff2d4665) : Colors.transparent),
        side: WidgetStateProperty.resolveWith((states) => BorderSide(color: states.contains(WidgetState.focused) ? ink : Colors.transparent, width: 2)),
      )),
      filledButtonTheme: FilledButtonThemeData(style: FilledButton.styleFrom(
        backgroundColor: blue, foregroundColor: canvas, minimumSize: const Size(110, 48),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)).copyWith(
          side: WidgetStateProperty.resolveWith((states) => BorderSide(color: states.contains(WidgetState.focused) ? ink : Colors.transparent, width: 2)))),
      outlinedButtonTheme: OutlinedButtonThemeData(style: OutlinedButton.styleFrom(
        foregroundColor: ink, minimumSize: const Size(92, 48),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12))).copyWith(
          side: WidgetStateProperty.resolveWith((states) => BorderSide(color: states.contains(WidgetState.focused) ? blue : const Color(0xff344c6b), width: 2)),
          backgroundColor: WidgetStateProperty.resolveWith((states) => states.contains(WidgetState.focused) ? const Color(0xff2d4665) : Colors.transparent))),
    ),
    // App-local logical density: every UI element is 90% of its previous size.
    // The TV system density and other applications are never modified.
    builder: (context, child) => LayoutBuilder(builder: (context, constraints) {
      final size = Size(constraints.maxWidth / .9, constraints.maxHeight / .9);
      return FittedBox(fit: BoxFit.contain, child: SizedBox(width: size.width, height: size.height,
        child: MediaQuery(data: MediaQuery.of(context).copyWith(size: size), child: child!)));
    }),
    shortcuts: {...WidgetsApp.defaultShortcuts, const SingleActivator(LogicalKeyboardKey.select, includeRepeats: false): const ActivateIntent()},
    home: const GeniePage(),
  );
}

class RemoteTile extends StatefulWidget {
  final String title;
  final String? subtitle;
  final IconData icon;
  final Widget? leading;
  final FocusNode? focusNode;
  final bool autofocus;
  final bool selected;
  final bool? toggle;
  final bool danger;
  final VoidCallback? onFocus;
  final VoidCallback onTap;
  const RemoteTile(this.title, {super.key, this.subtitle, this.icon = Icons.chevron_right_rounded,
    this.leading, this.focusNode, this.autofocus = false, this.selected = false, this.toggle, this.danger = false, this.onFocus, required this.onTap});
  @override
  State<RemoteTile> createState() => _RemoteTileState();
}
class _RemoteTileState extends State<RemoteTile> {
  bool focused = false;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 8),
    child: FocusableActionDetector(
      focusNode: widget.focusNode, autofocus: widget.autofocus,
      onFocusChange: (value) {
        if (mounted) setState(() => focused = value);
        if (value) {
          widget.onFocus?.call();
          Scrollable.ensureVisible(context, alignment: .5, duration: Duration.zero);
        }
      },
      shortcuts: const {
        SingleActivator(LogicalKeyboardKey.select, includeRepeats: false): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.enter, includeRepeats: false): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.space, includeRepeats: false): ActivateIntent(),
      },
      actions: {ActivateIntent: CallbackAction<ActivateIntent>(onInvoke: (_) { widget.onTap(); return null; })},
      child: Semantics(button: true, selected: widget.selected, toggled: widget.toggle, label: widget.title,
        child: GestureDetector(onTap: widget.onTap, child: AnimatedContainer(
          duration: Duration.zero,
          constraints: const BoxConstraints(minHeight: 56),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
          decoration: BoxDecoration(color: focused ? const Color(0xff2d4665) : widget.selected ? const Color(0xff193554) : const Color(0xff1b293b),
            borderRadius: BorderRadius.circular(10), border: Border.all(color: focused ? ink : Colors.transparent, width: 2)),
          child: Row(children: [
            if (widget.toggle == null) ...[Container(width: 34, height: 34, alignment: Alignment.center,
              decoration: BoxDecoration(color: widget.danger ? const Color(0xff40292e) : const Color(0xff1c3453), borderRadius: BorderRadius.circular(9)),
              child: widget.leading ?? Icon(widget.selected ? Icons.check_circle_rounded : widget.icon,
                color: widget.danger ? const Color(0xffffa4a4) : blue, size: 20)), const SizedBox(width: 12)],
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(widget.title, maxLines: 2, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 17, height: 1.3, fontWeight: FontWeight.w500,
                color: widget.danger ? const Color(0xffffb6b6) : ink)),
              if (widget.subtitle != null) ...[const SizedBox(height: 4), Text(widget.subtitle!, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(color: muted, fontSize: 12, height: 1.4))],
            ])),
            if (widget.toggle != null) ...[const SizedBox(width: 16), Icon(widget.toggle! ? Icons.toggle_on_rounded : Icons.toggle_off_outlined,
              size: 36, color: widget.toggle! ? blue : muted)],
          ]),
        )),
      ),
    ),
  );
}

/// Shared bounded surface for every menu and confirmation. Background belongs to the caller.
class PopupPanel extends StatelessWidget {
  final String title;
  final List<String> breadcrumbs;
  final Widget body;
  final Widget? footer;
  final String? errorCode;
  final bool wide;
  final VoidCallback onBack;
  final VoidCallback onClose;
  const PopupPanel({super.key, required this.title, this.breadcrumbs = const [], required this.body,
    this.footer, this.errorCode, this.wide = false, required this.onBack, required this.onClose});
  @override
  Widget build(BuildContext context) => ConstrainedBox(
    constraints: BoxConstraints(maxWidth: wide ? 660 : 490, maxHeight: MediaQuery.sizeOf(context).height - 64),
    child: Material(key: const ValueKey('popup-surface'), color: panel, surfaceTintColor: Colors.transparent,
      elevation: 24, shadowColor: Colors.black54, borderRadius: BorderRadius.circular(18), clipBehavior: Clip.antiAlias,
      child: DecoratedBox(decoration: BoxDecoration(border: Border.all(color: const Color(0xff30435e)), borderRadius: BorderRadius.circular(18)),
        child: Padding(padding: const EdgeInsets.fromLTRB(24, 18, 24, 16),
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            if (breadcrumbs.isNotEmpty) Padding(padding: const EdgeInsets.only(left: 4, bottom: 6),
              child: Text(breadcrumbs.join('  ›  '), maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(color: muted, fontSize: 12))),
            Row(children: [
              if (breadcrumbs.isNotEmpty) ...[IconButton(tooltip: '이전 메뉴', onPressed: onBack,
                icon: const Icon(Icons.arrow_back_rounded, color: ink, size: 22)), const SizedBox(width: 6)],
              Expanded(child: Text(title, style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w600), maxLines: 2, overflow: TextOverflow.ellipsis)),
              const SizedBox(width: 12), IconButton(tooltip: '메뉴 닫기', onPressed: onClose, icon: const Icon(Icons.close_rounded, color: muted, size: 22)),
            ]),
            if (errorCode != null) Padding(padding: const EdgeInsets.only(top: 10), child: Text('오류 코드  $errorCode', key: const ValueKey('error-code'), style: const TextStyle(color: blue, fontSize: 18, fontWeight: FontWeight.w600))),
            const SizedBox(height: 14),
            Flexible(fit: FlexFit.loose, child: body),
            if (footer != null) ...[const SizedBox(height: 14), const Divider(height: 1, color: Color(0xff293a53)), const SizedBox(height: 14), footer!],
          ]),
        ),
      ),
    ),
  );
}

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
  String location = '';
  bool working = false;
  bool advanced = false;
  Map<String, bool> cloneOptions = {};
  final Map<String, FocusNode> focusNodes = {};
  final Map<String, bool> expandedPages = {};
  FocusNode node(String key) => focusNodes.putIfAbsent(key, () => FocusNode(debugLabel: key));
  String get pageId => '${data?['pageId'] ?? 'root'}';
  FocusNode menuNode(String key) => node('$pageId:$key');
  void remember(String key) => bridge.invokeMethod<void>('focus', key);
  void restoreFocus() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || data?['shell'] == true) return;
      final key = data?['focusKey'] as String?;
      final initial = data?['cloneOptions'] == true ? (cloneOptions.containsKey('netflixCompatibility') ? 'netflixCompatibility' : 'renameApp') : 'item-${data?['focus'] ?? 0}';
      final target = focusNodes['$pageId:${key?.isNotEmpty == true ? key : initial}'];
      if (target?.context != null && target!.canRequestFocus) target.requestFocus();
    });
  }
  @override
  void initState() {
    super.initState();
    bridge.setMethodCallHandler((call) async {
      if (!mounted) return;
      if (call.method == 'shell') {
        final next = Map<String, dynamic>.from(call.arguments as Map);
        setState(() { shellData = next; if (data?['shell'] == true) data = next; });
      }
      if (call.method == 'dismiss') { setState(() => data = shellData); homeKey.currentState?.restore(); }
      if (call.method == 'filesChanged') homeKey.currentState?.filesChanged();
      if (call.method == 'back') back();
      if (call.method == 'enter') restoreFocus();
      if (call.method == 'page') receive(Map<String, dynamic>.from(call.arguments as Map));
    });
    load('');
  }
  @override
  void dispose() { bridge.setMethodCallHandler(null); for (final node in focusNodes.values) { node.dispose(); } super.dispose(); }
  void receive(Map<String, dynamic> next) {
    if (!mounted) return;
    setState(() {
      final changed = next['pageId'] != data?['pageId'];
      data = next; if (next['shell'] == true) shellData = next; error = null;
      if (changed) { advanced = expandedPages[pageId] ?? false; working = false; }
      if (next['cloneOptions'] == true) cloneOptions = Map<String, bool>.from(next['options'] as Map);
    });
    restoreFocus();
  }
  String errorText(Object e) {
    final text = e is PlatformException ? e.message ?? '요청 실패' : '요청 실패';
    return text.contains('[GB-') ? text : '$text [GB-UI-0001]';
  }
  Future<void> load(String path) async {
    try {
      final previousFocus = FocusManager.instance.primaryFocus;
      final sameLocation = path == location;
      final next = Map<String, dynamic>.from(await bridge.invokeMethod(path.isEmpty ? 'ready' : 'list', path) as Map);
      if (!mounted) return;
      location = path; receive(next);
      if (sameLocation) WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && previousFocus?.context != null && previousFocus!.canRequestFocus) previousFocus.requestFocus();
      });
    } catch (e) { if (mounted) setState(() => error = errorText(e)); }
  }
  Future<void> perform(String method, [dynamic argument]) async {
    if (working) return;
    setState(() => working = true);
    try {
      final message = await bridge.invokeMethod(method, argument);
      if (mounted && message is String) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
    } catch (e) {
      if (mounted) setState(() => error = errorText(e));
    } finally { if (mounted) setState(() => working = false); }
  }
  void back() { perform('back'); }
  void toggleOption(String key) {
    setState(() => cloneOptions[key] = !(cloneOptions[key] ?? true));
    bridge.invokeMethod<void>('options', cloneOptions);
  }
  Widget messageBox(String message) => Container(
    margin: const EdgeInsets.only(bottom: 14), padding: const EdgeInsets.all(14),
    decoration: BoxDecoration(color: canvas, borderRadius: BorderRadius.circular(12)),
    constraints: const BoxConstraints(maxHeight: 180),
    child: SingleChildScrollView(child: Text(message, style: const TextStyle(color: muted, fontSize: 15, height: 1.5))),
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
    return SingleChildScrollView(key: ValueKey('body-${data?['pageId'] ?? 'root'}'), child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      if ((data?['message'] as String? ?? '').isNotEmpty) configure
        ? Padding(padding: const EdgeInsets.only(bottom: 12), child: Text(data!['message'] as String, style: const TextStyle(color: muted, fontSize: 14)))
        : messageBox(data!['message'] as String),
      if (error != null) Padding(padding: const EdgeInsets.only(bottom: 12), child: Text(error!, style: const TextStyle(color: Colors.orangeAccent))),
      if (configure) ...[
        if (cloneOptions.containsKey('netflixCompatibility'))
          RemoteTile('넷플릭스 실행 호환성', key: const ValueKey('netflixCompatibility'),
            focusNode: menuNode('netflixCompatibility'), onFocus: () => remember('netflixCompatibility'),
            subtitle: '버전 제한 없이 실행 코드 검사', toggle: cloneOptions['netflixCompatibility'] ?? false,
            onTap: () => toggleOption('netflixCompatibility')),
        for (final key in ['renameApp', 'renameLaunchers']) RemoteTile(labels[key]![0], key: ValueKey(key),
          focusNode: menuNode(key), onFocus: () => remember(key), autofocus: key == 'renameApp', subtitle: labels[key]![1], toggle: cloneOptions[key] ?? true, onTap: () => toggleOption(key)),
        RemoteTile('호환성 옵션', focusNode: menuNode('advanced'), onFocus: () => remember('advanced'), subtitle: advanced ? '접기' : '필요할 때만 변경', icon: advanced ? Icons.expand_less : Icons.tune_rounded,
          onTap: () => setState(() { advanced = !advanced; expandedPages[pageId] = advanced; })),
        if (advanced) ...[
          const Padding(padding: EdgeInsets.fromLTRB(4, 6, 4, 12), child: Text('패키지·공급자 주소·충돌 권한·서명은 별도 설치를 위해 변경됩니다. 호환성 옵션은 앱 실행을 보장하지 않습니다.', style: TextStyle(color: muted, fontSize: 12, height: 1.5))),
          if (cloneOptions['netflixCompatibility'] == true)
            const Padding(padding: EdgeInsets.all(4), child: Text('넷플릭스 프로필: 원본 리소스·작업 그룹 유지, 라이브러리 추출을 적용합니다.', style: TextStyle(color: muted, fontSize: 12))),
          if (cloneOptions['netflixCompatibility'] != true)
          for (final key in ['renameResources', 'isolateTasks', 'extractLibraries']) RemoteTile(labels[key]![0], key: ValueKey(key),
            focusNode: menuNode(key), onFocus: () => remember(key), subtitle: labels[key]![1], toggle: cloneOptions[key] ?? true, onTap: () => toggleOption(key)),
          TextButton(onPressed: () { setState(() => cloneOptions.updateAll((key, value) => key == 'netflixCompatibility' ? value : true)); bridge.invokeMethod<void>('options', cloneOptions); }, child: const Text('기본값 복원')),
        ],
      ] else if (!confirmation) ...[
        for (var i = 0; i < items.length; i++) RemoteTile(items[i], key: ValueKey('${data?['pageId'] ?? 'root'}-$i'),
          focusNode: menuNode('item-$i'), autofocus: i == (data?['focus'] ?? 0), selected: data?['selected'] == i,
          danger: RegExp('제거|삭제|지우기').hasMatch(items[i]),
          icon: data?['selected'] != null && data?['selected'] != -1 ? Icons.radio_button_unchecked : actionIcon(items[i]),
          onFocus: () => bridge.invokeMethod<void>('focus', i), onTap: () => perform('choose', i)),
      ],
    ]));
  }
  bool get isConfirmation {
    final items = (data?['items'] as List? ?? []).cast<String>();
    return items.length == 2 && ['취소', '나중에'].contains(items.first) && (data?['message'] as String? ?? '').isNotEmpty;
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
    if (configure) return Row(mainAxisAlignment: MainAxisAlignment.end, children: [
      OutlinedButton(onPressed: back, child: const Text('취소')), const SizedBox(width: 12),
      FilledButton(onPressed: working ? null : () => perform('build', cloneOptions), child: const Text('듀얼앱 만들기')),
    ]);
    if (!isConfirmation) return null;
    final items = (data!['items'] as List).cast<String>();
    final danger = RegExp('제거|삭제|지우기').hasMatch(items[1]);
    return Row(mainAxisAlignment: MainAxisAlignment.end, children: [
      OutlinedButton(autofocus: true, onPressed: () => perform('choose', 0), child: Text(items[0])), const SizedBox(width: 12),
      FilledButton(style: danger ? FilledButton.styleFrom(backgroundColor: const Color(0xffffb4ab)) : null,
        onPressed: working ? null : () => perform('choose', 1), child: Text(items[1])),
    ]);
  }
  Widget menuOverlay() {
    final configure = data?['cloneOptions'] == true;
    return Stack(children: [
      Positioned.fill(child: GestureDetector(key: const ValueKey('popup-barrier'), onTap: () => perform('close'),
        child: const ColoredBox(color: Color(0x99050b14)))),
      SafeArea(child: Center(child: Padding(padding: const EdgeInsets.all(24), child: FocusScope(
        key: ValueKey(data == null ? 'loading' : data?['pageId'] ?? 'root'),
        child: FocusTraversalGroup(policy: TvFocusPolicy(), child: PopupPanel(title: data?['title'] as String? ?? '불러오는 중',
          errorCode: (data?['title'] as String? ?? '').startsWith('문제 기록') ? null : RegExp(r'GB-[A-Z]{2}-[0-9A-F]{4}').firstMatch(data?['message'] as String? ?? error ?? '')?.group(0),
          wide: configure || (data?['message'] as String? ?? '').length > 180,
          breadcrumbs: (data?['breadcrumbs'] as List? ?? []).cast<String>(), onBack: back, onClose: () => perform('close'),
          body: data == null ? (error == null ? const SizedBox(height: 60, child: Center(child: CircularProgressIndicator())) : RemoteTile('다시 시도', autofocus: true, subtitle: error, onTap: () => load(location))) : menuBody(),
          footer: menuFooter(configure),
        )),
      )))),
    ]);
  }
  @override
  Widget build(BuildContext context) {
    final home = shellData;
    final menu = data?['shell'] != true;
    return PopScope(canPop: false, onPopInvokedWithResult: (didPop, _) { if (!didPop && menu) back(); },
      child: Scaffold(backgroundColor: home == null ? Colors.transparent : canvas,
        body: Stack(children: [
          if (home != null) ExcludeFocus(excluding: menu, child: ExcludeSemantics(excluding: menu,
            child: AppHome(key: homeKey, data: home, obscured: menu))),
          if (menu) Positioned.fill(child: menuOverlay()),
        ]),
      ),
    );
  }
}
