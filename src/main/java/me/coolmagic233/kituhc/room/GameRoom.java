package me.coolmagic233.kituhc.room;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockBeacon;
import cn.nukkit.block.BlockWood;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.Level;

import cn.nukkit.level.Location;
import cn.nukkit.level.biome.Biome;
import cn.nukkit.level.biome.impl.ocean.OceanBiome;
import cn.nukkit.math.Vector2;
import cn.nukkit.potion.Effect;
import lombok.Data;
import cn.nukkit.network.protocol.types.DisplaySlot;
import cn.nukkit.scoreboard.scoreboard.Scoreboard;
import me.coolmagic233.kituhc.Kits;
import me.coolmagic233.kituhc.Main;

import java.io.File;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
public class GameRoom {
    private List<Player> activePlayers = new CopyOnWriteArrayList<>();
    private List<Player> deathPlayers = new CopyOnWriteArrayList<>();
    private ArrayBlockingQueue<Player> deathQueue = new ArrayBlockingQueue<>(10);
    private ArrayBlockingQueue<Level> resetQueue = new ArrayBlockingQueue<>(10);
    private Level level;
    private Map<Player, Scoreboard> scoreboards = new HashMap<>();
    private BorderChecker borderChecker;
    private Map<Player,Location> lastLocation = new HashMap<>();
    private Map<Player,Kits> kits = new HashMap<>();
    private Map<Player,Player> lastDamager = new HashMap<>();
    private GameStatus gameStatus = GameStatus.INIT;
    private boolean kitChoose = true;
    private boolean protect = true;
    private boolean gameLoop = true;
    private int time;
    private int borderPauseTime = 20 * 3;
    private int rewardsRefresh;
    private int monkTimer;
    private String levelName;

    public void sendMessageAll(String text){
        getActivePlayers().forEach(player -> player.sendMessage(text));
        getDeathPlayers().forEach(player -> player.sendMessage(text));
    }

    public void sendMessage(String text){
        getAllPlayers().forEach(player -> player.sendMessage(text));
    }

    public void sendActionBar(String text){
        getActivePlayers().forEach(player -> player.sendActionBar(text));
    }

    public Scoreboard getScoreboard(Player player){
        return scoreboards.computeIfAbsent(player, p -> new Scoreboard("KitUHC-" + p.getName(), "KitUHC"));
    }

    public void hideScoreboard(Player player){
        Scoreboard scoreboard = scoreboards.remove(player);
        if (scoreboard != null){
            scoreboard.removeViewer(player, DisplaySlot.SIDEBAR);
        }
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

    public String getKitDesc(Kits kits){
        switch (kits){
            case MINER -> {
                return "挖掘矿物掉落随机奖励品";
            }
            case TANK -> {
                return "死亡时50%概率原地复活";
            }
            case MONK -> {
                return "血量低于50%时每5s获得一个金苹果";
            }
            case SHOOTER -> {
                return "弓箭射中玩家获得一支箭矢\n开局获得弓+5箭矢";
            }
            case SOLDIER -> {
                return "击杀玩家掉落双倍物资";
            }
        }
        return "";
    }

    public void startGameLoop(){
        Main.getInstance().getExecutor().execute(()->{
            while (gameLoop){
                try {


                    for (Player player : getAllPlayers()) {
                        Scoreboard scoreboard = getScoreboard(player);
                        List<String> lines = new ArrayList<>();
                        lines.add("§7房间: " + getLevelName());
                        if (gameStatus == GameStatus.GAME){
                            lines.add("边界: " + (int) borderChecker.get());
                            lines.add("职业: " + getKitName(kits.get(player)));
                            lines.add("存活玩家: "+getActivePlayers().size());
                        }else {
                            lines.add("等待玩家中...");
                        }
                        scoreboard.setLines(lines);
                        scoreboard.addViewer(player, DisplaySlot.SIDEBAR);
                    }

                    if (gameStatus == GameStatus.WAIT){

                        for (Player player : getAllPlayers()) {
                            if(player.isInsideOfSolid()){
                                for (int i = (int) player.getLocation().getY(); i < 255 ; i ++){
                                    Block block = player.getLevel().getBlock(player.getLocation().setY(i));
                                    if (block.isAir()){
                                        player.teleport(block);
                                    }
                                }
                            }
                        }

                        if (!checkPlayersCount()){
                            getActivePlayers().forEach(player -> player.sendActionBar("等待游戏开始"));
                            time = 10;
                            Thread.sleep(1000);
                            continue;
                        }
                        if (time < 1){
                            setGameStatus(GameStatus.GAME);
                            for (Player player : getActivePlayers()) {
                                player.setGamemode(0);
                                player.getInventory().clearAll();
                                player.addEffect(Effect.getEffect(27).setDuration(20 * 30));
                                player.setHealth(player.getMaxHealth());
                                player.getFoodData().setFoodLevel(player.getFoodData().getMaxLevel());
                                player.getInventory().addItem(Item.get(ItemID.STONE_SWORD));
                                player.getInventory().addItem(Item.get(ItemID.STONE_PICKAXE));
                                player.getInventory().addItem(Item.get(ItemID.STONE_AXE));
                                if (getKits().get(player) == null){
                                    getKits().put(player,Kits.values()[Main.RANDOM.nextInt(Kits.values().length - 1)]);
                                    player.sendMessage("本局你未选择职业，将随机分配职业为：§b" + getKitName(getKits().get(player)) + " §7- " + getKitDesc(getKits().get(player)));
                                }else {
                                    player.sendMessage("本局你的职业为：§b" + getKitName(getKits().get(player)) + " §7- " + getKitDesc(getKits().get(player)));
                                }

                                player.sendMessage("游戏开始!");
                                if (getKits().get(player) == Kits.SHOOTER){
                                    player.getInventory().addItem(Item.get(ItemID.BOW));
                                    player.getInventory().addItem(Item.get(ItemID.ARROW, 0, 32));
                                }
                            }
                            distributePlayers(getActivePlayers(),level);
                            for (Player player : getActivePlayers()) {
                                player.getInventory().addItem(Item.get(new BlockWood().getId(),0,20));
                            }
                            Thread.sleep(1000);
                            continue;
                        }
                        sendActionBar(String.format("游戏还有%s秒开始", time));
                        time --;

                    }
                    if (gameStatus == GameStatus.GAME){
                        Player poll = deathQueue.poll();
                        while (poll != null){
                            for (Item item : poll.getInventory().getContents().values()) {
                                poll.getLevel().dropItem(poll.getLocation(),item);
                            }
                            Player damager = lastDamager.get(poll);
                            if (damager != null && kits.get(damager) == Kits.SOLDIER){
                                for (Item item : poll.getInventory().getContents().values()) {
                                    poll.getLevel().dropItem(poll.getLocation(),item.clone());
                                }
                            }
                            poll.getInventory().clearAll();
                            poll.setGamemode(3);
                            getActivePlayers().remove(poll);
                            getDeathPlayers().add(poll);
                            sendMessageAll(poll.getName() + " 阵亡了。");
                            poll = deathQueue.poll();
                        }
                        setKitChoose(false);

                        monkTimer ++;
                        if (monkTimer >= 5){
                            monkTimer = 0;
                            for (Player player : getActivePlayers()) {
                                Kits kit = kits.get(player);
                                if (kit == Kits.MONK){
                                    if (player.getHealth() < player.getMaxHealth() / 2){
                                        player.getInventory().addItem(Item.get(ItemID.GOLDEN_APPLE));
                                    }
                                }
                            }
                        }

                        if (time == 60*5){
                            protect = false;
                            sendMessageAll("无敌保护结束，请各位玩家小心。");
                        }

                        if (time >= 60 * 5){
                            rewardsRefresh ++;
                            if (rewardsRefresh >= 60 * 2){
                                Vector2 vector2 = borderChecker.getRandomVectorOfInSideBorder(20);
                                int y = getLevel().getMaxBlockY();
                                while (y > 0){
                                    y --;
                                    Block block = getLevel().getBlock((int) vector2.x, y, (int) vector2.y);
                                    if (!block.isAir()){
                                        Location add = block.getLocation().add(0, 1, 0);
                                        getLevel().setBlock(add,new BlockBeacon());
                                        for (Player player : getAllPlayers()) {
                                            player.sendMessage("§e资源兑换点在坐标§a"+(int) add.getX()+" "+(int) add.getY()+" "+(int) add.getZ()+" §e刷新");
                                        }
                                        rewardsRefresh = 0;
                                        break;
                                    }
                                }
                            }
                        }


                        if (borderChecker.get() > 20){
                            borderChecker.shrink(2);
                        }else {
                            if (borderPauseTime > 0){
                                borderPauseTime --;
                            }else {
                                borderChecker.setBorder(0,0,0,0);
                            }
                        }


                        for (Player player : getActivePlayers()) {
                            for (Player activePlayer : getActivePlayers()) {
                                if (player.getName().equals(activePlayer.getName())) continue;
                                Location location = lastLocation.get(activePlayer);
                                if (location == null) break;
                                if (player.getLocation().distance(activePlayer.getLocation()) < 50){
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
                                player.setNameTag(player.getName() + "\n §b职业: " + getKitName(kit));
                            }
                            if (borderChecker.isOutsideBorder(player)){
                                player.sendActionBar("§c你正在边界外，请尽快返回安全区域！");
                                player.attack(1);
                            }
                        }

                        if (getAllPlayers().size() <= 1 || getActivePlayers().size() == 1){
                            if (getAllPlayers().size() == 1){
                                sendMessage(getActivePlayers().getFirst().getName() + "最终存活下来。");
                            }
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

                            String winnerName = "";
                            if (!getActivePlayers().isEmpty()){
                                winnerName = getActivePlayers().getFirst().getName();
                            }

                            for (Player player : getAllPlayers()) {
                                if (player.getLevel().getName().equals(levelName)){
                                    player.setGamemode(Main.getInstance().getServer().getDefaultGamemode());
                                    player.getInventory().setContents(RoomManager.playerContents.get(player));
                                    player.setNameTag(player.getName());
                                    RoomManager.playerContents.remove(player);
                                    player.removeAllEffects();
                                    hideScoreboard(player);
                                    player.teleport(Main.getInstance().getServer().getDefaultLevel().getSpawnLocation());
                                    Main.executeCommands("game-end-commands", java.util.Map.of("%player%", player.getName(), "%winner%", winnerName), player);
                                }
                            }

//                            getResetQueue().offer(level);

                            getActivePlayers().clear();
                            getDeathPlayers().clear();

                            setGameStatus(GameStatus.INIT);
                            continue;
                        }
                    }

                    if (gameStatus == GameStatus.INIT){
                        Main.getInstance().getServer().getScheduler().scheduleDelayedTask(Main.getInstance(),()->{
                            if (Main.getInstance().getServer().isLevelLoaded(levelName)){
                                Main.getInstance().getServer().unloadLevel(this.level);
                                setLevel(null);
                            }
                        },20 * 4);
                        Thread.sleep(6000);
                        Main.deleteDir(new File("./worlds/"+levelName));
                        Main.getInstance().getServer().getScheduler().scheduleDelayedTask(Main.getInstance(),()->{
                            Main.getInstance().getServer().generateLevel(levelName);
                        },20);
                        Thread.sleep(6000);
                        Level newLevel = Main.getInstance().getServer().getLevelByName(levelName);
//                        for (int i = 0; i < 5; i++) {
//                            if (newLevel == null){
//                                Main.getInstance().getServer().getScheduler().scheduleDelayedTask(new PluginTask(Main.getInstance()) {
//                                    @Override
//                                    public void onRun(int i) {
//                                        Main.getInstance().getServer().generateLevel(levelName);
//                                    }
//                                },1);
//                                Thread.sleep(1000);
//                                newLevel = Main.getInstance().getServer().getLevelByName(levelName);
//                                continue;
//                            }
//                            break;
//                        }
                        if (newLevel == null) {
                            throw new RuntimeException();
                        }
                        setLevel(newLevel);
                        getLevel().gameRules.setGameRule(GameRule.SHOW_COORDINATES,true);
                        setKitChoose(true);
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
        double minDistance = Math.max(150, Math.min(600, 1800 / Math.sqrt(playerCount)));
        double radius = Math.max(700, minDistance * Math.sqrt(playerCount) * 1.4);
        List<Vector2> points = new ArrayList<>();

        while (points.size() < playerCount && minDistance >= 80) {
            points = generatePoissonDiskPoints(level, playerCount, minDistance, radius);
            minDistance *= 0.85;
            radius *= 1.1;
        }

        while (points.size() < playerCount) {
            Vector2 point = findRandomSpawnPoint(level, points, 0, radius);
            if (point == null) break;
            points.add(point);
            radius *= 1.1;
        }

        if (points.isEmpty()) return;
        Collections.shuffle(points, Main.RANDOM);

        for (int i = 0; i < playerCount; i++) {
            Vector2 point = points.get(i % points.size());
            players.get(i).teleport(new Location(point.x, 150, point.y, level));
        }
    }

    private List<Vector2> generatePoissonDiskPoints(Level level, int count, double minDistance, double radius) {
        List<Vector2> points = new ArrayList<>();
        List<Vector2> activePoints = new ArrayList<>();
        Vector2 firstPoint = findRandomSpawnPoint(level, points, minDistance, radius);
        if (firstPoint == null) return points;

        points.add(firstPoint);
        activePoints.add(firstPoint);

        while (!activePoints.isEmpty() && points.size() < count) {
            int activeIndex = Main.RANDOM.nextInt(activePoints.size());
            Vector2 activePoint = activePoints.get(activeIndex);
            boolean found = false;

            for (int i = 0; i < 30; i++) {
                double angle = Main.RANDOM.nextDouble() * Math.PI * 2;
                double distance = minDistance * (1 + Main.RANDOM.nextDouble());
                double x = activePoint.x + Math.cos(angle) * distance;
                double z = activePoint.y + Math.sin(angle) * distance;

                if (isValidSpawnPoint(level, x, z, points, minDistance, radius)) {
                    Vector2 point = new Vector2(x, z);
                    points.add(point);
                    activePoints.add(point);
                    found = true;
                    break;
                }
            }

            if (!found) {
                activePoints.remove(activeIndex);
            }
        }

        return points;
    }

    private Vector2 findRandomSpawnPoint(Level level, List<Vector2> points, double minDistance, double radius) {
        for (int i = 0; i < 1000; i++) {
            double angle = Main.RANDOM.nextDouble() * Math.PI * 2;
            double distance = Math.sqrt(Main.RANDOM.nextDouble()) * radius;
            double x = Math.cos(angle) * distance;
            double z = Math.sin(angle) * distance;

            if (isValidSpawnPoint(level, x, z, points, minDistance, radius)) {
                return new Vector2(x, z);
            }
        }

        return null;
    }

    private boolean isValidSpawnPoint(Level level, double x, double z, List<Vector2> points, double minDistance, double radius) {
        if (x * x + z * z > radius * radius) return false;
        if (Biome.getBiome(level.getBiomeId((int) x, (int) z)) instanceof OceanBiome) return false;

        double minDistanceSquared = minDistance * minDistance;
        for (Vector2 point : points) {
            double dx = point.x - x;
            double dz = point.y - z;
            if (dx * dx + dz * dz < minDistanceSquared) return false;
        }

        return true;
    }

    public Item getRandomReward(String key){
        List<String> list = Main.getInstance().rewardConfig.getStringList(key);
        if (list.isEmpty()) return null;
        String s = list.get(Main.RANDOM.nextInt(list.size() - 1));
        String[] split = s.split(",");
        String nameSpaceId = split[0];
        int count;
        try{
            count = Integer.parseInt(split[1]);
        }catch (Exception e){
            count = 1;
        }
        Item item = Item.fromString(nameSpaceId);
        item.setCount(count);
        return item;
    }



}
