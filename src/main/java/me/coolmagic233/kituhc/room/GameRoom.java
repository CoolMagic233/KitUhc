package me.coolmagic233.kituhc.room;

import cn.nukkit.Player;
import cn.nukkit.level.Level;

import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.potion.Effect;
import cn.nukkit.scheduler.PluginTask;
import lombok.Data;
import me.coolmagic233.kituhc.Kits;
import me.coolmagic233.kituhc.Main;
import me.iwareq.scoreboard.Scoreboard;

import java.io.File;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
public class GameRoom {
    private List<Player> activePlayers = new CopyOnWriteArrayList<>();
    private List<Player> deathPlayers = new CopyOnWriteArrayList<>();
    private Level level;
    private Scoreboard scoreboard;
    private BorderChecker borderChecker;
    private Map<Player,Location> lastLocation = new HashMap<>();
    private Map<Player,Kits> kits = new HashMap<>();
    private GameStatus gameStatus = GameStatus.INIT;
    private boolean kitChoose = true;
    private boolean protect = true;
    private boolean gameLoop = true;
    private int time;
    private String levelName;

    public void sendMessageAll(String text){
        getActivePlayers().forEach(player -> player.sendMessage(text));
        getDeathPlayers().forEach(player -> player.sendMessage(text));
    }

    public void sendMessage(String text){
        getActivePlayers().forEach(player -> player.sendMessage(text));
    }

    public void sendActionBar(String text){
        getActivePlayers().forEach(player -> player.sendActionBar(text));
    }



    public boolean checkPlayersCount(){
        return getActivePlayers().size() >= 2;
    }

    public List<Player> getAllPlayers(){
        List<Player> list = new ArrayList<>();
        list.addAll(getActivePlayers());
        list.addAll(getDeathPlayers());
        return list;
    }

    public String getKitName(Kits kits){
        switch (kits){
            case MINER -> {
                return "矿工";
            }
            case TANK -> {
                return "坦克";
            }
            case MONK -> {
                return "法师";
            }
            case SHOOTER -> {
                return "射手";
            }
            case SOLDIER -> {
                return "战士";
            }
        }
        return "";
    }

    public void startGameLoop(){
        Main.getInstance().getExecutor().execute(()->{
            while (gameLoop){
                try {

                    List<String> list = new ArrayList<>(getActivePlayers().stream().map(player -> player.getName() + " 存活").toList());
                    list.addAll(getDeathPlayers().stream().map(player -> player.getName() + " 阵亡").toList());

                    scoreboard.refresh();
                    scoreboard.setHandler(pl -> {
                        scoreboard.addLine("边界: " + (int) borderChecker.get());
                        for (String s : list) {
                            scoreboard.addLine(s);
                        }
                    });

                    for (Player player : getAllPlayers()) {
                        scoreboard.show(player);
                    }

                    if (gameStatus == GameStatus.WAIT){
                        if (!checkPlayersCount()){
                            getActivePlayers().forEach(player -> player.sendActionBar("等待游戏开始"));
                            Thread.sleep(1000);
                            continue;
                        }
                        if (time < 1){
                            setGameStatus(GameStatus.GAME);
                            sendMessage("游戏开始!");
                            for (Player player : getActivePlayers()) {
                                player.setGamemode(0);
                                player.getInventory().clearAll();
                                player.addEffect(Effect.getEffect(27).setDuration(20 * 30));
                                player.setHealth(player.getMaxHealth());
                                player.getFoodData().setFoodLevel(player.getFoodData().getMaxLevel());
                                //TODO Kit Dev...
                            }
                            distributePlayers(getActivePlayers(),level);

                            Thread.sleep(1000);
                            continue;
                        }
                        sendActionBar(String.format("游戏还有%s秒开始", time));
                        time --;
                        if (!checkPlayersCount()){
                            setGameStatus(GameStatus.WAIT);
                            time = 10;
                        }

                    }
                    if (gameStatus == GameStatus.GAME){
                        setKitChoose(false);
                        if (time == 60*10){
                            protect = false;
                            sendMessageAll("无敌保护结束，请各位玩家小心。");
                        }

                        if (time <= 60 * 25){
                            borderChecker.shrink(2);
                        }

                        for (Player player : getActivePlayers()) {
                            for (Player activePlayer : getActivePlayers()) {
                                if (player.getName().equals(activePlayer.getName())) continue;
                                Location location = lastLocation.get(activePlayer);
                                if (location == null) break;
                                if (player.getLocation().distance(activePlayer.getLocation()) < 100){
                                    if (player.getLocation().distance(activePlayer.getLocation()) < player.getLocation().distance(location)){
                                        player.sendActionBar(activePlayer.getName() + " 正在向你靠近！");
                                        break;
                                    }
                                }
                            }
                        }

                        for (Player player : getActivePlayers()) {
                            lastLocation.put(player,player.getLocation());
                            Kits kit = kits.get(player);
                            if (kit != null){
                                if (kit == Kits.MINER){
                                    boolean hasDigSpeed = false;
                                    for (Effect effect : player.getEffects().values()) {
                                        if (effect.getId() == 3) {
                                            hasDigSpeed = true;
                                            break;
                                        }
                                    }
                                    if (!hasDigSpeed) {
                                        player.addEffect(Effect.getEffectByName("HASTE").setDuration(20*20).setAmplifier(2));
                                    }
                                }
                                player.setNameTag(player.getName() + "\n 职业: " + getKitName(kit));
                            }
                            if (borderChecker.isOutsideBorder(player)){
                                player.attack(1);
                            }
                        }

                        if (getAllPlayers().size() == 1 || getActivePlayers().size() == 1){
                            sendMessage(getActivePlayers().getFirst().getName() + "最终存活下来。");
                            setGameStatus(GameStatus.SETTLEMENT);
                            time = 5;
                            Thread.sleep(1000);
                            continue;
                        }

                        if (getAllPlayers().isEmpty()) {
                            setGameStatus(GameStatus.INIT);
                            Thread.sleep(1000);
                            continue;
                        }


                        time ++;
                    }

                    if (gameStatus == GameStatus.SETTLEMENT){
                        sendMessageAll(String.format("正在结算，游戏结束还有%s秒",time));
                        time --;
                        if (time < 0) {
                            for (Player player : getActivePlayers()) {
                                player.setGamemode(Main.getInstance().getServer().getDefaultGamemode());
                                player.getInventory().setContents(RoomManager.playerContents.get(player));
                                RoomManager.playerContents.remove(player);
                                player.removeAllEffects();
                                getScoreboard().hide(player);
                            }

                            for (Player player : getDeathPlayers()) {
                                player.setGamemode(Main.getInstance().getServer().getDefaultGamemode());
                                player.getInventory().setContents(RoomManager.playerContents.get(player));
                                RoomManager.playerContents.remove(player);
                                player.removeAllEffects();
                                getScoreboard().hide(player);
                            }

                            Main.getInstance().getServer().getScheduler().scheduleDelayedTask(new PluginTask(Main.getInstance()) {
                                @Override
                                public void onRun(int i) {
                                    level.unload(true);
                                }
                            },1);

                            getActivePlayers().clear();
                            getDeathPlayers().clear();

                            setGameStatus(GameStatus.INIT);

                            break;
                        }
                    }

                    if (gameStatus == GameStatus.INIT){
                        Main.deleteDir(new File("./worlds/"+levelName));
                        Level newLevel = Main.getInstance().getServer().getLevelByName(levelName);
                        if (newLevel == null){
                            throw new RuntimeException();
                        }
                        setLevel(newLevel);
                        setTime(RoomManager.WAIT_TIME);
                        setBorderChecker(new BorderChecker(-5000,5000,-5000,5000));
                        setGameStatus(GameStatus.WAIT);
                    }
                    Thread.sleep(1000);
                }catch (Exception e){
                    Main.getInstance().getLogger().error("Running Error!",e);
                    getActivePlayers().forEach(p->p.kick("游戏房间运行时错误"));
                    getDeathPlayers().forEach(p->p.kick("游戏房间运行时错误"));
                    RoomManager.rooms.remove(this);
                    gameLoop = false;
                    break;
                }
            }
        });
    }

    public void distributePlayers(List<Player> players, Level level) {
        if (players.isEmpty()) return;

        int playerCount = players.size();
        double angleIncrement = 2 * Math.PI / playerCount; // 角度增量
        double radius = 500; // 基础半径

        for (int i = 0; i < playerCount; i++) {
            Player player = players.get(i);

            // 计算圆形分布坐标
            double angle = i * angleIncrement;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);

            // 创建位置对象 (Y坐标固定为150)
            Location location = new Location(x, 150, z, level);

            // 传送玩家
            player.teleport(location);

        }
    }



}
