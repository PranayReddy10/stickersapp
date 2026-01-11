package com.stickersanimated.kissing.utils;

import com.stickersanimated.kissing.config.Config;

public class UrlUtils {


    public static String resolveImageUrl(String imagePath) {

        if (imagePath == null) return "";

        imagePath = imagePath.trim();

        // FULL URL → return as-is
        if (imagePath.startsWith("http")) {
            return imagePath;
        }

        // Build base URL from API
        String baseUrl = Config.API_URL.replaceAll("/api/?$", "");

        if (!imagePath.startsWith("/")) {
            imagePath = "/" + imagePath;
        }

        return baseUrl + imagePath;
    }


}
