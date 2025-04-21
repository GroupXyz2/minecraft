package de.groupxyz.cscraft2;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoundEconomyManager {
    private final GameConfig config;
    private final Map<String, Integer> playerMoney = new HashMap<>();
    private final int MAX_MONEY = 16000;

    public RoundEconomyManager(GameConfig config) {
        this.config = config;
    }

    public void resetPlayerMoney(Player player) {
        playerMoney.put(player.getName(), config.getMoneyPerRound());
    }

    public void resetAllPlayerMoney(List<Player> players) {
        for (Player player : players) {
            resetPlayerMoney(player);
        }
    }

    public void addMoneyForKill(Player killer) {
        addMoney(killer, config.getMoneyPerKill());
        killer.sendMessage(ChatColor.GREEN + "+" + ChatColor.GOLD + "$" + config.getMoneyPerKill() +
                ChatColor.GREEN + " für einen Kill");
    }

    public void addMoneyForBombAction(Player player, String action) {
        if (config.isRoundBased()) {
            int amount = 0;
            if (action.equals("plant")) {
                amount = config.getMoneyPerPlant();
                player.sendMessage(ChatColor.GREEN + "+" + ChatColor.GOLD + "$" + amount +
                        ChatColor.GREEN + " für das Platzieren der Bombe");
            } else if (action.equals("defuse")) {
                amount = config.getMoneyPerDefuse();
                player.sendMessage(ChatColor.GREEN + "+" + ChatColor.GOLD + "$" + amount +
                        ChatColor.GREEN + " für das Entschärfen der Bombe");
            }

            if (amount > 0) {
                addMoney(player, amount);
            }
        }
    }

    public void distributeRoundEndMoney(List<Player> winners, List<Player> losers) {
        for (Player player : winners) {
            addMoney(player, config.getWinnerBonus());
            player.sendMessage(ChatColor.GREEN + "+" + ChatColor.GOLD + "$" + config.getWinnerBonus() +
                    ChatColor.GREEN + " Siegerbonus");
        }

        for (Player player : losers) {
            addMoney(player, config.getLoserBonus());
            player.sendMessage(ChatColor.GREEN + "+" + ChatColor.GOLD + "$" + config.getLoserBonus() +
                    ChatColor.GREEN + " Verliererbonus");
        }
    }

    public void distributeStartRoundMoney(List<Player> players) {
        for (Player player : players) {
            if (!playerMoney.containsKey(player.getName())) {
                resetPlayerMoney(player);
            } else {
                addMoney(player, config.getMoneyPerRound());
                player.sendMessage(ChatColor.GREEN + "+" + ChatColor.GOLD + "$" + config.getMoneyPerRound() +
                        ChatColor.GREEN + " für die neue Runde");
            }
        }
    }

    public int getPlayerMoney(Player player) {
        return playerMoney.getOrDefault(player.getName(), config.getMoneyPerRound());
    }

    public void setPlayerMoney(Player player, int amount) {
        playerMoney.put(player.getName(), Math.min(amount, MAX_MONEY));
    }

    public void spendMoney(Player player, int amount) {
        int currentMoney = getPlayerMoney(player);
        if (currentMoney >= amount) {
            playerMoney.put(player.getName(), currentMoney - amount);
        }
    }

    private void addMoney(Player player, int amount) {
        int currentMoney = getPlayerMoney(player);
        playerMoney.put(player.getName(), Math.min(currentMoney + amount, MAX_MONEY));
    }

    public void clear() {
        playerMoney.clear();
    }
}