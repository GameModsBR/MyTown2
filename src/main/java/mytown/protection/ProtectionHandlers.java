package mytown.protection;

import br.com.gamemods.protectmyplane.event.AircraftAttackEvent;
import br.com.gamemods.protectmyplane.event.AircraftDropEvent;
import br.com.gamemods.protectmyplane.event.PlayerPilotAircraftEvent;
import br.com.gamemods.protectmyplane.event.PlayerSpawnVehicleEvent;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import myessentials.entities.BlockPos;
import myessentials.entities.Volume;
import myessentials.event.BlockTrampleEvent;
import myessentials.event.LiquidFlowEvent;
import myessentials.event.LiquidReplaceBlockEvent;
import mytown.MyTown;
import mytown.new_datasource.MyTownUniverse;
import mytown.config.Config;
import mytown.entities.*;
import mytown.entities.flag.FlagType;
import mytown.thread.ThreadPlacementCheck;
import mytown.util.MyTownUtils;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.world.BlockEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles all the protections
 */
public class ProtectionHandlers {

    public static final ProtectionHandlers instance = new ProtectionHandlers();


    public Map<TileEntity, Resident> ownedTileEntities = new HashMap<TileEntity, Resident>();

    public int activePlacementThreads = 0;
    public int maximalRange = 0;

    // ---- All the counters/tickers for preventing check every tick ----
    private int tickerTilesChecks = 20;
    private int tickerTilesChecksStart = 20;
    private int itemPickupCounter = 0;

    public Resident getOwnerForTileEntity(TileEntity te) {
        return this.ownedTileEntities.get(te);
    }

    // ---- Main ticking method ----

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent ev) {
        // TODO: Add a command to clean up the block whitelist table periodically
        if (MinecraftServer.getServer().getTickCounter() % 600 == 0) {
            for (Town town : MyTownUniverse.instance.towns)
                for (int i = 0; i < town.blockWhitelistsContainer.size(); i++) {
                    BlockWhitelist bw = town.blockWhitelistsContainer.get(i);
                    if (!ProtectionManager.isBlockWhitelistValid(bw)) {
                        MyTown.instance.datasource.deleteBlockWhitelist(bw, town);
                    }
                }
        }
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public void worldTick(TickEvent.WorldTickEvent ev) {
        if (ev.side == Side.CLIENT)
            return;
        if(ev.phase == TickEvent.Phase.END) {
            return;
        }

        //MyTown.instance.LOG.info("Tick number: " + MinecraftServer.getServer().getTickCounter());

        // Entity check
        // TODO: Rethink this system a couple million times before you come up with the best algorithm :P
        for (int i = 0; i < ev.world.loadedEntityList.size(); i++) {
            Entity entity = (Entity) ev.world.loadedEntityList.get(i);
            Town town = MyTownUtils.getTownAtPosition(entity.dimension, (int) Math.floor(entity.posX) >> 4, (int) Math.floor(entity.posZ) >> 4);
            //MyTown.instance.log.info("Checking player...");
            // Player check, every tick
            if (entity instanceof EntityPlayerMP && !(entity instanceof FakePlayer)) {
                ProtectionManager.check((EntityPlayerMP) entity);
            } else {
                // Other entity checks
                if(MinecraftServer.getServer().getTickCounter() % 20 == 0) {
                    ProtectionManager.check(entity);
                }
            }
        }

        // TileEntity check
        if(MinecraftServer.getServer().getTickCounter() % 20 == 0) {
            if (activePlacementThreads == 0) {
                for (int i = 0; i < ev.world.loadedTileEntityList.size(); i++) {
                    TileEntity te = (TileEntity) ev.world.loadedTileEntityList.get(i);
                    ProtectionManager.check(te);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerAttackEntityEvent(AttackEntityEvent ev) {
        if(ev.entity.worldObj.isRemote || ev.isCanceled()) {
            return;
        }

        Resident res = MyTownUniverse.instance.getOrMakeResident(ev.entityPlayer);
        ProtectionManager.checkInteraction(ev.target, res, ev);
    }

    @SubscribeEvent
    public void onBlockPlacement(BlockEvent.PlaceEvent ev) {
        onAnyBlockPlacement(ev.player, ev);
    }

    @SubscribeEvent
    public void onMultiBlockPlacement(BlockEvent.MultiPlaceEvent ev) {
        onAnyBlockPlacement(ev.player, ev);
    }

    public void onAnyBlockPlacement(EntityPlayer player, BlockEvent.PlaceEvent ev) {
        if(ev.world.isRemote || ev.isCanceled()) {
            return;
        }

        if(player instanceof FakePlayer) {
            if(!ProtectionManager.getFlagValueAtLocation(FlagType.FAKERS, ev.world.provider.dimensionId, ev.x, ev.y, ev.z)) {
                ev.setCanceled(true);
            }
        } else {
            Resident res = MyTownUniverse.instance.getOrMakeResident(player);

            if (!MyTownUniverse.instance.blocks.contains(ev.world.provider.dimensionId, ev.x >> 4, ev.z >> 4)) {
                int range = Config.instance.placeProtectionRange.get();
                Volume placeBox = new Volume(ev.x-range, ev.y-range, ev.z-range, ev.x+range, ev.y+range, ev.z+range);

                if(!ProtectionManager.hasPermission(res, FlagType.MODIFY, ev.world.provider.dimensionId, placeBox)) {
                    ev.setCanceled(true);
                    return;
                }
            } else {
                if(!ProtectionManager.hasPermission(res, FlagType.MODIFY, ev.world.provider.dimensionId, ev.x, ev.y, ev.z)) {
                    ev.setCanceled(true);
                    return;
                }
            }

            if(ev.block instanceof ITileEntityProvider && ev.itemInHand != null) {
                TileEntity te = ((ITileEntityProvider) ev.block).createNewTileEntity(MinecraftServer.getServer().worldServerForDimension(ev.world.provider.dimensionId), ev.itemInHand.getItemDamage());
                if (te != null && ProtectionManager.isOwnable(te.getClass())) {
                    ThreadPlacementCheck thread = new ThreadPlacementCheck(res, ev.x, ev.y, ev.z, ev.world.provider.dimensionId);
                    activePlacementThreads++;
                    thread.start();
                }
            }
        }
    }

    @SubscribeEvent
    public void onEntityInteract(EntityInteractEvent ev) {
        if(ev.entity.worldObj.isRemote || ev.isCanceled()) {
            return;
        }
        int x = (int) Math.floor(ev.target.posX);
        int y = (int) Math.floor(ev.target.posY);
        int z = (int) Math.floor(ev.target.posZ);

        if(ev.entityPlayer instanceof FakePlayer) {
            if(!ProtectionManager.getFlagValueAtLocation(FlagType.FAKERS, ev.target.dimension, x, y, z)) {
                ev.setCanceled(true);
            }
        } else {
            Resident res = MyTownUniverse.instance.getOrMakeResident(ev.entityPlayer);
            ProtectionManager.checkInteraction(ev.target, res, ev);
            if(ev.entityPlayer.getHeldItem() != null) {
                BlockPos bp = new BlockPos(x, y, z, ev.target.dimension);
                ProtectionManager.checkUsage(ev.entityPlayer.getHeldItem(), res, PlayerInteractEvent.Action.RIGHT_CLICK_AIR, bp, -1, ev);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent ev) {
        if (ev.entityPlayer.worldObj.isRemote || ev.isCanceled()) {
            return;
        }

        if(ev.entityPlayer instanceof FakePlayer) {
            if(!ProtectionManager.getFlagValueAtLocation(FlagType.FAKERS, ev.world.provider.dimensionId, ev.x, ev.y, ev.z)) {
                ev.setCanceled(true);
            }
        } else {
            Resident res = MyTownUniverse.instance.getOrMakeResident(ev.entityPlayer);
            if(ev.entityPlayer.getHeldItem() != null) {
                ProtectionManager.checkUsage(ev.entityPlayer.getHeldItem(), res, ev.action, createBlockPos(ev), ev.face, ev);
            }
            ProtectionManager.checkBlockInteraction(res, new BlockPos(ev.x, ev.y, ev.z, ev.world.provider.dimensionId), ev.action, ev);
        }
    }


    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockTrample(BlockTrampleEvent ev) {
        if(ev.world.isRemote || ev.isCanceled())
            return;

        Entity entity = ev.entity;
        Resident res = null;

        if(!(entity instanceof EntityPlayer)) {
            // Protect from players ridding any entity
            if(entity.riddenByEntity != null && (entity.riddenByEntity instanceof EntityPlayer))
                entity = entity.riddenByEntity;
            // Protect from players jumping and leaving the horse in mid-air
            else
                res = ProtectionManager.getOwner(entity);
        }

        // Fake players are special
        if(entity instanceof FakePlayer) {
            if(!ProtectionManager.getFlagValueAtLocation(FlagType.FAKERS, ev.world.provider.dimensionId, ev.x, ev.y, ev.z)) {
                ev.setCanceled(true);
            }
        } else {
            // Will be null if we didn't find the player responsible for this trampling
            if(res == null) {
                res = MyTownUniverse.instance.getOrMakeResident(entity);
                // Will be null if it wasn't caused by a known player
                if(res == null)
                    return;
            }

            // Trampling crops will break them and will modify the terrain
            if (!ProtectionManager.checkBlockBreak(ev.block)) {
                if(!ProtectionManager.hasPermission(res, FlagType.MODIFY, ev.world.provider.dimensionId, ev.x, ev.y, ev.z)) {
                    ev.setCanceled(true);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerBreaksBlock(BlockEvent.BreakEvent ev) {
        if(ev.world.isRemote || ev.isCanceled()) {
            return;
        }

        if(ev.getPlayer() instanceof FakePlayer) {
            if(!ProtectionManager.getFlagValueAtLocation(FlagType.FAKERS, ev.world.provider.dimensionId, ev.x, ev.y, ev.z)) {
                ev.setCanceled(true);
            }
        } else {
            Resident res = MyTownUniverse.instance.getOrMakeResident(ev.getPlayer());
            if (!ProtectionManager.checkBlockBreak(ev.block)) {
                if(!ProtectionManager.hasPermission(res, FlagType.MODIFY, ev.world.provider.dimensionId, ev.x, ev.y, ev.z)) {
                    ev.setCanceled(true);
                    return;
                }
            }

            if(ev.getPlayer().getHeldItem() != null) {
                ProtectionManager.checkBreakWithItem(ev.getPlayer().getHeldItem(), res, new BlockPos(ev.x, ev.y, ev.z, ev.world.provider.dimensionId), ev);
            }
        }

        if (!ev.isCanceled() && ev.block instanceof ITileEntityProvider) {
            TileEntity te = ((ITileEntityProvider) ev.block).createNewTileEntity(ev.world, ev.blockMetadata);
            if(te != null && ProtectionManager.isOwnable(te.getClass())) {
                te = ev.world.getTileEntity(ev.x, ev.y, ev.z);
                ownedTileEntities.remove(te);
                MyTown.instance.LOG.info("Removed te {}", te.toString());
            }
        }
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public void onItemPickup(EntityItemPickupEvent ev) {
        if(ev.entity.worldObj.isRemote || ev.isCanceled()) {
            return;
        }

        Resident res = MyTownUniverse.instance.getOrMakeResident(ev.entityPlayer);
        if(!ProtectionManager.hasPermission(res, FlagType.PICKUP, ev.item.dimension, (int) Math.floor(ev.item.posX), (int) Math.floor(ev.item.posY), (int) Math.floor(ev.item.posZ))) {
            ev.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent ev) {
        if(ev.entity.worldObj.isRemote || ev.isCanceled()) {
            return;
        }

        if(ev.source.getEntity() != null) {
            if(ev.entity instanceof EntityPlayer) {
                if (ev.source.getEntity() instanceof EntityPlayer) {
                    // Player vs Player
                    int x = (int) Math.floor(ev.entityLiving.posX);
                    int y = (int) Math.floor(ev.entityLiving.posY);
                    int z = (int) Math.floor(ev.entityLiving.posZ);
                    if(!ProtectionManager.getFlagValueAtLocation(FlagType.PVP, ev.entityLiving.dimension, x, y, z)) {
                        ev.setCanceled(true);
                    }
                } else {
                    // Entity vs Player (Check for Player owned Entity)
                    Resident res = MyTownUniverse.instance.getOrMakeResident(ev.entity);
                    ProtectionManager.checkPVP(ev.source.getEntity(), res, ev);
                }
            } else {
                if (ev.source.getEntity() instanceof EntityPlayer) {
                    // Player vs Living Entity
                    Resident res = MyTownUniverse.instance.getOrMakeResident(ev.source.getEntity());
                    ProtectionManager.checkInteraction(ev.entity, res, ev);
                } else {
                    // Entity vs Living Entity
                }
            }
        } else {
            // Non-Entity Damage
        }
    }

    @SubscribeEvent
    public void onAircraftAttack(AircraftAttackEvent ev) {
        if(ev.entity.worldObj.isRemote || ev.isCanceled()) {
            return;
        }

        if(ev.source.getEntity() != null) {
            if (ev.source.getEntity() instanceof EntityPlayer) {
                EntityPlayer entityPlayer = (EntityPlayer) ev.source.getEntity();
                // Player vs Plane
                Resident res = MyTownUniverse.instance.getOrMakeResident(ev.source.getEntity());
                ProtectionManager.checkInteraction(ev.entity, res, ev);

                if(!ev.isCanceled()) {
                    System.out.println("DamageType: "+ev.source.damageType+" ev.ownerId:"+ev.ownerId);
                    //if(ev.ownerId != null)
                        //System.out.println("N-equals:"+!ev.ownerId.equals(ev.source.getEntity().getPersistentID())+" " +
                        //        "Permission:"+!ProtectionManager.hasPermission(res, FlagType.MODIFY, ev.entity.worldObj.provider.dimensionId,
                        //        (int)ev.entity.posX, (int)ev.entity.posY, (int)ev.entity.posZ));
                    if(ev.ownerId != null && !ev.ownerId.equals(ev.source.getEntity().getPersistentID())) {
                        // Non-owner attack
                        if(ev.entity.riddenByEntity instanceof EntityPlayer) {
                            entityPlayer.addChatComponentMessage(new ChatComponentTranslation("vehicle.you.are.not.the.owner", ev.ownerName));
                            ev.setCanceled(true);
                            return;
                        }

                        if(ev.source.damageType.equals("arrow") || ev.source.damageType.equals("fireball")
                                || ev.source.damageType.equals("thrown") || ev.source.damageType.equals("player")) {
                            entityPlayer.addChatComponentMessage(new ChatComponentTranslation("vehicle.you.are.not.the.owner", ev.ownerName));
                            ev.setCanceled(true);
                            return;
                        }

                        /*if(ev.source.damageType.equals("player") &&
                                ProtectionManager.hasPermission(res, FlagType.MODIFY, ev.entity.worldObj.provider.dimensionId,
                                        (int)ev.entity.posX, (int)ev.entity.posY, (int)ev.entity.posZ)) {
                            //noinspection unchecked
                            for(EntityPlayer player: (List<EntityPlayer>) MinecraftServer.getServer().getConfigurationManager().playerEntityList){
                                if(player.getPersistentID().equals(ev.ownerId))
                                    return;
                            }

                            entityPlayer.addChatComponentMessage(new ChatComponentTranslation("vehicle.you.are.not.the.owner", ev.ownerName));
                            ev.setCanceled(true);
                            return;
                        }*/

                    }
                }
            } else {
                // Entity vs Living Entity
            }
        } else {
            // Non-Entity Damage
        }
    }

    @SubscribeEvent
    public void onPlayerPilotEvent(PlayerPilotAircraftEvent ev) {
        if(ev.entity.worldObj.isRemote || ev.isCanceled()
                || (ev.entityPlayer instanceof EntityPlayerMP
                && ((EntityPlayerMP) ev.entityPlayer).theItemInWorldManager.getGameType() == WorldSettings.GameType.CREATIVE)) {
            return;
        }

        Resident res = MyTownUniverse.instance.getOrMakeResident(ev.entityPlayer);
        ProtectionManager.checkInteraction(ev.entity, res, ev);

        if(!ev.isCanceled()) {
            if(ev.ownerId != null && !ev.ownerId.equals(ev.entityPlayer.getPersistentID())) {
                ev.setCanceled(true);
                ev.entityPlayer.addChatComponentMessage(new ChatComponentTranslation("vehicle.you.are.not.the.owner", ev.ownerName));
            }
        }
    }

    @SubscribeEvent
    public void onPlayerSpawnVehicle(PlayerSpawnVehicleEvent ev) {
        Resident res = MyTownUniverse.instance.getOrMakeResident(ev.entityPlayer);
        if(!ProtectionManager.hasPermission(res, FlagType.MODIFY, ev.entityPlayer.worldObj.provider.dimensionId,ev.x,ev.y,ev.z)) {
            int heightValue = 0;
            for(int x=-2; x<=2;x++)
                for(int z=-2; z<=2; z++)
                    for(int y = 255; y>=5;y--)
                        if(ev.entity.worldObj.getBlock(ev.x+x, y, ev.z+z) != Blocks.air) {
                            heightValue = y-1;
                            break;
                        };

            ev.entityPlayer.addChatComponentMessage(new ChatComponentText(heightValue+" "+ev.y));
            if(ev.y < heightValue) {
                ev.setCanceled(true);
                ev.entityPlayer.addChatComponentMessage(new ChatComponentText("Você só pode colocar este veículo em céu aberto"));
                return;
            }
        }

        ProtectionManager.checkUsage(ev.stack, res, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, new BlockPos(ev.x,ev.y,ev.z,ev.entityPlayer.worldObj.provider.dimensionId),0, ev);
    }

    @SubscribeEvent
    public void onAircraftDrop(AircraftDropEvent event) {
        if(event.ownerId == null)
            return;

        //noinspection unchecked
        for(EntityPlayer player: (List<EntityPlayer>)MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            if(player.getPersistentID().equals(event.ownerId)) {
                ItemStack stack = new ItemStack(event.item, event.amount, 0);
                if(!player.inventory.addItemStackToInventory(stack)) {
                    player.dropItem(event.item, event.amount);
                }

                player.addChatComponentMessage(new ChatComponentText("Seu veículo foi recuperado"));
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public void onBucketFill(FillBucketEvent ev) {
        if(ev.entity.worldObj.isRemote || ev.isCanceled()) {
            return;
        }

        int x = (int) Math.floor(ev.target.blockX);
        int y = (int) Math.floor(ev.target.blockY);
        int z = (int) Math.floor(ev.target.blockZ);

        if(ev.entityPlayer instanceof FakePlayer) {
            if(!ProtectionManager.getFlagValueAtLocation(FlagType.FAKERS, ev.world.provider.dimensionId, x, y, z)) {
                ev.setCanceled(true);
            }
        } else {
            Resident res = MyTownUniverse.instance.getOrMakeResident(ev.entityPlayer);
            if(!ProtectionManager.hasPermission(res, FlagType.USAGE, ev.world.provider.dimensionId, x, y, z)) {
                ev.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLiquidFlow(LiquidFlowEvent ev) {
        if(ev.world.isRemote || ev.isCanceled()) {
            return;
        }

        if(onAnyLiquidChange(ev.world.provider.dimensionId, ev.x, ev.y, ev.z, ev.toX, ev.toY, ev.toZ))
            ev.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLiquidReplaceBlock(LiquidReplaceBlockEvent ev) {
        if(ev.world.isRemote || ev.isCanceled()) {
            return;
        }

        if(onAnyLiquidChange(ev.world.provider.dimensionId, ev.x, ev.y, ev.z, ev.replacedX, ev.replacedY, ev.replacedZ))
            ev.setCanceled(true);
    }

    public boolean onAnyLiquidChange(int dim, int x, int y, int z, int toX, int toY, int toZ){
        TownBlock toTownBlock = MyTownUniverse.instance.blocks.get(dim, toX >> 4, toZ >> 4);
        Town toTown = toTownBlock == null? null : toTownBlock.getTown();
        Plot toPlot = toTown == null? null : toTown.plotsContainer.get(dim, toX, toY, toZ);

        TownBlock fromTownBlock = MyTownUniverse.instance.blocks.get(dim, x >> 4, z >> 4);
        Town fromTown = fromTownBlock == null? null : fromTownBlock.getTown();
        Plot fromPlot = fromTown == null? null : fromTown.plotsContainer.get(dim, x, y, z);

        if(toTown == null && fromTown == null || toPlot != null && fromPlot == toPlot) {
            // System.out.println("Allowed, toTown==null && fromTown == null || toPlot != null && fromPlot == toPlot");
            return false;
        }

        //System.out.println("\n\nFrom: "+x+" "+y+" "+z+" "+fromTownBlock+" "+fromTown+" "+fromPlot+"\n" +
        //        "To  : "+toX+" "+toY+" "+toZ+" "+toTownBlock+" "+toTown+" "+toPlot);

        if(toTown != null) {
            if(toPlot != null) {
                if(toPlot.flagsContainer.getValue(FlagType.MODIFY)) {
                    //System.out.println("Allowed, toPlot allows modify");
                    return false;
                }

                //System.out.println("Denied, toTown doesn't allows modify");
                return true;
            }

            if(toTown.flagsContainer.getValue(FlagType.MODIFY)) {
                //System.out.println("Allowed, toTown allows modify");
                return false;
            }

            if(fromTown != toTown) {
                //System.out.println("Denied, fromTown != toTown");
                return true;
            }
            else {
                if(fromPlot != null) {
                    //System.out.println("Denied, fromPlot != toPlot");
                    return true;
                }

                //System.out.println("Allowed, fromTown == toTown");
                return false;
            }
        }
        else {
            //noinspection RedundantIfStatement All this ifs can be simplified in a giant line that is hard to understand
            if(Wild.instance.flagsContainer.getValue(FlagType.MODIFY)) {
                //System.out.println("Allowed, wild allows modify");
                return false;
            }
            else {
                //System.out.println("Denied, wild doesn't allows modify");
                return true;
            }
        }
    }

    @SubscribeEvent
    public void entityJoinWorld(EntityJoinWorldEvent ev) {
        if(MyTown.instance.datasource == null) {
            return;
        }

        if (!(ev.entity instanceof EntityLiving)) {
            return;
        }

        ProtectionManager.check(ev.entity);
    }

    @SubscribeEvent
    public void specialSpawn(LivingSpawnEvent.SpecialSpawn ev) {
        if (ev.isCanceled()) return;

        ProtectionManager.check(ev.entity);
    }

    @SubscribeEvent
    public void checkSpawn(LivingSpawnEvent.CheckSpawn ev) {
        if (ev.getResult() == Event.Result.DENY) {
            return;
        }

        if(ProtectionManager.check(ev.entity)) {
            ev.setResult(Event.Result.DENY);
        }
    }

    // Fired AFTER the teleport
    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent ev) {
        Resident res = MyTownUniverse.instance.getOrMakeResident(ev.player);
        if(!ProtectionManager.hasPermission(res, FlagType.ENTER, ev.player.dimension, (int) Math.floor(ev.player.posX), (int) Math.floor(ev.player.posY), (int) Math.floor(ev.player.posZ))) {
            // Because of badly written teleportation code by Mojang we can only send the player back to spawn. :I
            res.respawnPlayer();
        }
    }

    private BlockPos createBlockPos(PlayerInteractEvent ev) {
        int x, y, z;

        if (ev.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR) {
            x = (int) Math.floor(ev.entityPlayer.posX);
            y = (int) Math.floor(ev.entityPlayer.posY);
            z = (int) Math.floor(ev.entityPlayer.posZ);
        } else {
            x = ev.x;
            y = ev.y;
            z = ev.z;
        }
        return new BlockPos(x, y, z, ev.world.provider.dimensionId);
    }
}
