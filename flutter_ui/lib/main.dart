import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

const bridge = MethodChannel('genie/ui');
const ink = Color(0xffeff4ff);
const muted = Color(0xff9baac1);
const blue = Color(0xff79adff);
void main() => runApp(const GenieUI());

class GenieUI extends StatelessWidget {
  const GenieUI({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: ThemeData.dark(useMaterial3: true).copyWith(
      scaffoldBackgroundColor: const Color(0xff0b1220),
      colorScheme: const ColorScheme.dark(primary: blue, surface: Color(0xff132039)),
      textTheme: ThemeData.dark().textTheme.apply(bodyColor: ink, displayColor: ink),
    ),
    home: const GeniePage(),
  );
}

class RemoteTile extends StatefulWidget {
  final String title;
  final String? subtitle;
  final IconData icon;
  final bool autofocus;
  final bool selected;
  final VoidCallback onTap;
  const RemoteTile(this.title, {super.key, this.subtitle, this.icon = Icons.chevron_right_rounded,
    this.autofocus = false, this.selected = false, required this.onTap});
  @override
  State<RemoteTile> createState() => _RemoteTileState();
}
class _RemoteTileState extends State<RemoteTile> {
  bool focused = false;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 10),
    child: FocusableActionDetector(
      autofocus: widget.autofocus,
      onShowFocusHighlight: (value) {
        setState(() => focused = value);
        if (value) Scrollable.ensureVisible(context, alignment: .5, duration: const Duration(milliseconds: 130));
      },
      shortcuts: const {
        SingleActivator(LogicalKeyboardKey.select): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.enter): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.space): ActivateIntent(),
      },
      actions: {ActivateIntent: CallbackAction<ActivateIntent>(onInvoke: (_) { widget.onTap(); return null; })},
      child: Semantics(button: true, selected: widget.selected, label: widget.title,
        child: GestureDetector(onTap: widget.onTap, child: AnimatedContainer(
          duration: const Duration(milliseconds: 110),
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
          decoration: BoxDecoration(color: focused ? const Color(0xff203c62) : const Color(0xff132039),
            borderRadius: BorderRadius.circular(16), border: Border.all(color: focused ? blue : Colors.transparent, width: 2)),
          child: Row(children: [Icon(widget.selected ? Icons.check_circle_rounded : widget.icon, color: focused || widget.selected ? blue : muted, size: 26),
            const SizedBox(width: 16), Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(widget.title, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w500)),
              if (widget.subtitle != null) ...[const SizedBox(height: 4), Text(widget.subtitle!, style: const TextStyle(color: muted, fontSize: 13))],
            ])),
          ]),
        )),
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
  Map<String, bool> cloneOptions = {};
  @override
  void initState() {
    super.initState();
    bridge.setMethodCallHandler((call) async { if (call.method == 'refresh') await load(location);
      if (call.method == 'back') back(); });
    load('');
  }
  Future<void> load(String path) async {
    try {
      final next = Map<String, dynamic>.from(await bridge.invokeMethod(path.isEmpty ? 'ready' : 'list', path) as Map);
      if (!mounted) return;
      setState(() { data = next; location = path; error = null;
        if (next['cloneOptions'] == true) cloneOptions = Map<String, bool>.from(next['options'] as Map);
      });
    } catch (e) { if (mounted) setState(() => error = e is PlatformException ? e.message : '$e'); }
  }
  Future<void> perform(String method, [dynamic argument]) async {
    if (working) return;
    setState(() => working = true);
    try {
      final message = await bridge.invokeMethod(method, argument);
      if (mounted && message is String) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
      if (data?['files'] == true) await load(location);
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e is PlatformException ? e.message ?? '요청 실패' : '$e')));
    } finally { if (mounted) setState(() => working = false); }
  }
  Future<void> cleanup(String kind) async {
    final accepted = await showDialog<bool>(context: context, builder: (ctx) => Dialog(
      backgroundColor: const Color(0xff0f1c30), child: SizedBox(width: 520, child: Padding(padding: const EdgeInsets.all(30),
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(kind == 'apks' ? '듀얼앱 설치파일 정리' : '캐시 정리', style: const TextStyle(fontSize: 25, fontWeight: FontWeight.bold)),
        const SizedBox(height: 16), const Text('설치된 앱과 재사용할 서명 키는 유지됩니다.\n생성한 파일은 다시 만들 수 있습니다.', style: TextStyle(color: muted, fontSize: 17)),
        const SizedBox(height: 24), RemoteTile('취소', autofocus: true, icon: Icons.close, onTap: () => Navigator.pop(ctx, false)),
        RemoteTile('정리하기', icon: Icons.delete_outline, onTap: () => Navigator.pop(ctx, true)),
      ])))));
    if (accepted == true) await perform('clean', kind);
  }
  void back() {
    if (parents.isNotEmpty) { load(parents.removeLast()); } else { perform(data?['files'] == true ? 'sidebar' : 'close'); }
  }
  @override
  Widget build(BuildContext context) {
    final files = data?['files'] == true;
    final configure = data?['cloneOptions'] == true;
    const optionLabels = {
      'renameApp': ['앱 이름 변경', '앱 이름에 듀얼 표시'],
      'renameLaunchers': ['런처 이름 변경', 'TV 홈 화면과 실행 항목 이름 변경'],
      'renameResources': ['리소스 패키지 변경', '끄면 리소스 내부 패키지명은 원본 유지 · 호환성 실험'],
      'isolateTasks': ['실행 작업 분리', '끄면 원본 taskAffinity 유지'],
      'extractLibraries': ['네이티브 라이브러리 추출', '끄면 원본 추출 설정 유지 · 설치 실패 가능'],
    };
    final entries = (data?['entries'] as List? ?? []).cast<Map>();
    final items = (data?['items'] as List? ?? []).cast<String>();
    return PopScope(canPop: !files || parents.isEmpty, onPopInvokedWithResult: (didPop, _) { if (!didPop) back(); },
      child: Focus(onKeyEvent: (_, event) {
        if (files && event is KeyDownEvent && event.logicalKey == LogicalKeyboardKey.arrowLeft) {
          bridge.invokeMethod<void>('sidebar'); return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      }, child: Scaffold(body: SafeArea(child: Padding(
        padding: EdgeInsets.all(files ? 8 : 36),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [Expanded(child: Text(data?['title'] as String? ?? '불러오는 중', style: const TextStyle(fontSize: 32, fontWeight: FontWeight.w600))),
            if (working) const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2)),
          ]),
          if ((data?['message'] as String? ?? '').isNotEmpty) ...[const SizedBox(height: 12),
            Flexible(child: SingleChildScrollView(child: SelectableText(data!['message'] as String, style: const TextStyle(color: muted, fontSize: 17, height: 1.5))))],
          if (error != null) Padding(padding: const EdgeInsets.all(12), child: Text(error!, style: const TextStyle(color: Colors.orangeAccent))),
          const SizedBox(height: 22),
          Expanded(flex: 3, child: FocusTraversalGroup(policy: ReadingOrderTraversalPolicy(), child: ListView(children: [
            if (files) ...[
              if (location.isNotEmpty) RemoteTile('상위 폴더', autofocus: true, icon: Icons.arrow_back, onTap: back),
              if (location.isEmpty) ...[
                RemoteTile('기기 폴더 연결', autofocus: true, subtitle: 'USB · 다운로드 · 허용한 폴더', icon: Icons.create_new_folder_outlined, onTap: () => perform('pick')),
                RemoteTile('듀얼앱 설치파일 정리', subtitle: '${data?['apkSize'] ?? ''}', icon: Icons.delete_outline, onTap: () => cleanup('apks')),
                RemoteTile('캐시 정리', subtitle: '${data?['cacheSize'] ?? ''}', icon: Icons.cleaning_services_outlined, onTap: () => cleanup('cache')),
              ],
              for (final entry in entries) RemoteTile(entry['name'] as String,
                subtitle: entry['detail'] as String?, icon: entry['directory'] == true ? Icons.folder_outlined : Icons.insert_drive_file_outlined,
                onTap: () { final path = entry['path'] as String; if (entry['directory'] == true) { parents.add(location); load(path); } else { perform('open', path); } }),
              if (location.isNotEmpty && entries.isEmpty) const Padding(padding: EdgeInsets.all(28), child: Text('폴더가 비어 있습니다', style: TextStyle(color: muted, fontSize: 20))),
            ] else if (configure) ...[
              const Padding(padding: EdgeInsets.only(bottom: 18), child: Text('패키지·공급자 주소·충돌 권한·서명은 별도 설치를 위해 변경됩니다.\n설치 검사는 앱 내부 실행·인증·DRM 호환성을 보장하지 않습니다.', style: TextStyle(color: muted, fontSize: 14))),
              RemoteTile('기본값 복원', autofocus: true, icon: Icons.restore, onTap: () => setState(() => cloneOptions.updateAll((key, value) => true))),
              for (final entry in optionLabels.entries) RemoteTile(entry.value[0], key: ValueKey(entry.key),
                subtitle: '${cloneOptions[entry.key] == true ? "켬" : "끔"} · ${entry.value[1]}',
                selected: cloneOptions[entry.key] == true, icon: Icons.toggle_off_outlined,
                onTap: () => setState(() => cloneOptions[entry.key] = !(cloneOptions[entry.key] ?? true))),
              RemoteTile('듀얼앱 만들기', icon: Icons.copy_all_rounded, onTap: () => perform('build', cloneOptions)),
              RemoteTile('취소', icon: Icons.close, onTap: () => perform('close')),
            ] else ...[
              for (var i = 0; i < items.length; i++) RemoteTile(items[i], autofocus: i == 0,
                selected: data?['selected'] == i, onTap: () => perform('choose', i)),
              RemoteTile('뒤로', icon: Icons.arrow_back, onTap: () => perform('close')),
            ],
          ]))),
          const SizedBox(height: 12), const Text('방향키  이동     확인  선택     뒤로  돌아가기', style: TextStyle(color: muted, fontSize: 12)),
        ]),
      )))),
    );
  }
}
