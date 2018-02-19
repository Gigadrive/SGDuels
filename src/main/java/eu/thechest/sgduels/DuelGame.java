package eu.thechest.sgduels;

import eu.thechest.chestapi.ChestAPI;
import eu.thechest.chestapi.game.Game;
import eu.thechest.chestapi.game.GameManager;
import eu.thechest.chestapi.maps.Map;
import eu.thechest.chestapi.maps.MapLocationData;
import eu.thechest.chestapi.maps.MapLocationType;
import eu.thechest.chestapi.mysql.MySQLManager;
import eu.thechest.chestapi.server.GameType;
import eu.thechest.chestapi.server.ServerSettingsManager;
import eu.thechest.chestapi.user.ChestUser;
import eu.thechest.chestapi.user.Rank;
import eu.thechest.chestapi.util.BountifulAPI;
import eu.thechest.chestapi.util.StringUtils;
import eu.thechest.sgduels.user.DuelPlayer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class DuelGame {
    public static HashMap<Integer,DuelGame> STORAGE = new HashMap<Integer,DuelGame>();

    private int id;
    private ArrayList<String> team1;
    private ArrayList<String> team2;
    private boolean ranked;

    public Game game;

    public Map map;
    public String mapWorldName;
    public Phase phase = Phase.PREGAME;
    public int warmupCountdown = 10;
    public int teamSize;
    public int timeLeft = 600;

    public ArrayList<String> team1OG;
    public ArrayList<String> team2OG;

    public ArrayList<Integer> schedulers = new ArrayList<Integer>();

    public static DuelGame getGameByWorld(World world){
        return getGameByWorld(world.getName());
    }

    private boolean mapLoaded = false;

    public static DuelGame getGameByWorld(String world){
        for(DuelGame g : STORAGE.values()){
            if(g.mapWorldName.equalsIgnoreCase(world)) return g;
        }

        return null;
    }

    public static DuelGame getGameByPlayer(Player p){
        DuelGame g = null;

        for(DuelGame dg : STORAGE.values()){
            if(dg.isInGame(p)) g = dg;
        }

        if(g == null){
            try {
                PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("SELECT * FROM `sgduels_upcomingGames` WHERE `team1` LIKE '%" + p.getUniqueId().toString() + "%' OR `team2` LIKE '%" + p.getUniqueId().toString() + "%' ORDER BY `time` DESC LIMIT 1");
                ResultSet rs = ps.executeQuery();

                if(rs.first()){
                    int id = rs.getInt("id");
                    String team1 = rs.getString("team1");
                    String team2 = rs.getString("team2");
                    boolean ranked = rs.getBoolean("ranked");

                    while(team1.endsWith(",")) team1 = team1.substring(0,team1.length()-1);
                    while(team2.endsWith(",")) team2 = team2.substring(0,team2.length()-1);

                    if(STORAGE.containsKey(id)){
                        g = STORAGE.get(id);
                    } else {
                        ArrayList<String> t1 = new ArrayList<String>();
                        ArrayList<String> t2 = new ArrayList<String>();

                        for(String s : Arrays.asList(team1.split(","))){
                            if(!s.replace(" ","").isEmpty()) t1.add(s);
                        }

                        for(String s : Arrays.asList(team2.split(","))){
                            if(!s.replace(" ","").isEmpty()) t2.add(s);
                        }

                        g = new DuelGame(id,t1,t2,ranked);
                    }
                }

                MySQLManager.getInstance().closeResources(rs,ps);
            } catch(Exception e){
                e.printStackTrace();
            }
        }

        return g;
    }

    public DuelGame(int id, ArrayList<String> team1, ArrayList<String> team2, boolean ranked){
        if(STORAGE.containsKey(id)) return;
        this.id = id;

        this.team1 = team1;
        this.team2 = team2;

        this.team1OG = team1;
        this.team2OG = team2;

        this.ranked = ranked;

        this.teamSize = team1.size();
        System.out.println(teamSize);

        STORAGE.put(id,this);
    }

    public int getID(){
        return this.id;
    }

    public ArrayList<String> getTeam1(){
        return this.team1;
    }

    public ArrayList<String> getTeam2(){
        return this.team2;
    }

    public boolean isRanked(){
        return this.ranked;
    }

    public void endGame(int team){
        for(int i : schedulers) Bukkit.getScheduler().cancelTask(i);
        schedulers.clear();

        Player winner = null;
        Player loser = null;

        if(game != null){
            game.setCompleted(true);
            game.saveData();
        }

        if(team == 1){
            for(String s : team1OG) game.getWinners().add(UUID.fromString(s));
            winner = Bukkit.getPlayer(UUID.fromString(team1.get(0)));
            loser = Bukkit.getPlayer(UUID.fromString(team2.get(0)));
        } else if(team == 2){
            for(String s : team2OG) game.getWinners().add(UUID.fromString(s));
            winner = Bukkit.getPlayer(UUID.fromString(team2.get(0)));
            loser = Bukkit.getPlayer(UUID.fromString(team1.get(0)));
        }

        phase = Phase.ENDING;

        int points = ChestAPI.calculateRating(DuelPlayer.get(winner).getElo(),DuelPlayer.get(loser).getElo(),1,20);
        int winnerOldElo = DuelPlayer.get(winner).getElo();
        int loserOldElo = DuelPlayer.get(loser).getElo();

        if(isRanked()){
            DuelPlayer.get(winner).addElo(points);
            DuelPlayer.get(loser).reduceElo(points);
        }

        for(Player p : getOnlinePlayers()){
            if(!ChestUser.isLoaded(p)) continue;
            ChestUser u = ChestUser.getUser(p);
            DuelPlayer d = DuelPlayer.get(p);
            d.updateScoreboard();
            d.handleAchievements();

            if((team == 1 && isInTeam1(p)) || team == 2 && isInTeam2(p)){
                u.playVictoryEffect();
                u.giveExp(9);

                p.playSound(p.getEyeLocation(), Sound.LEVEL_UP,2f,1f);
                BountifulAPI.sendTitle(p,10,5*20,10,ChatColor.GOLD.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("VICTORY!"),ChatColor.GRAY + u.getTranslatedMessage("You have won the duel!"));

                if(isRanked()) p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + String.format(u.getTranslatedMessage("You have gained %s elo!"),points));

                d.addVictories(1);
            } else {
                p.playSound(p.getEyeLocation(), Sound.NOTE_BASS,1f,0.5f);
                BountifulAPI.sendTitle(p,10,5*20,10,ChatColor.RED.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("DEFEAT!"),ChatColor.GRAY + u.getTranslatedMessage("You have lost the duel!"));

                if(isRanked()) p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + String.format(u.getTranslatedMessage("You have lost %s elo!"),points));
            }

            String display = winner.getDisplayName();
            if(u.hasPermission(Rank.VIP)) display = ChestUser.getUser(winner).getRank().getColor() + winner.getName();

            p.sendMessage("");
            p.sendMessage(ChatColor.DARK_GREEN + u.getTranslatedMessage("%p has won the Survival Games! Congratulations!").replace("%p",display + ChatColor.DARK_GREEN));
            p.sendMessage("");
        }

        ChestAPI.giveAfterGameCrate(getOnlinePlayers().toArray(new Player[]{}));

        Bukkit.getScheduler().scheduleSyncDelayedTask(SGDuels.getInstance(), new Runnable() {
            @Override
            public void run() {
                if(game != null) for(Player p : getOnlinePlayers()) if(ChestUser.isLoaded(p)) ChestUser.getUser(p).sendGameLogMessage(game.getID());
            }
        },3*20);

        Bukkit.getScheduler().scheduleSyncDelayedTask(SGDuels.getInstance(), new Runnable() {
            @Override
            public void run() {
                for(Player p : getOnlinePlayers()) if(ChestUser.isLoaded(p)) ChestUser.getUser(p).connectToLobby();
            }
        },10*20);

        Bukkit.getScheduler().scheduleSyncDelayedTask(SGDuels.getInstance(), new Runnable() {
            @Override
            public void run() {
                if(game != null) game.setCompleted(true);
                unregister();
            }
        },15*20);
    }

    public ArrayList<String> getOfflinePlayers(){
        ArrayList<String> a = new ArrayList<String>();

        for(String s : team1) if(Bukkit.getPlayer(UUID.fromString(s)) == null) a.add(s);
        for(String s : team2) if(Bukkit.getPlayer(UUID.fromString(s)) == null) a.add(s);

        return a;
    }

    public ArrayList<Player> getOnlinePlayers(){
        ArrayList<Player> a = new ArrayList<Player>();

        for(Player all : Bukkit.getOnlinePlayers()) if(isInGame(all)) a.add(all);

        return a;
    }

    public ArrayList<Player> getOnlinePlayers(int team){
        ArrayList<Player> a = new ArrayList<Player>();

        if(team == 1){
            for(Player all : Bukkit.getOnlinePlayers()) if(isInTeam1(all)) a.add(all);
        } else if(team == 2){
            for(Player all : Bukkit.getOnlinePlayers()) if(isInTeam2(all)) a.add(all);
        }

        return a;
    }

    public boolean isInGame(Player p){
        return isInTeam1(p) || isInTeam2(p) || team1OG.contains(p.getUniqueId().toString()) || team2OG.contains(p.getUniqueId().toString());
    }

    public boolean isInTeam1(Player p){
        return team1.contains(p.getUniqueId().toString());
    }

    public boolean isInTeam2(Player p){
        return team2.contains(p.getUniqueId().toString());
    }

    public void loadMap(){
        if(!mapLoaded){
            mapLoaded = true;
            Collections.shuffle(SGDuels.MAPS);
            map = SGDuels.MAPS.get(0);
            mapWorldName = map.loadMapToServer();
        }
    }

    public void startGame(){
        ChestAPI.async(() -> {
            unregister(false);
            ArrayList<Location> spawnpoints = new ArrayList<Location>();
            for(MapLocationData l : map.getLocations(MapLocationType.SPAWNPOINT)) spawnpoints.add(l.toBukkitLocation(mapWorldName));
            Collections.shuffle(spawnpoints);

            game = GameManager.initializeNewGame(GameType.SG_DUELS,map);
            phase = Phase.WARMUP;

            for(Player p : getOnlinePlayers()){
                ChestUser u = ChestUser.getUser(p);
                DuelPlayer.get(p).handleVanishing();
                DuelPlayer.get(p).addPlayedGames(1);

                game.getParticipants().add(p.getUniqueId());

                Location loc = spawnpoints.get(0);
                ChestAPI.sync(() -> p.teleport(loc));
                spawnpoints.remove(loc);

                u.bukkitReset();

                if(teamSize > 1){
                    for(Player all : getOnlinePlayers()){
                        if((isInTeam1(p) && isInTeam1(all)) || (isInTeam2(p) && isInTeam2(all))){
                            u.setPlayerPrefix(all.getName(), ChatColor.GREEN.toString());
                            u.setPlayerSuffix(all.getName(),"");
                        } else {
                            u.setPlayerPrefix(all.getName(), ChatColor.RED.toString());
                            u.setPlayerSuffix(all.getName(),"");
                        }
                    }
                }
            }

            schedulers.add(new BukkitRunnable(){
                @Override
                public void run() {
                    if(warmupCountdown == 10){
                        for(Player p : getOnlinePlayers()){
                            ChestUser u = ChestUser.getUser(p);

                            BountifulAPI.sendTitle(p,0,2*20,0, ChatColor.DARK_GREEN.toString() + warmupCountdown,"");
                            p.playSound(p.getEyeLocation(), Sound.NOTE_BASS,1f,1f);
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + u.getTranslatedMessage("The game starts in %s seconds!").replace("%s",ChatColor.AQUA + String.valueOf(warmupCountdown) + ChatColor.GOLD));
                        }
                    } else if(warmupCountdown == 5){
                        for(Player p : getOnlinePlayers()){
                            ChestUser u = ChestUser.getUser(p);

                            BountifulAPI.sendTitle(p,0,2*20,0,ChatColor.GREEN.toString() + warmupCountdown,"");
                            p.playSound(p.getEyeLocation(),Sound.NOTE_BASS,1f,1f);
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + u.getTranslatedMessage("The game starts in %s seconds!").replace("%s",ChatColor.AQUA + String.valueOf(warmupCountdown) + ChatColor.GOLD));
                        }
                    } else if(warmupCountdown == 4){
                        for(Player p : getOnlinePlayers()){
                            ChestUser u = ChestUser.getUser(p);

                            BountifulAPI.sendTitle(p,0,2*20,0,ChatColor.YELLOW.toString() + warmupCountdown,"");
                            p.playSound(p.getEyeLocation(),Sound.NOTE_BASS,1f,1f);
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + u.getTranslatedMessage("The game starts in %s seconds!").replace("%s",ChatColor.AQUA + String.valueOf(warmupCountdown) + ChatColor.GOLD));
                        }
                    } else if(warmupCountdown == 3){
                        for(Player p : getOnlinePlayers()){
                            ChestUser u = ChestUser.getUser(p);

                            BountifulAPI.sendTitle(p,0,2*20,0,ChatColor.GOLD.toString() + warmupCountdown,"");
                            p.playSound(p.getEyeLocation(),Sound.NOTE_BASS,1f,1f);
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + u.getTranslatedMessage("The game starts in %s seconds!").replace("%s",ChatColor.AQUA + String.valueOf(warmupCountdown) + ChatColor.GOLD));
                        }
                    } else if(warmupCountdown == 2){
                        for(Player p : getOnlinePlayers()){
                            ChestUser u = ChestUser.getUser(p);

                            BountifulAPI.sendTitle(p,0,2*20,0,ChatColor.RED.toString() + warmupCountdown,"");
                            p.playSound(p.getEyeLocation(),Sound.NOTE_BASS,1f,1f);
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + u.getTranslatedMessage("The game starts in %s seconds!").replace("%s",ChatColor.AQUA + String.valueOf(warmupCountdown) + ChatColor.GOLD));
                        }
                    } else if(warmupCountdown == 1){
                        for(Player p : getOnlinePlayers()){
                            ChestUser u = ChestUser.getUser(p);
                            BountifulAPI.sendTitle(p,0,2*20,0,ChatColor.DARK_RED.toString() + warmupCountdown,"");
                            p.playSound(p.getEyeLocation(),Sound.NOTE_BASS,1f,1f);
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + u.getTranslatedMessage("The game starts in %s second!").replace("%s",ChatColor.AQUA + String.valueOf(warmupCountdown) + ChatColor.GOLD));
                        }
                    } else if(warmupCountdown == 0){
                        phase = Phase.INGAME;

                        for(Player p : getOnlinePlayers()){
                            ChestUser u = ChestUser.getUser(p);

                            BountifulAPI.sendTitle(p,0,4*20,1*20,ChatColor.DARK_GREEN.toString() + u.getTranslatedMessage("GO!"),"");
                            p.playSound(p.getEyeLocation(),Sound.NOTE_PLING,1f,1f);
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + u.getTranslatedMessage("The game starts NOW!"));
                            DuelPlayer.get(p).updateScoreboard();
                        }

                        schedulers.add(new BukkitRunnable(){
                            @Override
                            public void run() {
                                for(Player all : getOnlinePlayers()){
                                    DuelPlayer.get(all).updateScoreboard(true);
                                }

                                if(timeLeft == 300 || timeLeft == 240 || timeLeft == 120 || timeLeft == 60 || timeLeft == 30 || timeLeft == 20 || timeLeft == 10 || timeLeft == 5 || timeLeft == 4 || timeLeft == 3 || timeLeft == 2){
                                    for(Player all : getOnlinePlayers()){
                                        DuelPlayer.get(all).updateScoreboard(true);
                                        all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + ChestUser.getUser(all).getTranslatedMessage("The game ends in %s seconds!").replace("%s",ChatColor.AQUA.toString() + timeLeft + ChatColor.GOLD.toString()));
                                    }
                                } else if(timeLeft == 1){
                                    for(Player all : getOnlinePlayers()){
                                        DuelPlayer.get(all).updateScoreboard(true);
                                        all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + ChestUser.getUser(all).getTranslatedMessage("The game ends in %s second!").replace("%s",ChatColor.AQUA.toString() + timeLeft + ChatColor.GOLD.toString()));
                                    }
                                } else if(timeLeft == 0){
                                    //endGame(StringUtils.randomInteger(1,2));

                                    int t = StringUtils.randomInteger(1,2);
                                    if(t == 1){
                                        Bukkit.getPlayer(UUID.fromString(getTeam1().get(0))).damage(40);
                                    } else if(t == 2){
                                        Bukkit.getPlayer(UUID.fromString(getTeam2().get(0))).damage(40);
                                    }
                                }

                                timeLeft--;
                            }
                        }.runTaskTimer(SGDuels.getInstance(),0,20).getTaskId());

                        cancel();
                    }

                    warmupCountdown--;
                }
            }.runTaskTimer(SGDuels.getInstance(),20,20).getTaskId());

            schedulers.add(new BukkitRunnable(){
                @Override
                public void run() {
                    for(Player all : Bukkit.getOnlinePlayers()){
                        if(all.getGameMode() != GameMode.SURVIVAL) return;
                        Player n = null;

                        if(phase != DuelGame.Phase.INGAME) return;

                        if(isInTeam1(all)){
                            n = Bukkit.getPlayer(UUID.fromString(getTeam2().get(0)));
                        } else if(isInTeam2(all)){
                            n = Bukkit.getPlayer(UUID.fromString(getTeam1().get(0)));
                        }

                        if(n != null){
                            all.setCompassTarget(n.getLocation());
                        }
                    }
                }
            }.runTaskTimer(SGDuels.getInstance(),20,20).getTaskId());
        });
    }

    public void unregister(){
        unregister(true);
    }

    public void unregister(boolean full){
        ChestAPI.async(() -> {
            try {
                PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("DELETE FROM `sgduels_upcomingGames` WHERE `id` = ?");
                ps.setInt(1,id);
                ps.executeUpdate();
                ps.close();
            } catch(Exception e){
                e.printStackTrace();
            }
        });

        if(full){
            if(mapWorldName != null) map.removeMap(mapWorldName);

            if(game != null){
                game.saveData();
                GameManager.getCurrentGames().remove(game);
            }

            if(STORAGE.containsKey(id)) STORAGE.remove(id);
        }
    }

    public enum Phase {
        PREGAME,WARMUP,INGAME,ENDING
    }
}
