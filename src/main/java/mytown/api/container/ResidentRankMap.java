package mytown.api.container;

import mytown.entities.Rank;
import mytown.entities.Resident;
import myessentials.utils.ColorUtils;

import java.util.HashMap;
import java.util.Map;

public class ResidentRankMap extends HashMap<Resident, Rank> {

    public void remove(Resident res) {
        /*
        for (Iterator<Plot> it = res.getCurrentTown().plotsContainer.asList().iterator(); it.hasNext(); ) {
            Plot plot = it.next();
            if (plot.ownersContainer.contains(res) && plot.ownersContainer.size() <= 1) {
                it.remove();
            }
        }
        */
        super.remove(res);
    }

    public boolean contains(String username) {
        for (Resident res : keySet()) {
            if (res.getPlayerName().equals(username)) {
                return true;
            }
        }
        return false;
    }

    public Resident getMayor() {
        for(Map.Entry<Resident, Rank> entry : entrySet()) {
            if(entry.getValue().getType() == Rank.Type.MAYOR) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public String toString() {
        String formattedList = null;

        for (Map.Entry<Resident, Rank> entry : entrySet()) {
            String toAdd = ColorUtils.colorPlayer + entry.getKey().getPlayerName() + ColorUtils.colorComma + " (" + String.valueOf(entry.getValue()) + ColorUtils.colorComma + ")";
            if (formattedList == null) {
                formattedList = toAdd;
            } else {
                formattedList += ColorUtils.colorComma + ", " + toAdd;
            }
        }
        if (isEmpty()) {
            formattedList = ColorUtils.colorEmpty + "NONE";
        }
        return formattedList;
    }
}
