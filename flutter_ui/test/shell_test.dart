import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:genie_ui/home.dart';
import 'package:genie_ui/main.dart';
import 'package:genie_ui/tv_dialogs.dart';

Map<String, dynamic> shell({List<Map>? devices, List<Map>? apps}) => {
  'shell': true, 'version': 'test', 'permission': true, 'enabled': true,
  'devices': devices ?? [
    {'id': 'r1', 'name': '등록 헤드폰', 'bonded': true},
    {'id': 'n1', 'name': '새 키보드', 'bonded': false},
    {'id': 'n2', 'name': '이름 확인 중', 'bonded': false},
  ],
  'apps': {'items': apps ?? [], 'status': '앱을 선택하세요'},
};
Future<void> arrow(WidgetTester tester, LogicalKeyboardKey key) async {
  await tester.sendKeyEvent(key); await tester.pumpAndSettle();
}
String? get focus => FocusManager.instance.primaryFocus?.debugLabel;
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'ready') return shell();
      if (call.method == 'files.list') return {'title': '파일 탐색기', 'entries': [], 'writable': false, 'clipboard': ''};
      if (call.method == 'storage.list') return {'total': '8 GB', 'free': '4 GB', 'usedFraction': .5};
      return null;
    });
  });
  testWidgets('Sidebar clamps and Bluetooth columns never wrap or jump diagonally', (tester) async {
    await tester.pumpWidget(const GenieUI()); await tester.pumpAndSettle();
    expect(focus, 'nav:bluetooth');
    await arrow(tester, LogicalKeyboardKey.arrowUp); expect(focus, 'nav:bluetooth');
    await arrow(tester, LogicalKeyboardKey.arrowRight); expect(focus, 'bt:scan');
    await arrow(tester, LogicalKeyboardKey.arrowDown); expect(focus, 'bt:r1');
    await arrow(tester, LogicalKeyboardKey.arrowDown); expect(focus, 'bt:r1');
    await arrow(tester, LogicalKeyboardKey.arrowRight); expect(focus, 'bt:n1');
    await arrow(tester, LogicalKeyboardKey.arrowDown); expect(focus, 'bt:n2');
    await arrow(tester, LogicalKeyboardKey.arrowLeft); expect(focus, 'bt:n2');
    await arrow(tester, LogicalKeyboardKey.arrowUp); await arrow(tester, LogicalKeyboardKey.arrowLeft); expect(focus, 'bt:r1');
    await arrow(tester, LogicalKeyboardKey.arrowLeft); expect(focus, 'nav:bluetooth');
    expect(find.textContaining('방향키'), findsNothing);
  });
  testWidgets('Shortcut opens Android app settings and storage has its own tab', (tester) async {
    String? method;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      method = call.method;
      if (call.method == 'ready') return shell();
      if (call.method == 'storage.list') return {'total': '8 GB', 'free': '4 GB', 'usedFraction': .5};
      return null;
    });
    await tester.pumpWidget(const GenieUI()); await tester.pumpAndSettle();
    await arrow(tester, LogicalKeyboardKey.arrowDown); await arrow(tester, LogicalKeyboardKey.arrowDown);
    await arrow(tester, LogicalKeyboardKey.arrowRight); expect(focus, 'shortcut:apps');
    await arrow(tester, LogicalKeyboardKey.select); expect(method, 'appSettings');
    await arrow(tester, LogicalKeyboardKey.arrowDown); expect(focus, 'shortcut:accessibility');
    await arrow(tester, LogicalKeyboardKey.select); expect(method, 'accessibility');
    await arrow(tester, LogicalKeyboardKey.arrowDown); expect(focus, 'shortcut:home');
    await arrow(tester, LogicalKeyboardKey.select); expect(method, 'homeSettings');
    await arrow(tester, LogicalKeyboardKey.arrowDown); expect(focus, 'shortcut:accounts');
    await arrow(tester, LogicalKeyboardKey.select); expect(method, 'accountSettings');
    await arrow(tester, LogicalKeyboardKey.arrowDown); expect(focus, 'shortcut:tv-update');
    await arrow(tester, LogicalKeyboardKey.select); expect(method, 'tvUpdateSettings');
    expect(find.text('계정 추가'), findsNothing);
    expect(find.text('넷플릭스 열기'), findsNothing);
    expect(find.text('리모컨 접근성 서비스'), findsNothing);
    await arrow(tester, LogicalKeyboardKey.arrowLeft);
    await arrow(tester, LogicalKeyboardKey.arrowDown); await arrow(tester, LogicalKeyboardKey.arrowDown);
    await arrow(tester, LogicalKeyboardKey.arrowRight); expect(focus, 'storage:refresh');
    expect(find.textContaining('전체 8 GB'), findsOneWidget);
    expect(find.text('저장소 연결'), findsNothing);
    expect(tester.takeException(), isNull);
  });
  testWidgets('Mapping and startup controls work with the remote on compact screens', (tester) async {
    await tester.binding.setSurfaceSize(const Size(640, 360));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final calls = <MethodCall>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      calls.add(call);
      if (call.method == 'ready') return {...shell(), 'controls': {
        'mappingStatus': 'active', 'bindings': [{'key': 183, 'label': '빨간색', 'action': '홈 화면'}],
        'needsOverlay': true, 'canStart': false, 'startupName': '샘플 TV 앱',
      }};
      return null;
    });
    await tester.pumpWidget(const GenieUI()); await tester.pumpAndSettle();
    for (var i = 0; i < 5; i++) { await arrow(tester, LogicalKeyboardKey.arrowDown); }
    await arrow(tester, LogicalKeyboardKey.arrowRight); expect(focus, 'mapping:add');
    await arrow(tester, LogicalKeyboardKey.select);
    expect(calls.last.method, 'chooseButton');
    await arrow(tester, LogicalKeyboardKey.arrowDown); await arrow(tester, LogicalKeyboardKey.select);
    expect(calls.last.method, 'editButton'); expect(calls.last.arguments, 183);
    await arrow(tester, LogicalKeyboardKey.arrowDown); await arrow(tester, LogicalKeyboardKey.select);
    expect(calls.last.method, 'mappingSettings');
    await arrow(tester, LogicalKeyboardKey.arrowLeft); await arrow(tester, LogicalKeyboardKey.arrowDown);
    await arrow(tester, LogicalKeyboardKey.arrowRight);
    expect(find.text('권한 필요'), findsOneWidget);
    expect(find.text('켜짐 · 다른 앱 위에 표시 권한 필요'), findsOneWidget);
    for (final method in ['chooseStartup', 'startupPermission', 'toggleWake', 'testStartup', 'clearStartup']) {
      await arrow(tester, LogicalKeyboardKey.select); expect(calls.last.method, method);
      await arrow(tester, LogicalKeyboardKey.arrowDown);
    }
    expect(tester.takeException(), isNull);
  });
  testWidgets('Folder Back restores originating row and cleanup is absent from explorer', (tester) async {
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'ready') return shell();
      if (call.method == 'files.list') return {'title': '내 파일', 'writable': true, 'clipboard': '',
        'entries': call.arguments == '' ? [{'name': '내 파일', 'path': 'local:', 'directory': true, 'root': true}] : []};
      return null;
    });
    await tester.pumpWidget(const GenieUI()); await tester.pumpAndSettle();
    for (var i = 0; i < 3; i++) { await arrow(tester, LogicalKeyboardKey.arrowDown); }
    await arrow(tester, LogicalKeyboardKey.arrowRight);
    expect(focus, 'files:refresh');
    await arrow(tester, LogicalKeyboardKey.arrowDown); expect(focus, 'file:local:');
    await arrow(tester, LogicalKeyboardKey.select);
    expect(find.text('상위 폴더'), findsOneWidget);
    expect(find.text('설치 파일 정리'), findsNothing);
    await arrow(tester, LogicalKeyboardKey.escape);
    expect(focus, 'file:local:');
    expect(find.text('상위 폴더'), findsNothing);
    expect(tester.takeException(), isNull);
  });
  testWidgets('Every file toolbar button reaches the first entry and Up returns to the initiating button', (tester) async {
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'ready') return shell();
      if (call.method == 'files.list') return {
        'entries': [{'path': 'local:', 'name': '내 파일', 'directory': true}],
        'writable': false, 'clipboard': '',
      };
      return null;
    });
    await tester.pumpWidget(const GenieUI()); await tester.pumpAndSettle();
    for (var i = 0; i < 3; i++) { await arrow(tester, LogicalKeyboardKey.arrowDown); }
    await arrow(tester, LogicalKeyboardKey.arrowRight);
    for (final id in ['files:refresh', 'files:sort', 'files:pick']) {
      expect(focus, id);
      await arrow(tester, LogicalKeyboardKey.arrowDown); expect(focus, 'file:local:');
      await arrow(tester, LogicalKeyboardKey.arrowUp); expect(focus, id);
      await arrow(tester, LogicalKeyboardKey.arrowRight);
    }
  });

  testWidgets('Deep folder path survives a failed Back and jumps to an available storage root', (tester) async {
    await tester.binding.setSurfaceSize(const Size(640, 360));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    var disconnected = false;
    const usb = 'content://sample/one';
    const pictures = 'content://sample/two';
    final requests = <String>[];
    Map<String, dynamic> folder(String path, String name) => {'path': path, 'name': name, 'directory': true};
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'ready') return shell();
      if (call.method != 'files.list') return null;
      final path = call.arguments as String;
      requests.add(path);
      if (disconnected && path.isNotEmpty) throw PlatformException(code: 'files', message: '저장소를 읽을 수 없습니다');
      final entries = switch (path) {
        '' => [folder('local:', '내 파일'), if (!disconnected) folder(usb, 'USB 저장장치')],
        usb => [folder(pictures, '사진')],
        _ => [],
      };
      return {'entries': entries, 'writable': false, 'clipboard': '', 'title': 'provider-id'};
    });
    await tester.pumpWidget(const GenieUI()); await tester.pumpAndSettle();
    for (var i = 0; i < 3; i++) { await arrow(tester, LogicalKeyboardKey.arrowDown); }
    await arrow(tester, LogicalKeyboardKey.arrowRight);
    await arrow(tester, LogicalKeyboardKey.arrowDown);
    expect(focus, 'file:$usb');
    await arrow(tester, LogicalKeyboardKey.select);
    expect(find.text('저장소 목록'), findsNothing);
    expect(focus, 'file:$pictures');
    await arrow(tester, LogicalKeyboardKey.arrowUp); expect(focus, 'files:up');
    await arrow(tester, LogicalKeyboardKey.arrowDown); expect(focus, 'file:$pictures');
    await arrow(tester, LogicalKeyboardKey.select);
    expect(find.text('USB 저장장치  ›  사진'), findsOneWidget);
    expect(find.text('폴더가 비어 있습니다'), findsOneWidget);

    await arrow(tester, LogicalKeyboardKey.escape);
    expect(focus, 'file:$pictures');
    await arrow(tester, LogicalKeyboardKey.select);
    disconnected = true;
    await arrow(tester, LogicalKeyboardKey.escape);
    expect(find.text('저장소를 읽을 수 없습니다'), findsOneWidget);
    expect(find.text('USB 저장장치  ›  사진'), findsOneWidget);
    expect(focus, 'files:refresh');

    await arrow(tester, LogicalKeyboardKey.arrowLeft);
    expect(focus, 'files:roots');
    await arrow(tester, LogicalKeyboardKey.select);
    expect(requests, ['', usb, pictures, usb, pictures, usb, '']);
    expect(focus, 'file:local:');
    expect(find.text('저장소를 읽을 수 없습니다'), findsNothing);
    expect(find.text('저장소 목록'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('File details have a single close action and omit unknown dates', (tester) async {
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'ready') return shell();
      if (call.method == 'files.list') return {
        'entries': [{'path': 'local:note', 'name': '메모', 'directory': false, 'root': true}],
        'writable': false, 'clipboard': '',
      };
      return null;
    });
    await tester.pumpWidget(const GenieUI()); await tester.pumpAndSettle();
    for (var i = 0; i < 3; i++) { await arrow(tester, LogicalKeyboardKey.arrowDown); }
    await arrow(tester, LogicalKeyboardKey.arrowRight);
    await arrow(tester, LogicalKeyboardKey.arrowDown);
    await arrow(tester, LogicalKeyboardKey.select);
    await arrow(tester, LogicalKeyboardKey.arrowDown);
    await arrow(tester, LogicalKeyboardKey.select);
    expect(find.text('닫기'), findsOneWidget);
    expect(find.textContaining('1970'), findsNothing);
    expect(find.text('취소'), findsNothing);
    await arrow(tester, LogicalKeyboardKey.select);
    expect(focus, 'file:local:note');
    expect(tester.takeException(), isNull);
  });

  testWidgets('Same-identity late names and background refresh preserve focus', (tester) async {
    await tester.pumpWidget(const GenieUI()); await tester.pumpAndSettle();
    await arrow(tester, LogicalKeyboardKey.arrowRight); await arrow(tester, LogicalKeyboardKey.arrowDown);
    final updated = shell(devices: [{'id': 'r1', 'name': '새로 확인된 기기 이름', 'bonded': true}]);
    await tester.binding.defaultBinaryMessenger.handlePlatformMessage('genie/ui', const StandardMethodCodec().encodeMethodCall(MethodCall('shell', updated)), (_) {});
    await tester.pumpAndSettle();
    expect(focus, 'bt:r1'); expect(find.text('새로 확인된 기기 이름'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
  testWidgets('Removed focused devices recover to an attached target', (tester) async {
    await tester.pumpWidget(const GenieUI()); await tester.pumpAndSettle();
    await arrow(tester, LogicalKeyboardKey.arrowRight); await arrow(tester, LogicalKeyboardKey.arrowDown);
    await tester.binding.defaultBinaryMessenger.handlePlatformMessage('genie/ui', const StandardMethodCodec().encodeMethodCall(MethodCall('shell', shell(devices: []))), (_) {});
    await tester.pumpAndSettle();
    await arrow(tester, LogicalKeyboardKey.arrowDown);
    expect(focus, isNotNull); expect(['bt:scan', 'nav:bluetooth'], contains(focus));
    expect(tester.takeException(), isNull);
  });
  testWidgets('Cleanup cancel never sends a destructive operation', (tester) async {
    var cleaned = false;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'ready') return shell();
      if (call.method == 'storage.list') return {'total': '8 GB', 'free': '4 GB', 'usedFraction': .5};
      if (call.method == 'clean') cleaned = true;
      return null;
    });
    await tester.pumpWidget(const GenieUI()); await tester.pumpAndSettle();
    for (var i = 0; i < 4; i++) { await arrow(tester, LogicalKeyboardKey.arrowDown); }
    await arrow(tester, LogicalKeyboardKey.arrowRight); await arrow(tester, LogicalKeyboardKey.arrowDown);
    await arrow(tester, LogicalKeyboardKey.select);
    expect(cleaned, false);
    await tester.tap(find.text('취소')); await tester.pumpAndSettle();
    expect(cleaned, false); expect(focus, 'storage:apks');
  });
  testWidgets('Menu traps focus and dismiss restores its content opener in the same surface', (tester) async {
    await tester.pumpWidget(const GenieUI()); await tester.pumpAndSettle();
    await arrow(tester, LogicalKeyboardKey.arrowRight); await arrow(tester, LogicalKeyboardKey.arrowDown);
    expect(focus, 'bt:r1');
    await tester.binding.defaultBinaryMessenger.handlePlatformMessage('genie/ui', const StandardMethodCodec().encodeMethodCall(
      const MethodCall('page', {'title': '기기 작업', 'pageId': 'device-menu', 'items': ['연결', '닫기'], 'focus': 0})), (_) {});
    await tester.pumpAndSettle(); expect(focus, 'device-menu:item-0');
    await arrow(tester, LogicalKeyboardKey.arrowLeft); expect(focus, 'device-menu:item-0');
    await tester.binding.defaultBinaryMessenger.handlePlatformMessage('genie/ui', const StandardMethodCodec().encodeMethodCall(const MethodCall('dismiss')), (_) {});
    await tester.pumpAndSettle(); expect(focus, 'bt:r1');
    expect(tester.takeException(), isNull);
  });
  testWidgets('Partial app rows clamp and narrow landscape keeps every tab reachable', (tester) async {
    await tester.binding.setSurfaceSize(const Size(640, 360));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'ready') return shell(apps: [
        for (var i = 0; i < 5; i++)
          {'id': 'p$i', 'name': '긴 이름을 가진 TV 앱 $i', 'detail': i == 0 ? '듀얼앱 설치됨' : ''},
      ]);
      return null;
    });
    await tester.pumpWidget(const GenieUI()); await tester.pumpAndSettle();
    await arrow(tester, LogicalKeyboardKey.arrowDown);
    expect(focus, 'nav:apps');
    expect(find.byKey(const ValueKey('app:p0')), findsOneWidget);
    expect(find.byIcon(Icons.check_rounded), findsNothing);
    final cardSize = tester.getSize(find.byKey(const ValueKey('app:p0')));
    for (var i = 1; i < 5; i++) {
      expect(tester.getSize(find.byKey(ValueKey('app:p$i'))), cardSize);
    }
    await arrow(tester, LogicalKeyboardKey.arrowRight);
    expect(focus, 'apps:filter');
    await arrow(tester, LogicalKeyboardKey.arrowDown); expect(focus, 'app:p0');
    await arrow(tester, LogicalKeyboardKey.arrowRight); expect(focus, 'app:p1');
    await arrow(tester, LogicalKeyboardKey.arrowDown); expect(focus, 'app:p3');
    await arrow(tester, LogicalKeyboardKey.arrowDown); expect(focus, 'app:p3');
    await arrow(tester, LogicalKeyboardKey.arrowLeft); await arrow(tester, LogicalKeyboardKey.arrowLeft);
    for (var i = 0; i < 8; i++) { await arrow(tester, LogicalKeyboardKey.arrowDown); }
    expect(focus, 'nav:settings');
    expect(tester.takeException(), isNull);
  });

  testWidgets('Rapid arrows during a tab transition stay in the requested content', (tester) async {
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'ready') return shell(apps: [{'id': 'first', 'name': 'TV 앱'}]);
      return null;
    });
    await tester.pumpWidget(const GenieUI()); await tester.pumpAndSettle();
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
    await tester.pumpAndSettle();
    expect(focus, 'app:first');
    expect(tester.takeException(), isNull);
  });

  testWidgets('A slow folder refresh never steals focus from a later user action', (tester) async {
    final response = Completer<Map<String, dynamic>>();
    var lists = 0;
    final folder = <String, dynamic>{'title': '파일 탐색기', 'entries': [], 'writable': false, 'clipboard': ''};
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'ready') return shell();
      if (call.method == 'files.list') return ++lists == 1 ? folder : response.future;
      return null;
    });
    await tester.pumpWidget(const GenieUI()); await tester.pumpAndSettle();
    for (var i = 0; i < 3; i++) { await arrow(tester, LogicalKeyboardKey.arrowDown); }
    await arrow(tester, LogicalKeyboardKey.arrowRight);
    expect(focus, 'files:refresh');
    await tester.sendKeyEvent(LogicalKeyboardKey.select); await tester.pump();
    await arrow(tester, LogicalKeyboardKey.arrowRight); expect(focus, 'files:sort');
    response.complete(folder); await tester.pumpAndSettle();
    expect(focus, 'files:sort'); expect(tester.takeException(), isNull);
  });

  testWidgets('A popup opened from the sidebar restores the sidebar, never a content header', (tester) async {
    await tester.pumpWidget(const GenieUI()); await tester.pumpAndSettle();
    await arrow(tester, LogicalKeyboardKey.arrowDown); await arrow(tester, LogicalKeyboardKey.select);
    expect(focus, 'nav:apps');
    await tester.binding.defaultBinaryMessenger.handlePlatformMessage('genie/ui', const StandardMethodCodec().encodeMethodCall(
      const MethodCall('page', {'title': '보기 설정', 'pageId': 'sidebar-popup', 'items': ['닫기'], 'focus': 0})), (_) {});
    await tester.pumpAndSettle();
    await tester.binding.defaultBinaryMessenger.handlePlatformMessage('genie/ui', const StandardMethodCodec().encodeMethodCall(const MethodCall('dismiss')), (_) {});
    await tester.pumpAndSettle(); expect(focus, 'nav:apps');
    await arrow(tester, LogicalKeyboardKey.arrowDown); expect(focus, 'nav:shortcuts');
    expect(tester.takeException(), isNull);
  });

  testWidgets('The first arrow after root focus loss both recovers and moves', (tester) async {
    await tester.pumpWidget(const GenieUI()); await tester.pumpAndSettle();
    FocusManager.instance.primaryFocus?.unfocus(); await tester.pumpAndSettle();
    await arrow(tester, LogicalKeyboardKey.arrowDown);
    expect(focus, 'nav:apps'); expect(tester.takeException(), isNull);
  });

  testWidgets('Window focus return never moves selection out of the open dialog', (tester) async {
    await tester.pumpWidget(const GenieUI()); await tester.pumpAndSettle();
    final context = tester.element(find.text('GenieBreaker'));
    final dialog = promptName(context, '폴더 이름', '새 폴더');
    await tester.pumpAndSettle();
    final inputFocus = FocusManager.instance.primaryFocus;
    expect(inputFocus?.context?.widget, isNotNull);
    expect(MediaQuery.of(tester.element(find.byType(TextFormField))).navigationMode, NavigationMode.directional);
    await tester.binding.defaultBinaryMessenger.handlePlatformMessage('genie/ui',
      const StandardMethodCodec().encodeMethodCall(const MethodCall('enter')), (_) {});
    await tester.pumpAndSettle();
    expect(FocusManager.instance.primaryFocus, same(inputFocus));
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pumpAndSettle();
    expect(FocusManager.instance.primaryFocus, same(inputFocus));
    Navigator.of(tester.element(find.byType(TextFormField))).pop();
    await tester.pumpAndSettle(); await dialog;
    await arrow(tester, LogicalKeyboardKey.arrowDown);
    expect(focus, 'nav:apps');
  });

  testWidgets('Idle Android focus return schedules a frame and preserves newer remote selection', (tester) async {
    await tester.pumpWidget(const GenieUI()); await tester.pumpAndSettle();
    await arrow(tester, LogicalKeyboardKey.arrowDown);
    FocusManager.instance.primaryFocus?.unfocus(); await tester.pumpAndSettle();
    expect(tester.binding.hasScheduledFrame, false);
    await tester.binding.defaultBinaryMessenger.handlePlatformMessage('genie/ui',
      const StandardMethodCodec().encodeMethodCall(const MethodCall('enter')), (_) {});
    expect(tester.binding.hasScheduledFrame, true);
    await tester.pumpAndSettle(); expect(focus, 'nav:apps');
    await arrow(tester, LogicalKeyboardKey.arrowDown); expect(focus, 'nav:shortcuts');
  });

  testWidgets('Gamepad confirm after focus loss restores its saved control and activates once', (tester) async {
    var opened = 0;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'ready') return shell();
      if (call.method == 'appSettings') opened++;
      return null;
    });
    await tester.pumpWidget(const GenieUI()); await tester.pumpAndSettle();
    await arrow(tester, LogicalKeyboardKey.arrowDown); await arrow(tester, LogicalKeyboardKey.arrowDown);
    await arrow(tester, LogicalKeyboardKey.arrowRight);
    FocusManager.instance.primaryFocus?.unfocus(); await tester.pumpAndSettle();
    await arrow(tester, LogicalKeyboardKey.gameButtonA);
    expect(focus, 'shortcut:apps'); expect(opened, 1);
  });

  testWidgets('Popup remembers the selected row across window focus loss and first arrow recovery', (tester) async {
    final choices = <int>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'ready') return shell();
      if (call.method == 'choose') choices.add(call.arguments as int);
      return null;
    });
    await tester.pumpWidget(const GenieUI()); await tester.pumpAndSettle();
    await tester.binding.defaultBinaryMessenger.handlePlatformMessage('genie/ui', const StandardMethodCodec().encodeMethodCall(
      const MethodCall('page', {'title': '동작 선택', 'pageId': 'remote-menu', 'items': ['첫 번째', '두 번째', '세 번째']})), (_) {});
    await tester.pumpAndSettle();
    await arrow(tester, LogicalKeyboardKey.arrowDown); expect(focus, 'remote-menu:item-1');
    FocusManager.instance.primaryFocus?.unfocus(); await tester.pumpAndSettle();
    await tester.binding.defaultBinaryMessenger.handlePlatformMessage('genie/ui',
      const StandardMethodCodec().encodeMethodCall(const MethodCall('enter')), (_) {});
    await tester.pumpAndSettle(); expect(focus, 'remote-menu:item-1');
    FocusManager.instance.primaryFocus?.unfocus(); await tester.pumpAndSettle();
    await arrow(tester, LogicalKeyboardKey.arrowDown); expect(focus, 'remote-menu:item-2');
    await arrow(tester, LogicalKeyboardKey.select); expect(choices, [2]);
  });

  testWidgets('Android remote navigation works without debug labels and respects foreground dialogs', (tester) async {
    final calls = <String>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'ready') return shell();
      calls.add(call.method);
      return null;
    });
    Future<void> remote(String command) async {
      await tester.binding.defaultBinaryMessenger.handlePlatformMessage(
        'genie/ui',
        const StandardMethodCodec().encodeMethodCall(MethodCall('remoteNavigation', command)),
        (_) {},
      );
      await tester.pumpAndSettle();
    }

    await tester.pumpWidget(const GenieUI());
    await tester.pumpAndSettle();
    final home = tester.state<AppHomeState>(find.byType(AppHome));
    void clearDebugLabels() {
      for (final node in home.nodes.values) {
        node.debugLabel = null;
      }
    }
    clearDebugLabels();
    expect(home.focused, 'nav:bluetooth');
    await remote('down');
    await remote('down');
    await remote('right');
    clearDebugLabels();
    expect(home.focused, 'shortcut:apps');
    await remote('activate');
    expect(calls.where((call) => call == 'appSettings'), hasLength(1));

    final context = tester.element(find.text('GenieBreaker'));
    final dialog = confirmAction(context, '검사', '계속하시겠습니까?', '계속');
    await tester.pumpAndSettle();
    await remote('right');
    await remote('activate');
    expect(find.text('계속하시겠습니까?'), findsNothing);
    expect(await dialog, true);
    expect(home.focused, 'shortcut:apps');
    expect(calls.where((call) => call == 'appSettings'), hasLength(1));
  });

  testWidgets('Lock enables from settings with one selection', (tester) async {
    final calls = <MethodCall>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      calls.add(call);
      if (call.method == 'ready') return {...shell(), 'buildCommit': '0Abc42A'};
      return null;
    });
    await tester.pumpWidget(const GenieUI());
    await tester.pumpAndSettle();
    for (var i = 0; i < 8; i++) {
      await arrow(tester, LogicalKeyboardKey.arrowDown);
    }
    expect(focus, 'nav:settings');
    expect(find.textContaining('현재 버전  test'), findsOneWidget);
    expect(find.textContaining('0Abc42A'), findsNothing);
    await arrow(tester, LogicalKeyboardKey.arrowRight);
    await arrow(tester, LogicalKeyboardKey.arrowDown);
    await arrow(tester, LogicalKeyboardKey.arrowDown);
    expect(focus, 'settings:lock');
    await arrow(tester, LogicalKeyboardKey.select);
    expect(calls.where((call) => call.method == 'enableLock'), hasLength(1));
    expect(find.byType(TextField), findsNothing);
  });

  testWidgets('Locked session shows the version and accepts a seven-character code', (tester) async {
    String? submitted;
    final messenger = tester.binding.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'ready') return shell();
      if (call.method == 'unlockApp') {
        submitted = call.arguments as String;
        return true;
      }
      return null;
    });
    await tester.pumpWidget(const GenieUI());
    await tester.pumpAndSettle();
    await messenger.handlePlatformMessage(
      'genie/ui',
      const StandardMethodCodec().encodeMethodCall(
        const MethodCall('session', {'shell': true, 'locked': true, 'version': '0.1.193'}),
      ),
      (_) {},
    );
    await tester.pumpAndSettle();
    expect(find.text('GenieBreaker 잠금'), findsOneWidget);
    expect(find.text('블루투스'), findsNothing);
    expect(find.text('v0.1.193'), findsOneWidget);
    expect(find.text('잠금 코드'), findsOneWidget);
    expect(find.textContaining('자리'), findsNothing);
    await tester.enterText(find.byType(TextField), '0AbF19');
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();
    expect(submitted, isNull);
    await tester.enterText(find.byType(TextField), '0AbF19C');
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();
    expect(submitted, '0AbF19C');
  });

}
