# Daily PvM Tracker

Daily PvM Tracker keeps a private, character-specific history of boss and raid activity. Boss completions
are recorded live from RuneLite kill-count messages and Loot Tracker events, then presented in a
RuneLite side panel. Boss and raid loot seen by RuneLite's built-in Loot Tracker is recorded locally
with its Grand Exchange value.

Data is stored only in the legacy-compatible `.runelite/pvm-raid-daily-tracker/` directory by default.
Users may explicitly opt in to uploading their complete character snapshot to PvM Hub; the setting
is disabled by default and
warns that the upload submits their IP address to a third-party server. Enabled uploads are sent at
most once every 30 minutes. An asynchronous hiscore lookup at login establishes or validates
absolute KC totals. When hiscores reveal activity missed while the plugin was closed, the difference
is marked as recovered and shown as a multi-day interval when necessary.

## Coverage

The boss catalog is discovered from every entry RuneLite marks as `HiscoreSkillType.BOSS`; this
automatically adopts new entries when RuneLite adds them. Raid
coverage includes Chambers of Xeric and Challenge Mode, Theatre of Blood and Hard Mode, and Tombs of
Amascut and Expert Mode. Loot uses the same catalog plus aliases for RuneLite reward names such as
Barrows, Lunar Chest, Fortis Colosseum, Gauntlet Hunlleffs, The Mimic, and the Royal Titans.

Clicking a day opens its boss and raid activity. Clicking a boss shows the interval KC change, KC at
the start and end of the interval, captured loot, and any manual GP adjustment. Manual adjustments
are useful for raid or shared-boss splits and are added to captured loot without replacing it. The
day view also provides the complete boss catalog so a split can be recorded when no personal drop
was captured.

The overview includes a **Save data** button. It opens a system save dialog with a character-specific
JSON filename, allowing the user to open the snapshot through PvM Hub's private local-file viewer.
Saving a copy does not enable website uploads or change the plugin's stored history.

## Estimated and confirmed GP

Estimated GP is the Grand Exchange value when loot is captured, plus any manually entered split.
Confirmed GP is stricter: it is added only when RuneLite observes a Grand Exchange sell fill for an
outstanding item previously captured from a boss or raid. Sell-slot progress is stored per character,
so partial or completed sales observed after a later login are matched back to the original loot day.
Matching uses oldest outstanding loot first and never confirms more units than the tracker recorded.
Because a manually entered split is raw GP already received, it contributes to confirmed GP as well.
Users can explicitly delete a split, captured loot for one boss and day, or all saved GP data for a
day. These deletion actions do not alter locally recorded KC.

When RuneLite's Loot Tracker reports a supported intermediate-item reveal, such as polishing a
Maggot King `Tarnished` item, Daily PvM Tracker replaces the oldest matching intermediate loot unit with the
revealed item on its original boss and day. The revealed item's current GE estimate and later sale
confirmation therefore remain attached to the kill that produced it.

There is no manual sync action and no scheduled hiscore polling. KC and loot update from local
RuneLite events while the client is running. Hiscores are checked once per login only as a fallback;
a lagging hiscore response never reduces locally observed totals. Unless PvM Hub uploads are
explicitly enabled, no tracker snapshot is sent to the website.
