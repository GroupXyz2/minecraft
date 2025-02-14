package de.groupxyz.whitelistchecker;


import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod("whitelistmod")
public class Whitelistchecker {
    private static final Logger LOGGER = LogUtils.getLogger();

    public Whitelistchecker() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onCommandsRegister(RegisterCommandsEvent event) {
        BetterWhitelistCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("WhitelistChecker by GroupXyz started!");
    }
}
