// SPDX-License-Identifier: MIT
package glanceplus;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Runs without a device: java -ea -cp ... glanceplus.GlanceFormatTest */
public final class GlanceFormatTest {
    private static void eq(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
    }

    public static void main(String[] args) {
        Map<String, Object> celsius = new HashMap<>();
        celsius.put("unit_of_measurement", "°C");
        Map<String, Object> watts = new HashMap<>();
        watts.put("unit_of_measurement", "W");
        eq("21.9°C", GlanceFormat.value("21.9", celsius));
        eq("1500 W", GlanceFormat.value("1500", watts));
        eq("Partly cloudy", GlanceFormat.value("partly_cloudy", Collections.<String, Object>emptyMap()));
        eq("Unavailable", GlanceFormat.value("unavailable", null));
        eq("KLM · FRA → AMS", GlanceFormat.value("KLM · FRA → AMS", null));
        eq("", GlanceFormat.value("  ", null));
        eq(true, GlanceFormat.blank(""));
        eq(false, GlanceFormat.blank("off"));
        eq(false, GlanceFormat.isNumber("12abc"));
        eq(false, GlanceFormat.isNumber("NaN"));
        eq(true, GlanceFormat.isNumber("-3.5"));

        Map<String, Object> friendly = new HashMap<>();
        friendly.put("friendly_name", "Nearest Plane");
        eq("Nearest Plane", GlanceFormat.name("sensor.nearest_plane", friendly, ""));
        eq("Override", GlanceFormat.name("sensor.nearest_plane", friendly, " Override "));
        eq("Nearest plane", GlanceFormat.name("sensor.nearest_plane", null, null));

        eq("mdi:custom", GlanceFormat.icon("sensor.x", null, "mdi:custom"));
        Map<String, Object> own = new HashMap<>();
        own.put("icon", "mdi:airplane");
        eq("mdi:airplane", GlanceFormat.icon("sensor.x", own, ""));
        eq("mdi:lightbulb", GlanceFormat.icon("light.kitchen", null, ""));
        Map<String, Object> temperature = new HashMap<>();
        temperature.put("device_class", "temperature");
        eq("mdi:thermometer", GlanceFormat.icon("sensor.x", temperature, ""));

        eq(0xFFFFB300, GlanceFormat.accent("light.kitchen", "on"));
        eq(0, GlanceFormat.accent("light.kitchen", "off"));
        eq(0xFFE53935, GlanceFormat.accent("lock.front", "unlocked"));
        eq(0, GlanceFormat.accent("sensor.x", "on"));

        eq(360, GlanceFormat.clamp(null, 120, 1000, 360));
        eq(1000, GlanceFormat.clamp(5000, 120, 1000, 360));
        eq(120, GlanceFormat.clamp(3, 120, 1000, 360));
        eq(7, GlanceFormat.clamp(6.6, 1, 8, 2));
        eq("bottom", GlanceFormat.anchor(null));
        eq("top-right", GlanceFormat.anchor("Top right"));
        eq("left", GlanceFormat.anchor("Left"));
        System.out.println("GlanceFormatTest OK");
    }
}
