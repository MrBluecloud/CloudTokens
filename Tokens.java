package me.mrbluecloud.tokens;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class Tokens extends JavaPlugin
  implements Listener
{
private final LinkedHashMap<ItemStack, Integer> items = new LinkedHashMap();
protected final HashMap<String, Integer> players = new HashMap();
  private final ArrayList<String> browsing = new ArrayList();
  protected static Tokens instance;
  private String notEnoughTokens;
  private String notEnoughSpace;
  private String inventoryTitle;
  private int inventoryRows;
  private Logger logger;
  protected File folder;

  public void onEnable()
  {
    instance = this;

    this.logger = getLogger();

    saveDefaultConfig();

    this.notEnoughTokens = colorize(getConfig().getString("general.notEnoughTokens"));
    this.notEnoughSpace = colorize(getConfig().getString("general.notEnoughTokens"));
    this.inventoryTitle = colorize(getConfig().getString("settings.inventoryTitle", "&bYour tokens: &3<tokens>"));
    this.inventoryRows = getConfig().getInt("settings.inventoryRows", 1);
    loadItems();

    this.folder = new File(getDataFolder(), "playerData");
    if (!this.folder.exists()) {
      this.folder.mkdirs();
    }

    File file = new File(getDataFolder() + File.separator + ".." + File.separator + "DawnTokens");
    if ((file.exists()) && (file.isDirectory())) {
      File dawnFile = new File(file, "config.yml");
      if (dawnFile.exists()) {
        int convertNumber = 0;
        FileConfiguration dawnConfig = YamlConfiguration.loadConfiguration(dawnFile);
        if (dawnConfig.contains("settings.tokens"))
          for (OfflinePlayer player : Bukkit.getOfflinePlayers())
            if (dawnConfig.contains("settings.tokens." + player.getName()))
            {
              int tokens = dawnConfig.getInt("settings.tokens." + player.getName(), -1);
              if (tokens != -1)
              {
                int loadTokens = loadTokens(new File(this.folder, player.getName() + ".tokens"));

                if (loadTokens == 0) {
                  convertNumber++;
                  this.players.put(player.getName(), Integer.valueOf(tokens));
                  saveTokens(player.getName());
                }
              }
            }
        this.logger.log(Level.INFO, "Converted {0} players to the new system!", Integer.valueOf(convertNumber));
        dawnFile.renameTo(new File(file, "old_config.yml"));

        this.players.clear();
      }
    }

    getServer().getPluginManager().registerEvents(this, this);

    for (Player player : getServer().getOnlinePlayers())
      this.players.put(player.getName(), Integer.valueOf(loadTokens(new File(this.folder, player.getName() + ".tokens"))));
  }

  public void onDisable()
  {
    for (Player player : getServer().getOnlinePlayers())
      saveTokens(player.getName());
  }

  private void loadItems()
  {
    this.items.clear();
    for (String item : getConfig().getStringList("settings.inventory")) {
      Material material = Material.valueOf(((String)item.get("type")).toUpperCase());
      if ((material == null) || (material.equals(Material.AIR)) || (!item.containsKey("cost"))) {
        this.logger.info("Failed to correctly create an item!");
        this.logger.info("Please check the config to make sure");
        this.logger.info("Everything is setup correctly!");
      }
      else {
        int quantity = 1;
        short damage = 0;

        if (item.containsKey("quantity")) {
          quantity = ((Integer)item.get("quantity")).intValue();
        }

        if (item.containsKey("damage")) {
          damage = Short.parseShort("" + item.get("damage"));
        }

        ItemStack i = new ItemStack(material, quantity, damage);

        if (item.containsKey("enchants"))
          for (String line : (ArrayList)item.get("enchants")) {
            String[] data = line.split(" ");
            if (data.length == 2)
            {
              Enchantment enchantment = Enchantment.getByName(data[0]);
              if (enchantment == null) {
                this.logger.info("Failed to correctly create an item!");
                this.logger.info("Please check the config to make sure");
                this.logger.info("Everything is setup correctly!");
              }
              else {
                i.addUnsafeEnchantment(enchantment, Integer.parseInt(data[1]));
              }
            }
          }
        ItemMeta itemMeta = i.getItemMeta();

        if (itemMeta != null) {
          if (item.containsKey("name")) {
            itemMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', (String)item.get("name")));
          }

          if (item.containsKey("lore")) {
            ArrayList lore = new ArrayList();
            for (String line : (ArrayList)item.get("lore")) {
              lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            itemMeta.setLore(lore);
          }
          i.setItemMeta(itemMeta);
        }

        this.items.put(i, Integer.valueOf(((Integer)item.get("cost")).intValue()));
      }
    }
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent e) {
    this.players.put(e.getPlayer().getName(), Integer.valueOf(loadTokens(new File(this.folder, e.getPlayer().getName() + ".tokens"))));
  }

  @EventHandler
  public void onPlayerLeave(PlayerQuitEvent e)
  {
    if (this.browsing.contains(e.getPlayer().getName())) {
      this.browsing.remove(e.getPlayer().getName());
    }
    saveTokens(e.getPlayer().getName());
  }

  @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
  public void onPlayerInventoryClick(InventoryClickEvent e)
  {
    if (!(e.getWhoClicked() instanceof Player)) {
      return;
    }

    Player player = (Player)e.getWhoClicked();

    if ((!this.browsing.contains(player.getName())) || (e.getCurrentItem() == null) || (e.getCurrentItem().getType().equals(Material.AIR)) || (!this.items.containsKey(e.getCurrentItem()))) {
      return;
    }

    e.setCancelled(true);

    if (((Integer)this.players.get(player.getName())).intValue() < ((Integer)this.items.get(e.getCurrentItem())).intValue()) {
      player.sendMessage(this.notEnoughTokens);
      player.closeInventory();
      return;
    }

    PlayerInventory inventory = player.getInventory();

    if (inventory.firstEmpty() == -1) {
      player.sendMessage(this.notEnoughSpace);
      player.closeInventory();
      return;
    }

    this.players.put(player.getName(), Integer.valueOf(((Integer)this.players.get(player.getName())).intValue() - ((Integer)this.items.get(e.getCurrentItem())).intValue()));

    inventory.addItem(new ItemStack[] { e.getCurrentItem() });
    player.closeInventory();
  }

  @EventHandler
  public void onPlayerCloseInventory(InventoryCloseEvent e)
  {
    if (this.browsing.contains(e.getPlayer().getName()))
      this.browsing.remove(e.getPlayer().getName());
  }

  public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
  {
    if (command.getName().equalsIgnoreCase("shop")) {
      if (!(sender instanceof Player)) {
        sender.sendMessage("You can't use that command from the console!");
        return true;
      }

      Player player = (Player)sender;

      Inventory inventory = Bukkit.createInventory(null, this.inventoryRows * 9, this.inventoryTitle.replace("<player>", player.getName()).replace("<tokens>", "" + this.players.get(player.getName())));
      for (ItemStack item : this.items.keySet()) {
        inventory.addItem(new ItemStack[] { item });
      }
      player.openInventory(inventory);
      this.browsing.add(player.getName());
    } else if (command.getName().equalsIgnoreCase("Tokens")) {
      if (args.length == 0) {
        sender.sendMessage(colorize("&eCloudTokens &6was developed by &eMrBluecloud"));
        sender.sendMessage(colorize("&6Plugin Version: &e" + getDescription().getVersion()));

        if (!(sender instanceof Player)) {
          Player player = (Player)sender;
          if (!player.hasPermission("tokens.admin")) {
            return true;
          }
        }
        sender.sendMessage("");
        sender.sendMessage(colorize("&6/tokens reload"));
        sender.sendMessage(colorize("&6 - &eReloads the config & shop items."));
        sender.sendMessage(colorize("&6 - &6Aliases: [&e/extoken, /extokens&6]"));
        sender.sendMessage(colorize("&6/Tokens give <player> <amount>"));
        sender.sendMessage(colorize("&6 - &eAddds tokens to a players balance."));
        sender.sendMessage(colorize("&6 - &6Aliases: [&e/token, /tokens&6]"));
        sender.sendMessage(colorize("&6/shop"));
        sender.sendMessage(colorize("&6 - &eOpens the shop."));
        sender.sendMessage(colorize("&6 - &6Aliases: [&e/token, /tokens&6]"));
      } else if (args[0].equalsIgnoreCase("reload")) {
        if (!(sender instanceof Player)) {
          Player player = (Player)sender;
          if (!player.hasPermission("Tokens.reload")) {
            player.sendMessage(colorize("&cYou're not allowed to use this command!"));
            return true;
          }
        }
        reloadConfig();

        this.notEnoughTokens = colorize(getConfig().getString("general.notEnoughTokens"));
        this.notEnoughSpace = colorize(getConfig().getString("general.notEnoughTokens"));
        this.inventoryTitle = colorize(getConfig().getString("settings.inventoryTitle", "&bYour tokens: &3<tokens>"));
        this.inventoryRows = getConfig().getInt("settings.inventoryRows", 1);

        loadItems();
        sender.sendMessage(colorize("&8[&eTokens&8] &aThe config have been reloaded successfully!"));
      } else if (args[0].equalsIgnoreCase("give"))
      {
        if ((sender instanceof Player)) {
          Player player = (Player)sender;
          if (!player.hasPermission("tokens.reload")) {
            player.sendMessage(colorize("&cYou're not allowed to use this command!"));
            return true;
          }
        }

        if (args.length != 3) {
          sender.sendMessage(colorize("&cInvalid format!"));
          sender.sendMessage(colorize("&c/tokens give <player> <amount>"));
        }

        int amount = Integer.parseInt(args[2]);
        String name = args[1];

        Player target = Bukkit.getPlayer(name);
        if (target != null) {
          name = target.getName();
          this.players.put(name, Integer.valueOf(amount + ((Integer)this.players.get(name)).intValue()));
        } else {
          this.players.put(name, Integer.valueOf(amount + loadTokens(new File(this.folder, name + ".tokens"))));
          saveTokens(name);
        }
        sender.sendMessage(colorize("&8[&eTokens&8] &asuccessfully added " + amount + " tokens to " + name + "!"));
      }
    }
    return true;
  }

  protected String colorize(String msg)
  {
    return ChatColor.translateAlternateColorCodes('&', msg);
  }

  protected void saveTokens(String name)
  {
    if (!this.players.containsKey(name)) {
      this.logger.log(Level.INFO, "{0} doesn''t exists in the players array!", name);
      return;
    }
    try
    {
      File invFile = new File(this.folder, name + ".tokens");
      if (invFile.exists()) {
        invFile.delete();
      }
      FileConfiguration invConfig = YamlConfiguration.loadConfiguration(invFile);
      invConfig.set("Tokens", this.players.get(name));
      invConfig.save(invFile);
      this.players.remove(name);
    } catch (IOException ex) {
      this.logger.log(Level.WARNING, "{0}", ex.getLocalizedMessage());
    }
  }

  protected int loadTokens(File file)
  {
    if (file == null) {
      this.logger.log(Level.WARNING, "Invalid File object given on line 304 in Tokens.java");
      return 0;
    }
    if ((!file.exists()) || (file.isDirectory()) || (!file.getAbsolutePath().endsWith(".tokens")))
      return 0;
    try
    {
      FileConfiguration invConfig = YamlConfiguration.loadConfiguration(file);
      return invConfig.getInt("Tokens", 0);
    } catch (Exception ex) {
      this.logger.log(Level.WARNING, "{0}", ex.getLocalizedMessage());
    }return 0;
  }
}