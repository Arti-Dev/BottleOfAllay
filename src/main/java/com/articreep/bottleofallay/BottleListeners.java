package com.articreep.bottleofallay;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BottleListeners implements Listener {
    private static final Set<Player> cooldowns = new HashSet<>();
    private static int maxAllayRecursion = 2;

    /*
    - Allays can be right-clicked to trap them into a glass bottle and called "Bottle of Allay"
        - This may be canceled by shifting
    - The item they are carrying will stay in the bottle
    - Their name will be carried over, and their duplication delay, and their health
    - Effects do not carry over (for now)
    - The Allay can be released by right-clicking a block
        - If the Allay was carrying an item, it will follow the player who released the Allay
    - You can drink the Bottle of Allay which will cause you to levitate for 30 seconds
        - The item that the Allay was carrying, if any, will drop (as if you burped it)

    Bugs:
    - You can capture the allay while it's picking up items, and the items will be deleted
     */

    public BottleListeners() {
        loadConfig();
    }

    protected static void loadConfig() {
        maxAllayRecursion = BottleOfAllay.getInstance().getConfig().getInt("maxAllayRecursion");
        if (maxAllayRecursion < 0) maxAllayRecursion = 0;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAllayRightClick(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (event.isCancelled()) return;
        if (player.isSneaking()) return;
        cooldowns.add(player);
        Bukkit.getScheduler().runTaskLater(BottleOfAllay.getInstance(), () -> cooldowns.remove(player), 5);

        if (event.getRightClicked() instanceof Allay allay) {
            PlayerInventory inv = player.getInventory();
            ItemStack handItem = inv.getItem(event.getHand());
            if (handItem != null && handItem.getType() == Material.GLASS_BOTTLE) {
                event.setCancelled(true);
                try {
                    // Check what the Allay is holding and whether it has too many subitems
                    ItemStack itemHeld = allay.getEquipment().getItemInMainHand();
                    if (listSubItems(itemHeld).size() > maxAllayRecursion) {
                        player.sendMessage(ChatColor.RED + "Have you ever considered the amount of " +
                                "allays and bottles you're sticking inside a single item?");
                        return;
                    }
                    // Add captured allay to inventory
                    if (handItem.getAmount() == 1) {
                        inv.setItem(event.getHand(), captureAllay(allay));
                    } else {
                        handItem.setAmount(handItem.getAmount() - 1);
                        inv.addItem(captureAllay(allay));
                    }
                    player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_ITEM_GIVEN, 1, 1);
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @EventHandler
    public void onAllayRelease(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking()) return;
        if (cooldowns.remove(player)) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        PlayerInventory inv = player.getInventory();
        ItemStack item = inv.getItem(event.getHand());
        if (item == null || item.getItemMeta() == null) return;
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        if (container.has(healthKey, PersistentDataType.DOUBLE)) {
            try {
                releaseAllay(player, item, event.getClickedBlock(), event.getBlockFace());
                if (item.getAmount() == 1) {
                    inv.setItem(event.getHand(), new ItemStack(Material.GLASS_BOTTLE));
                } else {
                    item.setAmount(item.getAmount() - 1);
                    inv.addItem(new ItemStack(Material.GLASS_BOTTLE));
                }
                player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_ITEM_TAKEN, 1, 1);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }

        }
    }

    @EventHandler
    public void onAllayDrink(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());
        if (item == null || item.getItemMeta() == null) return;

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        if (container.has(itemKey, PersistentDataType.BYTE_ARRAY)) {
            // Go to long efforts to obtain the item the allay was holding
            ItemStack heldItem = new ItemStack(Material.AIR);
            try {
                heldItem = retrieveItem(container.get(itemKey, PersistentDataType.BYTE_ARRAY));
            } catch (ClassNotFoundException | IOException e) {
                e.printStackTrace();
            }
            // Effectively final variable for use in lambda
            ItemStack finalHeldItem = heldItem;

            player.getWorld().spawn(player.getLocation(), Item.class, droppedItem -> {
                droppedItem.setPickupDelay(20);
                droppedItem.setItemStack(finalHeldItem);
                droppedItem.setVelocity(player.getLocation().getDirection().multiply(0.5));
            });
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1, 1);
        }

    }

    private static final NamespacedKey itemKey = new NamespacedKey(BottleOfAllay.getInstance(), "allayItem");
    private static final NamespacedKey nameKey = new NamespacedKey(BottleOfAllay.getInstance(), "allayName");
    private static final NamespacedKey dupeKey = new NamespacedKey(BottleOfAllay.getInstance(), "allayDupeCooldown");
    private static final NamespacedKey healthKey = new NamespacedKey(BottleOfAllay.getInstance(), "allayHealthKey");

    /**
     * Captures the given allay (removes it) and returns a bottle with it inside.
     * @param allay The allay to capture
     * @return A Bottle of Allay ItemStack
     * @throws IOException idk
     * @throws ClassNotFoundException idk
     */
    private ItemStack captureAllay(Allay allay) throws IOException, ClassNotFoundException {
        // Obtain data about the Allay
        ItemStack itemCarrying = allay.getEquipment().getItemInMainHand();
        String allayName = allay.getCustomName();
        if (allayName == null) allayName = "";
        long dupeCooldown = allay.getDuplicationCooldown();
        double health = allay.getHealth();

        ItemStack bottle = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) bottle.getItemMeta();
        meta.setColor(Color.AQUA);
        meta.addCustomEffect(new PotionEffect(PotionEffectType.LEVITATION, 30*20, 0), true);
        meta.setDisplayName(ChatColor.AQUA + "Bottle of Allay");

        List<String> lore = new ArrayList<>();
        if (!allayName.isEmpty()) lore.add(ChatColor.GRAY + "Name: " + allayName);
        lore.add(ChatColor.GRAY + "Item Holding: " + getName(itemCarrying));
        // if allay is holding a bottle of allay, show what that allay is holding
        for (ItemStack item : listSubItems(itemCarrying)) {
            lore.add(ChatColor.GRAY + "..holding " + getName(item));
        }
        meta.setLore(lore);

        // Persistent data containers
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(nameKey, PersistentDataType.STRING, allayName);
        container.set(dupeKey, PersistentDataType.LONG, dupeCooldown);
        container.set(healthKey, PersistentDataType.DOUBLE, health);
        container.set(itemKey, PersistentDataType.BYTE_ARRAY, encodeItem(itemCarrying));

        bottle.setItemMeta(meta);
        allay.remove();
        return bottle;
    }

    /**
     * Releases an Allay from a Bottle of Allay at the block
     * @param player Player who released this allay (which the allay will follow if it is carrying an item
     * @param bottle The Bottle of Allay that the Allay is being released from
     * @param block The block clicked to release the Allay
     * @param face The blockface clicked to release the Allay
     * @throws IOException idk
     * @throws ClassNotFoundException idk
     */
    private void releaseAllay(Player player, ItemStack bottle, Block block, BlockFace face) throws IOException, ClassNotFoundException {
        PersistentDataContainer container = bottle.getItemMeta().getPersistentDataContainer();
        String name = container.get(nameKey, PersistentDataType.STRING);
        Long dupeCooldown = container.get(dupeKey, PersistentDataType.LONG);
        Double health = container.get(healthKey, PersistentDataType.DOUBLE);

        byte[] itemBytes = container.get(itemKey, PersistentDataType.BYTE_ARRAY);
        ItemStack heldItem = retrieveItem(itemBytes);

        block.getWorld().spawn(block.getLocation(), Allay.class, allay -> {
            alignToFace(block, face, allay);
            allay.setHealth(health);
            allay.setCustomName(name);
            allay.setDuplicationCooldown(dupeCooldown);
            allay.getEquipment().setItemInMainHand(heldItem);

            if (heldItem.getType() != Material.AIR && player != null) {
                allay.setMemory(MemoryKey.LIKED_PLAYER, player.getUniqueId());
            }
        });
    }

    /**
     * Get name of an itemstack to display in the Bottle of Allay lore
     * @param item ItemStack to get name of
     * @return the name
     */
    private static String getName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        } else if (item.getType() != Material.AIR) {
            return item.getType().toString();
        } else return ChatColor.DARK_GRAY + "nothing";
    }

    /**
     * Uses BukkitObjectOutputStream to write an ItemStack to a byte array.
     * @param item ItemStack to encode
     * @return byte array representing the ItemStack
     * @throws IOException idk
     */
    private static byte[] encodeItem(ItemStack item) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BukkitObjectOutputStream bukkitStream = new BukkitObjectOutputStream(out);
        bukkitStream.writeObject(item);
        return out.toByteArray();
    }

    /**
     * Uses BukkitObjectInputStream to write an ItemStack to a byte array.
     * @param itemBytes byte array to decode
     * @return decoded ItemStack
     * @throws IOException idk
     */
    private static ItemStack retrieveItem(byte[] itemBytes) throws IOException, ClassNotFoundException {
        if (itemBytes == null) throw new IllegalStateException("bruh itembytes is null");
        ByteArrayInputStream in = new ByteArrayInputStream(itemBytes);
        BukkitObjectInputStream bukkitStream = new BukkitObjectInputStream(in);
        return (ItemStack) bukkitStream.readObject();
    }

    /**
     * Retrieves all subitems in a bottle of an allay.
     * e.g. passing a bottle of an allay holding a bottle of an allay holding a grass block would return:
     * (bottle of allay, grass block)
     * the original item passed in is not added to the list
     * @param item Item to get subitems of. Should be a bottle of allay
     * @return list of subitems
     */
    private static List<ItemStack> listSubItems(ItemStack item) throws IOException, ClassNotFoundException {
        List<ItemStack> items = new ArrayList<>();
        ItemStack subItem = item;
        while (subItem.hasItemMeta() && subItem.getItemMeta().getPersistentDataContainer()
                .has(itemKey, PersistentDataType.BYTE_ARRAY)) {
            PersistentDataContainer subContainer = subItem.getItemMeta().getPersistentDataContainer();
            subItem = retrieveItem(subContainer.get(itemKey, PersistentDataType.BYTE_ARRAY));
            items.add(subItem);
        }
        return items;
    }

    /**
     * Teleports an entity to the closest centered location to a Block's BlockFace
     * so that the Entity's BoundingBox is tangent to the blockface.
     * @param block Block to use
     * @param face BlockFace to place entity on
     * @param entity The entity
     */
    public static void alignToFace(Block block, BlockFace face, Entity entity) {
        // credit trollyloki for math ideas
        Location location = block.getLocation();
        // center up the location
        location.add(0.5, 0.5, 0.5);
        // move location to the center of the blockface via addition
        Vector v = face.getDirection();
        location.add(v.clone().multiply(0.5));
        // create vector representing the dimensions of the entity's bounding box
        BoundingBox box = entity.getBoundingBox();
        Vector diag = new Vector(box.getWidthX(), box.getHeight(), box.getWidthZ()).multiply(0.5);
        // dot product the bounding box vector with the direction
        // take the absolute value to prevent the location from ending up inside the block
        location.add(v.multiply(Math.abs(diag.dot(v))));
        // the entity's location point is at the bottom of their boundingbox.
        // therefore the location needs to be shifted down by half their boundingbox
        location.subtract(0, box.getHeight()/2, 0);
        entity.teleport(location);
    }
}
