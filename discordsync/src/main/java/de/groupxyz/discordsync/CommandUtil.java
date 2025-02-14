package de.groupxyz.discordsync;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class CommandUtil {
    public static String dispatchCommand(CommandSender sender, String command) {
        StringBuilder output = new StringBuilder();
        ConsoleOutputHandler handler = new ConsoleOutputHandler(output);
        Bukkit.getServer().getLogger().addHandler(handler);

        Bukkit.dispatchCommand(sender, command);

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Bukkit.getServer().getLogger().removeHandler(handler);

        Bukkit.getServer().getLogger().info(output.toString());

        return output.toString();
    }

    private static class ConsoleOutputHandler extends Handler {
        private final StringBuilder output;

        public ConsoleOutputHandler(StringBuilder output) {
            this.output = output;
        }

        @Override
        public void publish(LogRecord record) {
            output.append(record.getMessage()).append(System.lineSeparator());
        }

        @Override
        public void flush() {}

        @Override
        public void close() throws SecurityException {}
    }
}



