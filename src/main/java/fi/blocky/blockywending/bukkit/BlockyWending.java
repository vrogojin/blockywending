package fi.blocky.blockywending.bukkit;

import org.bukkit.ChatColor;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class BlockyWending extends JavaPlugin implements Listener {

    private static BlockyWending instance;

    private RegionScheduler regionScheduler;

    @Override
    public void onEnable() {

        getServer().getPluginManager().registerEvents(this, this);
	regionScheduler = getServer().getRegionScheduler();

        getLogger().info("BlockyWending plugin enabled");
    }

    public static BlockyWending getInstance() {
        return instance;
    }


}
