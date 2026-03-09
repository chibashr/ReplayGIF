package me.replaygif.command;

import me.replaygif.ReplayGifCommand;
import me.replaygif.ReplayGifPlugin;
import me.replaygif.compat.MessageSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for /replaygif subcommands and tab completion.
 */
class ReplayGifCommandTest {

    private ReplayGifPlugin plugin;
    private ReplayGifCommand command;
    private Command bukkitCommand;
    private List<String> sentMessages;
    private CommandSender sender;

    @BeforeEach
    void setUp() {
        plugin = mock(ReplayGifPlugin.class);
        sentMessages = new ArrayList<>();
        MessageSender testMessageSender = (s, msg) -> sentMessages.add(msg);
        command = new ReplayGifCommand(plugin, testMessageSender);
        bukkitCommand = mock(Command.class);
        when(bukkitCommand.getName()).thenReturn("replaygif");
        sender = mock(CommandSender.class);
    }

    @Test
    void unknownSubcommand_sendsUsage() {
        command.onCommand(sender, bukkitCommand, "replaygif", new String[]{"unknown"});
        assertTrue(sentMessages.stream().anyMatch(m -> m.contains("Usage")));
    }

    @Test
    void reload_callsPluginReloadAndSendsMessage() {
        command.onCommand(sender, bukkitCommand, "replaygif", new String[]{"reload"});
        verify(plugin).reload();
        assertTrue(sentMessages.stream().anyMatch(m -> m.contains("reloaded") && m.contains("ReplayGif")));
    }

    @Test
    void status_withNoBuffers_reportsZeroPlayers() {
        when(plugin.getSnapshotBuffers()).thenReturn(new ConcurrentHashMap<>());
        when(plugin.getTriggerHandler()).thenReturn(null);
        when(plugin.getSnapshotScheduler()).thenReturn(null);
        when(plugin.getWebhookInboundServer()).thenReturn(null);

        command.onCommand(sender, bukkitCommand, "replaygif", new String[]{"status"});
        assertTrue(sentMessages.stream().anyMatch(m -> m.contains("Buffers:") && m.contains("0")),
                "Expected status to report 0 buffers; got: " + String.join("; ", sentMessages));
    }

    @Test
    void tabComplete_firstArg_returnsMatchingSubcommands() {
        List<String> r = command.onTabComplete(sender, bukkitCommand, "replaygif", new String[]{"re"});
        assertEquals(List.of("reload"), r);

        List<String> all = command.onTabComplete(sender, bukkitCommand, "replaygif", new String[]{""});
        assertTrue(all.contains("reload"));
        assertTrue(all.contains("status"));
        assertTrue(all.contains("test"));
    }

    @Test
    void tabComplete_testSecondArg_returnsPlayerNames() {
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        when(plugin.getServer()).thenReturn(server);
        Player p = mock(Player.class);
        when(p.getName()).thenReturn("Steve");
        List<Player> online = new ArrayList<>();
        online.add(p);
        doReturn(online).when(server).getOnlinePlayers();

        List<String> r = command.onTabComplete(sender, bukkitCommand, "replaygif", new String[]{"test", "St"});
        assertNotNull(r);
        assertTrue(r.contains("Steve"));
    }
}
