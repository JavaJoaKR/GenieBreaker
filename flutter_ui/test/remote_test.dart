import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:genie_ui/main.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  testWidgets('Files move left through folders before returning to the sidebar', (tester) async {
    var sidebar = 0;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'ready') return {'title': '파일탐색기', 'files': true,
        'entries': [{'name': '듀얼앱 설치파일', 'path': 'apks:', 'directory': true, 'detail': '폴더'}], 'apkSize': '4 MB', 'cacheSize': '1 MB'};
      if (call.method == 'sidebar') sidebar++;
      return null;
    });
    await tester.pumpWidget(const GenieUI());
    await tester.pumpAndSettle();
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
    await tester.pumpAndSettle();
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowLeft);
    await tester.pumpAndSettle();
    expect(sidebar, 0);
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowLeft);
    await tester.pumpAndSettle();
    expect(sidebar, 1);
  });
  testWidgets('Confirmation actions stay visible with a long message', (tester) async {
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'ready') return {'title': '앱을 제거할까요?', 'items': ['취소', '제거 요청'], 'message': List.filled(50, '앱 데이터가 삭제됩니다.').join('\n')};
      return null;
    });
    await tester.pumpWidget(const GenieUI());
    await tester.pumpAndSettle();
    expect(tester.getRect(find.text('제거 요청')).bottom, lessThan(600));
    expect(tester.getRect(find.text('취소')).bottom, lessThan(600));
    expect(tester.takeException(), isNull);
  });
  testWidgets('Menu is bounded, centered, and dismisses through the outside barrier', (tester) async {
    var closed = false;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'ready') return {'title': '보기 설정', 'items': ['실행 가능한 앱', '전체 설치 앱'], 'focus': 0};
      if (call.method == 'close') closed = true;
      return null;
    });
    await tester.pumpWidget(const GenieUI());
    await tester.pumpAndSettle();
    final rect = tester.getRect(find.byKey(const ValueKey('popup-surface')));
    expect(rect.width, lessThanOrEqualTo(490));
    expect(rect.height, lessThan(536));
    expect(rect.center.dx, closeTo(400, 1));
    expect(rect.center.dy, closeTo(300, 1));
    await tester.tapAt(const Offset(8, 8));
    await tester.pumpAndSettle();
    expect(closed, true);
  });
  testWidgets('Compatibility settings stay collapsed until requested', (tester) async {
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'ready') return {'title': '듀얼앱 만들기', 'cloneOptions': true,
        'options': {'renameApp': true, 'renameLaunchers': true, 'renameResources': true, 'isolateTasks': true, 'extractLibraries': true}};
      return null;
    });
    await tester.pumpWidget(const GenieUI());
    await tester.pumpAndSettle();
    expect(find.text('리소스 패키지 변경'), findsNothing);
    await tester.tap(find.text('호환성 옵션'));
    await tester.pumpAndSettle();
    expect(find.text('리소스 패키지 변경'), findsOneWidget);
    expect(tester.getRect(find.text('듀얼앱 만들기').last).bottom, lessThan(600));
  });
  testWidgets('Clone options are submitted as selected without immediate generation', (tester) async {
    Map? submitted;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'ready') return {'title': '듀얼앱 옵션', 'cloneOptions': true,
        'options': {'renameApp': true, 'renameLaunchers': true, 'renameResources': true, 'isolateTasks': true, 'extractLibraries': true}};
      if (call.method == 'build') submitted = call.arguments as Map;
      return null;
    });
    await tester.pumpWidget(const GenieUI());
    await tester.pumpAndSettle();
    await tester.tap(find.text('앱 이름 변경'));
    await tester.pumpAndSettle();
    expect(submitted, isNull);
    await tester.scrollUntilVisible(find.text('듀얼앱 만들기'), 180);
    await tester.tap(find.text('듀얼앱 만들기'));
    await tester.pumpAndSettle();
    expect(submitted?['renameApp'], false);
    expect(submitted?['renameResources'], true);
  });
  testWidgets('Remote selection returns the focused menu item', (tester) async {
    Object? choice;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'ready') return {'title': '보기 설정', 'items': ['실행 가능한 앱', '전체 설치 앱'], 'selected': 0};
      if (call.method == 'choose') choice = call.arguments;
      return null;
    });
    await tester.pumpWidget(const GenieUI());
    await tester.pumpAndSettle();
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
    await tester.pumpAndSettle();
    await tester.sendKeyEvent(LogicalKeyboardKey.select);
    await tester.pumpAndSettle();
    expect(choice, 1);
  });
  testWidgets('Cleanup requires an explicit confirmation and cancel preserves files', (tester) async {
    var cleaned = false;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'ready') return {'title': '파일탐색기', 'files': true, 'entries': [], 'apkSize': '4 MB', 'cacheSize': '1 MB'};
      if (call.method == 'clean') cleaned = true;
      return null;
    });
    await tester.pumpWidget(const GenieUI());
    await tester.pumpAndSettle();
    await tester.tap(find.text('설치파일 정리'));
    await tester.pumpAndSettle();
    expect(cleaned, false);
    await tester.tap(find.text('취소'));
    await tester.pumpAndSettle();
    expect(cleaned, false);
    expect(find.byType(PopupPanel), findsNothing);
  });
}
