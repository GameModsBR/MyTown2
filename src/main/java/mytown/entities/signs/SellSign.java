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
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntitySign;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
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
            ArrayList<String> ownersName = new ArrayList<String>(plot.ownersContainer.size());
            for (Resident resInPlot : plot.ownersContainer) {
                if(mayorOwner && !resInPlot.getUUID().equals(plot.getTown().residentsMap.getMayor().getUUID()))
                    mayorOwner = false;
                ownersName.add(resInPlot.getPlayerName());
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
            double split = 0, change = 0;
            int value = 0;
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
                    split = ((double)price) / owners.size();
                    change = 0;
                    value = (int) split;
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
                mayorOwner = true;
                plot.getTown().bank.addAmount(price);
                for(Resident owner: owners) {
                    owner.sendMessage(getLocal().getLocalization("mytown.notification.plot.buy.oldOwner", plot.getName(), EconomyProxy.getCurrency(price)));
                }
            }

            deleteSignBlock();
            plot.deleteSignBlocks(signType, player.worldObj);

            ItemStack stack = new ItemStack(Items.written_book);
            stack.setTagInfo("author", new NBTTagString("#Recibo"));
            stack.setTagInfo("title", new NBTTagString("Comprovante de compra de lote"));
            NBTTagList pages = new NBTTagList();
            Date dateObj = new Date();
            String date = DateFormat.getDateInstance(DateFormat.SHORT).format(dateObj);
            String time = DateFormat.getTimeInstance(DateFormat.LONG).format(dateObj);
            String price = EconomyProxy.getCurrency(this.price);
            pages.appendTag(new NBTTagString("=== IMPORTANTE ===\n" +
                    "Guarde esse documento em local seguro, " +
                    "a perda deste documento resultara na perda da garantia de seu lote.\n\n" +
                    "Comprovante de aquisicao do lote "+plot.getName()+" da cidade "+plot.getTown().getName()+" por "+player.getCommandSenderName()+" no valor de "+price+" em "+date
            ));
            pages.appendTag(new NBTTagString("====== LOTE ======\n" +
                    "Nome: "+plot.getName()+"\n" +
                    "Cidade: "+plot.getTown().getName()+"\n" +
                    "Dimensao: "+plot.getDim()+"\n"+
                    "Localizacao: \n"+
                    String.format("%-7d %-3d %-7d\n", plot.getStartX(), plot.getStartY(),plot.getStartZ()) +
                    String.format("%-7d %-3d %-7d\n", plot.getEndX(), plot.getEndY(), plot.getEndZ()) +
                    "Valor: "+price+"\n" +
                    "Data: "+date+"\n"+
                    "Hora: "+time+"\n"
            ));
            pages.appendTag(new NBTTagString("==== COMPRADOR ====\n" +
                    "Nome: "+player.getCommandSenderName()+"\n" +
                    "UUID: "+player.getPersistentID()+"\n" +
                    "Data: "+date+"\n" +
                    "Hora: "+time+"\n"));
            pages.appendTag(new NBTTagString("===== VENDEDOR =====\n" +
                    "Nome: "+owner.getPlayerName()+"\n" +
                    "UUID: "+owner.getUUID()+"" +
                    "Data: "+date+"\n" +
                    "Hora: "+time+"\n"));
            pages.appendTag(new NBTTagString("===== PAGAMENTO =====\n" +
                    (mayorOwner? "Banco da prefeitura\nValor: "+price :
                        "Dividido entre os donos\nValor: "+price+"\nDonos: "+ownersName.size()+"\nDivisao: "+split+"" +
                            "\nTaxa: "+change+"\nRecebido: "+value)
            ));
            String nameString = ownersName.toString();
            pages.appendTag(new NBTTagString("== ANTIGOS DONOS ==\n" +
                    "Data: "+date+"\n" +
                    "Hora: "+time+"\n" +
                    "Nomes: "+ nameString.substring(1, nameString.length()-1)));
            stack.setTagInfo("pages", pages);
            player.inventory.addItemStackToInventory(stack);
            player.inventoryContainer.detectAndSendChanges();
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
