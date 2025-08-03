package me.coolmagic233.kituhc;

import cn.nukkit.level.Level;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import lombok.Getter;
import me.coolmagic233.kituhc.commands.AdminCommand;
import me.coolmagic233.kituhc.commands.DefaultCommand;
import me.coolmagic233.kituhc.room.RoomManager;
import me.iwareq.scoreboard.Scoreboard;
import me.iwareq.scoreboard.packet.data.DisplaySlot;

import java.io.File;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Main extends PluginBase {
    @Getter
    public static Main instance;
    public static final int FORM_ID_KIT_SELECT = 2025080201;
    public static Random RANDOM = new Random();
    public Scoreboard scoreboard = null;
    public Config rewardConfig;
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
        saveResource("rewards.yml");
        rewardConfig = new Config(getDataFolder() + File.separator + "rewards.yml",Config.YAML);
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