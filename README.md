# KiraSMP Plugin Overview

**KiraSMP** is a Bukkit/Spigot Minecraft plugin designed to enhance player experience on a Minecraft server with various features, including home management, teleportation, and weather control through voting. The plugin allows players to set and teleport to their homes, manage teleport requests, and vote on weather changes and sleep.

## Key Features

1. **Home Management**:
   - Players can set their home locations and teleport back to them easily.
   - Homes are saved in a configuration file, ensuring persistence across server restarts.

2. **Teleport Requests**:
   - Players can send teleport requests to others.
   - The recipient can accept the request, allowing for easy player movement across the server.

3. **Weather Voting**:
   - Players can vote to change the weather on the server.
   - If enough votes are collected, the weather toggles between clear and stormy.

4. **Sleep Voting**:
   - Players can vote to skip the night.
   - If enough votes are collected, the time will be set to day, skipping the night.

## Command List

Here’s a list of commands available in the KiraSMP plugin:

| Command         | Description                                           |
|------------------|-------------------------------------------------------|
| `/sethome`       | Set your current location as your home.              |
| `/home`          | Teleport to your set home location.                  |
| `/spawn`         | Teleport to the world's spawn location.              |
| `/tpa <player>`  | Send a teleport request to another player.           |
| `/tpaccept`      | Accept a teleport request from another player.       |
| `/weathervote`   | Vote to change the weather (clear/storm).            |
| `/sleepvote`     | Vote to skip the night and set the time to day.     |
