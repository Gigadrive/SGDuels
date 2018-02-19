package eu.thechest.sgduels.user;

import eu.thechest.chestapi.ChestAPI;
import eu.thechest.chestapi.mysql.MySQLManager;
import eu.thechest.chestapi.user.ChestUser;
import eu.thechest.chestapi.user.Rank;
import eu.thechest.chestapi.util.StringUtils;
import eu.thechest.sgduels.DuelGame;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.UUID;

public class DuelPlayer {
    public static HashMap<Player,DuelPlayer> STORAGE = new HashMap<Player,DuelPlayer>();

    public static DuelPlayer get(Player p){
        if(STORAGE.containsKey(p)){
            return STORAGE.get(p);
        } else {
            new DuelPlayer(p);

            if(STORAGE.containsKey(p)){
                return STORAGE.get(p);
            } else {
                return null;
            }
        }
    }

    public static void unregister(Player p){
        if(STORAGE.containsKey(p)){
            STORAGE.get(p).saveData();

            STORAGE.remove(p);
        }
    }

    private Player p;
    private int startElo;
    private int startKills;
    private int startDeaths;
    private int startPlayedGames;
    private int startVictories;
    private int startChestsOpened;

    private int elo;
    private int kills;
    private int deaths;
    private int playedGames;
    private int victories;
    private int chestsOpened;

    public DuelPlayer(Player p){
        if(STORAGE.containsKey(p)) return;
        this.p = p;

        try {
            PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("SELECT * FROM `sgduels_stats` WHERE `uuid` = ?");
            ps.setString(1,p.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();

            if(rs.first()){
                this.startElo = rs.getInt("elo");
                this.startKills = rs.getInt("kills");
                this.startDeaths = rs.getInt("deaths");
                this.startPlayedGames = rs.getInt("playedGames");
                this.startVictories = rs.getInt("victories");
                this.startChestsOpened = rs.getInt("chestsOpened");

                STORAGE.put(p,this);
            } else {
                PreparedStatement insert = MySQLManager.getInstance().getConnection().prepareStatement("INSERT INTO `sgduels_stats` (`uuid`) VALUES(?);");
                insert.setString(1,p.getUniqueId().toString());
                insert.executeUpdate();
                insert.close();

                new DuelPlayer(p);
            }

            MySQLManager.getInstance().closeResources(rs,ps);
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public Player getBukkitPlayer(){
        return this.p;
    }

    public ChestUser getUser(){
        return ChestUser.getUser(p);
    }

    public int getElo(){
        return this.startElo+this.elo;
    }

    public void addElo(int points){
        for(int i = 0; i < points; i++){
            //if((startElo+this.elo+i)<=0) break;

            this.elo++;
        }
    }

    public void reduceElo(int points){
        for(int i = 0; i < points; i++){
            if((startElo+this.elo+(i/-1))<=0) break;

            this.elo--;
        }
    }

    public int getKills(){
        return this.startKills+this.kills;
    }

    public void addKills(int i){
        this.kills += i;
    }

    public int getDeaths(){
        return this.startDeaths+this.deaths;
    }

    public void addDeaths(int i){
        this.deaths += i;
    }

    public int getPlayedGames(){
        return this.startPlayedGames+this.playedGames;
    }

    public void addPlayedGames(int i){
        this.playedGames += i;
    }

    public int getVictories(){
        return this.startVictories+this.victories;
    }

    public void addVictories(int i){
        this.victories += i;
    }

    public int getOpenedChests(){
        return this.startChestsOpened+this.chestsOpened;
    }

    public void addOpenedChests(int i){
        this.chestsOpened += i;
    }

    public DuelGame getGame(){
        return DuelGame.getGameByPlayer(p);
    }

    public void handleVanishing(){
        for(Player all : Bukkit.getOnlinePlayers()){
            if(getGame() != null){
                if(getGame().phase == DuelGame.Phase.PREGAME){
                    p.hidePlayer(all);
                } else {
                    if(getGame().isInGame(all)){
                        p.showPlayer(all);
                    } else {
                        p.hidePlayer(all);
                    }
                }
            } else {
                p.hidePlayer(all);
            }
        }
    }

    public void handleAchievements(){
        if(getVictories() >= 10) getUser().achieve(57);
        if(getVictories() >= 25) getUser().achieve(58);
        if(getVictories() >= 50) getUser().achieve(59);
    }

    public void updateScoreboard(){
        updateScoreboard(false);
    }

    public void updateScoreboard(boolean displayNameOnly){
        DuelGame g = getGame();
        ChestUser u = getUser();

        if(g != null){
            if(g.phase == DuelGame.Phase.INGAME || g.phase == DuelGame.Phase.WARMUP){
                String displayName = ChatColor.DARK_RED + "SGDuels " + ChatColor.GREEN + StringUtils.secondsToString(g.timeLeft);

                if(displayNameOnly){
                    if(u.getScoreboard().getObjective(DisplaySlot.SIDEBAR) != null) u.getScoreboard().getObjective(DisplaySlot.SIDEBAR).setDisplayName(displayName);
                } else {
                    Player opponent = null;
                    if(g.isInTeam1(p)){
                        opponent = Bukkit.getPlayer(UUID.fromString(g.getTeam2().get(0)));
                    } else {
                        opponent = Bukkit.getPlayer(UUID.fromString(g.getTeam1().get(0)));
                    }

                    if(opponent == null){
                        u.clearScoreboard();
                        return;
                    }

                    Objective o = u.getScoreboard().registerNewObjective("side","dummy");
                    o.setDisplayName(displayName);
                    o.setDisplaySlot(DisplaySlot.SIDEBAR);

                    if(g.isRanked()){
                        o.getScore(" ").setScore(12);
                        o.getScore(ChatColor.AQUA.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("Your opponent") + ":").setScore(11);
                        if(u.hasPermission(Rank.VIP)){
                            o.getScore(opponent.getName()).setScore(10);
                        } else {
                            o.getScore(ChatColor.stripColor(opponent.getDisplayName())).setScore(10);
                        }
                        o.getScore("  ").setScore(9);
                        o.getScore(ChatColor.AQUA.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("Map") + ":").setScore(8);
                        o.getScore(StringUtils.limitString(g.map.getName(),16)).setScore(7);
                        o.getScore("   ").setScore(6);
                        o.getScore(ChatColor.AQUA.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("Mode") + ":").setScore(5);
                        o.getScore(ChatColor.RED + u.getTranslatedMessage("Ranked")).setScore(4);
                        u.setPlayerSuffix(ChatColor.RED + u.getTranslatedMessage("Ranked"),ChatColor.GRAY + " [" + getElo() + " Elo]");
                        o.getScore("    ").setScore(3);
                        o.getScore(StringUtils.SCOREBOARD_LINE_SEPERATOR).setScore(2);
                        o.getScore(StringUtils.SCOREBOARD_FOOTER_IP).setScore(1);
                    } else {
                        o.getScore(" ").setScore(12);
                        o.getScore(ChatColor.AQUA.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("Your opponent") + ":").setScore(11);
                        if(u.hasPermission(Rank.VIP)){
                            o.getScore(opponent.getName()).setScore(10);
                        } else {
                            o.getScore(ChatColor.stripColor(opponent.getDisplayName())).setScore(10);
                        }
                        o.getScore("  ").setScore(9);
                        o.getScore(ChatColor.AQUA.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("Map") + ":").setScore(8);
                        o.getScore(StringUtils.limitString(g.map.getName(),16)).setScore(7);
                        o.getScore("   ").setScore(6);
                        o.getScore(ChatColor.AQUA.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("Mode") + ":").setScore(5);
                        o.getScore(ChatColor.GREEN + u.getTranslatedMessage("Unranked")).setScore(4);
                        o.getScore("    ").setScore(3);
                        o.getScore(StringUtils.SCOREBOARD_LINE_SEPERATOR).setScore(2);
                        o.getScore(StringUtils.SCOREBOARD_FOOTER_IP).setScore(1);
                    }
                }
            } else {
                u.clearScoreboard();
            }
        } else {
            u.clearScoreboard();
        }
    }

    public void saveData(){
        ChestAPI.async(() -> {
            try {
                PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("UPDATE `sgduels_stats` SET `elo`=`elo`+?, `monthlyElo`=`monthlyElo`+?, `kills`=`kills`+?, `deaths`=`deaths`+?, `playedGames`=`playedGames`+?, `victories`=`victories`+?, `chestsOpened`=`chestsOpened`+? WHERE `uuid` = ?");
                ps.setInt(1,this.elo);
                ps.setInt(2,this.elo);
                ps.setInt(3,this.kills);
                ps.setInt(4,this.deaths);
                ps.setInt(5,this.playedGames);
                ps.setInt(6,this.victories);
                ps.setInt(7,this.chestsOpened);
                ps.setString(8,p.getUniqueId().toString());
                ps.executeUpdate();
                ps.close();
            } catch(Exception e){
                e.printStackTrace();
            }
        });
    }
}
