package uk.antiperson.autotorch;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import uk.antiperson.autotorch.config.PlayerConfig;

import java.io.IOException;

public class TorchPlacer {

    public static final boolean BLOCKDATA_HAS_STURDY;

    static {
        boolean HAS_STURDY1;
        try {
            Class.forName("org.bukkit.block.data.BlockData");
            org.bukkit.block.data.BlockData.class.getMethod("isFaceSturdy", BlockFace.class, BlockSupport.class);
            HAS_STURDY1 = true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            HAS_STURDY1 = false;
        }
        BLOCKDATA_HAS_STURDY = HAS_STURDY1;
    }

    private final Player player;
    private final AutoTorch autoTorch;
    private PlayerConfig playerConfig;

    public TorchPlacer(AutoTorch autoTorch, Player player) {
        this.autoTorch = autoTorch;
        this.player = player;
    }

    public PlayerConfig getPlayerConfig() {
        if (playerConfig == null) {
            playerConfig = new PlayerConfig(autoTorch, player);
            try {
                playerConfig.init();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return playerConfig;
    }

    public boolean isEnabled() {
        return getPlayerConfig().isEnabled();
    }

    public void setEnabled(boolean enabled) {
        getPlayerConfig().setEnabled(enabled);
    }

    public void placeTorch() {
        if (!isEnabled()) {
            return;
        }
        if (autoTorch.getGlobalConfig().isWorldBlacklisted(getPlayer().getWorld())) {
            return;
        }
        Location loc = getPlayer().getLocation();
        int direction = getDirection();
        for (int i = 1; i < getPlayerConfig().getRadius(); i++) {
            Location torchLoc;
            int add = direction <= 2 ? i * -1 : i;
            if (direction % 2 == 1) {
                torchLoc = loc.clone().add(add, 0, 0);
            } else {
                torchLoc = loc.clone().add(0, 0, add);
            }
            Block supporting = torchLoc.clone().subtract(0, 1, 0).getBlock();
            BlockFace attachWall = BlockFace.UP;
            if (getPlayerConfig().isAttachToWalls()) {
                Location proposedTorchLocation = torchLoc.add(0, 1, 0);
                int initial = getPlayer().getFacing().ordinal();
                int counter = initial + 1 == 4 ? 0 : initial + 1;
                while (initial != counter) {
                    BlockFace blockFace = BlockFace.values()[counter];
                    counter++;
                    if (counter > 3) {
                        counter = 0;
                    }
                    if (blockFace == getPlayer().getFacing().getOppositeFace()) continue;
                    Block relative = proposedTorchLocation.getBlock().getRelative(blockFace);
                    if (!relative.isSolid()) continue;
                    supporting = relative;
                    torchLoc = proposedTorchLocation;
                    attachWall = blockFace.getOppositeFace();
                    break;
                }
            }
            // check torch location
            Block torchBlock = torchLoc.getBlock();
            if (torchBlock.getType().isSolid()) {
                return;
            }
            if (!(torchLoc.getY() >= getPlayerConfig().getYMin() && torchLoc.getY() <= getPlayerConfig().getYMax())) {
                continue;
            }
            if (!checkSupportingBlock(supporting, attachWall)) {
                continue;
            }
            if (torchBlock.getLightLevel() > getPlayerConfig().getMinLightLevel()) {
                continue;
            }
            if (autoTorch.getWorldGuardHandler() != null && !autoTorch.getWorldGuardHandler().canPlaceTorch(getPlayer(), torchBlock)){
                continue;
            }
            if (setTorch(torchBlock)) {
                if (attachWall != BlockFace.UP) {
                    BlockState blockState = torchBlock.getState();
                    Directional directional = (Directional) Material.WALL_TORCH.createBlockData();
                    directional.setFacing(attachWall);
                    blockState.setBlockData(directional);
                    blockState.update(true);
                }
                return;
            }
        }
    }

    private boolean setTorch(Block suggestTorch){
        if (!removeTorches()) {
            return false;
        }
        suggestTorch.setType(Material.TORCH);
        return true;
    }

    private boolean removeTorches() {
        if (!autoTorch.getGlobalConfig().isTorchesFromInventory()){
            return true;
        }
        if (getPlayer().getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        ItemStack toModify = locateTorches();
        if (toModify == null) {
            return false;
        }
        remove(toModify);
        return true;
    }

    private ItemStack locateTorches() {
        switch (getPlayerConfig().getTorchLocation()) {
            case OFF_HAND:
                ItemStack offHand = getPlayer().getInventory().getItem(EquipmentSlot.OFF_HAND);
                if (offHand.getType() == Material.TORCH) {
                    return offHand;
                }
                break;
            case HAND:
                ItemStack hand = getPlayer().getInventory().getItem(EquipmentSlot.HAND);
                if (hand.getType() == Material.TORCH) {
                    return hand;
                }
                break;
            case INVENTORY:
                for (ItemStack item : getPlayer().getInventory().getContents()) {
                    if (item == null) {
                        continue;
                    }
                    if (item.getType() != Material.TORCH) {
                        continue;
                    }
                    return item;
                }
                break;
        }
        return null;
    }

    private void remove(ItemStack item) {
        if (item.getAmount() == 1) {
            getPlayer().getInventory().remove(item);
            return;
        }
        item.setAmount(item.getAmount() - 1);
    }

    private int getDirection() {
        BlockFace blockFace = getPlayer().getFacing();
        switch (blockFace) {
            case WEST:
                return 1;
            case NORTH:
                return 2;
            case EAST:
                return 3;
            case SOUTH:
                return 4;
        }
        return 0;
    }

    public boolean checkSupportingBlock(Block block, BlockFace face){
        if (autoTorch.getGlobalConfig().isBlockTypeBlacklisted(block)) {
            return false;
        }
        if (BLOCKDATA_HAS_STURDY) {
            if (block.getBlockData().isFaceSturdy(face, BlockSupport.FULL)) {
                return true;
            }
        }
        return block.getType().isSolid();
    }

    public Player getPlayer() {
        return player;
    }
}
