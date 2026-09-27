package com.ceramic.platform.controller;

import com.ceramic.platform.entity.Product;
import com.ceramic.platform.service.ProductService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

@RestController
@RequestMapping("/images/products")
public class ImageController {

    private final ProductService productService;

    public ImageController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/{filename}")
    public byte[] getProductImage(@PathVariable String filename) throws IOException {
        Long productId = null;
        try {
            String idStr = filename.replace(".jpg", "").replace(".png", "");
            productId = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
        }

        String productName = "陶瓷产品";
        String price = "";
        Color bgColor = new Color(245, 240, 232);
        Color accentColor = new Color(138, 184, 160);

        if (productId != null) {
            try {
                Product product = productService.findById(productId);
                if (product != null) {
                    productName = product.getName();
                    price = "¥" + product.getPrice();
                    
                    if (product.getGlazeColor() != null) {
                        String glaze = product.getGlazeColor();
                        if (glaze.contains("青") || glaze.contains("蓝")) {
                            accentColor = new Color(138, 184, 160);
                        } else if (glaze.contains("白") || glaze.contains("月")) {
                            accentColor = new Color(200, 200, 200);
                        } else if (glaze.contains("红") || glaze.contains("窑")) {
                            accentColor = new Color(200, 100, 100);
                        } else if (glaze.contains("黄") || glaze.contains("金")) {
                            accentColor = new Color(200, 180, 100);
                        }
                    }
                }
            } catch (Exception e) {
            }
        }

        int width = 400;
        int height = 400;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        g.setColor(bgColor);
        g.fillRect(0, 0, width, height);

        g.setColor(accentColor);
        g.fillRoundRect(50, 80, 300, 220, 20, 20);

        g.setColor(new Color(255, 255, 255));
        g.fillOval(120, 100, 160, 160);

        g.setColor(accentColor);
        g.setFont(new Font("SimHei", Font.BOLD, 24));
        g.drawString(productName, 200 - g.getFontMetrics().stringWidth(productName) / 2, 330);

        if (!price.isEmpty()) {
            g.setColor(new Color(231, 76, 60));
            g.setFont(new Font("SimHei", Font.BOLD, 20));
            g.drawString(price, 200 - g.getFontMetrics().stringWidth(price) / 2, 360);
        }

        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }
}