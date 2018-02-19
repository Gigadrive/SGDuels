package eu.thechest.sgduels;

import eu.thechest.chestapi.maps.Map;
import eu.thechest.chestapi.mysql.MySQLManager;
import eu.thechest.chestapi.server.GameState;
import eu.thechest.chestapi.server.GameType;
import eu.thechest.chestapi.server.ServerSettingsManager;
import eu.thechest.sgduels.user.DuelPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;

public class SGDuels extends JavaPlugin {
    private static SGDuels instance;

    public static ArrayList<Map> MAPS;
    public ArrayList<Material> DISALLOWED_BLOCKS = new ArrayList<Material>();

    public void onEnable(){
        instance = this;

        ServerSettingsManager.updateGameState(GameState.JOINABLE);
        ServerSettingsManager.RUNNING_GAME = GameType.SG_DUELS;
        ServerSettingsManager.setMaxPlayers(24);
        ServerSettingsManager.VIP_JOIN = false;
        ServerSettingsManager.SHOW_LEVEL_IN_EXP_BAR = false;
        ServerSettingsManager.SHOW_FAME_TITLE_ABOVE_HEAD = false;
        ServerSettingsManager.PROTECT_ITEM_FRAMES = false;
        ServerSettingsManager.PROTECT_FARMS = false;
        ServerSettingsManager.PROTECT_ARMORSTANDS = true;
        ServerSettingsManager.ENABLE_NICK = true;
        ServerSettingsManager.ARROW_TRAILS = true;
        ServerSettingsManager.KILL_EFFECTS = true;
        ServerSettingsManager.MAP_VOTING = false;
        ServerSettingsManager.ADJUST_CHAT_FORMAT = false;

        //DISALLOWED_BLOCKS.add(Material.BREWING_STAND);
        //DISALLOWED_BLOCKS.add(Material.FURNACE);
        //DISALLOWED_BLOCKS.add(Material.BURNING_FURNACE);
        //DISALLOWED_BLOCKS.add(Material.WORKBENCH);
        //DISALLOWED_BLOCKS.add(Material.TRAP_DOOR);
        DISALLOWED_BLOCKS.add(Material.CHEST);
        DISALLOWED_BLOCKS.add(Material.TRAPPED_CHEST);
        //DISALLOWED_BLOCKS.add(Material.FENCE_GATE);
        //DISALLOWED_BLOCKS.add(Material.SPRUCE_FENCE_GATE);
        //DISALLOWED_BLOCKS.add(Material.BIRCH_FENCE_GATE);
        //DISALLOWED_BLOCKS.add(Material.JUNGLE_FENCE_GATE);
        //DISALLOWED_BLOCKS.add(Material.DARK_OAK_FENCE_GATE);
        //DISALLOWED_BLOCKS.add(Material.ACACIA_FENCE_GATE);
        DISALLOWED_BLOCKS.add(Material.DIODE_BLOCK_OFF);
        DISALLOWED_BLOCKS.add(Material.DIODE_BLOCK_ON);
        DISALLOWED_BLOCKS.add(Material.REDSTONE_COMPARATOR_OFF);
        DISALLOWED_BLOCKS.add(Material.REDSTONE_COMPARATOR_ON);
        DISALLOWED_BLOCKS.add(Material.HOPPER);
        DISALLOWED_BLOCKS.add(Material.DROPPER);
        DISALLOWED_BLOCKS.add(Material.DISPENSER);
        DISALLOWED_BLOCKS.add(Material.BED_BLOCK);
        DISALLOWED_BLOCKS.add(Material.BEACON);
        DISALLOWED_BLOCKS.add(Material.ANVIL);
        //DISALLOWED_BLOCKS.add(Material.ENCHANTMENT_TABLE);
        //DISALLOWED_BLOCKS.add(Material.STONE_BUTTON);
        //DISALLOWED_BLOCKS.add(Material.WOOD_BUTTON);
        DISALLOWED_BLOCKS.add(Material.JUKEBOX);
        DISALLOWED_BLOCKS.add(Material.NOTE_BLOCK);
        DISALLOWED_BLOCKS.add(Material.LEVER);

        Bukkit.getPluginManager().registerEvents(new MainListener(), this);

        MAPS = new ArrayList<Map>();

        try {
            PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("SELECT * FROM `maps` WHERE `mapType` = ? AND `active` = ?");
            ps.setString(1,"SG_DUELS");
            ps.setBoolean(2,true);
            ResultSet rs = ps.executeQuery();

            while(rs.next()){
                MAPS.add(Map.getMap(rs.getInt("id")));
            }

            MySQLManager.getInstance().closeResources(rs,ps);
        } catch(Exception e){
            e.printStackTrace();
        }

        if(MAPS.size() == 0){
            System.err.println("=========================");
            System.err.println("COULD NOT LOAD ANY MAPS!");
            System.err.println("SHUTTING DOWN!");
            System.err.println("=========================");

            Bukkit.shutdown();
        }
    }

    public void onDisable(){
        for(Player all : Bukkit.getOnlinePlayers()) DuelPlayer.unregister(all);
    }

    public static SGDuels getInstance(){
        return instance;
    }
}
