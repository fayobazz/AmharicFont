# Place your font here

Filename must be exactly:  fictional_font.ttf

The keyboard loads it via:
  Typeface.createFromAsset(context.getAssets(), "fonts/fictional_font.ttf")

If absent → safe fallback to system sans-serif (all Unicode still renders).
