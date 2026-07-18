package com.pvmtracker;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JComponent;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

final class DailyPvmTrackerPanel extends PluginPanel
{
	private static final Color BACKGROUND = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color SURFACE = ColorScheme.DARK_GRAY_COLOR;
	private static final Color SURFACE_HOVER = ColorScheme.MEDIUM_GRAY_COLOR;
	private static final Color TEXT = ColorScheme.TEXT_COLOR;
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
	private static final Color VALUE = ColorScheme.GRAND_EXCHANGE_PRICE;
	private static final Color ACCENT = new Color(246, 194, 76);
	private static final Color ERROR = new Color(225, 90, 90);
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, MMM d");
	private static final String ESTIMATED_GP_TOOLTIP = "Estimated GP values captured loot at current Grand Exchange prices, or High Alchemy prices when Use HA prices only is enabled. Items do not need to be sold.";
	private static final String CONFIRMED_GP_TOOLTIP = "Confirmed GP is the actual value realized from captured loot the plugin has matched to completed Grand Exchange sales (after tax) or High Alchemy. It also includes item values and splits you entered manually; unsold loot is not included.";

	private final Actions actions;
	private final CardLayout pages = new CardLayout();
	private final JPanel pageContainer = new JPanel(pages);
	private final JPanel timeline = verticalPanel();
	private final JLabel accountLabel = label("Waiting for login", 12, MUTED);
	private final JTextArea statusLabel = wrappedText("Log in to begin local tracking.", 10, MUTED, 2);
	private final JButton saveDataButton = new JButton("Save data");
	private JScrollPane detailPage;
	private List<DailySummary> summaries = Collections.emptyList();
	private View view = View.OVERVIEW;
	private LocalDate selectedDate;
	private String selectedBoss;

	DailyPvmTrackerPanel()
	{
		this(new Actions()
		{
			@Override
			public void saveSplit(LocalDate date, String boss, long value)
			{
			}

			@Override
			public void deleteBossLoot(LocalDate date, String boss)
			{
			}

			@Override
			public void deleteDayGp(LocalDate date)
			{
			}

			@Override
			public void saveData(Path destination)
			{
			}

			@Override
			public void setLootHidden(String boss, int itemId, boolean hidden)
			{
			}

			@Override
			public void setLootKept(LocalDate date, String boss, int itemId, boolean kept)
			{
			}

			@Override
			public void setConfirmedValueOverride(LocalDate date, String boss, int itemId, Long value)
			{
			}

		});
	}

	DailyPvmTrackerPanel(Actions actions)
	{
		super(false);
		this.actions = actions;
		setLayout(new BorderLayout());
		setBackground(BACKGROUND);
		pageContainer.setBackground(BACKGROUND);
		pageContainer.add(buildOverview(), "overview");
		add(pageContainer, BorderLayout.CENTER);
	}

	void update(String characterName, String status, List<DailySummary> summaries)
	{
		this.summaries = summaries == null ? Collections.emptyList() : summaries;
		accountLabel.setText(characterName == null || characterName.isEmpty() ? "Waiting for login" : characterName);
		saveDataButton.setEnabled(characterName != null && !characterName.isEmpty());
		statusLabel.setText(status == null ? "" : status);
		rebuildTimeline();

		DailySummary selected = findSummary(selectedDate);
		if (selected == null || view == View.OVERVIEW)
		{
			view = View.OVERVIEW;
			pages.show(pageContainer, "overview");
		}
		else if (view == View.BOSS && selectedBoss != null)
		{
			showBoss(selected, selectedBoss);
		}
		else
		{
			showDay(selected);
		}
	}

	private JScrollPane buildOverview()
	{
		JPanel content = verticalPanel();
		content.setBorder(BorderFactory.createEmptyBorder(10, 9, 14, 9));

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(BACKGROUND);
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.BORDER_COLOR),
			BorderFactory.createEmptyBorder(7, 4, 11, 4)));
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
		JLabel title = label("Daily PvM Tracker", 16, TEXT);
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		header.add(title);
		header.add(Box.createVerticalStrut(4));
		header.add(accountLabel);
		header.add(Box.createVerticalStrut(4));
		header.add(statusLabel);
		content.add(header);
		content.add(Box.createVerticalStrut(13));

		JPanel export = new JPanel();
		export.setLayout(new BoxLayout(export, BoxLayout.Y_AXIS));
		export.setBackground(SURFACE);
		export.setBorder(BorderFactory.createEmptyBorder(11, 11, 11, 11));
		JLabel exportTitle = label("View your data privately", 11, TEXT);
		exportTitle.setFont(exportTitle.getFont().deriveFont(Font.BOLD));
		export.add(exportTitle);
		export.add(Box.createVerticalStrut(4));
		export.add(wrappedText("Save a JSON copy to open locally on PvM Hub.", 9, MUTED, 2));
		export.add(Box.createVerticalStrut(8));
		saveDataButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		saveDataButton.setFocusPainted(false);
		saveDataButton.setEnabled(false);
		saveDataButton.setName("save-data-button");
		saveDataButton.addActionListener(event -> chooseSaveDestination());
		export.add(saveDataButton);
		fillWidth(export);
		content.add(export);
		content.add(Box.createVerticalStrut(13));

		JPanel about = new JPanel();
		about.setLayout(new BoxLayout(about, BoxLayout.Y_AXIS));
		about.setBackground(SURFACE);
		about.setBorder(BorderFactory.createEmptyBorder(11, 11, 11, 11));
		about.setName("pvm-hub-info");
		JLabel aboutTitle = label("How PvM Hub works", 11, TEXT);
		aboutTitle.setFont(aboutTitle.getFont().deriveFont(Font.BOLD));
		about.add(aboutTitle);
		about.add(Box.createVerticalStrut(4));
		JTextArea aboutText = wrappedText("Your boss kills, loot, and GP are tracked locally on this computer. Save a private file above, or enable Upload to PvM-Hub.com in the plugin settings to keep your web profile updated automatically.\n\nHover over Estimated and Confirmed GP to see how each value is calculated. In a boss view, right-click captured loot to mark it kept, edit its confirmed value, or hide it from totals.", 9, MUTED, 9);
		aboutText.setName("pvm-hub-info-text");
		about.add(aboutText);
		fillWidth(about);
		content.add(about);
		content.add(Box.createVerticalStrut(13));

		JLabel recent = sectionLabel("Recent activity");
		recent.setBorder(BorderFactory.createEmptyBorder(0, 2, 6, 0));
		content.add(recent);
		content.add(timeline);
		content.add(Box.createVerticalGlue());
		return scroll(content);
	}

	private void chooseSaveDestination()
	{
		String character = accountLabel.getText();
		String safeCharacter = character.replaceAll("[^A-Za-z0-9 _-]", "").trim().replace(' ', '-');
		if (safeCharacter.isEmpty())
		{
			safeCharacter = "character";
		}
		JFileChooser chooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
		chooser.setDialogTitle("Save Daily PvM Tracker data");
		chooser.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));
		chooser.setSelectedFile(new java.io.File(chooser.getCurrentDirectory(),
			"daily-pvm-tracker-" + safeCharacter + "-" + LocalDate.now() + ".json"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		Path destination = chooser.getSelectedFile().toPath();
		if (!destination.getFileName().toString().toLowerCase().endsWith(".json"))
		{
			destination = destination.resolveSibling(destination.getFileName() + ".json");
		}
		if (Files.exists(destination) && JOptionPane.showConfirmDialog(this,
			"Replace " + destination.getFileName() + "?", "Daily PvM Tracker",
			JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION)
		{
			return;
		}
		actions.saveData(destination);
	}

	void showSaveResult(Path destination, boolean successful)
	{
		String message = successful
			? "Saved " + destination.getFileName() + ".\nOpen PvM Hub and choose View a local file."
			: "Daily PvM Tracker could not save that file.";
		JOptionPane.showMessageDialog(this, message, "Daily PvM Tracker",
			successful ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
	}

	private void rebuildTimeline()
	{
		timeline.removeAll();
		if (summaries.isEmpty())
		{
			JPanel empty = new JPanel(new BorderLayout());
			empty.setBackground(SURFACE);
			empty.setBorder(BorderFactory.createEmptyBorder(18, 13, 18, 13));
			empty.add(label("<html><div style='width:170px;text-align:center'>Boss and raid completions will appear here as RuneLite observes them.</div></html>", 11, MUTED));
			timeline.add(empty);
		}
		else
		{
			for (DailySummary summary : summaries)
			{
				timeline.add(dayCard(summary));
				timeline.add(Box.createVerticalStrut(7));
			}
		}
		timeline.revalidate();
		timeline.repaint();
	}

	private JPanel dayCard(DailySummary summary)
	{
		ClickablePanel card = new ClickablePanel();
		card.setLayout(new BorderLayout(7, 0));
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR),
			BorderFactory.createEmptyBorder(10, 11, 10, 10)));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));

		JPanel left = transparentVerticalPanel();
		String dayTitle = summary.date.format(DAY);
		if (summary.intervalDays() > 1)
		{
			dayTitle += " · " + summary.intervalDays() + " days";
		}
		JLabel date = label(dayTitle, 12, TEXT);
		date.setFont(date.getFont().deriveFont(Font.BOLD));
		left.add(date);
		left.add(Box.createVerticalStrut(3));
		String activity = summary.completed ? summary.totalKills + " kc gained"
			: summary.totalKills > 0 ? summary.totalKills + " kc so far" : "Tracking locally";
		if (summary.totalRecoveredKills > 0)
		{
			activity += " · " + summary.totalRecoveredKills + " recovered";
		}
		left.add(label(activity, 10, MUTED));

		JPanel right = transparentVerticalPanel();
		JLabel estimated = label("Est. " + formatGp(summary.totalValue), 10, TEXT);
		estimated.setToolTipText(ESTIMATED_GP_TOOLTIP);
		estimated.setName("estimated-gp");
		estimated.setHorizontalAlignment(SwingConstants.RIGHT);
		JLabel confirmed = label("Confirmed " + formatGp(summary.confirmedValue), 10, VALUE);
		confirmed.setToolTipText(CONFIRMED_GP_TOOLTIP);
		confirmed.setName("confirmed-gp");
		confirmed.setFont(confirmed.getFont().deriveFont(Font.BOLD));
		confirmed.setHorizontalAlignment(SwingConstants.RIGHT);
		right.add(estimated);
		right.add(Box.createVerticalStrut(3));
		right.add(confirmed);
		card.add(left, BorderLayout.CENTER);
		card.add(right, BorderLayout.EAST);
		card.onClick(() -> showDay(summary));
		card.setName("day-card");
		fillWidth(card);
		return card;
	}

	private void showDay(DailySummary summary)
	{
		view = View.DAY;
		selectedDate = summary.date;
		selectedBoss = null;
		JPanel detail = detailContainer();
		detail.add(backButton("Back to timeline", () ->
		{
			view = View.OVERVIEW;
			selectedDate = null;
			pages.show(pageContainer, "overview");
		}));
		detail.add(dayHeader(summary));
		detail.add(Box.createVerticalStrut(15));
		detail.add(sectionLabel("Bosses and raids"));
		detail.add(Box.createVerticalStrut(6));

		List<String> bosses = bossesFor(summary);
		if (bosses.isEmpty())
		{
			detail.add(emptyRow(summary.completed ? "No boss or raid activity" : "Waiting for a local completion"));
		}
		else
		{
		for (String boss : bosses)
			{
				detail.add(bossRow(summary, boss));
				detail.add(Box.createVerticalStrut(2));
			}
		}
		detail.add(Box.createVerticalStrut(15));
		detail.add(splitPicker(summary));
		if (!summary.loot.isEmpty() || !summary.manualAdjustments.isEmpty())
		{
			detail.add(Box.createVerticalStrut(10));
			detail.add(deleteButton("Delete saved GP for this day", () ->
			{
				if (confirm("Delete all captured loot and splits for " + summary.date.format(DAY) + "?"))
				{
					actions.deleteDayGp(summary.date);
				}
			}));
		}
		renderDetail(detail);
	}

	private JPanel splitPicker(DailySummary summary)
	{
		JPanel picker = new JPanel();
		picker.setLayout(new BoxLayout(picker, BoxLayout.Y_AXIS));
		picker.setBackground(SURFACE);
		picker.setBorder(BorderFactory.createEmptyBorder(10, 11, 11, 11));
		JLabel title = label("Add a split", 11, TEXT);
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		picker.add(title);
		picker.add(Box.createVerticalStrut(4));
		picker.add(label("Choose any boss, even if no loot was captured.", 9, MUTED));
		picker.add(Box.createVerticalStrut(8));
		JComboBox<String> bosses = new JComboBox<>(BossRegistry.bosses().toArray(new String[0]));
		bosses.setMaximumSize(new Dimension(Integer.MAX_VALUE, 27));
		bosses.setAlignmentX(Component.LEFT_ALIGNMENT);
		picker.add(bosses);
		picker.add(Box.createVerticalStrut(7));
		JButton open = new JButton("Open boss");
		open.setAlignmentX(Component.LEFT_ALIGNMENT);
		open.setFocusPainted(false);
		open.addActionListener(event ->
		{
			String boss = (String) bosses.getSelectedItem();
			if (boss != null)
			{
				showBoss(summary, boss);
			}
		});
		picker.add(open);
		picker.setName("split-picker");
		fillWidth(picker);
		return picker;
	}

	private JPanel dayHeader(DailySummary summary)
	{
		JPanel header = new JPanel(new BorderLayout(8, 0));
		header.setBackground(SURFACE);
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR),
			BorderFactory.createEmptyBorder(12, 12, 12, 11)));
		JPanel left = transparentVerticalPanel();
		JLabel date = label(summary.date.format(DAY), 15, TEXT);
		date.setFont(date.getFont().deriveFont(Font.BOLD));
		left.add(date);
		left.add(Box.createVerticalStrut(4));
		left.add(label(summary.intervalDays() > 1 ? summary.intervalDays() + " day recovered interval" : "Calendar day", 9, MUTED));
		JPanel right = transparentVerticalPanel();
		JLabel estimated = label("Est. " + formatGp(summary.totalValue), 10, TEXT);
		estimated.setToolTipText(ESTIMATED_GP_TOOLTIP);
		estimated.setHorizontalAlignment(SwingConstants.RIGHT);
		JLabel confirmed = label("Confirmed " + formatGp(summary.confirmedValue), 10, VALUE);
		confirmed.setToolTipText(CONFIRMED_GP_TOOLTIP);
		confirmed.setFont(confirmed.getFont().deriveFont(Font.BOLD));
		confirmed.setHorizontalAlignment(SwingConstants.RIGHT);
		right.add(estimated);
		right.add(Box.createVerticalStrut(3));
		right.add(confirmed);
		header.add(left, BorderLayout.CENTER);
		header.add(right, BorderLayout.EAST);
		fillWidth(header);
		return header;
	}

	private JPanel bossRow(DailySummary summary, String boss)
	{
		ClickablePanel row = new ClickablePanel();
		row.setLayout(new BorderLayout(7, 0));
		row.setBorder(BorderFactory.createEmptyBorder(9, 11, 9, 9));
		JPanel left = transparentVerticalPanel();
		JLabel name = label("<html><div style='width:122px'>" + escape(boss) + "</div></html>", 11, TEXT);
		name.setFont(name.getFont().deriveFont(Font.BOLD));
		left.add(name);
		left.add(Box.createVerticalStrut(2));
		int kills = summary.kills.getOrDefault(boss, 0);
		int recovered = summary.recoveredKills.getOrDefault(boss, 0);
		left.add(label(kills + " kc" + (summary.completed ? " gained" : " so far")
			+ (recovered > 0 ? " · " + recovered + " recovered" : ""), 9, MUTED));
		long value = summary.bossTrackedValue(boss) + summary.bossAdjustment(boss);
		JPanel amounts = transparentVerticalPanel();
		JLabel amount = label("Est. " + formatGp(value) + "  ›", 9, value == 0 ? MUTED : TEXT);
		amount.setToolTipText(ESTIMATED_GP_TOOLTIP);
		amount.setHorizontalAlignment(SwingConstants.RIGHT);
		JLabel confirmed = label("Conf. " + formatGp(summary.bossConfirmedValue(boss)), 9, VALUE);
		confirmed.setToolTipText(CONFIRMED_GP_TOOLTIP);
		confirmed.setHorizontalAlignment(SwingConstants.RIGHT);
		amounts.add(amount);
		amounts.add(Box.createVerticalStrut(2));
		amounts.add(confirmed);
		row.add(left, BorderLayout.CENTER);
		row.add(amounts, BorderLayout.EAST);
		row.setName("boss-row");
		row.onClick(() -> showBoss(summary, boss));
		fillWidth(row);
		return row;
	}

	private void showBoss(DailySummary summary, String boss)
	{
		view = View.BOSS;
		selectedDate = summary.date;
		selectedBoss = boss;
		JPanel detail = detailContainer();
		detail.add(backButton("Back to " + summary.date.format(DAY), () -> showDay(summary)));

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(SURFACE);
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR),
			BorderFactory.createEmptyBorder(12, 12, 12, 11)));
		JLabel bossName = label("<html><div style='width:178px'>" + escape(boss) + "</div></html>", 14, TEXT);
		bossName.setFont(bossName.getFont().deriveFont(Font.BOLD));
		header.add(bossName);
		header.add(Box.createVerticalStrut(3));
		header.add(label(summary.date.format(DAY), 9, MUTED));
		fillWidth(header);
		detail.add(header);

		detail.add(Box.createVerticalStrut(15));
		detail.add(sectionLabel("Kill counts"));
		detail.add(Box.createVerticalStrut(6));
		detail.add(metricRow(summary.intervalDays() > 1 ? "Gained this interval" : "Gained this day",
			Integer.toString(summary.kills.getOrDefault(boss, 0)), TEXT));
		int recovered = summary.recoveredKills.getOrDefault(boss, 0);
		if (recovered > 0)
		{
			detail.add(metricRow("Recovered from hiscores", Integer.toString(recovered), MUTED));
		}
		detail.add(metricRow("KC at start", formatCount(summary.startingKillCounts.get(boss)), TEXT));
		detail.add(metricRow("KC at end", formatCount(summary.endingKillCounts.get(boss)), TEXT));

		DailySummary.RaidSummary raid = summary.findRaid(boss);
		if (raid != null)
		{
			detail.add(Box.createVerticalStrut(15));
			detail.add(sectionLabel("Raid details"));
			detail.add(Box.createVerticalStrut(6));
			detail.add(metricRow("Recorded raids", Integer.toString(raid.completions), TEXT));
			if (raid.pointRecords > 0)
			{
				detail.add(metricRow("Personal points", String.format("%,d", raid.personalPoints), TEXT));
				detail.add(metricRow("Average points", String.format("%,.0f",
					raid.personalPoints / (double) raid.pointRecords), TEXT));
				if (boss.startsWith("Tombs of Amascut"))
				{
					detail.add(metricRow("Loot-eligible points", String.format("%,d", raid.lootPoints), TEXT));
				}
			}
			else
			{
				detail.add(metricRow("Contribution points", "Hidden by Theatre", MUTED));
			}
			if (raid.teamPointRecords > 0)
			{
				detail.add(metricRow("Team points", String.format("%,d", raid.teamPoints), TEXT));
			}
			if (raid.minimumRaidLevel != null)
			{
				String levels = raid.minimumRaidLevel.equals(raid.maximumRaidLevel)
					? raid.minimumRaidLevel.toString() : raid.minimumRaidLevel + "–" + raid.maximumRaidLevel;
				detail.add(metricRow("Raid level", levels, TEXT));
			}
		}

		long tracked = summary.bossTrackedValue(boss);
		long adjustment = summary.bossAdjustment(boss);
		detail.add(Box.createVerticalStrut(15));
		detail.add(sectionLabel("GP breakdown"));
		detail.add(Box.createVerticalStrut(6));
		detail.add(metricRow("Estimated loot", formatGp(tracked), TEXT));
		long keptValue = lootKeptValue(summary.findLoot(boss));
		detail.add(metricRow("Confirmed returns", formatGp(summary.bossConfirmedSaleValue(boss)), VALUE));
		detail.add(metricRow("Kept value", formatGp(keptValue), keptValue == 0 ? MUTED : VALUE));
		detail.add(metricRow("Confirmed split", formatSignedGp(adjustment), adjustment == 0 ? MUTED : VALUE));
		detail.add(metricRow("Estimated total", formatGp(tracked + adjustment), TEXT));
		detail.add(metricRow("Confirmed total", formatGp(summary.bossConfirmedValue(boss)), VALUE));

		DailySummary.LootSummary loot = summary.findLoot(boss);
		if (loot != null && (!loot.items.isEmpty() || !loot.hiddenItems.isEmpty()))
		{
			detail.add(Box.createVerticalStrut(15));
			detail.add(sectionLabel("Captured items"));
			detail.add(Box.createVerticalStrut(6));
			for (DailySummary.ItemSummary item : loot.items)
			{
				String methods = item.confirmedValue == 0 ? ""
					: "GE " + formatGp(item.geConfirmedValue) + " · HA " + formatGp(item.alchConfirmedValue);
				detail.add(itemRow(summary.date, boss, item.quantity + " × " + item.name,
					"Est. " + formatGp(item.value) + " · Conf. " + formatGp(item.confirmedValue), methods,
					item));
			}
			if (!loot.items.isEmpty())
			{
				detail.add(Box.createVerticalStrut(5));
				JLabel contextMenuTip = label("Tip: Right-click an item for more options.", 9, MUTED);
				contextMenuTip.setName("loot-context-menu-tip");
				detail.add(contextMenuTip);
			}
			if (!loot.hiddenItems.isEmpty())
			{
				detail.add(Box.createVerticalStrut(6));
				detail.add(hiddenLootButton(boss, loot.hiddenItems));
			}
			detail.add(Box.createVerticalStrut(8));
			detail.add(deleteButton("Delete captured loot", () ->
			{
				if (confirm("Delete captured loot for " + boss + " on " + summary.date.format(DAY) + "?"))
				{
					actions.deleteBossLoot(summary.date, boss);
				}
			}));
		}

		detail.add(Box.createVerticalStrut(15));
		detail.add(adjustmentEditor(summary, boss, adjustment));
		renderDetail(detail);
	}

	private JPanel adjustmentEditor(DailySummary summary, String boss, long adjustment)
	{
		JPanel editor = new JPanel();
		editor.setLayout(new BoxLayout(editor, BoxLayout.Y_AXIS));
		editor.setBackground(SURFACE);
		editor.setBorder(BorderFactory.createEmptyBorder(11, 11, 11, 11));
		JLabel title = label("Confirmed split", 11, TEXT);
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		editor.add(title);
		editor.add(Box.createVerticalStrut(4));
		editor.add(label("Raw GP received from a raid or shared boss split.", 9, MUTED));
		editor.add(Box.createVerticalStrut(8));

		JTextField input = new JTextField(adjustment == 0 ? "" : Long.toString(adjustment));
		input.setMaximumSize(new Dimension(Integer.MAX_VALUE, 27));
		input.setBackground(ColorScheme.CONTROL_COLOR);
		input.setForeground(TEXT);
		input.setCaretColor(TEXT);
		input.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR),
			BorderFactory.createEmptyBorder(4, 6, 4, 6)));
		editor.add(input);
		editor.add(Box.createVerticalStrut(4));
		JLabel feedback = label("Examples: 12.5m, 750k, -200k · blank clears", 9, MUTED);
		editor.add(feedback);
		editor.add(Box.createVerticalStrut(8));
		JButton save = new JButton("Save adjustment");
		save.setAlignmentX(Component.LEFT_ALIGNMENT);
		save.setFocusPainted(false);
		save.addActionListener(event ->
		{
			try
			{
				long value = parseGp(input.getText());
				feedback.setForeground(VALUE);
				feedback.setText("Saved " + formatSignedGp(value));
				actions.saveSplit(summary.date, boss, value);
			}
			catch (IllegalArgumentException ex)
			{
				feedback.setForeground(ERROR);
				feedback.setText("Enter GP like 1250000, 1.25m, or 750k");
			}
		});
		editor.add(save);
		if (adjustment != 0)
		{
			editor.add(Box.createVerticalStrut(6));
			editor.add(deleteButton("Delete split", () ->
			{
				if (confirm("Delete this confirmed split?"))
				{
					actions.saveSplit(summary.date, boss, 0L);
				}
			}));
		}
		fillWidth(editor);
		return editor;
	}

	private List<String> bossesFor(DailySummary summary)
	{
		Set<String> names = new LinkedHashSet<>(summary.kills.keySet());
		for (DailySummary.LootSummary loot : summary.loot)
		{
			names.add(loot.source);
		}
		for (DailySummary.RaidSummary raid : summary.raids)
		{
			names.add(raid.source);
		}
		names.addAll(summary.manualAdjustments.keySet());
		List<String> result = new ArrayList<>(names);
		result.sort(Comparator
			.comparingInt((String boss) -> summary.kills.getOrDefault(boss, 0)).reversed()
			.thenComparingLong(boss -> -(summary.bossTrackedValue(boss) + summary.bossAdjustment(boss)))
			.thenComparing(String::compareTo));
		return result;
	}

	private DailySummary findSummary(LocalDate date)
	{
		if (date == null)
		{
			return null;
		}
		return summaries.stream().filter(summary -> summary.date.equals(date)).findFirst().orElse(null);
	}

	private void renderDetail(JPanel content)
	{
		if (detailPage != null)
		{
			pageContainer.remove(detailPage);
		}
		detailPage = scroll(content);
		pageContainer.add(detailPage, "detail");
		pageContainer.revalidate();
		pages.show(pageContainer, "detail");
	}

	private JPanel detailContainer()
	{
		JPanel panel = verticalPanel();
		panel.setBorder(BorderFactory.createEmptyBorder(8, 9, 16, 9));
		return panel;
	}

	private JButton backButton(String text, Runnable action)
	{
		JButton back = new JButton("‹  " + text);
		back.setForeground(MUTED);
		back.setBackground(BACKGROUND);
		back.setBorder(BorderFactory.createEmptyBorder(5, 1, 10, 1));
		back.setFocusPainted(false);
		back.setHorizontalAlignment(SwingConstants.LEFT);
		back.setAlignmentX(Component.LEFT_ALIGNMENT);
		back.setName("back-button");
		back.addActionListener(event -> action.run());
		return back;
	}

	private JButton deleteButton(String text, Runnable action)
	{
		JButton button = new JButton(text);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setName("delete-button");
		button.setForeground(ERROR);
		button.setFocusPainted(false);
		button.addActionListener(event -> action.run());
		return button;
	}

	private boolean confirm(String message)
	{
		return JOptionPane.showConfirmDialog(this, message, "Daily PvM Tracker",
			JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
	}

	private JPanel metricRow(String name, String value, Color valueColor)
	{
		JPanel row = new JPanel(new BorderLayout(7, 0));
		row.setBackground(SURFACE);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.BORDER_COLOR),
			BorderFactory.createEmptyBorder(9, 10, 9, 10)));
		JLabel metricName = label(name, 10, MUTED);
		JLabel amount = label(value, 10, valueColor);
		String tooltip = name.startsWith("Estimated") ? ESTIMATED_GP_TOOLTIP
			: name.startsWith("Confirmed") ? CONFIRMED_GP_TOOLTIP : null;
		metricName.setToolTipText(tooltip);
		amount.setToolTipText(tooltip);
		row.setToolTipText(tooltip);
		row.add(metricName, BorderLayout.CENTER);
		amount.setFont(amount.getFont().deriveFont(Font.BOLD));
		row.add(amount, BorderLayout.EAST);
		fillWidth(row);
		return row;
	}

	private JPanel itemRow(LocalDate date, String boss, String name, String value, String methods, DailySummary.ItemSummary item)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setBackground(SURFACE);
		row.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
		row.add(label(name, 9, TEXT));
		row.add(Box.createVerticalStrut(2));
		JLabel amount = label(value, 9, MUTED);
		amount.setToolTipText("<html>Estimated: current GE prices, or HA prices when Use HA prices only is enabled. Items do not need to be sold.<br>Confirmed: actual matched GE proceeds after tax or HA value. Unsold items are excluded unless you enter a value manually."
			+ (methods.isEmpty() ? "" : "<br>Breakdown: " + methods) + "</html>");
		row.add(amount);
		JPopupMenu menu = new JPopupMenu();
		JMenuItem kept = new JMenuItem(item.kept ? "Mark as not kept" : "Mark as kept");
		kept.addActionListener(event -> actions.setLootKept(date, boss, item.itemId, !item.kept));
		menu.add(kept);
		JMenuItem editConfirmed = new JMenuItem("Edit confirmed value");
		editConfirmed.addActionListener(event -> editConfirmedValue(date, boss, item));
		menu.add(editConfirmed);
		if (item.confirmedValueOverride != null)
		{
			JMenuItem clearConfirmed = new JMenuItem("Use recorded GE/alch value");
			clearConfirmed.addActionListener(event -> actions.setConfirmedValueOverride(date, boss, item.itemId, null));
			menu.add(clearConfirmed);
		}
		JMenuItem hide = new JMenuItem("Hide this item for this boss");
		hide.addActionListener(event -> actions.setLootHidden(boss, item.itemId, true));
		menu.add(hide);
		addPopupListenerRecursively(row, menu);
		row.setName("loot-item-row");
		fillWidth(row);
		return row;
	}

	private void editConfirmedValue(LocalDate date, String boss, DailySummary.ItemSummary item)
	{
		String current = item.confirmedValueOverride == null ? Long.toString(item.confirmedValue)
			: Long.toString(item.confirmedValueOverride);
		String entered = JOptionPane.showInputDialog(this, "Confirmed value for " + item.name + " (for example, 200m):",
			current);
		if (entered == null)
		{
			return;
		}
		try
		{
			actions.setConfirmedValueOverride(date, boss, item.itemId, parseGp(entered));
		}
		catch (IllegalArgumentException ex)
		{
			JOptionPane.showMessageDialog(this, "Enter GP like 1250000, 1.25m, or 750k.", "Invalid GP value",
				JOptionPane.ERROR_MESSAGE);
		}
	}

	private static long lootKeptValue(DailySummary.LootSummary loot)
	{
		return loot == null ? 0L : loot.items.stream().filter(item -> item.kept).mapToLong(item -> item.value).sum();
	}

	private JButton hiddenLootButton(String boss, List<DailySummary.ItemSummary> hiddenItems)
	{
		JButton button = new JButton("Manage hidden loot (" + hiddenItems.size() + ")");
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setName("hidden-loot-button");
		button.setFocusPainted(false);
		button.addActionListener(event ->
		{
			DailySummary.ItemSummary selected = (DailySummary.ItemSummary) JOptionPane.showInputDialog(this,
				"Choose an item to show again for " + boss + ".", "Hidden loot",
				JOptionPane.PLAIN_MESSAGE, null, hiddenItems.toArray(), hiddenItems.get(0));
			if (selected != null)
			{
				actions.setLootHidden(boss, selected.itemId, false);
			}
		});
		return button;
	}

	private static void addPopupListenerRecursively(Component component, JPopupMenu menu)
	{
		component.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseReleased(MouseEvent event)
			{
				if (event.isPopupTrigger() || SwingUtilities.isRightMouseButton(event))
				{
					menu.show(event.getComponent(), event.getX(), event.getY());
				}
			}
		});
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				addPopupListenerRecursively(child, menu);
			}
		}
	}

	private JPanel emptyRow(String text)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(SURFACE);
		row.setBorder(BorderFactory.createEmptyBorder(13, 11, 13, 11));
		row.add(label(text, 10, MUTED));
		fillWidth(row);
		return row;
	}

	private static JLabel sectionLabel(String text)
	{
		JLabel label = label(text, 11, TEXT);
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		label.setName("section-label");
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
		return label;
	}

	private static void fillWidth(JComponent component)
	{
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
	}

	private static JScrollPane scroll(JPanel content)
	{
		JScrollPane scroll = new JScrollPane(content);
		scroll.setBorder(null);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getViewport().setBackground(BACKGROUND);
		return scroll;
	}

	private static ViewportPanel verticalPanel()
	{
		ViewportPanel panel = new ViewportPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(BACKGROUND);
		return panel;
	}

	private static JPanel transparentVerticalPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		return panel;
	}

	private static JLabel label(String text, int size, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
		label.setForeground(color);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static JTextArea wrappedText(String text, int size, Color color, int rows)
	{
		JTextArea area = new JTextArea(text, rows, 1);
		area.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
		area.setForeground(color);
		area.setOpaque(false);
		area.setEditable(false);
		area.setFocusable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setBorder(null);
		area.setAlignmentX(Component.LEFT_ALIGNMENT);
		area.setMaximumSize(new Dimension(Integer.MAX_VALUE, area.getPreferredSize().height));
		area.setName("status-label");
		return area;
	}

	private static String formatCount(Integer value)
	{
		return value == null ? "—" : Integer.toString(value);
	}

	private static String formatSignedGp(long value)
	{
		return value > 0 ? "+" + formatGp(value) : formatGp(value);
	}

	private static String formatGp(long value)
	{
		long absolute = Math.abs(value);
		String sign = value < 0 ? "-" : "";
		if (absolute >= 1_000_000_000L)
		{
			return sign + String.format("%.2fb", absolute / 1_000_000_000d);
		}
		if (absolute >= 1_000_000L)
		{
			return sign + String.format("%.2fm", absolute / 1_000_000d);
		}
		if (absolute >= 1_000L)
		{
			return sign + String.format("%.1fk", absolute / 1_000d);
		}
		return value + " gp";
	}

	static long parseGp(String input)
	{
		String value = input == null ? "" : input.trim().toLowerCase()
			.replace(",", "").replace("_", "").replace(" ", "");
		if (value.isEmpty())
		{
			return 0L;
		}
		if (value.endsWith("gp"))
		{
			value = value.substring(0, value.length() - 2);
		}
		BigDecimal multiplier = BigDecimal.ONE;
		if (value.endsWith("k") || value.endsWith("m") || value.endsWith("b"))
		{
			char suffix = value.charAt(value.length() - 1);
			value = value.substring(0, value.length() - 1);
			multiplier = suffix == 'k' ? BigDecimal.valueOf(1_000L)
				: suffix == 'm' ? BigDecimal.valueOf(1_000_000L) : BigDecimal.valueOf(1_000_000_000L);
		}
		try
		{
			return new BigDecimal(value).multiply(multiplier).longValueExact();
		}
		catch (NumberFormatException | ArithmeticException ex)
		{
			throw new IllegalArgumentException("Invalid GP value", ex);
		}
	}

	private static String escape(String value)
	{
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	interface Actions
	{
		void saveSplit(LocalDate date, String boss, long value);

		void deleteBossLoot(LocalDate date, String boss);

		void deleteDayGp(LocalDate date);

		void setLootHidden(String boss, int itemId, boolean hidden);

		void setLootKept(LocalDate date, String boss, int itemId, boolean kept);

		void setConfirmedValueOverride(LocalDate date, String boss, int itemId, Long value);

		void saveData(Path destination);

	}

	private enum View
	{
		OVERVIEW,
		DAY,
		BOSS
	}

	private static final class ClickablePanel extends JPanel
	{
		private ClickablePanel()
		{
			setBackground(SURFACE);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		}

		private void onClick(Runnable action)
		{
			MouseAdapter listener = new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent event)
				{
					setBackground(SURFACE_HOVER);
				}

				@Override
				public void mouseExited(MouseEvent event)
				{
					setBackground(SURFACE);
				}

				@Override
				public void mouseClicked(MouseEvent event)
				{
					action.run();
				}
			};
			addListenerRecursively(this, listener);
		}

		private static void addListenerRecursively(Component component, MouseAdapter listener)
		{
			component.addMouseListener(listener);
			component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			if (component instanceof Container)
			{
				for (Component child : ((Container) component).getComponents())
				{
					addListenerRecursively(child, listener);
				}
			}
		}
	}

	private static final class ViewportPanel extends JPanel implements Scrollable
	{
		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 64;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}
}
