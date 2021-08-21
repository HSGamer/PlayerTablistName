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
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.InvocationTargetException;
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
                String displayName = MessageUtils.colorize(replaceString(playerInfoData.getProfile()));
                return new PlayerInfoData(playerInfoData.getProfile(), playerInfoData.getLatency(), playerInfoData.getGameMode(), WrappedChatComponent.fromLegacyText(displayName));
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
            Collection<Player> players = Bukkit.getOnlinePlayers().stream().filter(OfflinePlayer::isOnline).collect(Collectors.toList());
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
            return new PlayerInfoData(profile, 0, EnumWrappers.NativeGameMode.fromBukkit(player.getGameMode()), WrappedChatComponent.fromLegacyText(replaceString(profile)));
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
            return profile.getName();
        }
        String string = tabName.replace("{player}", profile.getName());
        if (hasPlaceholderAPI) {
            Player player = Bukkit.getPlayer(profile.getName());
            if (player != null) {
                string = PlaceholderAPI.setPlaceholders(player, string);
            }
        }
        return string;
    }
}
