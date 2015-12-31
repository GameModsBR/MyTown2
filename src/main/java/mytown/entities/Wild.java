package mytown.entities;

import mytown.entities.flag.Flag;
import mytown.entities.flag.FlagType;
import net.minecraft.util.EnumChatFormatting;

/**
 * Wilderness permissions
 */
public class Wild {

    public static final Wild instance = new Wild();

    public final Flag.Container flagsContainer = new Flag.Container();

    public boolean hasPermission(Resident res, FlagType<Boolean> flagType) {
        return hasPermission(res, flagType, false);
    }

    /**
     * Checks if Resident is allowed to do the action specified by the FlagType in the Wild
     */
    public boolean hasPermission(Resident res, FlagType<Boolean> flagType, boolean silent) {
        if (!flagsContainer.getValue(flagType)) {
            if (res != null && !silent) {
                res.protectionDenial(flagType, EnumChatFormatting.RED + "SERVER OWNERS");
            }
            return false;
        }
        return true;
    }
}
