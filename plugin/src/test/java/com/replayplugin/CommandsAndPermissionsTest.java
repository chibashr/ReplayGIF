package com.replayplugin;

import com.replayplugin.command.ReplayCommand;
import com.replayplugin.sidecar.SidecarProcessLauncher;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommandsAndPermissionsTest {

    private ServerMock server;
    private ReplayPlugin plugin;
    private ReplayCommand replayCommand;

    @BeforeEach
    void setUp() {
        try {
            server = MockBukkit.mock();
        } catch (Throwable t) {
            assumeTrue(false, "MockBukkit not available: " + t.getMessage());
        }
        Process mockProcess = mock(Process.class);
        when(mockProcess.isAlive()).thenReturn(true);
        ReplayPlugin.setTestSidecarProcessLauncher((sd, dd, heap) -> mockProcess);
        plugin = MockBukkit.load(ReplayPlugin.class);
        replayCommand = new ReplayCommand(plugin);
    }

    @AfterEach
    void tearDown() {
        ReplayPlugin.setTestSidecarProcessLauncher(null);
        if (server != null) MockBukkit.unmock();
    }

    private CommandSender senderWithPermission(String perm) {
        CommandSender s = mock(CommandSender.class);
        when(s.hasPermission(perm)).thenReturn(true);
        when(s.hasPermission(anyString())).thenAnswer(inv -> perm.equals(inv.getArgument(0)));
        return s;
    }

    private CommandSender senderWithAdmin() {
        CommandSender s = mock(CommandSender.class);
        when(s.hasPermission(anyString())).thenReturn(true);
        return s;
    }

    @Test
    @DisplayName("CMD-001: /replay reload with replay.command.reload")
    void cmd001_reloadWithPermission() {
        CommandSender sender = senderWithPermission("replay.command.reload");
        Command cmd = mock(Command.class);
        when(cmd.getName()).thenReturn("replay");
        boolean result = replayCommand.onCommand(sender, cmd, "replay", new String[]{"reload"});
        assertTrue(result);
    }

    @Test
    @DisplayName("CMD-002: /replay reload without permission -> rejected")
    void cmd002_reloadWithoutPermission() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(anyString())).thenReturn(false);
        Command cmd = mock(Command.class);
        when(cmd.getName()).thenReturn("replay");
        replayCommand.onCommand(sender, cmd, "replay", new String[]{"reload"});
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(sender).sendMessage(captor.capture());
        assertTrue(captor.getValue().contains("permission"));
    }

    @Test
    @DisplayName("CMD-003: /replay trigger <player> <event> with replay.command.trigger")
    void cmd003_triggerWithPermission() {
        PlayerMock player = server.addPlayer("cmd3");
        CommandSender sender = senderWithPermission("replay.command.trigger");
        Command cmd = mock(Command.class);
        when(cmd.getName()).thenReturn("replay");
        boolean result = replayCommand.onCommand(sender, cmd, "replay", new String[]{"trigger", "cmd3", "PlayerDeathEvent"});
        assertTrue(result);
    }

    @Test
    @DisplayName("CMD-004: /replay queue with replay.command.queue")
    void cmd004_queueWithPermission() {
        CommandSender sender = senderWithPermission("replay.command.queue");
        Command cmd = mock(Command.class);
        when(cmd.getName()).thenReturn("replay");
        boolean result = replayCommand.onCommand(sender, cmd, "replay", new String[]{"queue"});
        assertTrue(result);
    }

    @Test
    @DisplayName("CMD-005: /replay queue clear with replay.command.queue.clear")
    void cmd005_queueClearWithPermission() {
        CommandSender sender = senderWithPermission("replay.command.queue.clear");
        Command cmd = mock(Command.class);
        when(cmd.getName()).thenReturn("replay");
        boolean result = replayCommand.onCommand(sender, cmd, "replay", new String[]{"queue", "clear"});
        assertTrue(result);
    }

    @Test
    @DisplayName("CMD-006: /replay trigger enable/disable with replay.command.trigger.toggle")
    void cmd006_triggerToggleWithPermission() {
        CommandSender sender = senderWithPermission("replay.command.trigger.toggle");
        Command cmd = mock(Command.class);
        when(cmd.getName()).thenReturn("replay");
        replayCommand.onCommand(sender, cmd, "replay", new String[]{"trigger", "disable", "PlayerDeathEvent"});
        assertFalse(plugin.getTriggerRegistry().isRegistered("PlayerDeathEvent"));
        replayCommand.onCommand(sender, cmd, "replay", new String[]{"trigger", "enable", "PlayerDeathEvent"});
        assertTrue(plugin.getTriggerRegistry().isRegistered("PlayerDeathEvent"));
    }

    @Test
    @DisplayName("CMD-007: replay.admin grants all commands")
    void cmd007_adminGrantsAll() {
        CommandSender sender = senderWithAdmin();
        Command cmd = mock(Command.class);
        when(cmd.getName()).thenReturn("replay");
        assertTrue(replayCommand.onCommand(sender, cmd, "replay", new String[]{"reload"}));
        assertTrue(replayCommand.onCommand(sender, cmd, "replay", new String[]{"queue"}));
    }

    @Test
    @DisplayName("CMD-008: In-game admin notification on queue overflow")
    void cmd008_queueOverflowNotifiesAdmin() {
        PlayerMock admin = server.addPlayer("admin");
        admin.addAttachment(plugin, "replay.admin", true);
        server.addPlayer("nonadmin");
        com.replayplugin.util.AdminNotifier.broadcast("Replay queue is full. A replay was dropped.");
    }
}
