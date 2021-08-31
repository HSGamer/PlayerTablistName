package me.hsgamer.playertablistname;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import me.clip.placeholderapi.PlaceholderAPI;
import me.hsgamer.hscore.bukkit.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.comphenix.protocol.ProtocolLibrary.getProtocolManager;

public final class PlayerTablistName extends JavaPlugin {
    private String tabName = "&e{player}";
    private long updatePeriod = 40;
    private boolean hasPlaceholderAPI;
    private final PacketAdapter adapter = new PacketAdapter(this, ListenerPriority.LOWEST, PacketType.Play.Server.PLAYER_INFO) {
        @Override
        public void onPacketSending(PacketEvent event) {
            if (tabName.isEmpty()) {
                return;
            }

            PacketContainer packet = event.getPacket();
            StructureModifier<List<PlayerInfoData>> modifier = packet.getPlayerInfoDataLists();
            List<PlayerInfoData> list = modifier.read(0);
            list.replaceAll(playerInfoData -> {
                String displayName = replaceString(playerInfoData.getProfile());
                return new PlayerInfoData(playerInfoData.getProfile(), playerInfoData.getLatency(), playerInfoData.getGameMode(), displayName != null ? WrappedChatComponent.fromLegacyText(displayName) : null);
            });
            modifier.write(0, list);
        }
    };
    private final BukkitRunnable updateRunnable = new BukkitRunnable() {
        @Override
        public void run() {
            if (isCancelled()) {
                return;
            }
            Collection<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (players.isEmpty()) {
                return;
            }
            List<PlayerInfoData> data = players.parallelStream().map(this::constructInfo).collect(Collectors.toList());
            PacketContainer container = getProtocolManager().createPacket(PacketType.Play.Server.PLAYER_INFO, true);
            container.getPlayerInfoDataLists().writeSafely(0, data);
            container.getPlayerInfoAction().writeSafely(0, EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME);
            players.parallelStream().forEach(player -> {
                try {
                    getProtocolManager().sendServerPacket(player, container, false);
                } catch (InvocationTargetException e) {
                    getLogger().log(Level.WARNING, "Cannot send update display name packet to the player", e);
                }
            });
        }

        private PlayerInfoData constructInfo(Player player) {
            WrappedGameProfile profile = WrappedGameProfile.fromPlayer(player);
            String displayName = replaceString(profile);
            return new PlayerInfoData(profile, 0, EnumWrappers.NativeGameMode.fromBukkit(player.getGameMode()), displayName != null ? WrappedChatComponent.fromLegacyText(displayName) : null);
        }
    };

    @Override
    public void onEnable() {
        loadConfig();
        hasPlaceholderAPI = getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        getProtocolManager().addPacketListener(adapter);
        updateRunnable.runTaskTimerAsynchronously(this, updatePeriod, updatePeriod);
    }

    @Override
    public void onDisable() {
        updateRunnable.cancel();
        getProtocolManager().removePacketListener(adapter);
    }

    private void loadConfig() {
        getConfig().addDefault("tab-name", tabName);
        getConfig().addDefault("update-period", updatePeriod);
        getConfig().options().copyDefaults(true);
        saveConfig();

        tabName = getConfig().getString("tab-name", tabName);
        updatePeriod = getConfig().getLong("update-period", updatePeriod);
    }

    private String replaceString(WrappedGameProfile profile) {
        if (tabName.isEmpty()) {
            return null;
        }
        Player player = Bukkit.getPlayer(profile.getName());
        if (player == null) {
            return null;
        }
        String string = tabName.replace("{player}", profile.getName());
        if (hasPlaceholderAPI) {
            string = PlaceholderAPI.setPlaceholders(player, string);
        }
        return MessageUtils.colorize(string);
    }
}
