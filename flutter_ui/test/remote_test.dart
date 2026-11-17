import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:genie_ui/main.dart';
import 'package:genie_ui/tv_focus.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

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
  testWidgets('Netflix profile is explicit and submits the chosen state', (tester) async {
    Map? submitted;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'ready') return {'title': '듀얼앱 옵션', 'cloneOptions': true,
        'options': {'renameApp': true, 'renameLaunchers': true, 'renameResources': true, 'isolateTasks': true, 'extractLibraries': true, 'netflixCompatibility': true}};
      if (call.method == 'build') submitted = call.arguments as Map;
      return null;
    });
    await tester.pumpWidget(const GenieUI());
    await tester.pumpAndSettle();
    expect(find.text('넷플릭스 실행 호환성'), findsOneWidget);
    await tester.tap(find.text('넷플릭스 실행 호환성'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('듀얼앱 만들기'));
    await tester.pumpAndSettle();
    expect(submitted?['netflixCompatibility'], false);
    expect(tester.takeException(), isNull);
  });
  testWidgets('TV focus clamps edges and never jumps diagonally', (tester) async {
    final a = FocusNode(); final b = FocusNode(); final c = FocusNode();
    await tester.pumpWidget(MaterialApp(home: FocusTraversalGroup(policy: TvFocusPolicy(), child: Scaffold(body: Column(children: [
      Row(children: [SizedBox(width: 180, child: RemoteTile('A', focusNode: a, autofocus: true, onTap: () {})),
        const SizedBox(width: 20), SizedBox(width: 180, child: RemoteTile('B', focusNode: b, onTap: () {}))]),
      Align(alignment: Alignment.centerLeft, child: SizedBox(width: 180, child: RemoteTile('C', focusNode: c, onTap: () {}))),
    ])))));
    await tester.pumpAndSettle();
    expect(a.hasFocus, true);
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight); await tester.pumpAndSettle(); expect(b.hasFocus, true);
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown); await tester.pumpAndSettle(); expect(b.hasFocus, true);
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowLeft); await tester.pumpAndSettle(); expect(a.hasFocus, true);
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown); await tester.pumpAndSettle(); expect(c.hasFocus, true);
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown); await tester.pumpAndSettle(); expect(c.hasFocus, true);
    await tester.pumpWidget(const SizedBox()); a.dispose(); b.dispose(); c.dispose();
  });

  testWidgets('Error code stays visible above a long scrollable message', (tester) async {
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(bridge, (call) async {
      if (call.method == 'ready') return {'title': '안내', 'items': ['닫기'], 'message': '${List.filled(50, '오류 설명').join('\n')} [GB-BT-3A2F]'};
      return null;
    });
    await tester.pumpWidget(const GenieUI()); await tester.pumpAndSettle();
    final code = find.byKey(const ValueKey('error-code'));
    expect(code, findsOneWidget);
    expect(tester.getRect(code).top, greaterThan(0));
    expect(tester.getRect(code).bottom, lessThan(600));
    expect(tester.takeException(), isNull);
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

}
