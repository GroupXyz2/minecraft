package de.groupxyz.cscraft2;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.List;

public class TeamManager {
    private final Scoreboard scoreboard;
    private final Team ctTeam;
    private final Team tTeam;

    public TeamManager(Cscraft2 plugin) {
        ScoreboardManager scoreboardManager = plugin.getServer().getScoreboardManager();
        this.scoreboard = scoreboardManager.getNewScoreboard();

        this.ctTeam = scoreboard.registerNewTeam("CT");
        ctTeam.setColor(ChatColor.BLUE);
        ctTeam.setPrefix(ChatColor.BLUE + "[CT] ");
        ctTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OWN_TEAM);

        this.tTeam = scoreboard.registerNewTeam("T");
        tTeam.setColor(ChatColor.RED);
        tTeam.setPrefix(ChatColor.RED + "[T] ");
        tTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OWN_TEAM);
    }

    public void assignToTeam(Player player, String team) {
        if (team.equals("CT")) {
            ctTeam.addEntry(player.getName());
        } else if (team.equals("T")) {
            tTeam.addEntry(player.getName());
        }
        player.setScoreboard(scoreboard);
    }

    public void assignTeams(List<Player> ctPlayers, List<Player> tPlayers) {
        for (Player player : ctPlayers) {
            assignToTeam(player, "CT");
        }

        for (Player player : tPlayers) {
            assignToTeam(player, "T");
        }
    }

    public void resetTeams() {
        ctTeam.getEntries().forEach(ctTeam::removeEntry);
        tTeam.getEntries().forEach(tTeam::removeEntry);
    }

    public String getPlayerTeam(Player player) {
        if (ctTeam.hasEntry(player.getName())) {
            return "CT";
        } else if (tTeam.hasEntry(player.getName())) {
            return "T";
        }
        return null;
    }
}