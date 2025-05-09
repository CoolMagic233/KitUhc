package me.coolmagic233.kituhc.room;

import cn.nukkit.Player;
import cn.nukkit.level.Location;

public class BorderChecker {
    // 边界坐标 (可以根据需要调整)
    private double minX;
    private double maxX;
    private double minZ;
    private double maxZ;

    public BorderChecker(double minX, double maxX, double minZ, double maxZ) {
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    public void setBorder(double minX,double minZ,double maxX,double maxZ){
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
    }

    public void shrink(int mount){
        this.minX += mount;
        this.minZ += mount;
        this.maxX -= mount;
        this.maxZ -= mount;
    }

    public double get(){
        return maxX;
    }

    /**
     * 检查玩家是否超出二维边界
     * @param player 要检查的玩家
     * @return true如果在边界外，false在边界内
     */
    public boolean isOutsideBorder(Player player) {
        Location loc = player.getLocation();
        double x = loc.getX();
        double z = loc.getZ();

        return x < minX || x > maxX || z < minZ || z > maxZ;
    }

    /**
     * 检查玩家是否超出边界并在边界时传送回安全位置
     * @param player 要检查的玩家
     * @param safeLocation 安全位置(边界内)
     */
    public void checkAndTeleport(Player player, Location safeLocation) {
        if (isOutsideBorder(player)) {
            player.teleport(safeLocation);
            player.sendMessage("§c你已到达世界边界，已被传送回安全区域!");
        }
    }
}
