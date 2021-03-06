package org.fatecrafters.plugins;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class MEListener implements Listener {	

	private final MultiEco plugin;
	private List<Long> longArray = new ArrayList<Long>();

	public MEListener(MultiEco plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onPlayerJoin(PlayerJoinEvent e) {
		final String name = e.getPlayer().getName(), worldname = e.getPlayer().getWorld().getName();

		plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
			public void run() {
				File file = new File(plugin.getDataFolder()+File.separator+"userdata"+File.separator+name+".yml");
				if (!file.exists()) {
					try {
						file.createNewFile();
						FileConfiguration config = YamlConfiguration.loadConfiguration(file);
						config.set("Data.Worlds."+worldname+".balance", MultiEco.econ.getBalance(name));
						config.set("Data.Worlds."+worldname+".time", System.currentTimeMillis());
						config.save(file);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		});

	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onCommand(PlayerCommandPreprocessEvent e) {
		if (plugin.isBlockingCmds()) {
			String[] args = e.getMessage().split(" ");
			for (String[] s : MEUtil.cmds) {
				if (args[0].equalsIgnoreCase(s[0])) {
					int i = 0;
					int rArg = 0;
					for (String string : s) {
						if (string.equals("$player"))  {
							rArg = i;
							break;
						}
						i++;
					}
					String pGroup = null;
					Player p = e.getPlayer();
					String world = p.getWorld().getName();
					for (String group : MEUtil.groups) {
						if (MEUtil.groupMap.get(group).contains(world)) {
							pGroup = group;
							break;
						}
					}
					if (args.length >= rArg+1) {
						Player payee = plugin.getServer().getPlayer(args[rArg]);
						if (payee != null && !p.hasPermission("multieco.commandblock.bypass") && !MEUtil.groupMap.get(pGroup).contains(payee.getWorld().getName())) {
							e.setCancelled(true);
							p.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.blockedMessage()));
						}
						break;
					}
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onChangeWorld(PlayerChangedWorldEvent e) {
		final String name = e.getPlayer().getName(), worldName = e.getPlayer().getWorld().getName(), fromWorld = e.getFrom().getName();

		plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
			public void run() {
				if (MultiEco.econ.hasAccount(name)) {

					File file = new File(plugin.getDataFolder()+File.separator+"userdata"+File.separator+name+".yml");
					FileConfiguration config = YamlConfiguration.loadConfiguration(file);
					String toWorldGroup = null;
					boolean toWorldGrouped = false;
					double bal = MultiEco.econ.getBalance(name);
					Long now = System.currentTimeMillis();

					try {

						for (String group : MEUtil.groups) {
							if (MEUtil.groupMap.get(group).contains(worldName)) {
								toWorldGroup = group;
								toWorldGrouped = true;
								if (MEUtil.groupMap.get(group).contains(fromWorld)) {
									config.set("Data.Worlds."+fromWorld+".balance", MultiEco.econ.getBalance(name));
									config.set("Data.Worlds."+fromWorld+".time", now);
									config.set("Data.Worlds."+worldName+".balance", MultiEco.econ.getBalance(name));
									config.set("Data.Worlds."+worldName+".time", now);
									config.save(file);
									return;
								}
								break;
							}
						}

						config.set("Data.Worlds."+fromWorld+".balance", bal);
						config.set("Data.Worlds."+fromWorld+".time", now);
						MultiEco.econ.withdrawPlayer(name, bal);

						if (toWorldGrouped) {
							List<String> worlds = MEUtil.groupMap.get(toWorldGroup);
							longArray.clear();
							for (String groupedWorld : worlds) {
								longArray.add(config.getLong("Data.Worlds."+groupedWorld+".time"));
							}
							Long highestTime = Collections.max(longArray);
							String newestWorld = null;
							for (String groupedWorld : worlds) {
								if (config.getLong("Data.Worlds."+groupedWorld+".time") == highestTime) {
									newestWorld = groupedWorld;
									break;
								}
							}
							double worldBal = config.getDouble("Data.Worlds."+newestWorld+".balance");
							for (String groupedWorld : worlds) {
								if (groupedWorld.equals(newestWorld)) continue;
								config.set("Data.Worlds."+groupedWorld+".balance", worldBal);
								config.set("Data.Worlds."+groupedWorld+".time", now);
							}
							MultiEco.econ.depositPlayer(name, worldBal);
						} else {
							MultiEco.econ.depositPlayer(name, config.getDouble("Data.Worlds."+worldName+".balance"));
						}

						config.save(file);

					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		});

	}
}
