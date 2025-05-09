package me.coolmagic233.kituhc.room;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.player.PlayerTeleportEvent;
import cn.nukkit.level.Level;

public class FastMode implements Listener {
    public static Level level = null;
    public static boolean gaming = false;
    public static int time = 0;

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e){
       if (level != null){
           if (gaming){
               if (e.getTo().getLevel().getName().equals(level.getName())){
                   e.setCancelled();
                   e.getPlayer().sendMessage("游戏已开始无法传送。");
               }
           }
       }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e){
        if (level != null){
            if (gaming){
                if (e.getEntity().getLevel().getName().equals(level.getName())){
                    if (time < 60 * 10){
                        e.setCancelled();
                        if (e.getDamager() instanceof Player player){
                            player.sendMessage("你无法在无敌保护阶段内攻击其他玩家。");
                            e.setCancelled();
                        }
                    }
                }
            }
        }
    }


}
