import 'package:flutter/material.dart';

class TvAction {
  final String id, title, detail, method;
  final IconData icon;
  final int? keyCode;
  const TvAction(this.id, this.title, this.detail, this.icon, this.method, [this.keyCode]);
}

List<TvAction> mappingActions(Map state) => [
  const TvAction(
    'mapping:add',
    '버튼 추가',
    '리모컨에서 추가할 버튼을 누르세요',
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
  TvAction(
    'mapping:service',
    '버튼 맵핑 설정',
    switch (state['mappingStatus']) {
      'active' => '접근성 서비스 연결됨',
      'connecting' => '접근성에서 켜짐 · 연결 대기 중',
      'restricted' => '앱 정보에서 제한된 설정 허용 필요',
      'unknown' => '접근성 설정에서 서비스 상태를 확인하세요',
      _ => '지정한 동작을 실행하려면 접근성에서 켜세요',
    },
    Icons.settings_outlined,
    'mappingSettings',
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
        state['wake'] == false
            ? '꺼짐 · TV를 재부팅할 때만 실행'
            : needsOverlay && state['hasOverlay'] != true
            ? '켜짐 · 다른 앱 위에 표시 권한 필요'
            : '켜짐 · TV가 대기 상태에서 깨어날 때 실행',
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
