package me.coolmagic233.kituhc;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import cn.nukkit.level.Level;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import lombok.Getter;
import me.coolmagic233.kituhc.commands.AdminCommand;
import me.coolmagic233.kituhc.commands.DefaultCommand;
import me.coolmagic233.kituhc.room.RoomManager;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Main extends PluginBase {
    @Getter
    public static Main instance;
    public static final int FORM_ID_KIT_SELECT = 2025080201;
    public static Random RANDOM = new Random();
    public Config rewardConfig;
    public Config roomConfig;
    @Getter
    public final Executor executor = Executors.newCachedThreadPool();
    @Override
    public void onEnable(){
        instance = this;
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
        saveResource("room.yml");
        roomConfig = new Config(getDataFolder() + File.separator + "room.yml",Config.YAML);
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


    public void dispatchCommand(CommandSender commandSender,String cmd){
        getServer().dispatchCommand(commandSender, cmd);
    }

    @SuppressWarnings("unchecked")
    public static void executeCommands(String configKey, Map<String, String> placeholders, Player player){
        Config config = getInstance().roomConfig;
        Object raw = config.get(configKey);
        if (!(raw instanceof List<?> list)) return;
        for (Object item : list){
            if (!(item instanceof Map<?,?> map)) continue;
            Object cmdObj = map.get("cmd");
            Object typeObj = map.get("type");
            String cmd = cmdObj != null ? cmdObj.toString() : "";
            String type = typeObj != null ? typeObj.toString() : "console";
            if (cmd.isEmpty()) continue;
            for (Map.Entry<String, String> ph : placeholders.entrySet()){
                cmd = cmd.replace(ph.getKey(), ph.getValue());
            }
            if (type.equals("player") && player != null){
                getInstance().dispatchCommand(player, cmd);
            } else {
                getInstance().dispatchCommand(getInstance().getServer().getConsoleSender(), cmd);
            }
        }
    }
}