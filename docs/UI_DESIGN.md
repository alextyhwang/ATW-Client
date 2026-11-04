# Launcher and config UI design

Application icons and in-game identity follow [BRANDING.md](BRANDING.md).

The Qt window shared by `atw-config` (and the legacy `atw-client`) and the
sign-in dialog that `atw-launch` also shows follow the Figma reference
**Frame 21, node 2427:642** (Game tab; Frame 23, node 2427:888, is a variant).
The Figma file belongs to the maintainer's account; ask for access rather than
re-deriving values. Its raw exports remain in `res/launchbutton/*.svg`,
`res/tabs/*.svg`, `res/toggle/*.svg`, `res/slider/*.svg` and `res/icon.svg` as
references; they are not compiled into the binaries.

## Frame geometry (572 x 749, radius 10)

| Element | Figma position | Code |
| --- | --- | --- |
| Header / body split | y 160 (`#252526` / `#343435`) | `WindowFrame` |
| ATW badge, close tile | 34 px tiles at y 24, 22 px side margins | `CustomTitleBar` |
| LAUNCH | 322 x 81 face at y 107, 6 px edge | `LaunchButton` |
| Tab tray | 528 x 66 at y 210, 9 px padding/gaps | `CustomTabBar` in `QFrame#tray` |
| Content card | 528 x 434 at y 288, 18 px padding | `QFrame#card` + page stack |
| Card rows | 24 px label blocks, 21 px fields, 126 px JVM box, 14 px gaps | `GamePage`, `WidgetUtils` |

`AtwTheme::Metrics` holds these numbers and `AtwTheme::Color` the tokens:
`#252526` frame/cards, `#343435` body/inputs, `#2C2C2C` 3D edges, `#929292`
labels, `#BFBFBF` values, `#6C6C6C` input text, `#5C5C60` inactive tabs,
`#E7EDF9` active text, `#4779D8` accent.

## Rendering

- Raised surfaces (LAUNCH, tabs) are painted, not bitmaps: vertical gradient,
  Figma inner glow (`inset 0 0 7px 4px`, rendered as Gaussian rings) and a hard
  edge below the face. They stay sharp at any size and display scale.
- The slider thumb and toggle are drawn from the Figma pixel rectangles.
- UI text uses the bundled `res/fonts/Minecraft.ttf` (family "Minecraft",
  Medium/500). The stylesheet pins `font-weight: 500`: Figma's LAUNCH/ATW face,
  Minecraft Ten, registers as typographic family "Minecraft" at weight 400 on
  machines that have it installed. It is not bundled; LAUNCH, CLOSE and ATW
  use the outlined Figma paths in `res/ui/launch-button` and `res/ui/window`.
- Text uses grayscale antialiasing to match the Figma render.
- Windows paints a soft shadow in an 18 px transparent margin; macOS uses its
  native shadow, which follows the rounded alpha mask.

## Behaviour beyond the frame

These parts have no Figma frame and reuse the same components and tokens:

- Hover, pressed, keyboard-focus, disabled and busy states.
- The account row, placed in the card's free space below the last Figma row so
  every Figma element keeps its position.
- Mods, Packs and Other tabs, list empty states, the status toast in the bottom
  margin and the sign-in dialog.
- LAUNCH in `atw-config` saves settings and starts the package's `atw-launch`
  (sign-in and launch stay there). While it runs, the button shows a busy state
  and the Logs tab tails its logs. With "Launcher Open after Launch" off, the
  config window closes instead. The legacy launcher keeps its in-process
  LAUNCH/CLOSE behaviour.
- Inputs stretch to the card's content width (Figma fixes them at 488 px, 4 px
  short of the toggle and value column).

## Responsive behaviour

The window opens at the frame size, centred and clamped to the screen's work
area. It can be resized from any edge (native resize on Windows; manual
fallback on macOS): tabs share the tray width, LAUNCH stays centred, the JVM
box absorbs extra height, and pages scroll when the window is shorter than the
frame. Ctrl+1 to Ctrl+4 (Cmd on macOS) select tabs; Ctrl+W/Cmd+W closes.

## Checking changes

Build `atw-config` and `atw-launch`, then compare the Game tab against Frame 21
at 100% scale: surface edges should land on the y positions above. Exercise
tabs, the slider, toggles, both path pickers (cancel), the account button
(cancel the sign-in dialog), Mods row selection/reordering and LAUNCH in a
scratch package without account files. UI changes do not need game launches.
