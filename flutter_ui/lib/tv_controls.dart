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
    '짧게 누르기와 길게 누르기를 따로 등록합니다',
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
    '버튼 매핑 설정',
    switch (state['mappingStatus']) {
      'active' => '접근성 서비스 연결됨',
      'connecting' => '접근성에서 켜짐 · 연결 대기 중',
      'restricted' => '앱 정보에서 제한된 설정 허용 필요',
      'unknown' => '접근성 설정에서 서비스 상태를 확인하세요',
      _ => '버튼에 지정한 동작을 실행하려면 접근성 서비스를 켜세요',
    },
    Icons.settings_outlined,
    'mappingSettings',
  ),
];
