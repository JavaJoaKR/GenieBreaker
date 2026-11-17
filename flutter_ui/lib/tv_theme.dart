import 'package:flutter/material.dart';

const ink = Color(0xfff4f6fb);
const muted = Color(0xffb2bfd2);
const blue = Color(0xffaacaff);
const canvas = Color(0xff0d1521);
const panel = Color(0xff192738);
const railSurface = Color(0xff121f2f);
const raised = Color(0xff203044);
const focusFill = Color(0xff304c6a);
const divider = Color(0xff35485f);

ThemeData tvTheme() => ThemeData.dark(useMaterial3: true).copyWith(
  scaffoldBackgroundColor: Colors.transparent,
  colorScheme: const ColorScheme.dark(primary: blue, surface: panel, surfaceTint: Colors.transparent),
  textTheme: ThemeData.dark().textTheme.apply(bodyColor: ink, displayColor: ink),
  iconButtonTheme: IconButtonThemeData(
    style: ButtonStyle(
      backgroundColor: WidgetStateProperty.resolveWith(
        (states) => states.contains(WidgetState.focused) ? focusFill : Colors.transparent,
      ),
      side: WidgetStateProperty.resolveWith(
        (states) =>
            BorderSide(color: states.contains(WidgetState.focused) ? ink : Colors.transparent, width: 2),
      ),
    ),
  ),
  filledButtonTheme: FilledButtonThemeData(
    style:
        FilledButton.styleFrom(
          backgroundColor: blue,
          foregroundColor: canvas,
          minimumSize: const Size(110, 48),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
          textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
        ).copyWith(
          side: WidgetStateProperty.resolveWith(
            (states) => BorderSide(
              color: states.contains(WidgetState.focused) ? ink : Colors.transparent,
              width: 2,
            ),
          ),
        ),
  ),
  outlinedButtonTheme: OutlinedButtonThemeData(
    style:
        OutlinedButton.styleFrom(
          foregroundColor: ink,
          minimumSize: const Size(92, 48),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        ).copyWith(
          side: WidgetStateProperty.resolveWith(
            (states) => BorderSide(color: states.contains(WidgetState.focused) ? ink : divider, width: 2),
          ),
          backgroundColor: WidgetStateProperty.resolveWith(
            (states) => states.contains(WidgetState.focused) ? focusFill : Colors.transparent,
          ),
        ),
  ),
);
