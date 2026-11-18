import 'package:flutter/material.dart';

class TvAction {
  final String id, title, detail, method;
  final IconData icon;
  final int? keyCode;
  const TvAction(this.id, this.title, this.detail, this.icon, this.method, [this.keyCode]);
}

List<TvAction> mappingActions(Map state) => [
  TvAction(
    'mapping:service',
    '리모컨 접근성 서비스',
    state['mappingActive'] == true ? '사용 중 · 접근성 설정 열기' : '접근성에서 GenieBreaker 버튼 맵핑을 켜세요',
    Icons.accessibility_new_rounded,
    'accessibility',
  ),
  const TvAction(
    'mapping:add',
    '버튼 지정',
    '리모컨 버튼으로 앱을 열거나 홈·뒤로 등의 동작을 실행합니다',
    Icons.add_rounded,
    'chooseButton',
  ),
  for (final binding in state['bindings'] as List? ?? [])
    TvAction(
      'mapping:${binding['key']}',
      binding['label'] as String,
      binding['action'] as String,
      Icons.gamepad_outlined,
      'editButton',
      binding['key'] as int,
    ),
];

List<TvAction> startupActions(Map state) {
  final name = state['startupName'] as String? ?? '';
  final needsOverlay = state['needsOverlay'] == true;
  return [
    TvAction(
      'startup:choose',
      name.isEmpty ? '시작할 앱 선택' : name,
      name.isEmpty ? 'TV를 켜고 부팅을 마치면 실행할 앱을 선택하세요' : '시작 앱 변경',
      Icons.apps_rounded,
      'chooseStartup',
    ),
    if (name.isNotEmpty) ...[
      if (needsOverlay)
        TvAction(
          'startup:permission',
          '다른 앱 위에 표시',
          state['hasOverlay'] == true
              ? '허용됨 · 권한 설정 열기'
              : state['defaultHome'] == true
              ? '대기 해제 시에도 실행하려면 권한을 허용하세요'
              : 'TV를 켤 때 실행하려면 권한을 허용하세요',
          Icons.open_in_new_rounded,
          'startupPermission',
        ),
      TvAction(
        'startup:wake',
        '대기 해제 시에도 실행',
        state['wake'] == true ? '켜짐 · 대기 해제 감지 중' : '꺼짐 · TV를 재부팅할 때만 실행',
        Icons.wb_sunny_outlined,
        'toggleWake',
      ),
      const TvAction('startup:test', '지금 실행', '선택한 앱을 열어 확인합니다', Icons.play_arrow_rounded, 'testStartup'),
      const TvAction(
        'startup:clear',
        '시작 앱 해제',
        '부팅 시 자동 실행을 끕니다',
        Icons.power_settings_new_rounded,
        'clearStartup',
      ),
    ],
  ];
}
