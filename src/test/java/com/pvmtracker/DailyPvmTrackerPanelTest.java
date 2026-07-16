package com.pvmtracker;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JScrollPane;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNotNull;

public class DailyPvmTrackerPanelTest
{
	@Test
	public void usesOneWidthBoundedScrollLayer() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			DailyPvmTrackerPanel panel = new DailyPvmTrackerPanel();
			panel.update("", "Log in to create your first baseline.", new ArrayList<>());

			assertSame(panel, panel.getWrappedPanel());
			List<JScrollPane> scrollPanes = findScrollPanes(panel);
			assertFalse(scrollPanes.isEmpty());
			for (JScrollPane scrollPane : scrollPanes)
			{
				if (scrollPane.getParent() != null)
				{
					org.junit.Assert.assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
						scrollPane.getHorizontalScrollBarPolicy());
				}
			}
		});
	}

	@Test
	public void parsesFriendlyGpAdjustments()
	{
		assertEquals(12_500_000L, DailyPvmTrackerPanel.parseGp("12.5m"));
		assertEquals(750_000L, DailyPvmTrackerPanel.parseGp("750k"));
		assertEquals(-200_000L, DailyPvmTrackerPanel.parseGp("-200k"));
		assertEquals(1_250_000L, DailyPvmTrackerPanel.parseGp("1,250,000 gp"));
		assertEquals(0L, DailyPvmTrackerPanel.parseGp(""));
	}

	@Test
	public void statusTextWrapsWithinTheSidebar() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			DailyPvmTrackerPanel panel = new DailyPvmTrackerPanel();
			panel.update("LogicalHash", "Tracking locally. Hiscores checked at login.", new ArrayList<>());

			Component component = findByName(panel, "status-label");
			assertNotNull(component);
			JTextArea status = (JTextArea) component;
			assertEquals("Tracking locally. Hiscores checked at login.", status.getText());
			org.junit.Assert.assertTrue(status.getLineWrap());
			org.junit.Assert.assertTrue(status.getWrapStyleWord());
			assertEquals(Integer.MAX_VALUE, status.getMaximumSize().width);
		});
	}

	@Test
	public void saveDataButtonRequiresACharacter() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			DailyPvmTrackerPanel panel = new DailyPvmTrackerPanel();
			JComponent button = (JComponent) findByName(panel, "save-data-button");
			assertNotNull(button);
			assertFalse(button.isEnabled());
			panel.update("Logical", "Tracking locally.", new ArrayList<>());
			org.junit.Assert.assertTrue(button.isEnabled());
		});
	}

	@Test
	public void overviewExplainsLocalAndAutomaticPvmHubOptions() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			DailyPvmTrackerPanel panel = new DailyPvmTrackerPanel();
			Component info = findByName(panel, "pvm-hub-info");
			assertNotNull(info);
			JTextArea infoText = (JTextArea) findByName((Container) info, "pvm-hub-info-text");
			assertNotNull(infoText);
			String text = infoText.getText();
			org.junit.Assert.assertTrue(text.contains("tracked locally"));
			org.junit.Assert.assertTrue(text.contains("plugin settings"));
			org.junit.Assert.assertTrue(text.contains("Upload to PvM-Hub.com"));
			org.junit.Assert.assertTrue(text.contains("Hover over Estimated and Confirmed GP"));
			org.junit.Assert.assertTrue(text.contains("right-click captured loot"));
			org.junit.Assert.assertTrue(text.contains("edit its confirmed value"));
		});
	}

	@Test
	public void estimatedAndConfirmedGpExplainTheirSourcesOnHover() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			LocalDate date = LocalDate.parse("2026-07-15");
			TrackerData data = new TrackerData();
			data.lootDays.put(date.toString(), new TrackerData.LootDay());
			DailySummary summary = new DailySummaryService().build(data, date, true).get(0);
			DailyPvmTrackerPanel panel = new DailyPvmTrackerPanel();
			panel.update("Logical", "Tracking locally.", java.util.Collections.singletonList(summary));

			JComponent estimated = (JComponent) findByName(panel, "estimated-gp");
			JComponent confirmed = (JComponent) findByName(panel, "confirmed-gp");
			assertNotNull(estimated);
			assertNotNull(confirmed);
			org.junit.Assert.assertTrue(estimated.getToolTipText().contains("Grand Exchange prices"));
			org.junit.Assert.assertTrue(estimated.getToolTipText().contains("Use HA prices only"));
			org.junit.Assert.assertTrue(confirmed.getToolTipText().contains("Grand Exchange sales"));
			org.junit.Assert.assertTrue(confirmed.getToolTipText().contains("after tax"));
			org.junit.Assert.assertTrue(confirmed.getToolTipText().contains("unsold loot is not included"));
			org.junit.Assert.assertTrue(confirmed.getToolTipText().contains("splits"));
		});
	}

	@Test
	public void detailControlsUseOneLeftAlignmentContract() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			LocalDate date = LocalDate.parse("2026-07-15");
			TrackerData data = new TrackerData();
			TrackerData.KcDay kcDay = new TrackerData.KcDay();
			kcDay.kills.put("Maggot King", 1);
			data.kcDays.put(date.toString(), kcDay);
			TrackerData.LootDay day = new TrackerData.LootDay();
			day.sources.put("Maggot King", new TrackerData.LootSource());
			day.manualAdjustments.put("Maggot King", 1_000_000L);
			data.lootDays.put(date.toString(), day);
			DailySummary summary = new DailySummaryService().build(data, date, true).get(0);

			DailyPvmTrackerPanel panel = new DailyPvmTrackerPanel();
			panel.update("Logical", "Tracking locally.", java.util.Collections.singletonList(summary));
			Component dayCard = findByName(panel, "day-card");
			assertNotNull(dayCard);
			for (MouseListener listener : dayCard.getMouseListeners())
			{
				listener.mouseClicked(new MouseEvent(dayCard, MouseEvent.MOUSE_CLICKED, 0L, 0, 1, 1, 1, false));
			}

			for (String name : new String[]{"back-button", "section-label", "split-picker", "delete-button"})
			{
				JComponent component = (JComponent) findByName(panel, name);
				assertNotNull(name, component);
				assertEquals(name, Component.LEFT_ALIGNMENT, component.getAlignmentX(), 0f);
			}
			JComponent splitPicker = (JComponent) findByName(panel, "split-picker");
			assertEquals(Integer.MAX_VALUE, splitPicker.getMaximumSize().width);
		});
	}

	@Test
	public void bossViewHintsAtTheLootContextMenu() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			LocalDate date = LocalDate.parse("2026-07-15");
			TrackerData data = new TrackerData();
			TrackerData.LootDay day = new TrackerData.LootDay();
			TrackerData.LootSource source = new TrackerData.LootSource();
			TrackerData.LootItem item = new TrackerData.LootItem(1, "Valuable drop");
			item.quantity = 1;
			item.totalValue = 1_000_000L;
			source.items.put(item.itemId, item);
			day.sources.put("Maggot King", source);
			data.lootDays.put(date.toString(), day);
			DailySummary summary = new DailySummaryService().build(data, date, true).get(0);

			DailyPvmTrackerPanel panel = new DailyPvmTrackerPanel();
			panel.update("Logical", "Tracking locally.", java.util.Collections.singletonList(summary));
			click(findByName(panel, "day-card"));
			click(findByName(panel, "boss-row"));

			Component tip = findByName(panel, "loot-context-menu-tip");
			assertNotNull(tip);
			assertEquals("Tip: Right-click an item for more options.", ((javax.swing.JLabel) tip).getText());
		});
	}

	private static void click(Component component)
	{
		assertNotNull(component);
		for (MouseListener listener : component.getMouseListeners())
		{
			listener.mouseClicked(new MouseEvent(component, MouseEvent.MOUSE_CLICKED, 0L, 0, 1, 1, 1, false));
		}
	}

	private static List<JScrollPane> findScrollPanes(Container root)
	{
		List<JScrollPane> result = new ArrayList<>();
		for (Component component : root.getComponents())
		{
			if (component instanceof JScrollPane)
			{
				result.add((JScrollPane) component);
			}
			if (component instanceof Container)
			{
				result.addAll(findScrollPanes((Container) component));
			}
		}
		return result;
	}

	private static Component findByName(Container root, String name)
	{
		for (Component component : root.getComponents())
		{
			if (name.equals(component.getName()))
			{
				return component;
			}
			if (component instanceof Container)
			{
				Component found = findByName((Container) component, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}
}
