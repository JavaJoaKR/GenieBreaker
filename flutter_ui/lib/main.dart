import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'tv_focus.dart';

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
    shortcuts: {...WidgetsApp.defaultShortcuts, const SingleActivator(LogicalKeyboardKey.select, includeRepeats: false): const ActivateIntent()},
    home: const GeniePage(),
  );
}

class RemoteTile extends StatefulWidget {
  final String title;
  final String? subtitle;
  final IconData icon;
  final FocusNode? focusNode;
  final bool autofocus;
  final bool selected;
  final bool? toggle;
  final bool danger;
  final VoidCallback? onFocus;
  final VoidCallback onTap;
  const RemoteTile(this.title, {super.key, this.subtitle, this.icon = Icons.chevron_right_rounded,
    this.focusNode, this.autofocus = false, this.selected = false, this.toggle, this.danger = false, this.onFocus, required this.onTap});
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
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          decoration: BoxDecoration(color: focused ? const Color(0xff2d4665) : const Color(0xff1b293b),
            borderRadius: BorderRadius.circular(10), border: Border.all(color: focused ? ink : Colors.transparent, width: 2)),
          child: Row(children: [
            if (widget.toggle == null) ...[Container(width: 34, height: 34, alignment: Alignment.center,
              decoration: BoxDecoration(color: widget.danger ? const Color(0xff40292e) : const Color(0xff1c3453), borderRadius: BorderRadius.circular(9)),
              child: Icon(widget.selected ? Icons.check_circle_rounded : widget.icon,
                color: widget.danger ? const Color(0xffffa4a4) : blue, size: 20)), const SizedBox(width: 12)],
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(widget.title, maxLines: 2, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 18, fontWeight: FontWeight.w500,
                color: widget.danger ? const Color(0xffffb6b6) : ink)),
              if (widget.subtitle != null) ...[const SizedBox(height: 4), Text(widget.subtitle!, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(color: muted, fontSize: 13, height: 1.45))],
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
            const SizedBox(height: 12), const Text('방향키  이동    확인  선택    뒤로  이전', style: TextStyle(color: muted, fontSize: 11)),
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
  String? error;
  final List<String> parents = [];
  String location = '';
  bool working = false;
  bool advanced = false;
  bool storageFocused = false;
  bool storageOnRight = false;
  Map<String, bool> cloneOptions = {};
  final Map<String, FocusNode> focusNodes = {};
  final Map<String, bool> expandedPages = {};
  final Map<String, String> fileFocus = {};
  FocusNode node(String key) => focusNodes.putIfAbsent(key, () => FocusNode(debugLabel: key));
  String get pageId => '${data?['pageId'] ?? 'root'}';
  FocusNode menuNode(String key) => node('$pageId:$key');
  void remember(String key) => bridge.invokeMethod<void>('focus', key);
  void restoreFocus() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      FocusNode? target;
      if (data?['files'] == true) {
        final entries = (data?['entries'] as List? ?? []).cast<Map>();
        final key = fileFocus[location] ?? (location.isNotEmpty ? 'parent' : entries.isEmpty ? 'pick' : entries.first['path'] as String);
        target = focusNodes['file:$location:$key'];
      } else {
        final key = data?['focusKey'] as String?;
        final initial = data?['cloneOptions'] == true ? (cloneOptions.containsKey('netflixCompatibility') ? 'netflixCompatibility' : 'renameApp') : 'item-${data?['focus'] ?? 0}';
        target = focusNodes['$pageId:${key?.isNotEmpty == true ? key : initial}'];
      }
      if (target?.context != null && target!.canRequestFocus) target.requestFocus();
    });
  }
  @override
  void initState() {
    super.initState();
    bridge.setMethodCallHandler((call) async {
      if (!mounted) return;
      if (call.method == 'refresh') await load(location);
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
      data = next; error = null;
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
      if (data?['files'] == true) await load(location);
    } catch (e) {
      if (mounted) setState(() => error = errorText(e));
    } finally { if (mounted) setState(() => working = false); }
  }
  Future<void> cleanup(String kind) async {
    final accepted = await showDialog<bool>(context: context, barrierColor: const Color(0x99050b14), builder: (ctx) => Center(
      child: Padding(padding: const EdgeInsets.all(24), child: PopupPanel(
        title: kind == 'apks' ? '듀얼앱 설치파일 정리' : kind == 'updates' ? '업데이트 파일 정리' : '캐시 정리',
        onBack: () => Navigator.pop(ctx, false), onClose: () => Navigator.pop(ctx, false),
        body: const Padding(padding: EdgeInsets.symmetric(vertical: 8), child: Text('설치된 앱과 서명 키는 유지됩니다.\n다운로드와 파일 작업 중에는 정리할 수 없습니다.', style: TextStyle(color: muted, fontSize: 16, height: 1.6))),
        footer: Row(mainAxisAlignment: MainAxisAlignment.end, children: [
          OutlinedButton(autofocus: true, onPressed: () => Navigator.pop(ctx, false), child: const Text('취소')),
          const SizedBox(width: 12), FilledButton(style: FilledButton.styleFrom(backgroundColor: const Color(0xffffb4ab)),
            onPressed: () => Navigator.pop(ctx, true), child: const Text('정리하기')),
        ]),
      )),
    ));
    if (accepted == true) await perform('clean', kind);
  }
  void back() {
    if (data?['files'] == true) {
      if (parents.isNotEmpty) { load(parents.removeLast()); } else { perform('sidebar'); }
    } else { perform('back'); }
  }
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
  Widget filesPage() {
    final entries = (data?['entries'] as List? ?? []).cast<Map>();
    final root = location.isEmpty;
    final folders = Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Padding(padding: const EdgeInsets.only(bottom: 12), child: Row(children: [
        Expanded(child: Text(root ? '내 폴더' : '폴더 내용', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600))),
        Text('${entries.length}개', style: const TextStyle(color: muted, fontSize: 12)),
      ])),
      Expanded(child: ListView(children: [
        if (!root) RemoteTile('상위 폴더', focusNode: node('file:$location:parent'), onFocus: () => fileFocus[location] = 'parent', autofocus: true, icon: Icons.arrow_back, onTap: back),
        for (var i = 0; i < entries.length; i++) RemoteTile(entries[i]['name'] as String, key: ValueKey(entries[i]['path']),
          focusNode: node('file:$location:${entries[i]['path']}'), onFocus: () => fileFocus[location] = entries[i]['path'] as String, autofocus: root && i == 0, subtitle: entries[i]['detail'] as String?,
          icon: entries[i]['directory'] == true ? Icons.folder_outlined : (entries[i]['path'] as String).endsWith('.apk') ? Icons.android_rounded : Icons.insert_drive_file_outlined,
          onTap: () { final path = entries[i]['path'] as String; if (entries[i]['directory'] == true) { parents.add(location); load(path); } else { perform('open', path); } }),
        if (entries.isEmpty) const Padding(padding: EdgeInsets.symmetric(vertical: 48), child: Column(children: [
          Icon(Icons.folder_open_rounded, color: muted, size: 42), SizedBox(height: 16), Text('폴더가 비어 있습니다', style: TextStyle(color: muted, fontSize: 17)),
        ])),
      ])),
    ]);
    final storage = Container(padding: const EdgeInsets.all(16), decoration: BoxDecoration(
      color: panel, borderRadius: BorderRadius.circular(18), border: Border.all(color: const Color(0xff304057))),
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        const Text('저장공간', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)), const SizedBox(height: 16),
        Text('듀얼앱 설치파일  ${data?['apkSize'] ?? '—'}', style: const TextStyle(color: muted, fontSize: 12)), const SizedBox(height: 8),
        OutlinedButton.icon(focusNode: node('storage:apks'), onPressed: () => cleanup('apks'), icon: const Icon(Icons.delete_outline, size: 18), label: const Text('설치파일 정리')),
        const SizedBox(height: 12), Text('업데이트 다운로드  ${data?['updateSize'] ?? '—'}', style: const TextStyle(color: muted, fontSize: 12)), const SizedBox(height: 8),
        OutlinedButton.icon(focusNode: node('storage:updates'), onPressed: () => cleanup('updates'), icon: const Icon(Icons.downloading_rounded, size: 18), label: const Text('업데이트 파일 정리')),
        const SizedBox(height: 12), Text('임시 캐시  ${data?['cacheSize'] ?? '—'}', style: const TextStyle(color: muted, fontSize: 12)), const SizedBox(height: 8),
        OutlinedButton.icon(focusNode: node('storage:cache'), onPressed: () => cleanup('cache'), icon: const Icon(Icons.cleaning_services_outlined, size: 18), label: const Text('캐시 정리')),
        const SizedBox(height: 8), const Text('설치된 앱과 서명 키는 유지됩니다.', style: TextStyle(color: muted, fontSize: 11, height: 1.5)),
      ]),
    );
    return ColoredBox(color: canvas, child: Padding(padding: const EdgeInsets.all(0),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(data?['title'] as String? ?? '파일탐색기', maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 30, fontWeight: FontWeight.w600)),
          const SizedBox(height: 6), Text(root ? '기기 폴더와 설치파일을 한곳에서' : '파일탐색기  ›  ${data?['title'] ?? ''}', style: const TextStyle(color: muted, fontSize: 12)),
        ])), if (root) ...[const SizedBox(width: 12), OutlinedButton.icon(focusNode: node('file:$location:pick'), onPressed: () => perform('pick'), icon: const Icon(Icons.add_rounded, size: 19), label: const Text('폴더 연결'))]]),
        const SizedBox(height: 24),
        if (error != null) Padding(padding: const EdgeInsets.only(bottom: 12), child: Text(error!, style: const TextStyle(color: Colors.orangeAccent))),
        Expanded(child: LayoutBuilder(builder: (context, constraints) {
          storageOnRight = root && constraints.maxWidth >= 560;
          if (!root) return folders;
          if (constraints.maxWidth < 560) return Column(children: [Expanded(child: folders), SizedBox(height: 155, child: SingleChildScrollView(child: storage))]);
          return Row(crossAxisAlignment: CrossAxisAlignment.start, children: [Expanded(child: folders), const SizedBox(width: 16), SizedBox(width: 220, child: Focus(skipTraversal: true, onFocusChange: (value) => storageFocused = value, child: SingleChildScrollView(child: storage)))]);
        })),
        const SizedBox(height: 14), const Text('방향키  이동     확인  선택     뒤로  돌아가기', style: TextStyle(color: muted, fontSize: 11)),
      ]),
    ));
  }
  @override
  Widget build(BuildContext context) {
    final files = data?['files'] == true;
    return PopScope(canPop: false, onPopInvokedWithResult: (didPop, _) { if (!didPop) back(); },
      child: Focus(onKeyEvent: (_, event) {
        if (files && event is KeyDownEvent && event.logicalKey == LogicalKeyboardKey.arrowLeft && !(storageOnRight && storageFocused)) {
          back(); return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      }, child: Scaffold(backgroundColor: Colors.transparent, body: files ? FocusTraversalGroup(policy: TvFocusPolicy(), child: filesPage()) : menuOverlay())),
    );
  }
}
