package eu.thechest.sgduels;

import eu.thechest.chestapi.ChestAPI;
import eu.thechest.chestapi.event.PlayerDataLoadedEvent;
import eu.thechest.chestapi.items.ItemUtil;
import eu.thechest.chestapi.server.ServerSettingsManager;
import eu.thechest.chestapi.user.ChestUser;
import eu.thechest.chestapi.user.Rank;
import eu.thechest.chestapi.util.StringUtils;
import eu.thechest.sgduels.user.DuelPlayer;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class MainListener implements Listener {
    public static HashMap<Location,Inventory> CHESTS = new HashMap<Location,Inventory>();
    public static HashMap<Location,Inventory> PERMANENT_CHESTS = new HashMap<Location,Inventory>();

    @EventHandler
    public void onJoin(PlayerJoinEvent e){
        Player p = e.getPlayer();

        p.teleport(new Location(Bukkit.getWorld("world"),0,80,0));
        e.setJoinMessage(null);

        for(Player all : Bukkit.getOnlinePlayers()) if(ChestUser.isLoaded(all)) DuelPlayer.get(all).handleVanishing();
    }

    @EventHandler
    public void onLoaded(PlayerDataLoadedEvent e){
        Player p = e.getPlayer();

        ChestAPI.async(() -> {
            ChestUser u = ChestUser.getUser(p);
            DuelPlayer d = DuelPlayer.get(p);

            for(Player all : Bukkit.getOnlinePlayers()) if(ChestUser.isLoaded(all)) ChestAPI.sync(() -> DuelPlayer.get(all).handleVanishing());

            DuelGame g = d.getGame();
            if(g == null){
                Bukkit.getScheduler().scheduleSyncDelayedTask(SGDuels.getInstance(),new Runnable(){
                    @Override
                    public void run() {
                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("No game found!"));
                        u.connectToLobby();
                    }
                },20);
            } else {
                ChestAPI.sync(() -> p.teleport(new Location(Bukkit.getWorld("world"),0,80,0)));
                d.handleAchievements();

                if(g.getOfflinePlayers().size() == 0){
                    ChestAPI.sync(() -> g.loadMap());
                }
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e){
        Player p = e.getPlayer();
        ChestUser u = ChestUser.getUser(p);
        DuelPlayer d = DuelPlayer.get(p);
        DuelGame g = d.getGame();

        e.setQuitMessage(null);

        if(g != null){
            if(g.phase == DuelGame.Phase.PREGAME){
                for(Player all : g.getOnlinePlayers()){
                    if(!ChestUser.isLoaded(all) || all == p) continue;

                    all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + ChestUser.getUser(all).getTranslatedMessage("Match cancelled!"));
                    ChestUser.getUser(all).connectToLobby();
                }

                g.unregister();
            } else if(g.phase == DuelGame.Phase.WARMUP || g.phase == DuelGame.Phase.INGAME){
                for(Player all : g.getOnlinePlayers()){
                    if(ChestUser.isLoaded(all) && all != p){
                        ChestUser a = ChestUser.getUser(all);

                        if(a.hasPermission(Rank.VIP)){
                            all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("%p has left the server.").replace("%p",u.getRank().getColor() + p.getName() + ChatColor.GOLD));
                        } else {
                            all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("%p has left the server.").replace("%p",p.getDisplayName() + ChatColor.GOLD));
                        }
                    }
                }

                if(g.isInTeam1(p)){
                    g.endGame(2);
                } else if(g.isInTeam2(p)){
                    g.endGame(1);
                }
            }
        }

        DuelPlayer.unregister(p);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e){
        Player p = e.getPlayer();

        if(ChestUser.isLoaded(p)){
            ChestUser u = ChestUser.getUser(p);
            DuelPlayer d = DuelPlayer.get(p);
            DuelGame g = d.getGame();

            Location from = e.getFrom();
            Location to = e.getTo();
            double x = Math.floor(from.getX());
            double z = Math.floor(from.getZ());

            if(g != null){
                if(g.phase != DuelGame.Phase.INGAME && g.phase != DuelGame.Phase.ENDING){
                    if(Math.floor(to.getX()) != x || Math.floor(to.getZ()) != z){
                        x += .5;
                        z += .5;
                        e.getPlayer().teleport(new Location(from.getWorld(),x,from.getY(),z,from.getYaw(),from.getPitch()));
                    }
                }
            } else {
                if(Math.floor(to.getX()) != x || Math.floor(to.getZ()) != z){
                    x += .5;
                    z += .5;
                    e.getPlayer().teleport(new Location(from.getWorld(),x,from.getY(),z,from.getYaw(),from.getPitch()));
                }
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e){
        if(e.getEntity() instanceof Player){
            Player p = (Player)e.getEntity();
            ChestUser u = ChestUser.getUser(p);
            DuelPlayer d = DuelPlayer.get(p);
            DuelGame g = d.getGame();

            if(g != null){
                if(g.phase != DuelGame.Phase.INGAME){
                    e.setCancelled(true);
                } else {
                    if(e.getCause() == EntityDamageEvent.DamageCause.VOID){
                        p.damage(p.getHealth());
                    }
                }
            } else {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e){
        Player p = e.getPlayer();
        ChestUser u = ChestUser.getUser(p);
        DuelPlayer d = DuelPlayer.get(p);
        DuelGame g = d.getGame();

        if(e.getAction() == Action.RIGHT_CLICK_BLOCK){
            if(e.getClickedBlock() != null && e.getClickedBlock().getType() != null){
                if(SGDuels.getInstance().DISALLOWED_BLOCKS.contains(e.getClickedBlock().getType())){
                    e.setCancelled(true);
                    e.setUseItemInHand(Event.Result.DENY);
                    e.setUseInteractedBlock(Event.Result.DENY);
                    return;
                }
            }
        }

        if(g != null && g.phase == DuelGame.Phase.INGAME){
            if(e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.RIGHT_CLICK_AIR){
                if(p.getItemInHand() != null && p.getItemInHand().getType() != null){
                    if(p.getItemInHand().getType() == Material.COMPASS){
                        Player n = null;

                        if(g != null){
                            if(g.phase != DuelGame.Phase.INGAME) return;

                            if(g.isInTeam1(p)){
                                n = Bukkit.getPlayer(UUID.fromString(g.getTeam2().get(0)));
                            } else if(g.isInTeam2(p)){
                                n = Bukkit.getPlayer(UUID.fromString(g.getTeam1().get(0)));
                            }
                        }

                        if(n != null){
                            if(u.hasPermission(Rank.VIP)){
                                p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + u.getTranslatedMessage("Nearest target: %p.").replace("%p",ChestUser.getUser(n).getRank().getColor() + n.getName() + ChatColor.GOLD) + " " + ChatColor.YELLOW + "(" + u.getTranslatedMessage("%b blocks away.").replace("%b",String.valueOf(((Double)n.getLocation().distance(p.getLocation())).intValue())) + ")");
                            } else {
                                p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + u.getTranslatedMessage("Nearest target: %p.").replace("%p",n.getDisplayName() + ChatColor.GOLD) + " " + ChatColor.YELLOW + "(" + u.getTranslatedMessage("%b blocks away.").replace("%b",String.valueOf(((Double)n.getLocation().distance(p.getLocation())).intValue())) + ")");
                            }
                        } else {
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("No target found."));
                        }
                    }

                    if(p.getItemInHand().getType() == Material.LEATHER_HELMET || p.getItemInHand().getType() == Material.CHAINMAIL_HELMET || p.getItemInHand().getType() == Material.IRON_HELMET || p.getItemInHand().getType() == Material.GOLD_HELMET || p.getItemInHand().getType() == Material.DIAMOND_HELMET){
                        if(p.getInventory().getHelmet() != null){
                            ItemStack oldItem = p.getInventory().getHelmet();
                            ItemStack newItem = p.getItemInHand();

                            p.getInventory().setHelmet(newItem);
                            p.getInventory().setItemInHand(oldItem);
                        }
                    }

                    if(p.getItemInHand().getType() == Material.LEATHER_CHESTPLATE || p.getItemInHand().getType() == Material.CHAINMAIL_CHESTPLATE || p.getItemInHand().getType() == Material.IRON_CHESTPLATE || p.getItemInHand().getType() == Material.GOLD_CHESTPLATE || p.getItemInHand().getType() == Material.DIAMOND_CHESTPLATE){
                        if(p.getInventory().getChestplate() != null){
                            ItemStack oldItem = p.getInventory().getChestplate();
                            ItemStack newItem = p.getItemInHand();

                            p.getInventory().setChestplate(newItem);
                            p.getInventory().setItemInHand(oldItem);
                        }
                    }

                    if(p.getItemInHand().getType() == Material.LEATHER_LEGGINGS || p.getItemInHand().getType() == Material.CHAINMAIL_LEGGINGS || p.getItemInHand().getType() == Material.IRON_LEGGINGS || p.getItemInHand().getType() == Material.GOLD_LEGGINGS || p.getItemInHand().getType() == Material.DIAMOND_LEGGINGS){
                        if(p.getInventory().getLeggings() != null){
                            ItemStack oldItem = p.getInventory().getLeggings();
                            ItemStack newItem = p.getItemInHand();

                            p.getInventory().setLeggings(newItem);
                            p.getInventory().setItemInHand(oldItem);
                        }
                    }

                    if(p.getItemInHand().getType() == Material.LEATHER_BOOTS || p.getItemInHand().getType() == Material.CHAINMAIL_BOOTS || p.getItemInHand().getType() == Material.IRON_BOOTS || p.getItemInHand().getType() == Material.GOLD_BOOTS || p.getItemInHand().getType() == Material.DIAMOND_BOOTS){
                        if(p.getInventory().getBoots() != null){
                            ItemStack oldItem = p.getInventory().getBoots();
                            ItemStack newItem = p.getItemInHand();

                            p.getInventory().setBoots(newItem);
                            p.getInventory().setItemInHand(oldItem);
                        }
                    }
                }
            }

            if(e.getAction() == Action.RIGHT_CLICK_BLOCK){
                if(e.getClickedBlock() != null && e.getClickedBlock().getType() != null && e.getClickedBlock().getType() == Material.REDSTONE_BLOCK && p.getGameMode() == GameMode.SURVIVAL){
                    if(p.getItemInHand() != null && p.getItemInHand().getType() != null && (p.getItemInHand().getType() == Material.WOOD_SWORD || p.getItemInHand().getType() == Material.STONE_SWORD || p.getItemInHand().getType() == Material.IRON_SWORD || p.getItemInHand().getType() == Material.GOLD_SWORD || p.getItemInHand().getType() == Material.DIAMOND_SWORD || p.getItemInHand().getType() == Material.BOW)){
                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You cannot use that item to open a chest."));
                        e.setCancelled(true);
                        return;
                    }

                    if(CHESTS.containsKey(e.getClickedBlock().getLocation())){
                        e.setCancelled(true);
                        e.setUseItemInHand(Event.Result.DENY);
                        e.setUseInteractedBlock(Event.Result.DENY);
                        p.openInventory(CHESTS.get(e.getClickedBlock().getLocation()));
                        p.getWorld().playSound(p.getEyeLocation(), Sound.CHEST_OPEN,1f,1f);
                    } else if(PERMANENT_CHESTS.containsKey(e.getClickedBlock().getLocation())){
                        e.setCancelled(true);
                        e.setUseItemInHand(Event.Result.DENY);
                        e.setUseInteractedBlock(Event.Result.DENY);
                        p.openInventory(PERMANENT_CHESTS.get(e.getClickedBlock().getLocation()));
                        p.getWorld().playSound(p.getEyeLocation(), Sound.CHEST_OPEN,1f,1f);
                    } else {
                        Random rnd = new Random();
                        int n = 1;
                        n = StringUtils.randomInteger(1, 7);
                        Inventory inv = Bukkit.createInventory(null, InventoryType.CHEST);
                        List<ItemStack> items = new ArrayList<ItemStack>();

                        for(int i = 1; i <= 51; i++) {
                            if(u.hasGamePerk(7)){
                                items.add(ItemUtil.namedItem(Material.WOOD_SWORD,u.getRank().getColor() + p.getName(),null));
                            } else {
                                items.add(new ItemStack(Material.WOOD_SWORD));
                            }
                            items.add(new ItemStack(Material.WOOD_AXE));
                            if(u.hasGamePerk(7)){
                                items.add(ItemUtil.namedItem(Material.GOLD_SWORD,u.getRank().getColor() + p.getName(),null));
                            } else {
                                items.add(new ItemStack(Material.GOLD_SWORD));
                            }
                            items.add(new ItemStack(Material.GOLD_INGOT));
                            if(u.hasGamePerk(7)){
                                items.add(ItemUtil.namedItem(Material.STONE_SWORD,u.getRank().getColor() + p.getName(),null));
                            } else {
                                items.add(new ItemStack(Material.STONE_SWORD));
                            }
                            items.add(new ItemStack(Material.STONE_AXE));
                            items.add(new ItemStack(Material.BREAD));
                            items.add(new ItemStack(Material.PORK, StringUtils.randomInteger(1, 3)));
                            items.add(new ItemStack(Material.APPLE, StringUtils.randomInteger(1, 3)));
                            items.add(new ItemStack(Material.GOLD_HELMET));
                            items.add(new ItemStack(Material.GOLD_BOOTS));
                            items.add(new ItemStack(Material.LEATHER_HELMET));
                            items.add(new ItemStack(Material.CHAINMAIL_HELMET));
                            items.add(new ItemStack(Material.CHAINMAIL_LEGGINGS));
                        }

                        for(int i = 1; i <= 41; i++) {
                            items.add(new ItemStack(Material.FISHING_ROD));
                            //items.add(new ItemStack(Material.FLINT_AND_STEEL));
                            items.add(new ItemStack(Material.STICK, StringUtils.randomInteger(1, 4)));
                            items.add(new ItemStack(Material.TNT, StringUtils.randomInteger(1, 3)));
                            items.add(new ItemStack(Material.GRILLED_PORK, StringUtils.randomInteger(1, 5)));
                            items.add(new ItemStack(Material.RAW_BEEF));
                            items.add(new ItemStack(Material.COOKED_BEEF));
                            items.add(new ItemStack(Material.RAW_CHICKEN, StringUtils.randomInteger(1, 3)));
                            items.add(new ItemStack(Material.PUMPKIN_PIE, StringUtils.randomInteger(1, 5)));
                            items.add(new ItemStack(Material.GOLD_CHESTPLATE));
                            items.add(new ItemStack(Material.GOLD_LEGGINGS));
                            items.add(new ItemStack(Material.LEATHER_CHESTPLATE));
                            items.add(new ItemStack(Material.LEATHER_LEGGINGS));
                            items.add(new ItemStack(Material.CHAINMAIL_BOOTS));
                            items.add(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
                        }

                        for(int i = 1; i <= 31; i++) {
                            items.add(new ItemStack(Material.DIAMOND_PICKAXE));
                            items.add(new ItemStack(Material.IRON_PICKAXE));
                            items.add(new ItemStack(Material.IRON_AXE));
                            items.add(new ItemStack(Material.COMPASS));
                            items.add(new ItemStack(Material.IRON_HELMET));
                            items.add(new ItemStack(Material.IRON_BOOTS));
                            items.add(new ItemStack(Material.BOW));
                            items.add(new ItemStack(Material.ARROW, StringUtils.randomInteger(1, 37)));
                            items.add(new ItemStack(Material.EXP_BOTTLE, StringUtils.randomInteger(1, 3)));
                            items.add(new ItemStack(Material.RAW_FISH));
                            items.add(new ItemStack(Material.CAKE));
                            items.add(new ItemStack(Material.COOKED_FISH, StringUtils.randomInteger(1, 5)));
                            items.add(new ItemStack(Material.LEATHER_BOOTS));
                        }

                        for(int i = 1; i <= 21; i++) {
                            items.add(new ItemStack(Material.DIAMOND_AXE));
                            items.add(new ItemStack(Material.IRON_INGOT));
                            if(u.hasGamePerk(7)){
                                items.add(ItemUtil.namedItem(Material.IRON_SWORD,u.getRank().getColor() + p.getName(),null));
                            } else {
                                items.add(new ItemStack(Material.IRON_SWORD));
                            }
                            items.add(new ItemStack(Material.IRON_CHESTPLATE));
                            items.add(new ItemStack(Material.IRON_LEGGINGS));
                            items.add(new ItemStack(Material.GOLDEN_APPLE));
                            items.add(new ItemStack(Material.MELON, StringUtils.randomInteger(1, 3)));
                        }

                        for(int i = 1; i <= 6; i++) {
                            ItemStack regen = new ItemStack(Material.POTION);
                            regen.setDurability((short)8257);

                            ItemStack heal = new ItemStack(Material.POTION);
                            regen.setDurability((short)8229);

                            items.add(regen);
                            items.add(heal);
                        }

                        while(n != 0){
                            n--;
                            inv.setItem(StringUtils.randomInteger(1, 26), items.get(StringUtils.randomInteger(0, items.size())));
                        }

                        CHESTS.put(e.getClickedBlock().getLocation(),inv);
                        e.setCancelled(true);
                        e.setUseItemInHand(Event.Result.DENY);
                        e.setUseInteractedBlock(Event.Result.DENY);
                        p.openInventory(inv);
                        p.getWorld().playSound(p.getEyeLocation(), Sound.CHEST_OPEN,1f,1f);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e){
        Player p = e.getEntity();
        ChestUser u = ChestUser.getUser(p);
        DuelPlayer d = DuelPlayer.get(p);
        DuelGame g = d.getGame();

        e.setDeathMessage(null);

        if(g != null){
            if(g.phase == DuelGame.Phase.INGAME){
                d.addDeaths(1);
                /*if(g.isInTeam1(p)) g.getTeam1().remove(p.getUniqueId().toString());
                if(g.isInTeam2(p)) g.getTeam2().remove(p.getUniqueId().toString());*/

                u.bukkitReset();
                p.setGameMode(GameMode.SPECTATOR);

                if(p.getKiller() != null){
                    Player killer = p.getKiller();
                    ChestUser ku = ChestUser.getUser(killer);
                    DuelPlayer kd = DuelPlayer.get(killer);

                    kd.addKills(1);
                    ku.addCoins(5);
                    ku.giveExp(2);

                    for(Player all : g.getOnlinePlayers()){
                        ChestUser a = ChestUser.getUser(all);

                        if(a.hasPermission(Rank.VIP)){
                            all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("%p was killed by %k.").replace("%p",u.getRank().getColor() + p.getName() + ChatColor.GOLD).replace("%k",ku.getRank().getColor() + killer.getName() + ChatColor.GOLD));
                        } else {
                            all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("%p was killed by %k.").replace("%p",p.getDisplayName() + ChatColor.GOLD).replace("%k",killer.getDisplayName() + ChatColor.GOLD));
                        }
                    }

                    if(g.game != null) g.game.addPlayerDeathEvent(p,killer);
                } else {
                    for(Player all : g.getOnlinePlayers()){
                        ChestUser a = ChestUser.getUser(all);

                        if(a.hasPermission(Rank.VIP)){
                            all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("%p died.").replace("%p",u.getRank().getColor() + p.getName() + ChatColor.GOLD));
                        } else {
                            all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("%p died.").replace("%p",p.getDisplayName() + ChatColor.GOLD));
                        }
                    }

                    if(g.game != null) g.game.addPlayerDeathEvent(p);
                }

                if(g.isInTeam1(p)){
                    g.endGame(2);
                } else if(g.isInTeam2(p)){
                    g.endGame(1);
                }
            }
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e){
        e.setYield(0);
        e.blockList().clear();
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e){
        Player p = e.getPlayer();
        ChestUser u = ChestUser.getUser(p);
        DuelPlayer d = DuelPlayer.get(p);
        DuelGame g = d.getGame();

        String msg = e.getMessage();

        e.setCancelled(true);

        if(g != null){
            if(g.phase != DuelGame.Phase.PREGAME){
                for(Player all : g.getOnlinePlayers()){
                    ChestUser a = ChestUser.getUser(all);

                    if(u.isNicked()){
                        String nick = u.getNick();

                        if(a.hasPermission(Rank.VIP)){
                            //all.sendMessage(ChatColor.GRAY + nick + " " + ChatColor.AQUA + "(" + p.getName() + ")" + " " + ChatColor.DARK_GRAY + ">> " + ChatColor.WHITE + msg);
                            all.sendMessage(net.md_5.bungee.api.ChatColor.DARK_GRAY + "<[" + net.md_5.bungee.api.ChatColor.AQUA + p.getName() + net.md_5.bungee.api.ChatColor.DARK_GRAY + "] " + net.md_5.bungee.api.ChatColor.GRAY + nick + net.md_5.bungee.api.ChatColor.DARK_GRAY + "> " + net.md_5.bungee.api.ChatColor.GRAY + msg);
                        } else {
                            //all.sendMessage(ChatColor.GRAY + nick + " " + ChatColor.DARK_GRAY + ">> " + ChatColor.WHITE + msg);
                            all.sendMessage(net.md_5.bungee.api.ChatColor.DARK_GRAY + "<" + net.md_5.bungee.api.ChatColor.GRAY + nick + net.md_5.bungee.api.ChatColor.DARK_GRAY + "> " + net.md_5.bungee.api.ChatColor.GRAY + msg);
                        }
                    } else {
                        if(u.getRank().getPrefix() != null){
                            //all.sendMessage(u.getRank().getColor() + p.getName() + " " + ChatColor.DARK_GRAY + ">> " + ChatColor.WHITE + msg);
                            all.sendMessage(net.md_5.bungee.api.ChatColor.DARK_GRAY + "<[" + net.md_5.bungee.api.ChatColor.GRAY + u.getRank().getPrefix() + net.md_5.bungee.api.ChatColor.DARK_GRAY + "] " + u.getRank().getColor() + p.getName() + net.md_5.bungee.api.ChatColor.DARK_GRAY + "> " + net.md_5.bungee.api.ChatColor.GRAY + msg);
                        } else {
                            //all.sendMessage(u.getRank().getColor() + p.getName() + " " + ChatColor.DARK_GRAY + ">> " + ChatColor.WHITE + msg);
                            all.sendMessage(net.md_5.bungee.api.ChatColor.DARK_GRAY + "<" + u.getRank().getColor() + p.getName() + net.md_5.bungee.api.ChatColor.DARK_GRAY + "> " + net.md_5.bungee.api.ChatColor.GRAY + msg);
                        }
                    }
                }

                if(g.game != null) g.game.addPlayerChatEvent(p,msg);
            }
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e){
        Player p = e.getPlayer();
        ChestUser u = ChestUser.getUser(p);
        DuelPlayer d = DuelPlayer.get(p);
        DuelGame g = d.getGame();

        if(g != null){
            if(g.phase == DuelGame.Phase.INGAME){
                if(e.getBlockPlaced().getType() != Material.CAKE_BLOCK){
                    if(e.getBlockPlaced().getType() == Material.TNT){
                        e.getBlock().setType(Material.AIR);
                        TNTPrimed tnt = (TNTPrimed)e.getBlock().getLocation().getWorld().spawnEntity(e.getBlock().getLocation(), EntityType.PRIMED_TNT);
                        tnt.setFuseTicks(30);
                    } else {
                        e.setCancelled(true);
                    }
                }
            } else {
                e.setCancelled(true);
            }
        } else {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e){
        Player p = e.getPlayer();
        ChestUser u = ChestUser.getUser(p);
        DuelPlayer d = DuelPlayer.get(p);
        DuelGame g = d.getGame();

        if(g != null){
            if(g.phase == DuelGame.Phase.INGAME){
                if(e.getBlock().getType() != Material.LEAVES && e.getBlock().getType() != Material.LEAVES_2 && e.getBlock().getType() != Material.VINE && e.getBlock().getType() != Material.LONG_GRASS){
                    e.setCancelled(true);
                }
            } else {
                e.setCancelled(true);
            }
        } else {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onLoad(WorldInitEvent e){
        Bukkit.getScheduler().scheduleSyncDelayedTask(SGDuels.getInstance(), new Runnable() {
            @Override
            public void run() {
                World w = e.getWorld();

                w.setTime(0);
                w.setGameRuleValue("doFireTick","false");
                w.setGameRuleValue("doDaylightCycle","false");
                w.setGameRuleValue("doMobSpawning","false");
                w.setGameRuleValue("doTileDrops","false");
                w.setGameRuleValue("mobGriefing","false");

                for(Entity entity : w.getEntities()){
                    if(entity.getType() != EntityType.PLAYER && entity.getType() != EntityType.ITEM_FRAME && entity.getType() != EntityType.ARMOR_STAND && entity.getType() != EntityType.PAINTING) entity.remove();
                }

                DuelGame game = DuelGame.getGameByWorld(w);
                if(game != null){
                    game.startGame();
                } else {
                    System.out.println("World " + w.getName() + " has no game!");
                }
            }
        },20);
    }
}
