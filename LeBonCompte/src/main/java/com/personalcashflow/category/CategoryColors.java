package com.personalcashflow.category;

/**
 * Assigns a new category a hue spaced out from every other by the golden angle, so consecutive
 * categories always look visually distinct instead of clustering near the same hue. Same formula
 * used to backfill existing rows in V19__add_category_color.sql.
 */
final class CategoryColors {

    private static final double GOLDEN_ANGLE = 137.508;
    private static final double SATURATION = 65;
    private static final double LIGHTNESS = 55;

    private CategoryColors() {
    }

    static String next(long existingCount) {
        double hue = (existingCount * GOLDEN_ANGLE) % 360;
        return hslToHex(hue, SATURATION, LIGHTNESS);
    }

    private static String hslToHex(double h, double s, double l) {
        s /= 100;
        l /= 100;
        double c = (1 - Math.abs(2 * l - 1)) * s;
        double x = c * (1 - Math.abs((h / 60) % 2 - 1));
        double m = l - c / 2;
        double r, g, b;
        if (h < 60) {
            r = c; g = x; b = 0;
        } else if (h < 120) {
            r = x; g = c; b = 0;
        } else if (h < 180) {
            r = 0; g = c; b = x;
        } else if (h < 240) {
            r = 0; g = x; b = c;
        } else if (h < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }
        return String.format("#%02x%02x%02x", toByte(r + m), toByte(g + m), toByte(b + m));
    }

    private static int toByte(double channel) {
        return (int) Math.round(channel * 255);
    }
}
