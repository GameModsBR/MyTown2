package mytown.commands;

import myessentials.entities.tool.ToolManager;
import myessentials.utils.ChatUtils;
import myessentials.utils.ColorUtils;
import myessentials.utils.StringUtils;
import mypermissions.api.command.CommandResponse;
import mypermissions.api.command.annotation.Command;
import mytown.config.Config;
import mytown.entities.signs.SellSign;
import mytown.new_datasource.MyTownUniverse;
import mytown.entities.*;
import mytown.entities.flag.Flag;
import mytown.entities.flag.FlagType;
import mytown.entities.tools.PlotSelectionTool;
import mytown.entities.tools.PlotSellTool;
import myessentials.entities.tool.Tool;
import mytown.entities.tools.WhitelisterTool;
import mytown.proxies.EconomyProxy;
import mytown.util.exceptions.MyTownCommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import java.util.Iterator;
import java.util.List;

/**
 * Process methods for all commands that can be used by everyone
 */
public class CommandsEveryone extends Commands {

    @Command(
            name = "cidade",
            permission = "mytown.cmd",
            alias = {"t", "town", "mytown"},
            syntax = "/cidade <comando>")
    public static CommandResponse townCommand(ICommandSender sender, List<String> args) {
        return CommandResponse.SEND_HELP_MESSAGE;
    }

    @Command(
            name = "sair",
            permission = "mytown.cmd.everyone.leave",
            parentName = "mytown.cmd",
            syntax = "/cidade sair [apagar]")
    public static CommandResponse leaveCommand(ICommandSender sender, List<String> args) {
        Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
        Town town = getTownFromResident(res);

        if (town.residentsMap.get(res) != null && town.residentsMap.get(res).getType() == Rank.Type.MAYOR) {
            throw new MyTownCommandException("mytown.notification.town.left.asMayor");
        }

        getDatasource().unlinkResidentFromTown(res, town);

        res.sendMessage(getLocal().getLocalization("mytown.notification.town.left.self", town.getName()));
        town.notifyEveryone(getLocal().getLocalization("mytown.notification.town.left", res.getPlayerName(), town.getName()));
        return CommandResponse.DONE;
    }

    @Command(
            name = "spawn",
            permission = "mytown.cmd.everyone.spawn",
            parentName = "mytown.cmd",
            syntax = "/cidade spawn [town]",
            completionKeys = {"townCompletion"})
    public static CommandResponse spawnCommand(ICommandSender sender, List<String> args) {
        EntityPlayer player = (EntityPlayer)sender;
        Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
        Town town;
        int amount;

        if (args.isEmpty()) {
            town = getTownFromResident(res);
            amount = Config.instance.costAmountSpawn.get();
        } else {
            town = getTownFromName(args.get(0));
            amount = Config.instance.costAmountOtherSpawn.get();
        }

        if (!town.hasSpawn())
            throw new MyTownCommandException("mytown.cmd.err.spawn.notexist", town.getName());

        if(!town.hasPermission(res, FlagType.ENTER, town.getSpawn().getDim(), (int) town.getSpawn().getX(), (int) town.getSpawn().getY(), (int) town.getSpawn().getZ()))
            throw new MyTownCommandException("mytown.cmd.err.spawn.protected", town.getName());

        if(res.getTeleportCooldown() > 0)
            throw new MyTownCommandException("mytown.cmd.err.spawn.cooldown", res.getTeleportCooldown(), res.getTeleportCooldown() / 20);

        makePayment(player, amount);
        town.bank.addAmount(amount);
        getDatasource().saveTownBank(town.bank);
        town.sendToSpawn(res);
        return CommandResponse.DONE;
    }

    @Command(
            name = "selecionar",
            permission = "mytown.cmd.everyone.select",
            parentName = "mytown.cmd",
            syntax = "/cidade selecionar <town>",
            completionKeys = {"townCompletion"})
    public static CommandResponse selectCommand(ICommandSender sender, List<String> args) {
        if (args.size() < 1)
            return CommandResponse.SEND_SYNTAX;
        Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
        Town town = getTownFromName(args.get(0));
        if (!town.residentsMap.containsKey(res))
            throw new MyTownCommandException("mytown.cmd.err.select.notpart", args.get(0));
        getDatasource().saveSelectedTown(res, town);
        res.sendMessage(getLocal().getLocalization("mytown.notification.town.select", args.get(0)));
        return CommandResponse.DONE;
    }


    @Command(
            name = "blocos",
            permission = "mytown.cmd.everyone.blocks",
            parentName = "mytown.cmd",
            syntax = "/cidade blocos <comando>")
    public static CommandResponse blocksCommand(ICommandSender sender, List<String> args) {
        return CommandResponse.SEND_HELP_MESSAGE;
    }

    @Command(
            name = "listar",
            permission = "mytown.cmd.everyone.blocks.list",
            parentName = "mytown.cmd.everyone.blocks",
            syntax = "/cidade blocos listar")
    public static CommandResponse blocksListCommand(ICommandSender sender, List<String> args) {
        Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
        Town town = getTownFromResident(res);

        sendMessageBackToSender(sender, getLocal().getLocalization("mytown.notification.block.list", town.getName(), "\\n" + town.townBlocksContainer.toString()));
        return CommandResponse.DONE;
    }

    @Command(
            name = "info",
            permission = "mytown.cmd.everyone.blocks.info",
            parentName = "mytown.cmd.everyone.blocks",
            syntax = "/cidade blocos info")
    public static CommandResponse blocksInfoCommand(ICommandSender sender, List<String> args) {
        Resident res = getUniverse().getOrMakeResident(sender);
        Town town = getTownFromResident(res);

        String blocks = town.townBlocksContainer.size() + "/" + town.getMaxBlocks();
        String extraBlocks = town.getExtraBlocks() + "\\n";
        String dash = ColorUtils.colorInfoText + " - ";
        extraBlocks += dash + "TOWN (" + town.townBlocksContainer.getExtraBlocks() + ")\\n";
        for(Iterator<Resident> it = town.residentsMap.keySet().iterator(); it.hasNext();) {
            Resident resInTown = it.next();
            extraBlocks += dash + ColorUtils.colorInfoText + resInTown.getPlayerName() + " (" + resInTown.getExtraBlocks() + ")";
            if(it.hasNext()) {
                extraBlocks += "\\n";
            }
        }

        String farBlocks = town.townBlocksContainer.getFarClaims() + "/" + town.getMaxFarClaims();

        res.sendMessage(getLocal().getLocalization("mytown.notification.blocks.info", blocks, extraBlocks, farBlocks));

        return CommandResponse.DONE;
    }

    @Command(
            name = "perm",
            permission = "mytown.cmd.everyone.perm",
            parentName = "mytown.cmd",
            syntax = "/cidade perm <comando>")
    public static CommandResponse permCommand(ICommandSender sender, List<String> args) {
        return CommandResponse.SEND_HELP_MESSAGE;
    }

    @Command(
            name = "listar",
            permission = "mytown.cmd.everyone.perm.list",
            parentName = "mytown.cmd.everyone.perm",
            syntax = "/cidade perm listar")
    public static CommandResponse permListCommand(ICommandSender sender, List<String> args) {
        Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
        Town town = getTownFromResident(res);
        res.sendMessage(town.flagsContainer.toStringForTowns());
        return CommandResponse.DONE;
    }

    public static class Plots {

        @Command(
                name = "perm",
                permission = "mytown.cmd.everyone.plot.perm",
                parentName = "mytown.cmd.everyone.plot",
                syntax = "/cidade lote perm <comando>")
        public static CommandResponse plotPermCommand(ICommandSender sender, List<String> args) {
            return CommandResponse.SEND_HELP_MESSAGE;
        }

        @Command(
                name = "definir",
                permission = "mytown.cmd.everyone.plot.perm.set",
                parentName = "mytown.cmd.everyone.plot.perm",
                syntax = "/cidade lote perm definir <flag> <value>",
                completionKeys = {"flagCompletion"})
        public static CommandResponse plotPermSetCommand(ICommandSender sender, List<String> args) {
            if (args.size() < 2) {
                return CommandResponse.SEND_SYNTAX;
            }

            Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
            Plot plot = getPlotAtResident(res);
            if (!plot.ownersContainer.contains(res) && !plot.getTown().hasPermission(res, "mytown.bypass.plot")) {
                throw new MyTownCommandException("mytown.cmd.err.plot.perm.set.noPermission");
            }

            Flag flag = getFlagFromName(plot.flagsContainer, args.get(0));

            if (flag.setValue(args.get(1))) {
                ChatUtils.sendLocalizedChat(sender, getLocal(), "mytown.notification.perm.success");
            } else {
                throw new MyTownCommandException("mytown.cmd.err.perm.valueNotValid", args.get(1));
            }

            getDatasource().saveFlag(flag, plot);
            return CommandResponse.DONE;
        }

        @Command(
                name = "alternar",
                permission = "mytown.cmd.everyone.plot.perm.toggle",
                parentName = "mytown.cmd.everyone.plot.perm",
                syntax = "/cidade lote perm alternar <flag>",
                completionKeys = {"flagCompletion"})
        public static CommandResponse plotPermToggleCommand(ICommandSender sender, List<String> args) {
            if (args.size() < 1) {
                return CommandResponse.SEND_SYNTAX;
            }

            Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
            Plot plot = getPlotAtResident(res);
            if (!plot.ownersContainer.contains(res) && !plot.getTown().hasPermission(res, "mytown.bypass.plot")) {
                throw new MyTownCommandException("mytown.cmd.err.plot.perm.set.noPermission");
            }

            Flag flag = getFlagFromName(plot.flagsContainer, args.get(0));

            if (flag.toggle()) {
                ChatUtils.sendLocalizedChat(sender, getLocal(), "mytown.notification.perm.success");
            } else {
                throw new MyTownCommandException("mytown.cmd.err.perm.valueNotValid", args.get(1));
            }

            getDatasource().saveFlag(flag, plot);
            return CommandResponse.DONE;
        }

        @Command(
                name = "listar",
                permission = "mytown.cmd.everyone.plot.perm.list",
                parentName = "mytown.cmd.everyone.plot.perm",
                syntax = "/cidade lote perm listar")
        public static CommandResponse plotPermListCommand(ICommandSender sender, List<String> args) {
            Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
            Plot plot = getPlotAtResident(res);
            res.sendMessage(plot.flagsContainer.toStringForPlot(plot.getTown()));
            return CommandResponse.DONE;
        }

        @Command(
                name = "ignorar",
                permission = "mytown.cmd.everyone.plot.perm.whitelist",
                parentName = "mytown.cmd.everyone.plot.perm",
                syntax = "/cidade lote perm ignorar")
        public static CommandResponse plotPermWhitelistCommand(ICommandSender sender, List<String> args) {
            Resident res = MyTownUniverse.instance.getOrMakeResident(sender);

            ToolManager.instance.register(new WhitelisterTool(res));
            res.sendMessage(getLocal().getLocalization("mytown.notification.perm.whitelist.start"));
            return CommandResponse.DONE;
        }

        @Command(
                name = "lote",
                permission = "mytown.cmd.everyone.plot",
                parentName = "mytown.cmd",
                syntax = "/cidade lote <comando>")
        public static CommandResponse plotCommand(ICommandSender sender, List<String> args) {
            return CommandResponse.SEND_HELP_MESSAGE;
        }

        @Command(
                name = "renomear",
                permission = "mytown.cmd.everyone.plot.rename",
                parentName = "mytown.cmd.everyone.plot",
                syntax = "/cidade lote renomear <nome>")
        public static CommandResponse plotRenameCommand(ICommandSender sender, List<String> args) {
            if (args.size() < 1) {
                return CommandResponse.SEND_SYNTAX;
            }

            Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
            Plot plot = getPlotAtResident(res);

            if (!plot.ownersContainer.contains(res) && !plot.getTown().hasPermission(res, "mytown.bypass.plot")) {
                throw new MyTownCommandException("mytown.cmd.err.plot.perm.set.noPermission");
            }

            plot.setName(args.get(0));
            getDatasource().savePlot(plot);

            res.sendMessage(getLocal().getLocalization("mytown.notification.plot.renamed"));
            return CommandResponse.DONE;
        }

        @Command(
                name = "criar",
                permission = "mytown.cmd.everyone.plot.new",
                parentName = "mytown.cmd.everyone.plot",
                syntax = "/cidade lote criar <lote>")
        public static CommandResponse plotNewCommand(ICommandSender sender, List<String> args) {
            if(args.size() < 1) {
                return CommandResponse.SEND_SYNTAX;
            }

            Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
            ToolManager.instance.register(new PlotSelectionTool(res, args.get(0)));
            return CommandResponse.DONE;
        }

        @Command(
                name = "selecionar",
                permission = "mytown.cmd.everyone.plot.select",
                parentName = "mytown.cmd.everyone.plot",
                syntax = "/cidade lote selecionar <comando>")
        public static CommandResponse plotSelectCommand(ICommandSender sender, List<String> args) {
            return CommandResponse.SEND_HELP_MESSAGE;
        }

        @Command(
                name = "redefinir",
                permission = "mytown.cmd.everyone.plot.select.reset",
                parentName = "mytown.cmd.everyone.plot.select",
                syntax = "/cidade lote selecionar redefinir")
        public static CommandResponse plotSelectResetCommand(ICommandSender sender, List<String> args) {
            Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
            Tool currentTool = ToolManager.instance.get(res.getPlayer());
            if(currentTool == null || !(currentTool instanceof PlotSelectionTool)) {
                throw new MyTownCommandException("mytown.cmd.err.plot.noPermission");
            }
            ((PlotSelectionTool) currentTool).resetSelection(true, 0);
            res.sendMessage(getLocal().getLocalization("mytown.notification.plot.selectionReset"));
            return CommandResponse.DONE;
        }

        @Command(
                name = "mostrar",
                permission = "mytown.cmd.everyone.plot.show",
                parentName = "mytown.cmd.everyone.plot",
                syntax = "/cidade lote mostrar")
        public static CommandResponse plotShowCommand(ICommandSender sender, List<String> args) {
            Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
            Town town = getTownFromResident(res);
            town.plotsContainer.show(res);
            ChatUtils.sendLocalizedChat(sender, getLocal(), "mytown.notification.plot.showing");
            return CommandResponse.DONE;
        }

        @Command(
                name = "ocultar",
                permission = "mytown.cmd.everyone.plot.hide",
                parentName = "mytown.cmd.everyone.plot",
                syntax = "/cidade lote ocultar")
        public static CommandResponse plotHideCommand(ICommandSender sender, List<String> args) {
            Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
            Town town = getTownFromResident(res);
            town.plotsContainer.hide(res);
            ChatUtils.sendLocalizedChat(sender, getLocal(), "mytown.notification.plot.vanished");
            return CommandResponse.DONE;
        }

        @Command(
                name = "adicionar",
                permission = "mytown.cmd.everyone.plot.add",
                parentName = "mytown.cmd.everyone.plot",
                syntax = "/cidade lote adicionar <comando>")
        public static CommandResponse plotAddCommand(ICommandSender sender, List<String> args) {
            return CommandResponse.SEND_HELP_MESSAGE;
        }

        @Command(
                name = "dono",
                permission = "mytown.cmd.everyone.plot.add.owner",
                parentName = "mytown.cmd.everyone.plot.add",
                syntax = "/cidade lote adicionar dono <habitante>",
                completionKeys = {"residentCompletion"})
        public static CommandResponse plotAddOwnerCommand(ICommandSender sender, List<String> args) {
            if (args.size() < 1) {
                return CommandResponse.SEND_SYNTAX;
            }

            Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
            Resident target = getResidentFromName(args.get(0));

            Town town = getTownFromResident(res);
            if (!target.townsContainer.contains(town)) {
                throw new MyTownCommandException("mytown.cmd.err.resident.notsametown", target.getPlayerName(), town.getName());
            }

            Plot plot = getPlotAtResident(res);

            if(!plot.ownersContainer.contains(res) && !town.hasPermission(res, "mytown.bypass.plot")) {
                throw new MyTownCommandException("mytown.cmd.err.plot.noPermission");
            }

            if(plot.ownersContainer.contains(target) || plot.membersContainer.contains(target)) {
                throw new MyTownCommandException("mytown.cmd.err.plot.add.alreadyInPlot");
            }

            if (!town.plotsContainer.canResidentMakePlot(target)) {
                throw new MyTownCommandException("mytown.cmd.err.plot.limit.toPlayer", target.getPlayerName());
            }

            getDatasource().linkResidentToPlot(target, plot, true);

            res.sendMessage(getLocal().getLocalization("mytown.notification.plot.owner.sender.added", target.getPlayerName(), plot.getName()));
            target.sendMessage(getLocal().getLocalization("mytown.notification.plot.owner.target.added", plot.getName()));
            return CommandResponse.DONE;
        }

        @Command(
                name = "membro",
                permission = "mytown.cmd.everyone.plot.add.member",
                parentName = "mytown.cmd.everyone.plot.add",
                syntax = "/cidade lote adicionar membro <habitante>",
                completionKeys = {"residentCompletion"})
        public static CommandResponse plotAddMemberCommand(ICommandSender sender, List<String> args) {
            if (args.size() < 1) {
                return CommandResponse.SEND_SYNTAX;
            }

            Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
            Resident target = getResidentFromName(args.get(0));
            Plot plot = getPlotAtResident(res);

            if(!plot.ownersContainer.contains(res) && !plot.getTown().hasPermission(res, "mytown.bypass.plot")) {
                throw new MyTownCommandException("mytown.cmd.err.plot.notOwner");
            }

            if(plot.ownersContainer.contains(target) || plot.membersContainer.contains(target)) {
                throw new MyTownCommandException("mytown.cmd.err.plot.add.alreadyInPlot");
            }

            getDatasource().linkResidentToPlot(target, plot, false);

            res.sendMessage(getLocal().getLocalization("mytown.notification.plot.member.sender.added", target.getPlayerName(), plot.getName()));
            target.sendMessage(getLocal().getLocalization("mytown.notification.plot.member.target.added", plot.getName()));
            return CommandResponse.DONE;
        }

        @Command(
                name = "remover",
                permission = "mytown.cmd.everyone.plot.remove",
                parentName = "mytown.cmd.everyone.plot",
                syntax = "/cidade lote remover <habitante>",
                completionKeys = {"residentCompletion"})
        public static CommandResponse plotRemoveCommand(ICommandSender sender, List<String> args) {
            if (args.size() < 1) {
                return CommandResponse.SEND_SYNTAX;
            }

            Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
            Resident target = getResidentFromName(args.get(0));
            Plot plot = getPlotAtResident(res);

            if(!plot.ownersContainer.contains(res) && !plot.getTown().hasPermission(res, "mytown.bypass.plot")) {
                throw new MyTownCommandException("mytown.cmd.err.plot.noPermission");
            }

            if(!plot.ownersContainer.contains(target) && !plot.membersContainer.contains(target)) {
                throw new MyTownCommandException("mytown.cmd.err.plot.remove.notInPlot");
            }

            if(plot.ownersContainer.contains(target) && plot.ownersContainer.size() == 1) {
                throw new MyTownCommandException("mytown.cmd.err.plot.remove.onlyOwner");
            }

            getDatasource().unlinkResidentFromPlot(target, plot);

            res.sendMessage(getLocal().getLocalization("mytown.notification.plot.sender.removed", target.getPlayerName(), plot.getName()));
            target.sendMessage(getLocal().getLocalization("mytown.notification.plot.target.removed", plot.getName()));
            return CommandResponse.DONE;

        }

        @Command(
                name = "info",
                permission = "mytown.cmd.everyone.plot.info",
                parentName = "mytown.cmd.everyone.plot",
                syntax = "/cidade lote info")
        public static CommandResponse plotInfoCommand(ICommandSender sender, List<String> args) {
            Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
            Plot plot = getPlotAtResident(res);
            res.sendMessage(getLocal().getLocalization("mytown.notification.plot.info", plot.getName(), plot.ownersContainer.toString(), plot.membersContainer.toString(), plot.getStartX(), plot.getStartY(), plot.getStartZ(), plot.getEndX(), plot.getEndY(), plot.getEndZ()));
            return CommandResponse.DONE;
        }

        @Command(
                name = "apagar",
                permission = "mytown.cmd.everyone.plot.delete",
                parentName = "mytown.cmd.everyone.plot",
                syntax = "/cidade lote apagar")
        public static CommandResponse plotDeleteCommand(ICommandSender sender, List<String> args) {
            Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
            Plot plot = getPlotAtResident(res);
            if (!plot.ownersContainer.contains(res) && !plot.getTown().hasPermission(res, "mytown.bypass.plot")) {
                throw new MyTownCommandException("mytown.cmd.err.plot.noPermission");
            }

            World world;
            if(sender instanceof EntityPlayer)
                world = ((EntityPlayer) sender).worldObj;
            else
                world = MinecraftServer.getServer().worldServerForDimension(plot.getDim());

            plot.deleteSignBlocks(SellSign.SellSignType.instance, world);

            getDatasource().deletePlot(plot);
            res.sendMessage(getLocal().getLocalization("mytown.notification.plot.deleted", plot.getName()));
            return CommandResponse.DONE;
        }

        @Command(
                name = "vender",
                permission = "mytown.cmd.everyone.plot.sell",
                parentName = "mytown.cmd.everyone.plot",
                syntax = "/cidade lote vender <preco>")
        public static CommandResponse plotSellCommand(ICommandSender sender, List<String> args) {
            if(args.size() < 1) {
                return CommandResponse.SEND_SYNTAX;
            }

            Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
            Town town = getTownFromResident(res);

            if(!StringUtils.tryParseInt(args.get(0)) || Integer.parseInt(args.get(0)) < 0) {
                throw new MyTownCommandException("mytown.cmd.err.notPositiveInteger", args.get(0));
            }

            int price = Integer.parseInt(args.get(0));
            ToolManager.instance.register(new PlotSellTool(res, price));
            return CommandResponse.DONE;
        }
    }

    @Command(
            name = "postos",
            permission = "mytown.cmd.everyone.ranks",
            parentName = "mytown.cmd",
            syntax = "/cidade postos <comando>")
    public static CommandResponse ranksCommand(ICommandSender sender, List<String> args) {
        return CommandResponse.SEND_HELP_MESSAGE;
    }

    @Command(
            name = "listar",
            permission = "mytown.cmd.everyone.ranks.list",
            parentName = "mytown.cmd.everyone.ranks",
            syntax = "/cidade postos listar")
    public static CommandResponse listRanksCommand(ICommandSender sender, List<String> args) {
        Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
        Town town = getTownFromResident(res);

        ChatUtils.sendLocalizedChat(sender, getLocal(), "mytown.notification.town.ranks", town.ranksContainer.toString());
        return CommandResponse.DONE;
    }

    @Command(
            name = "bordas",
            permission = "mytown.cmd.everyone.borders",
            parentName = "mytown.cmd",
            syntax = "/cidade bordas <comando>")
    public static CommandResponse bordersCommand(ICommandSender sender, List<String> args) {
        return CommandResponse.SEND_HELP_MESSAGE;
    }

    @Command(
            name = "mostrar",
            permission = "mytown.cmd.everyone.borders.show",
            parentName = "mytown.cmd.everyone.borders",
            syntax = "/cidade bordas mostrar")
    public static CommandResponse bordersShowCommand(ICommandSender sender, List<String> args) {
        Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
        Town town = getTownFromResident(res);

        town.townBlocksContainer.show(res);
        res.sendMessage(getLocal().getLocalization("mytown.notification.town.borders.show", town.getName()));
        return CommandResponse.DONE;
    }

    @Command(
            name = "ocultar",
            permission = "mytown.cmd.everyone.borders.hide",
            parentName = "mytown.cmd.everyone.borders",
            syntax = "/cidade bordas ocultar")
    public static CommandResponse bordersHideCommand(ICommandSender sender, List<String> args) {
        Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
        Town town = getTownFromResident(res);

        town.townBlocksContainer.hide(res);
        res.sendMessage(getLocal().getLocalization("mytown.notification.town.borders.hide"));
        return CommandResponse.DONE;
    }

    @Command(
            name = "banco",
            permission = "mytown.cmd.everyone.bank",
            parentName = "mytown.cmd",
            syntax = "/cidade banco <comando>")
    public static CommandResponse bankCommand(ICommandSender sender, List<String> args) {
        return CommandResponse.SEND_HELP_MESSAGE;
    }

    @Command(
            name = "info",
            permission = "mytown.cmd.everyone.bank.info",
            parentName = "mytown.cmd.everyone.bank",
            syntax = "/cidade banco info")
    public static CommandResponse bankAmountCommand(ICommandSender sender, List<String> args) {
        Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
        Town town = getTownFromResident(res);

        if(town instanceof AdminTown)
            throw new MyTownCommandException("mytown.cmd.err.adminTown", town.getName());

        res.sendMessage(getLocal().getLocalization("mytown.notification.town.bank.info", EconomyProxy.getCurrency(town.bank.getAmount()), EconomyProxy.getCurrency(town.bank.getNextPaymentAmount())));
        return CommandResponse.DONE;
    }

    @Command(
            name = "depositar",
            permission = "mytown.cmd.everyone.bank.deposit",
            parentName = "mytown.cmd.everyone.bank",
            syntax = "/cidade banco depositar <quantia>")
    public static CommandResponse bankPayCommand(ICommandSender sender, List<String> args) {
        if(args.size() < 1)
            return CommandResponse.SEND_SYNTAX;

        if(!StringUtils.tryParseInt(args.get(0)))
            throw new MyTownCommandException("mytown.cmd.err.notPositiveInteger", args.get(0));

        Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
        Town town = getTownFromResident(res);

        if(town instanceof AdminTown)
            throw new MyTownCommandException("mytown.cmd.err.adminTown", town.getName());

        int amount = Integer.parseInt(args.get(0));
        makePayment(res.getPlayer(), amount);
        town.bank.addAmount(amount);
        getDatasource().saveTownBank(town.bank);
        return CommandResponse.DONE;
    }

    @Command(
            name = "natureza",
            permission = "mytown.cmd.everyone.wild",
            parentName = "mytown.cmd",
            syntax = "/cidade natureza <comando>")
    public static CommandResponse permWildCommand(ICommandSender sender, List<String> args) {
        return CommandResponse.SEND_HELP_MESSAGE;
    }

    @Command(
            name = "perm",
            permission = "mytown.cmd.everyone.wild.perm",
            parentName = "mytown.cmd.everyone.wild",
            syntax = "/cidade natureza perm")
    public static CommandResponse permWildListCommand(ICommandSender sender, List<String> args) {
        Resident res = MyTownUniverse.instance.getOrMakeResident(sender);
        res.sendMessage(Wild.instance.flagsContainer.toStringForWild());
        return CommandResponse.DONE;
    }
}