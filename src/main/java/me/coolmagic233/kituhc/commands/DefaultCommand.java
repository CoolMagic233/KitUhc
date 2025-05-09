package me.coolmagic233.kituhc.commands;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import me.coolmagic233.kituhc.room.RoomManager;

import java.util.Arrays;

public class DefaultCommand extends Command {
    public DefaultCommand() {
        super("kituhc");
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            if (args.length > 0){
                switch (args[0]){
                    case "join" -> {
                        if (sender instanceof Player player){
                            RoomManager.join(player);
                        }
                    }
                    case "quit" -> {
                        if (sender instanceof Player player) RoomManager.quit(player);
                    }
                }
            }

        return false;
    }
}
