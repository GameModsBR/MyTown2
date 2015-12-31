package mytown.protection.eventhandlers;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import myessentials.utils.WorldUtils;
import mytown.entities.Resident;
import mytown.new_datasource.MyTownUniverse;
import mytown.entities.TownBlock;
import mytown.entities.Wild;
import mytown.entities.flag.FlagType;
import myessentials.entities.ChunkPos;
import mytown.protection.ProtectionManager;
import mytown.protection.segment.SegmentEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ExplosionEvent;

import java.util.Iterator;
import java.util.List;

/**
 * Handling any events that are not yet compatible with the most commonly used version of forge.
 */
public class ExtraEventsHandler {

    private static ExtraEventsHandler instance;
    public static ExtraEventsHandler getInstance() {
        if(instance == null)
            instance = new ExtraEventsHandler();
        return instance;
    }

    /**
     * Forge 1254 is needed for this
     */
    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Start ev) {
        if(ev.world.isRemote)
            return;
        if (ev.isCanceled())
            return;
        List<ChunkPos> chunks = WorldUtils.getChunksInBox(ev.world.provider.dimensionId, (int) (ev.explosion.explosionX - ev.explosion.explosionSize - 2), (int) (ev.explosion.explosionZ - ev.explosion.explosionSize - 2), (int) (ev.explosion.explosionX + ev.explosion.explosionSize + 2), (int) (ev.explosion.explosionZ + ev.explosion.explosionSize + 2));
        for(ChunkPos chunk : chunks) {
            TownBlock block = MyTownUniverse.instance.blocks.get(ev.world.provider.dimensionId, chunk.getX(), chunk.getZ());
            if(block == null) {
                if(!(Boolean)Wild.instance.flagsContainer.getValue(FlagType.EXPLOSIONS)) {
                    ev.setCanceled(true);
                    return;
                }
            } else {
                if (!(Boolean) block.getTown().flagsContainer.getValue(FlagType.EXPLOSIONS)) {
                    ev.setCanceled(true);
                    block.getTown().notifyEveryone(FlagType.EXPLOSIONS.getLocalizedTownNotification());
                    return;
                }
            }
        }

        if(ev.explosion.exploder == null){
            ev.explosion.exploder = findExploder(ev.world, ev.explosion.explosionX, ev.explosion.explosionY, ev.explosion.explosionZ, 0.5, Entity.class);

            if(ev.explosion.exploder == null) {
                ev.explosion.exploder = findExploder(ev.world, ev.explosion.explosionX, ev.explosion.explosionY, ev.explosion.explosionZ, 40, EntityPlayer.class);
            }
        }
    }

    private Entity findExploder(World world, double explosionX, double explosionY, double explosionZ, double range, Class filter){
        @SuppressWarnings("unchecked")
        List<Entity> list = world.getEntitiesWithinAABB(filter, AxisAlignedBB.getBoundingBox(
                explosionX-range, explosionY-range, explosionZ-range,
                explosionX+range, explosionY+range, explosionZ+range
        ));

        if(list.size() == 1)
            return list.get(0);
        else {
            double distance = Double.MAX_VALUE;
            Entity closest = null;

            for(Entity e: list){
                double d = e.getDistanceSq(explosionX, explosionY, explosionZ);
                if(d < distance){
                    distance = d;
                    closest = e;
                    if(distance == 0)
                        break;
                }
            }

            return closest;
        }
    }

    @SubscribeEvent
    public void onDetonate(ExplosionEvent.Detonate ev) {
        if(ev.world.isRemote || ev.isCanceled())
            return;

        Resident exploder = ev.explosion.exploder == null? null : (ev.explosion.exploder instanceof EntityPlayer)?
                MyTownUniverse.instance.getOrMakeResident(ev.explosion.exploder):
                ProtectionManager.getOwner(ev.explosion.exploder);

        boolean skipPlayerCheck = false;
        if(exploder == null && ev.explosion.exploder instanceof EntityCreature){
            EntityCreature creature = (EntityCreature) ev.explosion.exploder;
            Entity target = creature.getAttackTarget();
            exploder = target == null? null : (target instanceof EntityPlayer)?
                    MyTownUniverse.instance.getOrMakeResident(target):
                    ProtectionManager.getOwner(target);
            skipPlayerCheck = true;
        }

        Iterator<ChunkPosition> blockIterator = ev.getAffectedBlocks().iterator();
        int dimensionId = ev.world.provider.dimensionId;
        boolean silent = false;
        while (blockIterator.hasNext()){
            ChunkPosition pos = blockIterator.next();
            if(exploder == null){
                if(ProtectionManager.getFlagValueAtLocation(FlagType.MODIFY, dimensionId, pos.chunkPosX, pos.chunkPosY, pos.chunkPosZ) == Boolean.FALSE)
                    blockIterator.remove();
            }
            else if(!ProtectionManager.hasPermission(exploder, FlagType.MODIFY, dimensionId, pos.chunkPosX, pos.chunkPosY, pos.chunkPosZ, silent)){
                blockIterator.remove();
                silent = true;
            }
        }

        if(exploder == null)
            skipPlayerCheck = true;

        boolean silentPvP = false;
        boolean silentPvE = false;
        Iterator<Entity> entityIterator = ev.getAffectedEntities().iterator();
        while (entityIterator.hasNext()){
            Entity entity = entityIterator.next();
            if(entity instanceof EntityPlayer) {
                if(!skipPlayerCheck && !entity.getPersistentID().equals(exploder.getUUID())
                        && !ProtectionManager.hasPermission(exploder, FlagType.PVP, dimensionId, (int)entity.posX, (int)entity.posY, (int)entity.posZ, silentPvP)) {
                    entityIterator.remove();
                    silentPvP = true;
                }
            }
            else if(exploder == null)
                entityIterator.remove();
            else {
                for (SegmentEntity segment: ProtectionManager.segmentsEntity.get(entity.getClass())){
                    if(!segment.shouldInteract(entity, exploder)){
                        entityIterator.remove();
                        if(!silentPvE){
                            exploder.protectionDenial(FlagType.PVE);
                            silentPvE = true;
                        }
                        break;
                    }
                }
            }
        }
    }
}
