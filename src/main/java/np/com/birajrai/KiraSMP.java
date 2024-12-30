package np.com.birajrai;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class KiraSMP extends JavaPlugin {

    private final HashMap<UUID, Location> playerHomes = new HashMap<>();
    private final HashSet<UUID> sleepVoters = new HashSet<>();
    private final HashSet<UUID> weatherVoters = new HashSet<>();
    private final HashMap<UUID, UUID> teleportRequests = new HashMap<>();

    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        getLogger().info("Plugin Enabled!");
        createDataFile();
        loadHomes();
        saveDefaultConfig();
    }

    @Override
    public void onDisable() {
        saveHomes();
        getLogger().info("Plugin Disabled!");
    }

    private void createDataFile() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            saveResource("data.yml", false);
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void loadHomes() {
        if (dataConfig.contains("homes")) {
            for (String uuid : dataConfig.getConfigurationSection("homes").getKeys(false)) {
                Location location = dataConfig.getLocation("homes." + uuid);
                if (location != null) {
                    playerHomes.put(UUID.fromString(uuid), location);
                }
            }
        }
    }

    private void saveHomes() {
        for (UUID uuid : playerHomes.keySet()) {
            dataConfig.set("homes." + uuid.toString(), playerHomes.get(uuid));
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save homes to data.yml!");
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        World world = player.getWorld();

        switch (command.getName().toLowerCase()) {
            case "sethome":
                playerHomes.put(player.getUniqueId(), player.getLocation());
                player.sendMessage("Home set successfully!");
                return true;

            case "home":
                if (playerHomes.containsKey(player.getUniqueId())) {
                    player.teleport(playerHomes.get(player.getUniqueId()));
                    player.sendMessage("Teleported to home!");
                } else {
                    player.sendMessage("You haven't set a home yet. Use /sethome.");
                }
                return true;

            case "spawn":
                player.teleport(world.getSpawnLocation());
                player.sendMessage("Teleported to spawn!");
                return true;

            case "tpa":
                if (args.length == 1) {
                    Player target = Bukkit.getPlayer(args[0]);
                    if (target != null && target.isOnline()) {
                        teleportRequests.put(target.getUniqueId(), player.getUniqueId());
                        target.sendMessage(player.getName() + " has requested to teleport to you. Type /tpaccept to accept.");
                        player.sendMessage("Teleport request sent to " + target.getName() + ".");
                    } else {
                        player.sendMessage("Player not found or offline.");
                    }
                } else {
                    player.sendMessage("Usage: /tpa <player>");
                }
                return true;

            case "tpaccept":
                UUID requesterId = teleportRequests.remove(player.getUniqueId());
                if (requesterId != null) {
                    Player requester = Bukkit.getPlayer(requesterId);
                    if (requester != null && requester.isOnline()) {
                        requester.teleport(player.getLocation());
                        requester.sendMessage("Teleport request accepted by " + player.getName() + ".");
                        player.sendMessage("Teleport request accepted.");
                    } else {
                        player.sendMessage("Requester is no longer online.");
                    }
                } else {
                    player.sendMessage("No teleport requests to accept.");
                }
                return true;

            case "weathervote":
                weatherVoters.add(player.getUniqueId());
                int weatherVotes = weatherVoters.size();
                if (weatherVotes >= Bukkit.getOnlinePlayers().size() / 2) {
                    world.setStorm(!world.hasStorm());
                    Bukkit.broadcastMessage("Weather changed by vote!");
                    weatherVoters.clear();
                } else {
                    player.sendMessage("Vote registered. Current votes: " + weatherVotes);
                }
                return true;

            case "sleepvote":
                sleepVoters.add(player.getUniqueId());
                int sleepVotes = sleepVoters.size();
                if (sleepVotes >= Bukkit.getOnlinePlayers().size() / 2) {
                    world.setTime(0);
                    Bukkit.broadcastMessage("Night skipped by vote!");
                    sleepVoters.clear();
                } else {
                    player.sendMessage("Vote registered. Current votes: " + sleepVotes);
                }
                return true;

            default:
                return false;
        }
    }
}
