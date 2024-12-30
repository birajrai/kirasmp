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
import org.bukkit.ChatColor;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class KiraSMP extends JavaPlugin {

    private static final String PREFIX = ChatColor.GREEN + "[KiraSMP] " + ChatColor.RESET;

    private final HashMap<UUID, Location> playerHomes = new HashMap<>();
    private final HashSet<UUID> sleepVoters = new HashSet<>();
    private final HashSet<UUID> weatherVoters = new HashSet<>();
    private final HashMap<UUID, UUID> teleportRequests = new HashMap<>();

    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        getLogger().info("Plugin Enabled!");
        createConfigAndDataFiles();
        loadHomes();
    }

    @Override
    public void onDisable() {
        if (dataConfig != null) {
            saveHomes();
        } else {
            getLogger().warning("dataConfig is null, skipping save!");
        }
        getLogger().info("Plugin Disabled!");
    }

    private void createConfigAndDataFiles() {
        // Ensure the plugin's data folder exists
        if (!getDataFolder().exists()) {
            boolean created = getDataFolder().mkdirs();
            if (created) {
                getLogger().info(PREFIX + "Data folder created!");
            } else {
                getLogger().warning(PREFIX + "Data folder already exists or could not be created.");
            }
        }

        // Set up the config file
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
            getLogger().info(PREFIX + "config.yml created!");
        }

        // Set up the data file
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                boolean created = dataFile.createNewFile();
                if (created) {
                    getLogger().info(PREFIX + "data.yml created!");
                } else {
                    getLogger().warning(PREFIX + "data.yml already exists or could not be created.");
                }
            } catch (IOException e) {
                getLogger().severe(PREFIX + "Could not create data.yml! " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void loadHomes() {
        if (dataConfig.contains("homes")) {
            var homesSection = dataConfig.getConfigurationSection("homes");
            if (homesSection != null) {
                for (String uuid : homesSection.getKeys(false)) {
                    Location location = homesSection.getLocation(uuid);
                    if (location != null) {
                        playerHomes.put(UUID.fromString(uuid), location);
                    }
                }
            } else {
                getLogger().warning(PREFIX + "No homes section found in data.yml.");
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
            getLogger().severe(PREFIX + "Could not save homes to data.yml! " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Only players can use this command.");
            return true;
        }

        World world = player.getWorld();

        switch (command.getName().toLowerCase()) {
            case "sethome":
                playerHomes.put(player.getUniqueId(), player.getLocation());
                player.sendMessage(PREFIX + ChatColor.GREEN + "Home set successfully!");
                return true;

            case "home":
                if (playerHomes.containsKey(player.getUniqueId())) {
                    player.teleport(playerHomes.get(player.getUniqueId()));
                    player.sendMessage(PREFIX + ChatColor.GREEN + "Teleported to home!");
                } else {
                    player.sendMessage(PREFIX + ChatColor.RED + "You haven't set a home yet. Use /sethome.");
                }
                return true;

            case "spawn":
                player.teleport(world.getSpawnLocation());
                player.sendMessage(PREFIX + ChatColor.GREEN + "Teleported to spawn!");
                return true;

            case "tpa":
                if (args.length == 1) {
                    Player target = Bukkit.getPlayer(args[0]);
                    if (target != null && target.isOnline()) {
                        teleportRequests.put(target.getUniqueId(), player.getUniqueId());
                        target.sendMessage(PREFIX + ChatColor.YELLOW + player.getName() + " has requested to teleport to you. Type /tpaccept to accept.");
                        player.sendMessage(PREFIX + ChatColor.YELLOW + "Teleport request sent to " + target.getName() + ".");
                    } else {
                        player.sendMessage(PREFIX + ChatColor.RED + "Player not found or offline.");
                    }
                } else {
                    player.sendMessage(PREFIX + ChatColor.RED + "Usage: /tpa <player>");
                }
                return true;

            case "tpaccept":
                UUID requesterId = teleportRequests.remove(player.getUniqueId());
                if (requesterId != null) {
                    Player requester = Bukkit.getPlayer(requesterId);
                    if (requester != null && requester.isOnline()) {
                        requester.teleport(player.getLocation());
                        requester.sendMessage(PREFIX + ChatColor.GREEN + "Teleport request accepted by " + player.getName() + ".");
                        player.sendMessage(PREFIX + ChatColor.GREEN + "Teleport request accepted.");
                    } else {
                        player.sendMessage(PREFIX + ChatColor.RED + "Requester is no longer online.");
                    }
                } else {
                    player.sendMessage(PREFIX + ChatColor.RED + "No teleport requests to accept.");
                }
                return true;

            case "weathervote":
                weatherVoters.add(player.getUniqueId());
                int weatherVotes = weatherVoters.size();
                if (weatherVotes >= Bukkit.getOnlinePlayers().size() / 2) {
                    world.setStorm(!world.hasStorm());
                    Bukkit.broadcastMessage(PREFIX + ChatColor.GREEN + "Weather changed by vote!");
                    weatherVoters.clear();
                } else {
                    player.sendMessage(PREFIX + ChatColor.YELLOW + "Vote registered. Current votes: " + weatherVotes);
                }
                return true;

            case "sleepvote":
                sleepVoters.add(player.getUniqueId());
                int sleepVotes = sleepVoters.size();
                if (sleepVotes >= Bukkit.getOnlinePlayers().size() / 2) {
                    world.setTime(0);
                    Bukkit.broadcastMessage(PREFIX + ChatColor.GREEN + "Night skipped by vote!");
                    sleepVoters.clear();
                } else {
                    player.sendMessage(PREFIX + ChatColor.YELLOW + "Vote registered. Current votes: " + sleepVotes);
                }
                return true;

            default:
                return false;
        }
    }
}
