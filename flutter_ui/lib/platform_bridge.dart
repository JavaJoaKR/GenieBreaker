import 'package:flutter/services.dart';

const bridge = MethodChannel('genie/ui');

String requestError(Object error) {
  final message = error is PlatformException ? error.message ?? '요청 실패' : '요청 실패';
  return message.contains('[GB-') ? message : '$message [GB-UI-0001]';
}
