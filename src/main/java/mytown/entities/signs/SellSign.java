package mytown.entities.signs;

import myessentials.Localization;
import myessentials.classtransformers.SignClassTransformer;
import myessentials.economy.IEconManager;
import myessentials.entities.BlockPos;
import myessentials.entities.sign.Sign;
import myessentials.entities.sign.SignType;
import mytown.MyTown;
import mytown.entities.Plot;
import mytown.entities.Resident;
import mytown.new_datasource.MyTownUniverse;
import mytown.proxies.EconomyProxy;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntitySign;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SellSign extends Sign {
    private int price;
    private boolean restricted;
    private Resident owner;
    private Plot plot;

    public SellSign(BlockPos bp, int face, Resident owner, int price, boolean restricted) {
        super(SellSignType.instance);
        this.bp = bp;
        this.price = price;
        this.restricted = restricted;
        this.plot = MyTownUniverse.instance.plots.get(bp.getDim(), bp.getX(), bp.getY(), bp.getZ());
        this.owner = owner;
        NBTTagCompound data = new NBTTagCompound();
        data.setString("Owner", owner.getUUID().toString());
        data.setInteger("Price", price);
        data.setBoolean("Restricted", restricted);
        this.data = data;
        createSignBlock(owner.getPlayer(), bp, face);
    }

    public SellSign(TileEntitySign te, NBTTagCompound signData) {
        super(SellSignType.instance);
        this.bp = new BlockPos(te.xCoord, te.yCoord, te.zCoord, te.getWorldObj().provider.dimensionId);
        this.owner = MyTownUniverse.instance.getOrMakeResident(UUID.fromString(signData.getString("Owner")));
        this.price = signData.getInteger("Price");
        this.restricted = signData.getBoolean("Restricted");
        this.plot = MyTownUniverse.instance.plots.get(te.getWorldObj().provider.dimensionId, te.xCoord, te.yCoord, te.zCoord);
    }

    @Override
    public void onRightClick(EntityPlayer player) {
        Resident resident = MyTownUniverse.instance.getOrMakeResident(player);
        if(restricted && !plot.getTown().residentsMap.containsKey(resident)) {
            resident.sendMessage(getLocal().getLocalization("mytown.cmd.err.notInTown", plot.getTown().getName()));
            return;
        }

        if(plot.ownersContainer.contains(resident)) {
            resident.sendMessage(getLocal().getLocalization("mytown.cmd.err.plot.sell.alreadyOwner"));
            return;
        }

        if(!plot.ownersContainer.contains(owner)) {
            resident.sendMessage(getLocal().getLocalization("mytown.notification.plot.buy.alreadySold", owner.getPlayerName()));
            return;
        }

        if(!plot.getTown().plotsContainer.canResidentMakePlot(resident)) {
            resident.sendMessage(getLocal().getLocalization("mytown.cmd.err.plot.limit", plot.getTown().plotsContainer.getMaxPlots()));
            return;
        }

        if (EconomyProxy.getEconomy().takeMoneyFromPlayer(resident.getPlayer(), price)) {
            boolean mayorOwner = true;
            for (Resident resInPlot : plot.ownersContainer) {
                if(mayorOwner && !resInPlot.getUUID().equals(plot.getTown().residentsMap.getMayor().getUUID()))
                    mayorOwner = false;
            }

            Resident.Container residentsToRemove = new Resident.Container();
            Resident.Container owners = new Resident.Container();

            residentsToRemove.addAll(plot.membersContainer);
            residentsToRemove.addAll(plot.ownersContainer);
            owners.addAll(plot.ownersContainer);

            for (Resident resInPlot : residentsToRemove) {
                MyTown.instance.datasource.unlinkResidentFromPlot(resInPlot, plot);
            }

            if(!plot.getTown().residentsMap.containsKey(resident)) {
                MyTown.instance.datasource.linkResidentToTown(resident, plot.getTown(), plot.getTown().ranksContainer.getDefaultRank());
            }
            MyTown.instance.datasource.linkResidentToPlot(resident, plot, true);
            resident.sendMessage(getLocal().getLocalization("mytown.notification.plot.buy.newOwner", plot.getName()));
            boolean payed = false;
            if(!(mayorOwner || EconomyProxy.isItemEconomy() || owners.isEmpty()))
            {
                if(owners.size() == 1) {
                    IEconManager eco = EconomyProxy.getEconomy().economyManagerForUUID(owner.getUUID());
                    if(eco != null) {
                        eco.addToWallet(price);
                        owner.sendMessage(getLocal().getLocalization("mytown.notification.plot.buy.oldOwnerSingle", plot.getName(), EconomyProxy.getCurrency(price)));
                        payed = true;
                    }
                }
                else {
                    double split = ((double)price) / owners.size();
                    double change = 0;
                    int value = (int) split;
                    List<IEconManager> ownerAccounts = new ArrayList<IEconManager>(owners.size());
                    for(Resident owner: owners) {
                        IEconManager eco = EconomyProxy.getEconomy().economyManagerForUUID(owner.getUUID());
                        if(eco == null)
                            change += value;
                        else {
                            ownerAccounts.add(eco);
                            eco.addToWallet(value);
                            change += split - value;
                        }
                    }

                    if(!ownerAccounts.isEmpty()) {
                        while (change > 1.0) {
                            for(IEconManager account: ownerAccounts) {
                                account.addToWallet(1);
                                if(--change < 1.0)
                                    break;
                            }

                            value++;
                        }

                        for(Resident owner: owners) {
                            owner.sendMessage(getLocal().getLocalization("mytown.notification.plot.buy.oldOwnerSplit", plot.getName(), EconomyProxy.getCurrency(price), EconomyProxy.getCurrency(value)));
                        }
                        payed = true;
                    }
                }
            }
            if(!payed) {
                plot.getTown().bank.addAmount(price);
                for(Resident owner: owners) {
                    owner.sendMessage(getLocal().getLocalization("mytown.notification.plot.buy.oldOwner", plot.getName(), EconomyProxy.getCurrency(price)));
                }
            }

            deleteSignBlock();
            plot.deleteSignBlocks(signType, player.worldObj);
        } else {
            resident.sendMessage(getLocal().getLocalization("mytown.notification.plot.buy.failed", EconomyProxy.getCurrency(price)));
        }
    }

    @Override
    protected String[] getText() {
        return new String[] {
                MyTown.instance.LOCAL.getLocalization("mytown.sign.sell.title"),
                MyTown.instance.LOCAL.getLocalization("mytown.sign.sell.description.owner")+" " + owner.getPlayerName(),
                MyTown.instance.LOCAL.getLocalization("mytown.sign.sell.description.price") + price,
                restricted ? MyTown.instance.LOCAL.getLocalization("mytown.sign.sell.description.restricted") : ""
        };
    }

    @Override
    public void onShiftRightClick(EntityPlayer player) {
        if(player.getPersistentID().equals(owner.getUUID())) {
            deleteSignBlock();
        }
    }

    public Localization getLocal() {
        return MyTown.instance.LOCAL;
    }

    public static class SellSignType extends SignType {
        public static final SellSignType instance = new SellSignType();

        @Override
        public String getTypeID() {
            return "MyTown:SellSign";
        }

        @Override
        public Sign loadData(TileEntitySign tileEntity, NBTBase signData) {
            return new SellSign(tileEntity, (NBTTagCompound) signData);
        }

        @Override
        public boolean isTileValid(TileEntitySign te) {
            if (!te.signText[0].startsWith(Sign.IDENTIFIER)) {
                return false;
            }

            try {
                NBTTagCompound rootTag = SignClassTransformer.getMyEssentialsDataValue(te);
                if (rootTag == null)
                    return false;

                if (!rootTag.getString("Type").equals(SellSignType.instance.getTypeID()))
                    return false;

                NBTBase data = rootTag.getTag("Value");
                if (!(data instanceof NBTTagCompound))
                    return false;

                NBTTagCompound signData = (NBTTagCompound) data;

                MyTownUniverse.instance.getOrMakeResident(UUID.fromString(signData.getString("Owner")));
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
}
