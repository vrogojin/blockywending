package fi.blocky.blockyvending.bukkit;

import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockVector;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

public class BlockyVending extends JavaPlugin implements Listener {

    private static BlockyVending instance;

    private RegionScheduler regionScheduler;

    private final ConcurrentMap<UUID, Inventory> playerGuis = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {

        getServer().getPluginManager().registerEvents(this, this);
	regionScheduler = getServer().getRegionScheduler();

        getLogger().info("BlockyVending plugin enabled");
    }

    public static BlockyVending getInstance() {
        return instance;
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
	if (event.getLine(0).equalsIgnoreCase("[VENDING]")) {
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
/*	if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
	    Block clickedBlock = event.getClickedBlock();
	    if (clickedBlock != null) {
		BlockState signState = clickedBlock.getState();
		if(signState instanceof Sign){
		    Sign sign = (Sign) signState;
		    if (sign.getLine(0).equalsIgnoreCase("[VENDING]")) {
			BlockState chestState = clickedBlock.getRelative(BlockFace.DOWN).getState();
			if(chestState instanceof Chest){
			    Chest chest = (Chest)chestState;
			    // Create a custom inventory to show the player
                	    Inventory customInv = createVendingGUI(chest);

                	    // Open the custom inventory for the player
                	    Player player = event.getPlayer();
                	    player.openInventory(customInv);
			}
		    }
		}
	    }
	} else*/
	if (event.getAction() == Action.LEFT_CLICK_BLOCK){
	    Block clickedBlock = event.getClickedBlock();
	    if(clickedBlock == null)return;
	    BlockState blockState = clickedBlock.getState();
	    if (event.getClickedBlock().getType() == Material.CHEST || event.getClickedBlock().getType() == Material.TRAPPED_CHEST) {
    		BlockState state = clickedBlock.getRelative(BlockFace.UP).getState();
    		if (state instanceof Sign) {
        	    Sign sign = (Sign) state;
        	    if (sign.getLine(0).equalsIgnoreCase("[VENDING]")) {
            		processPurchase(event.getPlayer(), sign, (Chest) event.getClickedBlock().getState());
        	    }
    		}
	    }else if(blockState instanceof Sign){
		    Sign sign = (Sign) blockState;
		    if (sign.getLine(0).equalsIgnoreCase("[VENDING]")) {
			BlockState chestState = clickedBlock.getRelative(BlockFace.DOWN).getState();
			if(chestState instanceof Chest){
			    Chest chest = (Chest)chestState;
			    // Create a custom inventory to show the player
                	    Inventory customInv = createVendingGUI(chest);

                	    // Open the custom inventory for the player
                	    Player player = event.getPlayer();
			    registerGui(player, customInv);
                	    player.openInventory(customInv);
			}
		    }
		}
	}
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        
        if (isGui(player, event.getClickedInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        Player player = (Player) event.getPlayer();
        
        deregisterGui(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        deregisterGui(event.getPlayer());
    }

    private Inventory createVendingGUI(Chest chest) {
        // Create a custom inventory with a size of 9 rows (one chest row)
        Inventory customInv = Bukkit.createInventory(null, chest.getInventory().getSize(), "Vending Chest");
        
        // Add the chest's items to the custom inventory, excluding emeralds, emerald blocks, and bedrock
        for (ItemStack item : chest.getInventory().getContents()) {
            if (item != null && item.getType() != Material.EMERALD && item.getType() != Material.EMERALD_BLOCK && item.getType() != Material.BEDROCK) {
                customInv.addItem(item.clone()); // Use clone to avoid altering the original item
            }
        }
        
        return customInv;
    }

    private void processPurchase(Player player, Sign sign, Chest chest) {
	int price = Integer.parseInt(sign.getLine(1));

	// Preliminary checks: Check if the vending machine has items
	int sellItemIdx = firstItemIdx(chest.getInventory());
	if (sellItemIdx<0) {
    	    player.sendMessage(ChatColor.RED + "This vending chest has nothing for sale");
    	    return;
	}

	// Preliminary checks: Check player's inventory for payment
	Map<String, Map<Integer, Integer>> distrib = findPaymentDistribution(player, chest, price);
	if (distrib == null) {
    	    player.sendMessage(ChatColor.RED + "Could don't find emeralds and emerald blocks combination to pay for the purchase.");
    	    return;
	}

	// Taking initial inventories snapshots in case we will need to revert
	ItemStack[] playerSnapshot = snapshotInventory(player.getInventory());
	ItemStack[] chestSnapshot = snapshotInventory(chest.getInventory());

	// Perform the transaction
/*	ItemStack soldItem = chest.getInventory().getItem(sellItemIdx);
	chest.getInventory().removeItem(soldItem);
	player.getInventory().addItem(soldItem);
	deductPayment(player, price);
	chest.getInventory().addItem(new ItemStack(Material.EMERALD, price)); // Add payment to the chest*/
	if(executeTransaction(distrib, sellItemIdx, player.getInventory(), chest.getInventory())){
	    player.sendMessage(ChatColor.GREEN + "Purchase successful!");
	}else{
	    revertTransaction(player.getInventory(), chest.getInventory(), playerSnapshot, chestSnapshot);
	    player.sendMessage(ChatColor.RED + "Purchase failed. Not enough slots to receive items, either player or chest side.");
	}
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
		    int amount = ((totalEmeraldBlocks+item.getAmount())*9<=price)?item.getAmount():price/9-totalEmeraldBlocks;
//            	    totalEmeraldBlocks += ((totalEmeraldBlocks+item.getAmount())*9<=price)?item.getAmount();
            	    playerPaymentSlots.put(i, amount);
		    totalEmeraldBlocks += amount;
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
			price-(totalEmeraldBlocks*9+totalEmeralds);
//            	    totalEmeraldBlocks += ((totalEmeraldBlocks+item.getAmount())*9<=price)?item.getAmount();
            	    playerPaymentSlots.put(i, amount);
		    totalEmeralds += amount;
		    if(totalEmeraldBlocks*9+totalEmeralds>=price){
			break;
		    }
        	}
    	    }
	}

	// If we cannot find the exact block and emerald combination on the player side
	if ((totalEmeraldBlocks * 9 + totalEmeralds > price-9)&&(totalEmeraldBlocks * 9 + totalEmeralds < price)) {
    	    // Look for the change in the Vending chest
    	    int neededEmeralds = price - (totalEmeraldBlocks * 9 + totalEmeralds);
//	    getLogger().info("neededEmeralds: "+neededEmeralds);
    	    for (int i = 0; i < chestInv.getSize(); i++) {
        	ItemStack item = chestInv.getItem(i);
//		getLogger().info("chest: "+i+":"+item.getAmount());
        	if (item != null && item.getType() == Material.EMERALD) {
		    int amount = (chestEmeralds+item.getAmount()<=9-neededEmeralds)?item.getAmount():9-neededEmeralds-chestEmeralds;
            	    chestPaymentSlots.put(i, amount);
		    chestEmeralds += amount;
		    if(chestEmeralds>=9-neededEmeralds){
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
			int amount = playerPaymentSlots.containsKey(i)?playerPaymentSlots.get(i):0;
			if(amount<item.getAmount()){
			    playerPaymentSlots.put(i, amount+1);
			    totalEmeraldBlocks++;
			    break;
			}
        	    }
    		}
	    }
	}

/*	getLogger().info("totalEmeraldBlocks: "+totalEmeraldBlocks);
	getLogger().info("totalEmeralds: "+totalEmeralds);
	getLogger().info("chestEmeralds: "+chestEmeralds);
	getLogger().info("price: "+price);*/
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

    private boolean executeTransaction(Map<String, Map<Integer, Integer>> distrib, int sellItemIdx, 
	    Inventory playerInv, Inventory chestInv){
	    Map<Integer, Integer> playerSlots = distrib.get("playerInv");
	    Map<Integer, Integer> chestSlots = distrib.get("chestInv");

	    int totalEmeraldBlocks = 0;
	    int totalEmeralds = 0;
	    int chestEmeralds = 0;

	    for (Map.Entry<Integer, Integer> entry: playerSlots.entrySet()){
		int slotIdx = entry.getKey();
		int amount = entry.getValue();

		ItemStack item = playerInv.getItem(slotIdx);
		if(item.getType() == Material.EMERALD)
		    totalEmeralds+=amount;
		else
		    totalEmeraldBlocks+=amount;
		item.setAmount(item.getAmount()-amount);
	    }

	    for (Map.Entry<Integer, Integer> entry: chestSlots.entrySet()){
		int slotIdx = entry.getKey();
		int amount = entry.getValue();

		ItemStack item = chestInv.getItem(slotIdx);
		chestEmeralds+=amount;
		item.setAmount(item.getAmount()-amount);
	    }

	    ItemStack itemForSale = chestInv.getItem(sellItemIdx);
	    ItemStack soldItem = itemForSale.clone();
	    itemForSale.setAmount(itemForSale.getAmount()-1);
	    soldItem.setAmount(1);

	    ItemStack emeraldBlocks = new ItemStack(Material.EMERALD_BLOCK, totalEmeraldBlocks);
	    ItemStack emeralds = new ItemStack(Material.EMERALD, totalEmeralds);
	    ItemStack change = new ItemStack(Material.EMERALD, chestEmeralds);

	    return addItemToInventory(chestInv, emeraldBlocks) &&
		addItemToInventory(chestInv, emeralds) &&
		addItemToInventory(playerInv, change) &&
		addItemToInventory(playerInv, soldItem);
    }

    private boolean addItemToInventory(Inventory inventory, ItemStack item) {
	Material material = item.getType();

	// If item is stackable, check for existing stacks to add to
	if ((material.getMaxStackSize()>1)&&(!hasSpecialProperties(item))) {
    	    // Distribute item among slots with same material
    	    for (int i = 0; i < inventory.getSize(); i++) {
        	ItemStack currentStack = inventory.getItem(i);

        	// If the slot is not empty, and is of the same type, and is not at max stack size
        	if (currentStack != null &&
            	    currentStack.getType() == material) {

		    int amount = Math.min(currentStack.getMaxStackSize() - currentStack.getAmount(), item.getAmount());
            	    // Add to the existing stack
            	    currentStack.setAmount(currentStack.getAmount() + amount);
		    item.setAmount(item.getAmount()-amount);
		    if(item.getAmount() == 0)
            		return true;
        	}
    	    }
	}
	Map<Integer, ItemStack> leftover = inventory.addItem(item);
	return leftover.isEmpty();
     }

    private ItemStack[] snapshotInventory(Inventory inv){
	ItemStack[] original = inv.getContents();
	ItemStack[] copy = new ItemStack[original.length];
	for (int i = 0; i < original.length; i++) {
    	    ItemStack stack = original[i];
    	    copy[i] = stack == null ? null : new ItemStack(stack);
	}
	return copy;
    }

    private void revertTransaction(Inventory playerInv, Inventory chestInv, ItemStack[] playerSnapshot, ItemStack[] chestSnapshot){
	playerInv.setContents(playerSnapshot);
	chestInv.setContents(chestSnapshot);
    }

    private boolean hasSpecialProperties(ItemStack item){
	if(!item.hasItemMeta())return false;
	
	ItemStack defaultItem = new ItemStack(item.getType());
	ItemMeta defaultMeta = defaultItem.getItemMeta();
	ItemMeta itemMeta = item.getItemMeta();

	return itemMeta != null && !itemMeta.equals(defaultMeta);
    }

    private void registerGui(Player player, Inventory gui) {
        playerGuis.put(player.getUniqueId(), gui);
    }

    private boolean isGui(Player player, Inventory inventory) {
	if(playerGuis.containsKey(player.getUniqueId()))
    	    return playerGuis.get(player.getUniqueId()).equals(inventory);
	return false;
    }

    private void deregisterGui(Player player) {
        playerGuis.remove(player.getUniqueId());
    }
}
