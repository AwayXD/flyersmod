package xyz.awayxd.flyutils.utils;

import net.minecraft.util.IChatComponent;

public class ChatUtils {

    public static String getHypixelMessage(IChatComponent chatComponent) {
        if (chatComponent == null) {
            return null;
        }
        String cleanMessage = chatComponent.getUnformattedText().replaceAll("§.", "");
        return cleanMessage;
    }
}
