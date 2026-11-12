# Agent notes: branding

Read [README.md](README.md) in this folder first. It has the logo anatomy,
palette, type, the sizes the game needs, and the proposal workflow.

- New design work goes in `branding/proposals/<yyyy-mm-dd>-<short-name>/`.
  Leave `res/`, `scripts/generate_branding.py` and the shipped textures alone
  until the owner approves a proposal.
- Start from the existing vectors: `logo/atw-icon.svg` and the ATW paths in
  `res/ui/window/label-atw.svg`. Don't retype ATW in a font.
- Colours come from `colors/palette.json`. Introduce a new colour only as part
  of a proposal, and say which token it would replace or extend.
- Deliver SVG masters plus PNG previews on `#252526` and on white, at 16, 32
  and 256 px for icons.
- After changing anything the kit is built from, run
  `python branding/tools/build_kit.py` and look at the regenerated banners and
  wordmarks before committing.
- The repository is public. Check every image for account names, emails,
  tokens and personal file paths before adding it. Never commit files from
  `build/`, `runtime/` or a player's game folder other than vetted screenshots.
- Shipping an approved change to the launcher or game is covered by
  [docs/BRANDING.md](../docs/BRANDING.md), including the real-launch checks.
