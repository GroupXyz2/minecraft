package de.groupxyz.whitelistchecker;

import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.server.players.UserWhiteListEntry;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;

public class BetterWhitelistCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("whitelistchecker")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.literal("add")
                                .then(Commands.argument("username", StringArgumentType.word())
                                        .executes(context -> addPlayer(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "username")
                                        ))
                                )
                        )
        );
    }

    private static int addPlayer(CommandSourceStack source, String username) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            if (connection.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream())
                );
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JsonObject jsonResponse = new Gson().fromJson(response.toString(), JsonObject.class);
                String id = jsonResponse.get("id").getAsString();
                String name = jsonResponse.get("name").getAsString();

                String formattedUUID = id.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                        "$1-$2-$3-$4-$5"
                );
                UUID uuid = UUID.fromString(formattedUUID);

                UserWhiteList whitelist = source.getServer().getPlayerList().getWhiteList();
                GameProfile profile = new GameProfile(uuid, name);
                UserWhiteListEntry whitelistEntry = new UserWhiteListEntry(profile);

                whitelist.add(whitelistEntry);

                source.sendSuccess(
                        new TextComponent("Added " + name + " to the whitelist by WhitelistChecker"),
                        true
                );
                return 1;
            } else {
                source.sendFailure(
                        new TextComponent("Player " + username + " not found")
                );
                return 0;
            }

        } catch (Exception e) {
            source.sendFailure(
                    new TextComponent("Error while adding player: " + e.getMessage())
            );
            return 0;
        }
    }
}