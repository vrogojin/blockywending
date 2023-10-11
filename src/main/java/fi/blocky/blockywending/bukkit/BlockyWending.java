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

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
	if (event.getLine(0).equalsIgnoreCase("[WENDING]")) {
    	    int price;
    	    try {
        	price = Integer.parseInt(event.getLine(1));
    	    } catch (NumberFormatException e) {
        	event.getPlayer().sendMessage(ChatColor.RED + "Invalid price on the second line.");
        	return;
    	    }

    	    Block block = event.getBlock().getRelative(BlockFace.DOWN);
    	    if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
        	event.getPlayer().sendMessage(ChatColor.GREEN + "Vending machine set up with a price of " + price + " emeralds.");
    	    } else {
        	event.getPlayer().sendMessage(ChatColor.RED + "The block below the sign must be a chest.");
    	    }
	}
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
	if (event.getAction() == Action.LEFT_CLICK_BLOCK && 
    	    (event.getClickedBlock().getType() == Material.CHEST || event.getClickedBlock().getType() == Material.TRAPPED_CHEST)) {
        
    	    BlockState state = event.getClickedBlock().getRelative(BlockFace.UP).getState();
    	    if (state instanceof Sign) {
        	Sign sign = (Sign) state;
        	if (sign.getLine(0).equalsIgnoreCase("[VENDING]")) {
            	    processPurchase(event.getPlayer(), sign, (Chest) event.getClickedBlock().getState());
        	}
    	    }
	}
    }

    private void processPurchase(Player player, Sign sign, Chest chest) {
	int price = Integer.parseInt(sign.getLine(1));

	// Check if the vending machine has items
	int sellItemIdx = firstItemIdx(chest.getInventory());
	if (sellItemIdx<0) {
    	    player.sendMessage(ChatColor.RED + "This vending chest has nothing for sale");
    	    return;
	}

	// Check player's inventory for payment
	Map<String, Map<Integer, Integer>> distrib = findPaymentDistribution(player, chest, price);
	if (distrib == null) {
    	    player.sendMessage(ChatColor.RED + "Could don't find emeralds and emerald blocks combination to pay for the purchase.");
    	    return;
	}

	// Perform the transaction
	ItemStack soldItem = chest.getInventory().getItem(0); // Sample, get the first item. Adjust as needed.
	chest.getInventory().removeItem(soldItem);
	player.getInventory().addItem(soldItem);
	deductPayment(player, price);
	chest.getInventory().addItem(new ItemStack(Material.EMERALD, price)); // Add payment to the chest
	player.sendMessage(ChatColor.GREEN + "Purchase successful!");
    }

    private Map<String, Map<Integer, Integer>> findPaymentDistribution(Player player, Chest chest, int price) {
	Inventory playerInv = player.getInventory();
	Inventory chestInv = chest.getInventory();

	Map<Integer, Integer> playerPaymentSlots = new HashMap<>();
	Map<Integer, Integer> chestPaymentSlots = new HashMap<>();

	int totalEmeraldBlocks = 0;
	int totalEmeralds = 0;
	int chestEmeralds = 0;

	int block_idx=-1;

	for (int i = 0; i < playerInv.getSize(); i++) {
    	    ItemStack item = playerInv.getItem(i);
    	    if (item != null) {
        	if (item.getType() == Material.EMERALD_BLOCK) {
		    int amount = ((totalEmeraldBlocks+item.getAmount())*9<=price)?item.getAmount():price/9-totalemeraldBlocks;
//            	    totalEmeraldBlocks += ((totalEmeraldBlocks+item.getAmount())*9<=price)?item.getAmount();
            	    playerPaymentSlots.put(i, amount);
		    totalemeraldBlocks += amount;
		    if(totalEmeraldBlocks*9>price-9){
			block_idx = i;
			break;
		    }
        	}
    	    }
	}

	for (int i = 0; i < playerInv.getSize(); i++) {
    	    ItemStack item = playerInv.getItem(i);
    	    if (item != null) {
        	if (item.getType() == Material.EMERALD) {
		    int amount = (totalEmeraldBlocks*9+totalEmeralds+item.getAmount()<=price)?item.getAmount():
			price-(totalemeraldBlocks*9+totalEmeralds);
//            	    totalEmeraldBlocks += ((totalEmeraldBlocks+item.getAmount())*9<=price)?item.getAmount();
            	    playerPaymentSlots.put(i, amount);
		    totalEmeralds += amount;
		    if(totalemeraldBlocks*9+totalEmeralds>=price){
			break;
		    }
        	}
    	    }
	}

	// If we cannot find the exact block and emerald combination on the player side
	if (totalEmeraldBlocks * 9 + totalEmeralds < price) {
    	    // Look for the change in the wending chest
    	    int neededEmeralds = price - (totalEmeraldBlocks * 9 + totalEmeralds);
    	    for (int i = 0; i < chestInv.getSize(); i++) {
        	ItemStack item = chestInv.getItem(i);
        	if (item != null && item.getType() == Material.EMERALD) {
		    int amount = (chestEmeralds+item.getAmount()<=neededEmeralds)?item.getAmount():neededEmeralds-chestEmeralds;
            	    chestPaymentSlots.put(i, amount);
		    chestEmeralds += amount;
		    if(chestEmeralds>=neededAmount){
            		break;
		    }
        	}
    	    }
	}

	if(chestEmeralds > 0){
	    for (int i = block_idx; i < playerInv.getSize(); i++) {
    		ItemStack item = playerInv.getItem(i);
    		if (item != null) {
        	    if (item.getType() == Material.EMERALD_BLOCK) {
			int amount = playerPaymentSlots.get(i);
			if(amount<item.getAmount()){
			    playerPaymentSlots.put(i, amount+1);
			    totalEmeraldBlocks++;
			    break;
			}
        	    }
    		}
	    }
	}

//	if ((totalEmeraldBlocks * 9 + totalEmeralds + chestPaymentSlots.values().stream().mapToInt(Integer::intValue).sum()) == price) {
	if(totalEmeraldBlocks * 9 + totalEmeralds - chestEmeralds == price){
    	    Map<String, Map<Integer, Integer>> result = new HashMap<>();
    	    result.put("playerInv", playerPaymentSlots);
    	    result.put("chestInv", chestPaymentSlots);
    	    return result;
	} else {
    	    // Return an empty map if we can't find the exact distribution
//    	    return new HashMap<>();
	    return null;
	}
    }

    private int firstItemIdx(Inventory inv){
	for(int i=0;i<inv.getSize();i++){
	    ItemStack item = inv.getItem(i);
	    if(item != null){
		if((item.getType() != Material.EMERALD)&&(item.getType() != Material.EMERALD_BLOCK)&&
		    (item.getType() != Material.BEDROCK))
		    return i;
	    }
	}
	return -1;
    }
}
