package me.coolmagic233.kituhc.room;

import cn.nukkit.Player;
import cn.nukkit.block.*;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.*;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.response.FormResponseSimple;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.item.*;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.TextFormat;
import cn.nukkit.utils.Utils;
import me.coolmagic233.kituhc.Kits;
import me.coolmagic233.kituhc.Main;
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
                player.teleport(room.getLevel().getSafeSpawn());
                player.setGamemode(2);
                player.removeAllEffects();
                playerContents.put(player,player.getInventory().getContents());
                player.getInventory().clearAll();
                player.getInventory().setItem(8,Item.get(ItemID.FEATHER).setCustomName("§a退出房间"));
                room.getLevel().setRaining(false);
                FormWindowSimple formWindowSimple = new FormWindowSimple("§l§6选择你的职业", "§7点击选择你想要使用的职业");
                for (Kits kit : Kits.values()) {
                    formWindowSimple.addButton(new ElementButton("§b" + room.getKitName(kit) + "\n§7" + room.getKitDesc(kit)));
                }
                player.showFormWindow(formWindowSimple,Main.FORM_ID_KIT_SELECT);
                return;
            }

        }
        delayJoin(player);
    }

    public static void quit(Player player){
        for (GameRoom room : rooms) {
            if (room.getActivePlayers().contains(player) || room.getDeathPlayers().contains(player)){
                room.getActivePlayers().forEach(p -> p.sendMessage(player.getName() + " 退出了游戏"));
                room.getDeathPlayers().forEach(p -> p.sendMessage(player.getName() + " 退出了游戏"));
                player.teleport(Main.getInstance().getServer().getDefaultLevel().getSpawnLocation());
                player.setGamemode(Main.getInstance().getServer().getDefaultGamemode());
                player.getInventory().setContents(playerContents.get(player));
                playerContents.remove(player);
                player.removeAllEffects();
                room.hideScoreboard(player);
                room.getActivePlayers().remove(player);
                room.getDeathPlayers().remove(player);
                Main.executeCommands("exit-room-commands", java.util.Map.of("%player%", player.getName()), player);
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
        Main.getInstance().getServer().generateLevel(levelName,new Random().nextInt(100000000), Generator.getGenerator(Generator.TYPE_INFINITE));
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
    public void onFormResponse(PlayerFormRespondedEvent e){
        Player player = e.getPlayer();
        for (GameRoom room : rooms) {
            if (!inRoom(player,room)) return;
            if (room.getGameStatus() == GameStatus.WAIT){
                if (e.getFormID() == Main.FORM_ID_KIT_SELECT){
                    if (e.getResponse() instanceof FormResponseSimple response){
                        room.getKits().put(player,Kits.values()[response.getClickedButtonId()]);
                        player.sendMessage("你选择了职业: §b" + room.getKitName(room.getKits().get(player)) + " §7- " + room.getKitDesc(room.getKits().get(player)));
                    }
                }
            }
        }
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
            if (e.getFinalDamage() + 1 >= e.getEntity().getHealth()){
                    Kits kit = room.getKits().get(player);
                    if (kit == Kits.TANK && new Random().nextBoolean()){
                        e.setCancelled();
                        player.setHealth(player.getMaxHealth());
                        room.sendMessageAll(String.format("%s通过坦克之力复活！",e.getEntity().getName()));
                        return;
                    }
                   if (!room.getDeathQueue().contains(player)){
                       room.getDeathQueue().offer(player);
                   }
                   e.setCancelled();
                   return;
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
                        if (e.getEntity() instanceof Player){
                            damager.sendMessage("你无法在无敌保护时间内攻击其他玩家");
                            e.setCancelled();
                            return;
                        }
                    }
                    Kits damager_kit = room.getKits().get(damager);
                    if (damager_kit != null){
                        if (damager_kit == Kits.SHOOTER){
                            if (e.getCause() == EntityDamageEvent.DamageCause.PROJECTILE){
                                damager.getInventory().addItem(Item.get(ItemID.ARROW));
                            }
                        }
                    }
                    if (e.getEntity() instanceof Player player){
                        room.getLastDamager().put(player, damager);
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
                if (room.getGameStatus() == GameStatus.WAIT){
                    if (e.getPlayer().getInventory().getItemInHand().getCustomName().equals("§a退出房间")){
                        quit(e.getPlayer());
                        return;
                    }
                }
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

                    if (e.getBlock().getId() == new BlockBeacon().getId()){
                        Item itemInHand = e.getPlayer().getInventory().getItemInHand();
                        if (itemInHand.hasCompoundTag()){
                            if (itemInHand.getNamedTag().contains("level")){
                                int level = itemInHand.getNamedTag().getInt("level");
                                int count = itemInHand.getCount();
                                String rarity = switch (level) {
                                    case 1 -> "common";
                                    case 2 -> "rare";
                                    case 3 -> "epic";
                                    default -> null;
                                };
                                if (rarity == null) return;
                                Item first = room.getRandomReward(rarity);
                                if (first == null) return;
                                e.getPlayer().getInventory().setItemInHand(Item.get(0));
                                cn.nukkit.level.Location dropLoc = e.getBlock().getLocation().add(0, 1, 0);
                                cn.nukkit.level.Level dropLevel = e.getPlayer().getLevel();
                                for (int i = 0; i < count; i++) {
                                    int delay = 20 * i;
                                    Main.getInstance().getServer().getScheduler().scheduleDelayedTask(Main.getInstance(), () -> {
                                        Item reward = room.getRandomReward(rarity);
                                        if (reward != null) {
                                            dropLevel.dropItem(dropLoc, reward);
                                        }
                                    }, delay);
                                }
                            }
                        }
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
                        if (kits == Kits.MINER){
                            String rarity = null;
                            if (e.getBlock().getId() == new BlockOreIron().getId() || e.getBlock().getId() == new BlockOreCoal().getId() || e.getBlock().getId() == new BlockOreCopper().getId()){
                                rarity = "common";
                            } else if (e.getBlock().getId() == new BlockOreLapis().getId() || e.getBlock().getId() == new BlockOreRedstoneGlowing().getId() || e.getBlock().getId() == new BlockOreQuartz().getId() || e.getBlock().getId() == new BlockOreRedstone().getId()){
                                rarity = "rare";
                            } else if (e.getBlock().getId() == new BlockOreDiamond().getId() || e.getBlock().getId() == new BlockOreGold().getId() || e.getBlock().getId() == new BlockOreEmerald().getId() || e.getBlock().getId() == new BlockOreGoldNether().getId()){
                                rarity = "epic";
                            }
                            if (rarity != null){
                                Item reward = room.getRandomReward(rarity);
                                if (reward != null){
                                    e.getBlock().getLevel().dropItem(e.getBlock().getLocation(), reward);
                                }
                            }
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
            room.sendMessageAll(e.getPlayer().getName() + "-> " + e.getMessage());
            e.setCancelled();
        }
    }
}
