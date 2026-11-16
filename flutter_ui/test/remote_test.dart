import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:genie_ui/main.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
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
    await tester.tap(find.text('듀얼앱 설치파일 정리'));
    await tester.pumpAndSettle();
    expect(cleaned, false);
    await tester.tap(find.text('취소'));
    await tester.pumpAndSettle();
    expect(cleaned, false);
    expect(find.byType(Dialog), findsNothing);
  });
}
