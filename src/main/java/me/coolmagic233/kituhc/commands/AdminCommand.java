package me.coolmagic233.kituhc.commands;

import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import me.coolmagic233.kituhc.room.RoomManager;

import java.util.Arrays;
import java.util.stream.Collectors;

public class AdminCommand extends Command {
    public AdminCommand() {
        super("kituhc-admin");
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (sender.isOp()){
            if (args.length > 0){
                switch (args[0]){
                    case "list" -> {
                        sender.sendMessage("正在运行的房间有: " + String.join(",",Arrays.toString(RoomManager.rooms.stream().map(room -> room.getLevel().getName()).toList().toArray(new String[]{}))));
                    }
                }
            }
        }
        return false;
    }
}
