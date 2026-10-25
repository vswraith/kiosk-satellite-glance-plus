// SPDX-License-Identifier: MIT
package glanceplus;

import java.util.Locale;
import java.util.Map;

/** Pure helpers: how an entity state reads on a chip, its icon and its accent. No Android classes, so tests run anywhere. */
final class GlanceFormat {
    private GlanceFormat() {}

    /** The text for a chip's value: numbers carry their unit, words are capitalised the way HA shows them. */
    static String value(String state, Map<String, Object> attributes) {
        if (state == null) return "";
        String trimmed = state.trim();
        if (trimmed.isEmpty()) return "";
        Object unit = attributes == null ? null : attributes.get("unit_of_measurement");
        if (isNumber(trimmed)) {
            String text = trimmed;
            if (unit instanceof String && !((String) unit).isEmpty()) {
                String suffix = (String) unit;
                text += suffix.startsWith("%") || suffix.startsWith("°") ? suffix : " " + suffix;
            }
            return text;
        }
        String words = trimmed.replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /** A chip with nothing to say stays off the screen until its entity has a value again. */
    static boolean blank(String state) { return state == null || state.trim().isEmpty(); }

    static boolean isNumber(String text) {
        try {
            Double.parseDouble(text);
            return !text.endsWith("d") && !text.endsWith("f") && !text.equalsIgnoreCase("nan") && !text.toLowerCase(Locale.ROOT).contains("infinity");
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    /** The entity's own name, or a readable form of its ID. */
    static String name(String entityId, Map<String, Object> attributes, String override) {
        if (override != null && !override.trim().isEmpty()) return override.trim();
        Object friendly = attributes == null ? null : attributes.get("friendly_name");
        if (friendly instanceof String && !((String) friendly).trim().isEmpty()) return ((String) friendly).trim();
        String id = entityId.substring(entityId.indexOf('.') + 1).replace('_', ' ');
        return id.isEmpty() ? entityId : Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    /** The icon: the user's choice, then the entity's own, then one for its type. */
    static String icon(String entityId, Map<String, Object> attributes, String override) {
        if (override != null && override.startsWith("mdi:")) return override;
        Object own = attributes == null ? null : attributes.get("icon");
        if (own instanceof String && ((String) own).startsWith("mdi:")) return (String) own;
        String domain = entityId.substring(0, Math.max(0, entityId.indexOf('.')));
        Object deviceClass = attributes == null ? null : attributes.get("device_class");
        if ("temperature".equals(deviceClass)) return "mdi:thermometer";
        if ("humidity".equals(deviceClass)) return "mdi:water-percent";
        if ("power".equals(deviceClass)) return "mdi:flash";
        if ("battery".equals(deviceClass)) return "mdi:battery";
        switch (domain) {
            case "light": return "mdi:lightbulb";
            case "switch": return "mdi:toggle-switch";
            case "lock": return "mdi:lock";
            case "cover": return "mdi:window-shutter";
            case "climate": return "mdi:thermostat";
            case "media_player": return "mdi:speaker";
            case "weather": return "mdi:weather-partly-cloudy";
            case "person": return "mdi:account";
            case "calendar": return "mdi:calendar";
            case "vacuum": return "mdi:robot-vacuum";
            case "binary_sensor": return "mdi:checkbox-marked-circle-outline";
            case "sensor": return "mdi:eye";
            default: return "mdi:information-outline";
        }
    }

    /** The icon circle's accent as an ARGB colour, or 0 for the neutral grey. State lights the circle, never the words. */
    static int accent(String entityId, String state) {
        if (state == null) return 0;
        String domain = entityId.substring(0, Math.max(0, entityId.indexOf('.')));
        switch (domain) {
            case "light": return "on".equals(state) ? 0xFFFFB300 : 0;
            case "lock": return "unlocked".equals(state) || "open".equals(state) ? 0xFFE53935 : "locked".equals(state) ? 0xFF43A047 : 0;
            case "cover": return "open".equals(state) || "opening".equals(state) ? 0xFF8E24AA : 0;
            case "switch": return "on".equals(state) ? 0xFF1E88E5 : 0;
            case "binary_sensor": return "on".equals(state) ? 0xFFF4511E : 0;
            case "media_player": return "playing".equals(state) ? 0xFF1E88E5 : 0;
            default: return 0;
        }
    }

    /** A number setting clamped into its range, whatever type the host delivered it as. */
    static int clamp(Object value, int min, int max, int fallback) {
        if (!(value instanceof Number)) return fallback;
        double number = ((Number) value).doubleValue();
        if (Double.isNaN(number)) return fallback;
        return (int) Math.max(min, Math.min(max, Math.round(number)));
    }

    /** The overlay anchor for a position setting. */
    static String anchor(String position) {
        if (position == null) return "bottom";
        switch (position) {
            case "Top left": return "top-left";
            case "Top": return "top";
            case "Top right": return "top-right";
            case "Left": return "left";
            case "Center": return "center";
            case "Right": return "right";
            case "Bottom left": return "bottom-left";
            case "Bottom right": return "bottom-right";
            default: return "bottom";
        }
    }
}
