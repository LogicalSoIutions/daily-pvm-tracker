# Daily PvM Tracker

Daily PvM Tracker keeps a private, character-specific history of boss and raid activity. Boss completions
are recorded live from RuneLite kill-count messages and Loot Tracker events, then presented in a
RuneLite side panel. Boss and raid loot seen by RuneLite's built-in Loot Tracker is recorded locally
with its Grand Exchange value, falling back to High Alchemy value when no GE price is available.
Ironman users can instead select **Use HA prices only** in the plugin settings.

Data is stored in the `.runelite/pvm-raid-daily-tracker/` directory by default.
Users may explicitly opt in to uploading their complete character snapshot to PvM Hub; the setting
is disabled by default and warns that the upload submits their IP address to a third-party server.
Enabled uploads are sent at
most once every 5 minutes. An asynchronous hiscore lookup at login establishes the absolute KC
baseline. Daily kill totals include only completions observed locally by the plugin; hiscore changes
while the plugin was closed are not added to the timeline.

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

Each locally observed raid completion also records the contribution data RuneLite exposes. Chambers
stores personal and team points; Tombs stores personal reward points, loot-eligible points after the
starting 5,000-point deduction, and raid level. Theatre completion records do not fabricate contribution
points that RuneLite does not expose.

The overview includes a **Save data** button. It opens a system save dialog with a character-specific
JSON filename, allowing the user to open the snapshot through PvM Hub's private local-file viewer.
Saving a copy does not enable website uploads or change the plugin's stored history.

Each observed completion creates a durable kill-log entry immediately, before loot is required. A later
Loot Tracker event fills the oldest unlooted completion for the matching boss or raid, so delayed rewards
such as an instanced Maggot King loot claim remain attached to the original kill even across a long delay.
Raid metadata carries the kill entry's stable ID. If RuneLite reports loot before the KC message, the
plugin merges the two records when that message arrives instead of creating a duplicate. PvM Hub uses
these itemized entries for the click-through kill history on each boss card.

## PvM Hub website

[PvM Hub](https://osrs.pvm-hub.com) turns Daily PvM Tracker data into a browser dashboard with
7-day, 30-day, and all-time views. It summarizes boss kills, active days, estimated loot, confirmed
value, and the difference between estimates and actual returns. The dashboard also includes a daily
loot chart, recent activity, and a searchable boss-by-boss breakdown.

There are two ways to view tracker data on the website:

- **Private local file:** Use **Save data** in the plugin, then choose **View a local file** on PvM
  Hub. The file is read only in the current browser tab; it is not uploaded or stored by the website
  and disappears from the page when that local view is closed.
- **Automatic profile updates:** Enable **Upload to PvM Hub (optional)** in the plugin settings to
  send the character's complete snapshot at most once every 5 minutes. This creates or updates a
  website profile that can be found by searching for the character's RuneScape name. This mode sends
  the snapshot and IP address to a third-party server and is disabled by default.

## Estimated and confirmed GP

Estimated GP is the Grand Exchange value when loot is captured, plus any manually entered split.
Confirmed GP is stricter: it is added when RuneLite observes either a Grand Exchange sell fill or a
successful High Level Alchemy cast for an outstanding item previously captured from a boss or raid.
Grand Exchange confirmations use the coins actually collected after the 2% per-item tax, including
the game's round-down behavior and 5 million coin tax cap per item.
The confirmation method and its quantity/value are retained per item. Sell-slot progress is stored per character,
so partial or completed sales observed after a later login are matched back to the original loot day.
Matching uses oldest outstanding loot first and never confirms more units than the tracker recorded.
Because a manually entered split is raw GP already received, it contributes to confirmed GP as well.
Users can explicitly delete a split, captured loot for one boss and day, or all saved GP data for a
day. These deletion actions do not alter locally recorded KC.

Right-clicking a captured item hides it from the selected boss or raid only. The rule applies across
that boss's history and future drops, and hidden items contribute neither estimated nor confirmed GP
in the plugin or uploaded PvM Hub snapshot. **Manage hidden loot** in the boss view restores items.

When RuneLite's Loot Tracker reports a supported intermediate-item reveal, such as polishing a
Maggot King `Tarnished` item, Daily PvM Tracker replaces the oldest matching intermediate loot unit
with the revealed item on its original boss and day. The revealed item's current GE estimate and
later sale confirmation therefore remain attached to the kill that produced it.

There is no manual sync action and no scheduled hiscore polling. KC and loot update from local
RuneLite events while the client is running. Hiscores are checked once per login only as a fallback;
a lagging hiscore response never reduces locally observed totals. Unless PvM Hub uploads are
explicitly enabled, no tracker snapshot is sent to the website.
