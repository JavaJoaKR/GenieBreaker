import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'menu_host.dart';
import 'tv_theme.dart';

export 'platform_bridge.dart' show bridge;
export 'remote_tile.dart' show RemoteTile;
export 'menu_host.dart' show GeniePage;
export 'popup_panel.dart' show PopupPanel;

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  FocusManager.instance.highlightStrategy = FocusHighlightStrategy.alwaysTraditional;
  runApp(const GenieUI());
}

class GenieUI extends StatelessWidget {
  const GenieUI({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: tvTheme(),
    // App-local logical density: every UI element is 90% of its previous size.
    // The TV system density and other applications are never modified.
    builder: (context, child) => LayoutBuilder(
      builder: (context, constraints) {
        final size = Size(constraints.maxWidth / .9, constraints.maxHeight / .9);
        return FittedBox(
          fit: BoxFit.contain,
          child: SizedBox(
            width: size.width,
            height: size.height,
            child: MediaQuery(
              data: MediaQuery.of(context).copyWith(size: size, navigationMode: NavigationMode.directional),
              child: child!,
            ),
          ),
        );
      },
    ),
    shortcuts: {
      ...WidgetsApp.defaultShortcuts,
      const SingleActivator(LogicalKeyboardKey.select, includeRepeats: false): const ActivateIntent(),
      const SingleActivator(LogicalKeyboardKey.enter, includeRepeats: false): const ActivateIntent(),
      const SingleActivator(LogicalKeyboardKey.numpadEnter, includeRepeats: false):
          const ActivateIntent(),
      const SingleActivator(LogicalKeyboardKey.space, includeRepeats: false): const ActivateIntent(),
    },
    home: const GeniePage(),
  );
}
