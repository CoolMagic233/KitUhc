package me.coolmagic233.kituhc.room;

import cn.nukkit.Player;
import cn.nukkit.block.*;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.ProjectileHitEvent;
import cn.nukkit.event.player.PlayerChatEvent;
import cn.nukkit.event.player.PlayerCommandPreprocessEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.event.server.ServerCommandEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemCompass;
import cn.nukkit.item.ItemPotion;
import cn.nukkit.item.ItemPotionSplash;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.potion.Potion;
import me.coolmagic233.kituhc.Kits;
import me.coolmagic233.kituhc.Main;
import me.iwareq.scoreboard.Scoreboard;
import me.iwareq.scoreboard.packet.data.DisplaySlot;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public class RoomManager implements Listener {
    public static List<GameRoom> rooms = new CopyOnWriteArrayList<>();
    public static Map<Player,Map<Integer, Item>> playerContents = new HashMap<>();
    public static int WAIT_TIME = 10;

    public static void join(Player player){
        for (GameRoom room : rooms) {
            if (room.getAllPlayers().contains(player)){
                return;
            }
            if (room.getGameStatus() == GameStatus.WAIT){
                room.getActivePlayers().add(player);
                room.getActivePlayers().forEach(p -> p.sendMessage(p.getName() + " 加入了游戏"));
                player.sendMessage("请选择你的游戏职业(在聊天栏输入对应的序号): \n [1]矿工 [2]法师 [3]坦克 [4]射手 [5]战士");
                player.teleport(room.getLevel().getSafeSpawn());
                player.setGamemode(2);
                player.removeAllEffects();
                room.getKits().put(player,Kits.MINER);
                playerContents.put(player,player.getInventory().getContents());
                player.getInventory().clearAll();
                return;
            }

        }
        delayJoin(player);
    }

    public static void quit(Player player){
        for (GameRoom room : rooms) {
            if (room.getActivePlayers().contains(player) || room.getDeathPlayers().contains(player)){
                room.getActivePlayers().forEach(p -> p.sendMessage(p.getName() + " 退出了游戏"));
                room.getDeathPlayers().forEach(p -> p.sendMessage(p.getName() + " 退出了游戏"));
                player.teleport(Main.getInstance().getServer().getDefaultLevel().getSpawnLocation());
                player.setGamemode(Main.getInstance().getServer().getDefaultGamemode());
                player.getInventory().setContents(playerContents.get(player));
                playerContents.remove(player);
                player.removeAllEffects();
                room.getScoreboard().hide(player);
                room.getActivePlayers().remove(player);
                room.getDeathPlayers().remove(player);
                return;
            }
        }
    }

    public static boolean inRoom(Player player,GameRoom room){
        return room.getActivePlayers().contains(player) || room.getDeathPlayers().contains(player);
    }

    public static void delayJoin(Player player){
        Main.getInstance().getExecutor().execute(()->{
            try {
            player.sendMessage("正在创建房间，请耐心等待");
            GameRoom gameRoom = create(rooms.size() + 1);
            if (gameRoom.getLevel() != null){
                gameRoom.setGameStatus(GameStatus.WAIT);
            }
            gameRoom.setTime(WAIT_TIME);
            gameRoom.setScoreboard(new Scoreboard("KitUHC", DisplaySlot.SIDEBAR, 0));
            gameRoom.startGameLoop();
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                player.sendMessage("创建房间异常，请联系服务器管理员");
                throw new RuntimeException(e);
            }
            join(player);

        });
    }


    public static GameRoom create(int roomsNumber){
        GameRoom gameRoom = new GameRoom();
        String levelName = "kituhc-room-"+roomsNumber;
        if (Main.getInstance().getServer().getLevelByName(levelName) != null){
            while (Main.getInstance().getServer().getLevelByName(levelName) != null){
                roomsNumber ++;
                levelName = "kituhc-room-"+roomsNumber;
            }
        }
        Main.getInstance().getServer().generateLevel(levelName,new Random().nextInt(100000000), Generator.getGenerator("default"));
        gameRoom.setLevel(Main.getInstance().getServer().getLevelByName(levelName));
        if (gameRoom.getLevel() != null){
            gameRoom.getLevel().gameRules.setGameRule(GameRule.SHOW_COORDINATES,true);
        }
        gameRoom.setLevelName(levelName);
        gameRoom.setBorderChecker(new BorderChecker(-5000,5000,-5000,5000));
        rooms.add(gameRoom);
        return gameRoom;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e){
        quit(e.getPlayer());
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e){
        for (GameRoom room : rooms) {
            if (e.getEntity() instanceof Player player){
                if (!inRoom(player,room)) return;
                if (room.getGameStatus() != GameStatus.GAME){
                e.setCancelled();
                return;
            }
            if (e.getFinalDamage() >= e.getEntity().getHealth()){
                   room.getDeathQueue().offer(player);
                   e.setCancelled();
                }
            }
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e){
        Player player = e.getPlayer();
        if (e.getMessage().equals("kill")){
            for (GameRoom room : rooms) {
                if (!inRoom(player, room)) return;
                if (room.getGameStatus() == GameStatus.GAME) {
                    room.getDeathQueue().offer(player);
                    e.setCancelled();
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e){
        for (GameRoom room : rooms) {
            if (e.getDamager() instanceof Player damager) {
                if (!inRoom(damager, room)) return;
                if (room.getGameStatus() == GameStatus.GAME) {
                    if (room.isProtect()) {
                        damager.sendMessage("你无法在无敌保护时间内攻击其他玩家");
                        e.setCancelled();
                        return;
                    }
                    Kits damager_kit = room.getKits().get(damager);
                    if (damager_kit != null){
                        if (damager_kit == Kits.SHOOTER){
                            if (e.getCause() == EntityDamageEvent.DamageCause.PROJECTILE){
                                e.setDamage((float) (e.getFinalDamage() + e.getFinalDamage() * 0.2));
                            }
                        }
                        if (damager_kit == Kits.SHOOTER){
                            if (damager.getInventory().getItemInHand().isSword()){
                                e.setDamage((float) (e.getFinalDamage() + e.getFinalDamage() * 0.2));
                            }
                        }
                    }
                    if (e.getEntity() instanceof Player player){
                        Kits kit = room.getKits().get(damager);
                        if (kit != null){
                            if (kit == Kits.TANK){
                                e.setDamage((float) ((e.getFinalDamage() - e.getFinalDamage() * 0.2 < 0) ? 0 : e.getFinalDamage() - e.getFinalDamage() * 0.2));
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e){
        if (e.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || e.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_AIR){
            for (GameRoom room : rooms) {
                if (!inRoom(e.getPlayer(),room)) return;
                if (room.getGameStatus() == GameStatus.GAME) {
                    if (e.getItem().getId() == new ItemCompass().getId()){
                        if (new Random().nextInt(100) < 75){
                            Item itemInHand = e.getPlayer().getInventory().getItemInHand();
                            itemInHand.setCount(itemInHand.getCount() - 1);
                            e.getPlayer().getInventory().setItemInHand(itemInHand);
                        }
                        String join = String.join("\n", room.getActivePlayers().stream().map(p -> p.getName() + "->" + (int) p.getLocation().getX() + " " + (int) p.getLocation().getY() + " " + (int) p.getLocation().getZ()).toList().toArray(new String[]{}));
                        e.getPlayer().sendMessage("存活玩家的游戏坐标：\n "+join);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e){
        for (GameRoom room : rooms) {
            if (!inRoom(e.getPlayer(),room)) return;
            if (room.getGameStatus() == GameStatus.GAME){
                if (e.getBlock().getId() == new BlockOreCoal().getId() ||
                        e.getBlock().getId() == new BlockOreCopper().getId() ||
                        e.getBlock().getId() == new BlockOreDiamond().getId() ||
                        e.getBlock().getId() == new BlockOreEmerald().getId() ||
                        e.getBlock().getId() == new BlockOreGold().getId() ||
                        e.getBlock().getId() == new BlockOreGoldNether().getId() ||
                        e.getBlock().getId() == new BlockOreIron().getId() ||
                        e.getBlock().getId() == new BlockOreLapis().getId() ||
                        e.getBlock().getId() == new BlockOreQuartz().getId() ||
                        e.getBlock().getId() == new BlockOreRedstone().getId() ||
                        e.getBlock().getId() == new BlockOreRedstoneGlowing().getId()) {
                    Kits kits = room.getKits().get(e.getPlayer());
                    if (kits != null){
                        if (kits == Kits.MONK){
                            e.getPlayer().getLevel().dropItem(e.getBlock().getLocation(),new ItemPotionSplash(new Random().nextInt(36)));
                        }
                    }
                }
            }
        }
    }


    @EventHandler
    public void onChat(PlayerChatEvent e){
        for (GameRoom room : rooms) {
            if (!inRoom(e.getPlayer(),room)) return;
            if (room.getGameStatus() == GameStatus.WAIT){
                if (room.isKitChoose()){
                    int i = 0;
                    try {
                        i = Integer.parseInt(e.getMessage());
                    }catch (Exception ignore){
                        return;
                    }
                    switch (i) {
                        case 2 -> room.getKits().put(e.getPlayer(), Kits.MONK);
                        case 3-> room.getKits().put(e.getPlayer(), Kits.TANK);
                        case 4-> room.getKits().put(e.getPlayer(), Kits.SHOOTER);
                        case 5-> room.getKits().put(e.getPlayer(), Kits.SOLDIER);
                        default -> room.getKits().put(e.getPlayer(), Kits.MINER);
                    }

                    e.getPlayer().sendMessage("你选择了职业: "+room.getKitName(room.getKits().get(e.getPlayer())));
                    e.setCancelled();
                }
            }
            room.sendMessageAll(e.getPlayer().getName() + "-> " + e.getMessage());
            e.setCancelled();
        }
    }

}
