import 'package:flutter/material.dart';

/// TV arrows move within the current row/column. Never wrap or jump diagonally.
class TvFocusPolicy extends FocusTraversalPolicy {
  @override
  Iterable<FocusNode> sortDescendants(Iterable<FocusNode> descendants, FocusNode currentNode) => descendants;
  @override
  bool inDirection(FocusNode currentNode, TraversalDirection direction) {
    final origin = currentNode.rect;
    final vertical = direction == TraversalDirection.up || direction == TraversalDirection.down;
    final forward = direction == TraversalDirection.down || direction == TraversalDirection.right;
    final candidates = (currentNode.nearestScope?.traversalDescendants ?? <FocusNode>[]).where((node) {
      if (node == currentNode || !node.canRequestFocus || node.skipTraversal || node.context == null) return false;
      final rect = node.rect;
      if (rect.isEmpty) return false;
      final aligned = vertical ? rect.left < origin.right && rect.right > origin.left : rect.top < origin.bottom && rect.bottom > origin.top;
      final delta = vertical ? rect.center.dy - origin.center.dy : rect.center.dx - origin.center.dx;
      return aligned && (forward ? delta > 1 : delta < -1);
    }).toList();
    double score(FocusNode node) {
      final delta = node.rect.center - origin.center;
      return (vertical ? delta.dy.abs() : delta.dx.abs()) * 1000 + (vertical ? delta.dx.abs() : delta.dy.abs());
    }
    candidates.sort((a, b) => score(a).compareTo(score(b)));
    if (candidates.isNotEmpty) {
      candidates.first.requestFocus();
      final context = candidates.first.context;
      if (context != null) Scrollable.ensureVisible(context, alignment: .5);
    } else if (vertical && currentNode.context != null) {
      final scrollable = Scrollable.maybeOf(currentNode.context!);
      final position = scrollable?.position;
      if (position != null && (forward ? position.extentAfter > 1 : position.extentBefore > 1)) {
        position.jumpTo((position.pixels + (forward ? 1 : -1) * position.viewportDimension * .7).clamp(position.minScrollExtent, position.maxScrollExtent).toDouble());
        WidgetsBinding.instance.addPostFrameCallback((_) { if (currentNode.context != null) inDirection(currentNode, direction); });
      }
    }
    return true;
  }
}
