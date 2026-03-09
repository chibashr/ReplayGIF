# Entity sprite diagnostic (1.18–1.21)

Same philosophy as block colors (`.planning/decisions.md`): entity type → sprite/marker
are data (entity_sprites_default/ + entity_bounds.json), not code. Unknown types get
a fallback (gray marker). The diagnostic (`/replaygif diag`) reports living entity
types that have neither a bundled sprite nor a configured marker color in
entity_bounds.json.

## Gaps resolved for 1.19–1.21 new mobs

### Hostile (bundled stand-in sprites)

These commonly cause player deaths and therefore have **bundled sprites** in
`entity_sprites_default/` (simple geometric placeholders; replace with proper art when available):

| Entity   | Version | File          | Why sprite |
|----------|---------|---------------|------------|
| WARDEN   | 1.19    | warden.png    | Boss-level hostile, common death cause in Deep Dark |
| BREEZE   | 1.21    | breeze.png    | Trial Chambers hostile, wind charges cause deaths |
| BOGGED   | 1.21    | bogged.png    | Skeleton variant, ranged hostile |

### Passive / neutral (marker color only)

Unlikely to be the direct cause of death in replay context; **marker color** in
`entity_bounds.json` is sufficient:

| Entity    | Version | Color (entity_bounds.json) | Why marker only |
|-----------|---------|----------------------------|------------------|
| ALLAY     | 1.19    | #8B9DC3                    | Passive helper |
| FROG      | 1.19    | #3DD68C                    | Passive |
| TADPOLE   | 1.19    | #2D5016                    | Passive |
| CAMEL     | 1.20    | #C19A6B                    | Neutral mount |
| SNIFFER   | 1.20    | #8B6914                    | Passive |
| ARMADILLO | 1.21    | #8B7355                    | Passive (1.21; note 1.24 in request was typo) |

### Projectiles / non-living

Wind Charge and other projectiles are not living entities; they are not included
in the living-entity diagnostic. If they appear in snapshots they use the generic
marker until we add explicit handling.

## Verification

- **Zero hostile-mob gaps on 1.21.4:** Warden, Breeze, and Bogged have bundled
  sprites; the diagnostic reports no living-entity gaps for those types.
- **Passive/neutral:** Allay, Frog, Tadpole, Camel, Sniffer, Armadillo have
  a `color` entry in entity_bounds.json and are not in the gap list.
