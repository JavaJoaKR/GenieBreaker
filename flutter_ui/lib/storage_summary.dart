import 'package:flutter/material.dart';
import 'tv_theme.dart';

class StorageSummary extends StatelessWidget {
  final Map<String, dynamic> storage;
  const StorageSummary(this.storage, {super.key});

  @override
  Widget build(BuildContext context) {
    final fraction = (storage['usedFraction'] as num?)?.toDouble().clamp(0.0, 1.0);
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(color: panel, borderRadius: BorderRadius.circular(14)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Wrap(
            alignment: WrapAlignment.spaceBetween,
            crossAxisAlignment: WrapCrossAlignment.center,
            spacing: 12,
            children: [
              Text(
                '사용 가능  ${storage['free'] ?? '—'}',
                style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w600),
              ),
              Text('전체 ${storage['total'] ?? '—'}', style: const TextStyle(color: muted, fontSize: 15)),
            ],
          ),
          const SizedBox(height: 12),
          LinearProgressIndicator(
            value: fraction ?? 0,
            minHeight: 6,
            color: blue,
            backgroundColor: divider,
            borderRadius: BorderRadius.circular(4),
            semanticsLabel: '사용 중인 저장공간',
            semanticsValue: fraction == null ? '불러오는 중' : '${(fraction * 100).round()}%',
          ),
        ],
      ),
    );
  }
}
