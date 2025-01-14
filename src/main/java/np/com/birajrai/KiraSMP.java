package np.com.birajrai;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONObject;

public class KiraSMP extends JavaPlugin implements Listener {
    private static final String PREFIX = ChatColor.GREEN + "[KiraSMP] " + ChatColor.RESET;

    private final ConcurrentHashMap<UUID, Location> playerHomes = new ConcurrentHashMap<>();
    private final HashSet<UUID> sleepVoters = new HashSet<>();
    private final HashSet<UUID> weatherVoters = new HashSet<>();
    private final HashMap<UUID, UUID> teleportRequests = new HashMap<>();
    private final ConcurrentHashMap<String, Location> warps = new ConcurrentHashMap<>();

    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        createDataFile();
        loadData();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("KiraSMP has been enabled!");

        // Start auto-save task
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::saveData,
                6000L, 6000L); // Save every 5 minutes
    }

    @Override
    public void onDisable() {
        saveData();
        getLogger().info("KiraSMP has been disabled!");
    }

    private void createDataFile() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create data.yml!");
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void loadData() {
        // Load homes
        if (dataConfig.contains("homes")) {
            dataConfig.getConfigurationSection("homes").getKeys(false).forEach(uuid -> {
                playerHomes.put(UUID.fromString(uuid),
                        dataConfig.getLocation("homes." + uuid));
            });
        }

        // Load warps
        if (dataConfig.contains("warps")) {
            dataConfig.getConfigurationSection("warps").getKeys(false).forEach(name -> {
                warps.put(name, dataConfig.getLocation("warps." + name));
            });
        }
    }

    private void saveData() {
        // Save homes
        playerHomes.forEach((uuid, location) ->
                dataConfig.set("homes." + uuid.toString(), location));

        // Save warps
        warps.forEach((name, location) ->
                dataConfig.set("warps." + name, location));

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save data.yml!");
            e.printStackTrace();
        }
    }

    // Update the cooldowns map declaration to store String keys
    private final ConcurrentHashMap<String, Long> cooldowns = new ConcurrentHashMap<>();

    private boolean checkCooldown(Player player, String command) {
        // Create a unique key for the player and command combination
        String cooldownKey = player.getUniqueId().toString() + ":" + command;
        long cooldownTime = getConfig().getLong("cooldowns." + command, 30) * 1000;
        long currentTime = System.currentTimeMillis();

        if (cooldowns.containsKey(cooldownKey)) {
            long timeElapsed = currentTime - cooldowns.get(cooldownKey);
            if (timeElapsed < cooldownTime) {
                long remainingSeconds = (cooldownTime - timeElapsed) / 1000;
                player.sendMessage(PREFIX + ChatColor.RED +
                        "Please wait " + remainingSeconds + " seconds before using this command again.");
                return false;
            }
        }

        cooldowns.put(cooldownKey, currentTime);
        return true;
    }

    private boolean checkAndChargeXP(Player player, String actionPath) {
        int cost = getConfig().getInt("economy.costs." + actionPath);
        if (player.getLevel() >= cost) {
            player.setLevel(player.getLevel() - cost);
            if (getConfig().getBoolean("economy.show-cost-messages")) {
                String message = ChatColor.translateAlternateColorCodes('&',
                        getConfig().getString("messages.xp-deducted")
                                .replace("%levels%", String.valueOf(cost)));
                player.sendMessage(PREFIX + message);
            }
            return true;
        } else {
            String message = ChatColor.translateAlternateColorCodes('&',
                    getConfig().getString("messages.insufficient-xp")
                            .replace("%levels%", String.valueOf(cost)));
            player.sendMessage(PREFIX + message);
            return false;
        }
    }

    private void rewardXP(Player player, String actionPath) {
        int reward = getConfig().getInt("voting." + actionPath + ".reward-levels");
        player.setLevel(player.getLevel() + reward);
        if (getConfig().getBoolean("economy.show-cost-messages")) {
            String message = ChatColor.translateAlternateColorCodes('&',
                    getConfig().getString("messages.xp-reward")
                            .replace("%levels%", String.valueOf(reward)));
            player.sendMessage(PREFIX + message);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Clear any existing votes from disconnected players
        sleepVoters.remove(player.getUniqueId());
        weatherVoters.remove(player.getUniqueId());
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
                if (!checkCooldown(player, "home")) return true;
                if (!checkAndChargeXP(player, "set-home")) return true;

                playerHomes.put(player.getUniqueId(), player.getLocation());
                player.sendMessage(PREFIX + ChatColor.translateAlternateColorCodes('&',
                        getConfig().getString("messages.home-set")));
                return true;

            case "home":
                if (!checkCooldown(player, "home")) return true;
                Location home = playerHomes.get(player.getUniqueId());
                if (home != null) {
                    player.teleport(home);
                    player.sendMessage(PREFIX + ChatColor.translateAlternateColorCodes('&',
                            getConfig().getString("messages.home-teleport")));
                } else {
                    player.sendMessage(PREFIX + ChatColor.RED + "You haven't set a home yet!");
                }
                return true;

            case "tpa":
                if (!checkCooldown(player, "tpa")) return true;
                if (args.length != 1) {
                    player.sendMessage(PREFIX + ChatColor.RED + "Usage: /tpa <player>");
                    return true;
                }

                Player target = Bukkit.getPlayer(args[0]);
                if (target != null && target.isOnline()) {
                    teleportRequests.put(target.getUniqueId(), player.getUniqueId());
                    target.sendMessage(PREFIX + ChatColor.translateAlternateColorCodes('&',
                            getConfig().getString("messages.teleport-request-received")
                                    .replace("%player%", player.getName())));
                    player.sendMessage(PREFIX + ChatColor.translateAlternateColorCodes('&',
                            getConfig().getString("messages.teleport-request-sent")
                                    .replace("%player%", target.getName())));

                    // Expire the request after configured time
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (teleportRequests.remove(target.getUniqueId(), player.getUniqueId())) {
                            player.sendMessage(PREFIX + ChatColor.translateAlternateColorCodes('&',
                                    getConfig().getString("messages.teleport-expired")));
                        }
                    }, getConfig().getLong("teleport.request-expiry") * 20L);
                } else {
                    player.sendMessage(PREFIX + ChatColor.RED + "Player not found or offline.");
                }
                return true;

            case "tpaccept":
                UUID requesterId = teleportRequests.remove(player.getUniqueId());
                if (requesterId != null) {
                    Player requester = Bukkit.getPlayer(requesterId);
                    if (requester != null && requester.isOnline()) {
                        requester.teleport(player.getLocation());
                        requester.sendMessage(PREFIX + ChatColor.translateAlternateColorCodes('&',
                                getConfig().getString("messages.teleport-success")));
                    }
                } else {
                    player.sendMessage(PREFIX + ChatColor.RED + "You have no pending teleport requests.");
                }
                return true;

            case "addwarp":
                if (!checkCooldown(player, "warp")) return true;
                if (!checkAndChargeXP(player, "create-warp")) return true;
                if (args.length != 1) {
                    player.sendMessage(PREFIX + ChatColor.RED + "Usage: /addwarp <name>");
                    return true;
                }

                String warpName = args[0].toLowerCase();
                warps.put(warpName, player.getLocation());
                player.sendMessage(PREFIX + ChatColor.translateAlternateColorCodes('&',
                        getConfig().getString("messages.warp-created")
                                .replace("%warp%", warpName)));
                return true;

            case "warp":
                if (!checkCooldown(player, "warp")) return true;
                if (!checkAndChargeXP(player, "use-warp")) return true;
                if (args.length != 1) {
                    player.sendMessage(PREFIX + ChatColor.RED + "Usage: /warp <name>");
                    return true;
                }

                Location warpLoc = warps.get(args[0].toLowerCase());
                if (warpLoc != null) {
                    player.teleport(warpLoc);
                    player.sendMessage(PREFIX + ChatColor.translateAlternateColorCodes('&',
                            getConfig().getString("messages.warp-teleport")
                                    .replace("%warp%", args[0])));
                } else {
                    player.sendMessage(PREFIX + ChatColor.translateAlternateColorCodes('&',
                            getConfig().getString("messages.warp-not-found")
                                    .replace("%warp%", args[0])));
                }
                return true;

            case "warps":
                if (warps.isEmpty()) {
                    player.sendMessage(PREFIX + ChatColor.YELLOW + "No warps available.");
                } else {
                    player.sendMessage(PREFIX + ChatColor.YELLOW + "Available warps: " +
                            String.join(", ", warps.keySet()));
                }
                return true;

            case "rtp":
                if (!checkCooldown(player, "rtp")) return true;
                if (!checkAndChargeXP(player, "rtp")) return true;

                int maxDistance = getConfig().getInt("rtp.max-distance", 5000);
                int minDistance = getConfig().getInt("rtp.min-distance", 100);
                int attempts = 0;
                int maxAttempts = 50;

                while (attempts < maxAttempts) {
                    int x = (int) (Math.random() * (maxDistance * 2)) - maxDistance;
                    int z = (int) (Math.random() * (maxDistance * 2)) - maxDistance;

                    // Check if distance is greater than minimum
                    if (Math.sqrt(x * x + z * z) < minDistance) {
                        attempts++;
                        continue;
                    }

                    Location randomLoc = new Location(world, x, 0, z);
                    int y = world.getHighestBlockYAt(x, z);
                    randomLoc.setY(y + 1);

                    // Check if location is safe
                    if (randomLoc.getBlock().getType().isSolid() &&
                            !randomLoc.getBlock().isLiquid()) {
                        player.teleport(randomLoc);
                        player.sendMessage(PREFIX + ChatColor.GREEN +
                                "Teleported to random location!");
                        return true;
                    }
                    attempts++;
                }
                player.sendMessage(PREFIX + ChatColor.RED +
                        "Could not find a safe location. Please try again.");
                return true;

            case "weathervote":
                if (!checkCooldown(player, "weathervote")) return true;

                int minPlayers = getConfig().getInt("voting.weather.min-players", 2);
                int onlinePlayers = Bukkit.getOnlinePlayers().size();

                if (onlinePlayers < minPlayers) {
                    player.sendMessage(PREFIX + ChatColor.RED +
                            "At least " + minPlayers + " players must be online to vote.");
                    return true;
                }

                weatherVoters.add(player.getUniqueId());
                int requiredVotes = (int) Math.ceil(onlinePlayers *
                        getConfig().getDouble("voting.weather.required-percentage", 50) / 100);

                if (weatherVoters.size() >= requiredVotes) {
                    world.setStorm(!world.hasStorm());
                    Bukkit.broadcastMessage(PREFIX + ChatColor.translateAlternateColorCodes('&',
                            getConfig().getString("messages.vote-weather-broadcast")));

                    // Reward voters
                    for (UUID uuid : weatherVoters) {
                        Player voter = Bukkit.getPlayer(uuid);
                        if (voter != null && voter.isOnline()) {
                            rewardXP(voter, "weather");
                        }
                    }
                    weatherVoters.clear();
                } else {
                    player.sendMessage(PREFIX + ChatColor.GREEN +
                            "Vote registered! " + weatherVoters.size() + "/" + requiredVotes +
                            " votes needed to change weather.");
                }
                return true;

            case "sleepvote":
                if (!checkCooldown(player, "sleepvote")) return true;

                int minSleepPlayers = getConfig().getInt("voting.sleep.min-players", 2);
                int onlineSleepPlayers = Bukkit.getOnlinePlayers().size();

                if (onlineSleepPlayers < minSleepPlayers) {
                    player.sendMessage(PREFIX + ChatColor.RED +
                            "At least " + minSleepPlayers + " players must be online to vote.");
                    return true;
                }

                sleepVoters.add(player.getUniqueId());
                int requiredSleepVotes = (int) Math.ceil(onlineSleepPlayers *
                        getConfig().getDouble("voting.sleep.required-percentage", 50) / 100);

                if (sleepVoters.size() >= requiredSleepVotes) {
                    world.setTime(0);
                    world.setStorm(false);
                    world.setThundering(false);
                    Bukkit.broadcastMessage(PREFIX + ChatColor.translateAlternateColorCodes('&',
                            getConfig().getString("messages.vote-sleep-broadcast")));

                    // Reward voters
                    for (UUID uuid : sleepVoters) {
                        Player voter = Bukkit.getPlayer(uuid);
                        if (voter != null && voter.isOnline()) {
                            rewardXP(voter, "sleep");
                        }
                    }
                    sleepVoters.clear();
                } else {
                    player.sendMessage(PREFIX + ChatColor.GREEN +
                            "Vote registered! " + sleepVoters.size() + "/" + requiredSleepVotes +
                            " votes needed to skip night.");
                }
                return true;

            case "skin":
                if (!checkCooldown(player, "skin")) return true;
                if (!checkAndChargeXP(player, "change-skin")) return true;
                if (args.length != 1) {
                    player.sendMessage(PREFIX + ChatColor.RED + "Usage: /skin <playername>");
                    return true;
                }

                String targetName = args[0];
                String skinUrl = getSkinUrl(targetName);
                if (skinUrl != null) {
                    if (setPlayerSkin(player, skinUrl)) {
                        player.sendMessage(PREFIX + ChatColor.translateAlternateColorCodes('&',
                                getConfig().getString("messages.skin-changed")
                                        .replace("%player%", targetName)));
                    } else {
                        player.sendMessage(PREFIX + ChatColor.translateAlternateColorCodes('&',
                                getConfig().getString("messages.skin-error")));
                    }
                } else {
                    player.sendMessage(PREFIX + ChatColor.RED +
                            "Could not find skin for player: " + targetName);
                }
                return true;

            default:
                return false;
        }
    }

    private String getSkinUrl(String playerName) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + playerName);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(response.toString());
                String uuid = json.getString("id");

                // Get skin URL from UUID
                URL skinUrl = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
                HttpURLConnection skinConn = (HttpURLConnection) skinUrl.openConnection();
                skinConn.setRequestMethod("GET");

                if (skinConn.getResponseCode() == 200) {
                    reader = new BufferedReader(new InputStreamReader(skinConn.getInputStream()));
                    response = new StringBuilder();

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject skinJson = new JSONObject(response.toString());
                    return skinJson.getJSONArray("properties")
                            .getJSONObject(0)
                            .getString("value");
                }
            }
        } catch (Exception e) {
            getLogger().warning("Error fetching skin: " + e.getMessage());
        }
        return null;
    }

    private boolean setPlayerSkin(Player player, String skinUrl) {
        try {
            // Note: This is a placeholder. You need to implement skin changing using
            // server-specific API or a skin changing plugin API
            return true;
        } catch (Exception e) {
            getLogger().warning("Error setting skin: " + e.getMessage());
            return false;
        }
    }

    private int getRequiredVotes(int onlinePlayers) {
        return (int) Math.ceil(onlinePlayers / 2.0);
    }
}