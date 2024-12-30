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
import org.bukkit.ChatColor; // Import ChatColor

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class KiraSMP extends JavaPlugin {

    private static final String PREFIX = ChatColor.GREEN + "[KiraSMP] " + ChatColor.RESET; // Define prefix with color

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
        if (dataConfig != null) { // Ensure dataConfig is not null before saving
            saveHomes();
        } else {
            getLogger().warning("dataConfig is null, skipping save!");
        }
        getLogger().info("Plugin Disabled!");
    }

    private void createConfigAndDataFiles() {
        // Ensure the plugin's data folder exists
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Set up the config file
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig(); // This method saves the default config from the jar
            getLogger().info(PREFIX + "config.yml created!");
        }

        // Set up the data file
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
                getLogger().info(PREFIX + "data.yml created!");
            } catch (IOException e) {
                getLogger().severe(PREFIX + "Could not create data.yml!");
                e.printStackTrace();
            }
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
            getLogger().severe(PREFIX + "Could not save homes to data.yml!");
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Only players can use this command."); // Use prefix
            return true;
        }

        Player player = (Player) sender;
        World world = player.getWorld();

        switch (command.getName().toLowerCase()) {
            case "sethome":
                playerHomes.put(player.getUniqueId(), player.getLocation());
                player.sendMessage(PREFIX + ChatColor.GREEN + "Home set successfully!"); // Use prefix
                return true;

            case "home":
                if (playerHomes.containsKey(player.getUniqueId())) {
                    player.teleport(playerHomes.get(player.getUniqueId()));
                    player.sendMessage(PREFIX + ChatColor.GREEN + "Teleported to home!"); // Use prefix
                } else {
                    player.sendMessage(PREFIX + ChatColor.RED + "You haven't set a home yet. Use /sethome."); // Use prefix
                }
                return true;

            case "spawn":
                player.teleport(world.getSpawnLocation());
                player.sendMessage(PREFIX + ChatColor.GREEN + "Teleported to spawn!"); // Use prefix
                return true;

            case "tpa":
                if (args.length == 1) {
                    Player target = Bukkit.getPlayer(args[0]);
                    if (target != null && target.isOnline()) {
                        teleportRequests.put(target.getUniqueId(), player.getUniqueId());
                        target.sendMessage(PREFIX + ChatColor.YELLOW + player.getName() + " has requested to teleport to you. Type /tpaccept to accept."); // Use prefix
                        player.sendMessage(PREFIX + ChatColor.YELLOW + "Teleport request sent to " + target.getName() + "."); // Use prefix
                    } else {
                        player.sendMessage(PREFIX + ChatColor.RED + "Player not found or offline."); // Use prefix
                    }
                } else {
                    player.sendMessage(PREFIX + ChatColor.RED + "Usage: /tpa <player>"); // Use prefix
                }
                return true;

            case "tpaccept":
                UUID requesterId = teleportRequests.remove(player.getUniqueId());
                if (requesterId != null) {
                    Player requester = Bukkit.getPlayer(requesterId);
                    if (requester != null && requester.isOnline()) {
                        requester.teleport(player.getLocation());
                        requester.sendMessage(PREFIX + ChatColor.GREEN + "Teleport request accepted by " + player.getName() + "."); // Use prefix
                        player.sendMessage(PREFIX + ChatColor.GREEN + "Teleport request accepted."); // Use prefix
                    } else {
                        player.sendMessage(PREFIX + ChatColor.RED + "Requester is no longer online."); // Use prefix
                    }
                } else {
                    player.sendMessage(PREFIX + ChatColor.RED + "No teleport requests to accept."); // Use prefix
                }
                return true;

            case "weathervote":
                weatherVoters.add(player.getUniqueId());
                int weatherVotes = weatherVoters.size();
                if (weatherVotes >= Bukkit.getOnlinePlayers().size() / 2) {
                    world.setStorm(!world.hasStorm());
                    Bukkit.broadcastMessage(PREFIX + ChatColor.GREEN + "Weather changed by vote!"); // Use prefix
                    weatherVoters.clear();
                } else {
                    player.sendMessage(PREFIX + ChatColor.YELLOW + "Vote registered. Current votes: " + weatherVotes); // Use prefix
                }
                return true;

            case "sleepvote":
                sleepVoters.add(player.getUniqueId());
                int sleepVotes = sleepVoters.size();
                if (sleepVotes >= Bukkit.getOnlinePlayers().size() / 2) {
                    world.setTime(0);
                    Bukkit.broadcastMessage(PREFIX + ChatColor.GREEN + "Night skipped by vote!"); // Use prefix
                    sleepVoters.clear();
                } else {
                    player.sendMessage(PREFIX + ChatColor.YELLOW + "Vote registered. Current votes: " + sleepVotes); // Use prefix
                }
                return true;

            default:
                return false;
        }
    }
}
