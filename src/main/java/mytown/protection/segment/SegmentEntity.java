package mytown.protection.segment;

import myessentials.entities.Volume;
import mytown.entities.Resident;
import mytown.entities.flag.FlagType;
import mytown.protection.ProtectionManager;
import mytown.protection.segment.enums.EntityType;
import net.minecraft.command.IEntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;

import java.util.ArrayList;
import java.util.List;

/**
 * Segment that protects against an Entity
 */
public class SegmentEntity extends Segment {

    public final List<EntityType> types = new ArrayList<EntityType>();

    public boolean shouldExist(Entity entity) {
        if(!types.contains(EntityType.TRACKED)) {
            return true;
        }

        if(!shouldCheck(entity)) {
            return true;
        }

        Resident owner = getOwner(entity);
        int range = getRange(entity);
        int dim = entity.dimension;
        int x = (int) Math.floor(entity.posX);
        int y = (int) Math.floor(entity.posY);
        int z = (int) Math.floor(entity.posZ);

        if(range == 0) {
            if (!hasPermissionAtLocation(owner, dim, x, y, z)) {
                return false;
            }
        } else {
            Volume rangeBox = new Volume(x-range, y-range, z-range, x+range, y+range, z+range);
            if (!hasPermissionAtLocation(owner, dim, rangeBox)) {
                return false;
            }
        }
        return true;
    }

    public boolean shouldImpact(Entity entity, final Resident owner, MovingObjectPosition mop) {
        if(!types.contains(EntityType.IMPACT)) {
            return true;
        }

        if(!shouldCheck(entity)) {
            return true;
        }

        int range = getRange(entity);
        int dim = entity.dimension;
        int x = (int) Math.floor(mop.hitVec.xCoord);
        int y = (int) Math.floor(mop.hitVec.yCoord);
        int z = (int) Math.floor(mop.hitVec.zCoord);

        final boolean pvp = flags.contains(FlagType.PVP);
        final boolean pve = flags.contains(FlagType.PVE);

        IEntitySelector selector = new IEntitySelector() {
            @Override
            public boolean isEntityApplicable(Entity entity) {
                if(entity instanceof EntityPlayer) {
                    if(!pvp) return false;
                    if(owner == null) return false;
                    return !entity.getPersistentID().equals(owner.getUUID())
                            && !ProtectionManager.hasPermission(owner, FlagType.PVP, entity.worldObj.provider.dimensionId, (int)entity.posX, (int)entity.posY, (int)entity.posZ);
                }

                if(entity instanceof EntityLivingBase) {
                    if(!pve) return false;
                    for(SegmentEntity segmentEntity: ProtectionManager.segmentsEntity.get(entity.getClass())) {
                        if(!segmentEntity.shouldInteract(entity, owner))
                            return true;
                    }
                    return false;
                }

                return false;
            }
        };

        if(range > 0) {
            @SuppressWarnings("unchecked")
            List<Entity> permissionFailedEntities = !pve && !pvp? null : entity.worldObj.getEntitiesWithinAABBExcludingEntity(entity,
                    AxisAlignedBB.getBoundingBox(mop.hitVec.xCoord-range, mop.hitVec.yCoord-range, mop.hitVec.zCoord-range, mop.hitVec.xCoord+range, mop.hitVec.yCoord+range, mop.hitVec.zCoord+range),
                    selector
            );

            if(permissionFailedEntities != null && !permissionFailedEntities.isEmpty() || selector.isEntityApplicable(mop.entityHit))
                return false;
        }
        else if(selector.isEntityApplicable(mop.entityHit))
            return false;

        if(range == 0) {
            if (!hasPermissionAtLocationExcluding(owner, dim, x, y, z, FlagType.PVP, FlagType.PVE)) {
                return false;
            }
        } else {
            Volume rangeBox = new Volume(x-range, y-range, z-range, x+range, y+range, z+range);
            if (!hasPermissionAtLocationExcluding(owner, dim, rangeBox, FlagType.PVP, FlagType.PVE)) {
                return false;
            }
        }
        return true;
    }

    public boolean shouldInteract(Entity entity, Resident res) {
        if(!types.contains(EntityType.PROTECT)) {
            return true;
        }

        if(!shouldCheck(entity)) {
            return true;
        }

        Resident owner = getOwner(entity);
        int dim = entity.dimension;
        int x = (int) Math.floor(entity.posX);
        int y = (int) Math.floor(entity.posY);
        int z = (int) Math.floor(entity.posZ);

        if (owner != null && res.getUUID().equals(owner.getUUID())) {
            return true;
        }

        if (!hasPermissionAtLocation(res, dim, x, y, z)) {
            return false;
        }

        return true;
    }

    public boolean shouldAttack(Entity entity, Resident res) {
        if(!types.contains(EntityType.PVP)) {
            return true;
        }

        if(!shouldCheck(entity)) {
            return true;
        }

        Resident owner = getOwner(entity);
        EntityPlayer attackedPlayer = res.getPlayer();
        int dim = attackedPlayer.dimension;
        int x = (int) Math.floor(attackedPlayer.posX);
        int y = (int) Math.floor(attackedPlayer.posY);
        int z = (int) Math.floor(attackedPlayer.posZ);

        if(owner != null && !ProtectionManager.getFlagValueAtLocation(FlagType.PVP, dim, x, y, z)) {
            return false;
        }

        return true;
    }
}
