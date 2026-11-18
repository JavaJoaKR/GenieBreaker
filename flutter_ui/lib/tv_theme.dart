import 'package:flutter/material.dart';

const ink = Color(0xfff5f5f5);
const muted = Color(0xffbdbdbd);
const accentTone = Color(0xffdddddd);
const canvas = Color(0xff121212);
const panel = Color(0xff222222);
const railSurface = Color(0xff1b1b1b);
const raised = Color(0xff2a2a2a);
const selectedFill = Color(0xff393939);
const focusFill = Color(0xff5c5c5c);
const divider = Color(0xff4b4b4b);

// Keep the existing size hierarchy with each explicit radius halved.
const tileRadius = 5.0;
const insetRadius = 6.0;
const panelRadius = 7.0;
const railRadius = 8.0;
const modalRadius = 9.0;

ThemeData tvTheme() => ThemeData.dark(useMaterial3: true).copyWith(
  scaffoldBackgroundColor: Colors.transparent,
  colorScheme: ColorScheme.fromSeed(
    seedColor: accentTone,
    brightness: Brightness.dark,
    dynamicSchemeVariant: DynamicSchemeVariant.monochrome,
    primary: accentTone,
    onPrimary: canvas,
    surface: panel,
    surfaceTint: Colors.transparent,
    error: ink,
    onError: canvas,
    errorContainer: raised,
    onErrorContainer: ink,
  ),
  textTheme: ThemeData.dark().textTheme.apply(bodyColor: ink, displayColor: ink),
  iconButtonTheme: IconButtonThemeData(
    style: ButtonStyle(
      shape: const WidgetStatePropertyAll(RoundedRectangleBorder(
        borderRadius: BorderRadius.all(Radius.circular(10)),
      )),
      backgroundColor: WidgetStateProperty.resolveWith(
        (states) => states.contains(WidgetState.focused) ? focusFill : Colors.transparent,
      ),
      side: WidgetStateProperty.resolveWith(
        (states) =>
            BorderSide(color: states.contains(WidgetState.focused) ? ink : Colors.transparent, width: 2),
      ),
    ),
  ),
  textButtonTheme: TextButtonThemeData(
    style: TextButton.styleFrom(
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(10))),
    ),
  ),
  filledButtonTheme: FilledButtonThemeData(
    style:
        FilledButton.styleFrom(
          backgroundColor: accentTone,
          foregroundColor: canvas,
          minimumSize: const Size(100, 44),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(tileRadius)),
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
          minimumSize: const Size(84, 44),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(tileRadius)),
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
