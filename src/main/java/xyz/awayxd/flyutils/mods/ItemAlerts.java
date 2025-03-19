package xyz.awayxd.flyutils.mods;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.util.ChatComponentText;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ItemAlerts {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private String biggestThreat = "";

    private final boolean enableItemAlerts = true;
    private boolean enableThreatDetection = true;

    private final Map<String, Integer> threatMap = new HashMap<>();

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        String message = getHypixelMessage(event.message);

        if (message == null) {
            return;
        }

        if (message.contains("The game starts in")) {
            showGameStartOverlay();
            if (enableThreatDetection) fetchAllPlayerStats();
        }

        if (enableItemAlerts && message.contains("bought") && !isTeammate(message)) {
            String playerName = extractPlayerName(message);

            if (message.contains("Obsidian") || message.contains("Diamond Armor") || message.contains("Chain Armor") || message.contains("Diamond Sword")) {
                mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("note.pling"), 1.0F));
                mc.thePlayer.addChatMessage(new ChatComponentText("§cEnemy " + playerName + " §fbought: " + extractItemName(message)));
            }
        }
    }

    private void showGameStartOverlay() {
        if (!biggestThreat.isEmpty()) {
            mc.thePlayer.addChatMessage(new ChatComponentText("Biggest Threat: " + biggestThreat));
        }
        mc.thePlayer.sendChatMessage("/who");
    }

    private void fetchAllPlayerStats() {
        new Thread(() -> {
            int highestScore = -1;
            String topThreat = "";

            for (String playerName : mc.getNetHandler().getPlayerInfoMap().stream()
                    .map(p -> p.getGameProfile().getName())
                    .collect(Collectors.toList())) {
                try {
                    URL url = new URL("https://api.polsu.xyz/v2/stats/" + playerName);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");

                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    String stats = parseStats(response.toString());
                    mc.thePlayer.addChatMessage(new ChatComponentText("§e" + playerName + "'s Stats: " + stats));

                    int score = calculateThreatScore(response.toString());
                    threatMap.put(playerName, score);

                    if (score > highestScore) {
                        highestScore = score;
                        topThreat = getThreatLevelColor(score) + playerName;
                    }

                } catch (Exception e) {
                    mc.thePlayer.addChatMessage(new ChatComponentText("§cFailed to load stats for " + playerName));
                }
            }

            biggestThreat = topThreat;
        }).start();
    }

    private String getThreatLevelColor(int score) {
        if (score > 50) {
            return "§c";
        } else if (score >= 20) {
            return "§e";
        } else {
            return "§a";
        }
    }

    private String parseStats(String json) {
        if (json.contains("final_kills")) {
            int finalKills = Integer.parseInt(json.split("\"final_kills\":")[1].split(",")[0]);
            int bedsBroken = Integer.parseInt(json.split("\"beds_broken\":")[1].split(",")[0]);
            int wins = Integer.parseInt(json.split("\"wins\":")[1].split(",")[0]);
            return "Final Kills: " + finalKills + " | Beds Broken: " + bedsBroken + " | Wins: " + wins;
        }
        return "No stats available";
    }

    private int calculateThreatScore(String json) {
        if (json.contains("final_kills")) {
            int finalKills = Integer.parseInt(json.split("\"final_kills\":")[1].split(",")[0]);
            int bedsBroken = Integer.parseInt(json.split("\"beds_broken\":")[1].split(",")[0]);
            int wins = Integer.parseInt(json.split("\"wins\":")[1].split(",")[0]);
            return finalKills * 3 + bedsBroken * 2 + wins;
        }
        return 0;
    }

    private boolean isTeammate(String message) {
        return message.contains(mc.thePlayer.getDisplayNameString());
    }

    private String extractPlayerName(String message) {
        String[] parts = message.split(" ");
        if (parts.length > 2) {
            return parts[1];
        }
        return "Unknown";
    }

    private String extractItemName(String message) {
        if (message.contains("Obsidian")) return "Obsidian";
        if (message.contains("Diamond Armor")) return "Diamond Armor";
        if (message.contains("Chain Armor")) return "Chain Armor";
        if (message.contains("Diamond Sword")) return "Diamond Sword";
        return "Unknown";
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        final int[] yOffset = {10};
        threatMap.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(3)
                .forEach(entry -> {
                    String color = getThreatLevelColor(entry.getValue());
                    String displayText = color + entry.getKey() + ": " + entry.getValue();
                    mc.fontRendererObj.drawStringWithShadow(displayText, 10, yOffset[0], 0xFFFFFF);
                    yOffset[0] += 10;
                });
    }

    public class ConfigGui extends GuiScreen {
        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            drawCenteredString(fontRendererObj, "Item Alerts Settings", width / 2, height / 4, 0xFFFFFF);
            drawCenteredString(fontRendererObj, "Enable Item Alerts: " + enableItemAlerts, width / 2, height / 4 + 30, 0xFFFFFF);
            drawCenteredString(fontRendererObj, "Enable Threat Detection: " + enableThreatDetection, width / 2, height / 4 + 50, 0xFFFFFF);
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        protected void keyTyped(char typedChar, int keyCode) {
            if (keyCode == 1) {
                mc.displayGuiScreen(null);
            }
        }
    }

    public static String getHypixelMessage(IChatComponent chatComponent) {
        if (chatComponent == null) {
            return null;
        }
        String cleanMessage = chatComponent.getUnformattedText().replaceAll("§.", "");
        return cleanMessage;
    }
}
