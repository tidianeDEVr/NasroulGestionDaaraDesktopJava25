package com.nasroul.util;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;

public class ImageUtil {
    private static final int AVATAR_SIZE = 300;

    public static byte[] resizeImage(File imageFile) throws IOException {
        BufferedImage originalImage = ImageIO.read(imageFile);
        return resizeImage(originalImage);
    }

    public static byte[] resizeImage(BufferedImage originalImage) throws IOException {
        Image fxImage = SwingFXUtils.toFXImage(originalImage, null);
        double width = fxImage.getWidth();
        double height = fxImage.getHeight();

        double scaleFactor = Math.min(AVATAR_SIZE / width, AVATAR_SIZE / height);
        int newWidth = (int) (width * scaleFactor);
        int newHeight = (int) (height * scaleFactor);

        BufferedImage resizedImage = new BufferedImage(AVATAR_SIZE, AVATAR_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, AVATAR_SIZE, AVATAR_SIZE);

        int x = (AVATAR_SIZE - newWidth) / 2;
        int y = (AVATAR_SIZE - newHeight) / 2;

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.drawImage(originalImage, x, y, newWidth, newHeight, null);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(resizedImage, "jpg", baos);
        return baos.toByteArray();
    }

    public static Image bytesToImage(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
        return new Image(bais);
    }
}
