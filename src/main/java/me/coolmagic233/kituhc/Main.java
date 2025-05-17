package me.coolmagic233.kituhc;


import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.level.Level;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import lombok.Getter;
import me.coolmagic233.kituhc.commands.AdminCommand;
import me.coolmagic233.kituhc.commands.DefaultCommand;
import me.coolmagic233.kituhc.room.FastMode;
import me.coolmagic233.kituhc.room.GameRoom;
import me.coolmagic233.kituhc.room.RoomManager;
import me.iwareq.scoreboard.Scoreboard;
import me.iwareq.scoreboard.packet.data.DisplaySlot;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Main extends PluginBase {
    @Getter
    public static Main instance;
    public Scoreboard scoreboard = null;
    @Getter
    public final Executor executor = Executors.newCachedThreadPool();
    @Override
    public void onEnable(){
        instance = this;
        scoreboard = new Scoreboard("uhc", DisplaySlot.SIDEBAR, 20);
        getServer().getCommandMap().register("",new AdminCommand());
        getServer().getCommandMap().register("",new DefaultCommand());
        getServer().getPluginManager().registerEvents(new RoomManager(),this);
        for (Level level : getServer().getLevels().values()) {
            if (level.getName().startsWith("kituhc-room")){
                level.unload();
                File file = new File("./worlds/"+level.getFolderName());
                deleteDir(file);
            }
        }

//        getServer().getScheduler().scheduleRepeatingTask(this,() -> {
//
//            for (GameRoom room : RoomManager.rooms) {
//                Level level = room.getResetQueue().poll();
//                if (level == null) continue;
//                level.setAutoSave(false);
//                level.getProvider().close();
//                level.unload();
//            }
//
//        },10);

        getServer().getCommandMap().register("", new Command("uhc") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                executor.execute(() -> {
                    if (sender instanceof Player player){
                        getServer().generateLevel("uhclevel");
                        Level level = getServer().getLevelByName("uhclevel");
                        player.teleport(level.getSpawnLocation());
                        level.setAutoSave(false);
                        level.unload(true);
                    }
                });
                return false;
            }
        });
        getLogger().info("Kituhc started.");
    }

    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir
                        (new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
       return dir.delete();
    }

}