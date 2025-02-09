package org.kiko.dev.gold_graph;

import org.kiko.dev.dtos.CompletedGameInfoParticipant;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.List;


public class GraphGenerator {

    public static BufferedImage generateDmgGraph(List<CompletedGameInfoParticipant> participantList) {
        int width = 800;
        int height = 600;
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, width, height);
        // Enable anti-aliasing for smoother lines
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw axes in white (opaque)
        g2d.setColor(Color.WHITE);
        g2d.drawLine(100, 50, 100, 550);   // y-axis
        g2d.drawLine(100, 550, 700, 550);    // x-axis



        int waterMarkX = (width / 2) - 50; // Adjust for text width
        int waterMarkY =40;             // A bit below the axis
        String waterMarkText =  "Generated by YamatoCannon bot";
        g2d.drawString(waterMarkText, waterMarkX, waterMarkY);

        int maxDmg = 0;
        for (CompletedGameInfoParticipant c : participantList) {
            if (c.getTotalDmgDealtToChampions() > maxDmg) {
                maxDmg = c.getTotalDmgDealtToChampions();
            }
        }
        // increase the totalDmg till is divisble by 1000
        while((maxDmg%1000) != 0){
            maxDmg++;
        }

        // Draw Axis Markers (Grid Lines and Labels)
        Composite originalComposite = g2d.getComposite();
        // Increased opacity to 40%
        AlphaComposite translucent = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f);
        g2d.setComposite(translucent);

        int numOfDmgMarkers = 5;

        // Draw x-axis markers (time markers)
        for (int i = 0; i <= numOfDmgMarkers; i++) {
            int x = 100 + (i * (600 / numOfDmgMarkers));
            g2d.drawLine(x, 50, x, 555);
            g2d.drawString(String.valueOf(i * (maxDmg / numOfDmgMarkers)), x - 15, 570);
        }

        // Restore original composite for the rest of the drawing
        g2d.setComposite(originalComposite);

        int iconSize = 35;
        // Calculate spacing with extra gap between teams
        int teamSize = participantList.size() / 2;
        int regularSpacing = (height - 150) / participantList.size();
        int yOffset = 70;

        for (int i = 0; i < participantList.size(); i++) {
            CompletedGameInfoParticipant participant = participantList.get(i);
            try {
                BufferedImage championIcon = getChampionIconFromURL(participant.getChampion());

                // when first 5 players are drawn(blue team), the gap is increased to
                // make more visual the distinction between the two teams
                int extraGap = (i >= teamSize) ? 30 : 0;
                int y = yOffset + (i * regularSpacing) + extraGap;

                // Draw the champion icon with full opacity
                g2d.drawImage(championIcon, 50, y, iconSize, iconSize, null);

                int dmgBarWidth = (int) ((participant.getTotalDmgDealtToChampions() / (double) maxDmg) * 600);

                // Set color based on team (first half blue, second half red)
                if (i < teamSize) {
                    g2d.setColor(new Color(30, 144, 255)); // Dodger Blue
                } else {
                    g2d.setColor(new Color(220, 20, 60));  // Crimson Red
                }

                g2d.fillRect(100, y + (iconSize/4), dmgBarWidth, iconSize/2);

                // Draw damage value in white
                g2d.setColor(Color.WHITE);
                g2d.drawString(String.valueOf(participant.getTotalDmgDealtToChampions()),
                        dmgBarWidth + 110, y + (iconSize/2));

            } catch (IOException e) {
                System.err.println("Could not load champion icon for: " + participant.getChampion());
                e.printStackTrace();
            }
        }

        try {
            File file = new File("DMG_GRAPH.png");
            ImageIO.write(bufferedImage, "png", file);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return bufferedImage;
    }

    public static BufferedImage generateGoldGraph(int[] blueTeam, int[] redTeam) {

        int width = 800;
        int height = 600;
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = bufferedImage.createGraphics();

        // Fill the background with black
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, width, height);

        // Enable anti-aliasing for smoother lines
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw axes in white (opaque)
        g2d.setColor(Color.WHITE);
        g2d.drawLine(100, 50, 100, 550);   // y-axis
        g2d.drawLine(100, 550, 700, 550);    // x-axis


        int waterMarkX = (width / 2) - 50; // Adjust for text width
        int waterMarkY =40;             // A bit below the axis
        String waterMarkText =  "Generated by YamatoCannon bot";
        g2d.drawString(waterMarkText, waterMarkX, waterMarkY);


        // --- Add axis labels ---
        // "Time" label (horizontal, centered below the x-axis)
        String timeLabel = "Time(Minutes)";
        // We'll place this slightly below the x-axis.
        // The x position is roughly the midpoint (width / 2), but we shift to center it better.
        int timeLabelX = (width / 2) - 20; // Adjust for text width
        int timeLabelY = 590;             // A bit below the axis
        g2d.drawString(timeLabel, timeLabelX, timeLabelY);

        // "Gold" label (vertical, along the y-axis)
        // We create a copy of Graphics2D so we can rotate without affecting other drawings.
        String goldLabel = "Gold";
        Graphics2D g2dGold = (Graphics2D) g2d.create();
        // Move the origin to a point near the y-axis, then rotate -90 degrees clockwise
        g2dGold.translate(30, height / 2);
        g2dGold.rotate(-Math.PI / 2);
        // After rotation, drawing at (0,0) means that "Gold" is written vertically centered
        // with respect to the original coordinate system
        g2dGold.drawString(goldLabel, 0, 0);
        // Dispose this temporary graphics context
        g2dGold.dispose();
        // --- End axis labels ---


        // Calculate the maximum gold quantity (and round up to a multiple of 5000)
        int blueTeamMaxGold = blueTeam[blueTeam.length - 1];
        int redTeamMaxGold = redTeam[redTeam.length - 1];
        int maxGoldQty = Math.max(blueTeamMaxGold, redTeamMaxGold);
        while (maxGoldQty % 5000 != 0) {
            maxGoldQty++;
        }
        int time = blueTeam.length;

        // Calculate scaling factors
        int pixelsPerMinute = 600 / time;
        int goldPerPixel = maxGoldQty / 500;

        // --- Draw Axis Markers (Grid Lines and Labels) ---
        // Save the original composite to restore later
        Composite originalComposite = g2d.getComposite();
        // Set a composite with reduced opacity (e.g., 30% opacity)
        AlphaComposite translucent = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f);
        g2d.setComposite(translucent);

        // Draw x-axis markers (time markers)
        for (int i = 0; i < time; i = i + 5) {
            int x = 100 + (i * pixelsPerMinute);
            // Draw a small vertical tick on the x-axis
            g2d.drawLine(x, 50, x, 555);
            // Label the tick mark
            g2d.drawString(String.valueOf(i), x - 5, 570);
        }

        // Draw y-axis markers (gold markers)
        int numGoldMarkers = 10; // Number of markers along the y-axis
        for (int i = 0; i <= numGoldMarkers; i++) {
            // Calculate y position for each marker (500 pixels available for the graph area)
            int y = 550 - (i * (500 / numGoldMarkers));
            // Draw a horizontal grid line
            g2d.drawLine(95, y, 700, y);
            // Calculate the gold value for this marker
            int goldValue = i * (maxGoldQty / numGoldMarkers);
            // Draw the gold label to the left of the y-axis tick mark
            g2d.drawString(String.valueOf(goldValue), 50, y + 5);
        }

        // Restore the composite to fully opaque for drawing the main graph lines
        g2d.setComposite(originalComposite);

        // --- Draw the Graph Lines (These will appear over the grid markers) ---
        int previousX = 100;
        int previousY = 550;

        // Draw blue team's graph line
        g2d.setColor(Color.BLUE);
        for (int i = 0; i < blueTeam.length; i++) {
            int currentX = 100 + (i * pixelsPerMinute);
            int currentY = 550 - (blueTeam[i] / goldPerPixel);
            g2d.drawLine(previousX, previousY, currentX, currentY);
            previousX = currentX;
            previousY = currentY;
        }

        // Draw red team's graph line
        previousX = 100;
        previousY = 550;
        g2d.setColor(Color.RED);
        for (int i = 0; i < redTeam.length; i++) {
            int currentX = 100 + (i * pixelsPerMinute);
            int currentY = 550 - (redTeam[i] / goldPerPixel);
            g2d.drawLine(previousX, previousY, currentX, currentY);
            previousX = currentX;
            previousY = currentY;
        }

//        try {
//            File file = new File("GOLD_GRAPH.png");
//            ImageIO.write(bufferedImage, "png", file);
//
//            file = new File("GOLD_GRAPH.jpg");
//            ImageIO.write(bufferedImage, "jpg", file);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        g2d.dispose();
        return bufferedImage;
    }


    private static BufferedImage getChampionIconFromURL(String championName) throws IOException {
        String urlString = "https://ddragon.leagueoflegends.com/cdn/15.2.1/img/champion/" + championName + ".png";
        URL url = new URL(urlString);

        // Open connection with timeout settings
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        try (InputStream in = connection.getInputStream()) {
            return ImageIO.read(in);
        } catch (IOException e) {
            throw new IOException("Failed to load champion icon for " + championName + ": " + e.getMessage(), e);
        }
    }
}
