package mytown.new_protection.segment;

import mytown.new_protection.segment.enums.EntityType;
import mytown.new_protection.segment.getter.Caller;
import mytown.new_protection.segment.getter.Getters;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;

import java.util.List;
import java.util.Map;

/**
 * Created by AfterWind on 1/1/2015.
 * Segment that protects against an Entity
 */
public class SegmentEntity extends Segment {

    public EntityType type;

    public SegmentEntity(Class<?> theClass, Getters getters, String conditionString, EntityType type) {
        super(theClass, getters, conditionString);
        this.type = type;
    }
}
