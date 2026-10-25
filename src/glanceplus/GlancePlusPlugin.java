// SPDX-License-Identifier: MIT
package glanceplus;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.KsTheme;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * Glance Plus: Kiosk Satellite's At a Glance chips with room to grow. Each chip is an icon circle, a name and a value,
 * like the stock row, but its width, minimum height and line counts are settings, so a long value can wrap instead of
 * ending in an ellipsis. Drawn as one visual-only overlay above the screensaver.
 */
public final class GlancePlusPlugin implements KioskPlugin {
    private static final String OVERLAY = "chips";
    private static final int MAX_CHIPS = 12;

    /** One configured chip. */
    private static final class Chip {
        final String entity;
        final String name;
        final String icon;
        Chip(String entity, String name, String icon) { this.entity = entity; this.name = name; this.icon = icon; }
    }

    /** The latest state of one entity. */
    private static final class Reading {
        final String state;
        final Map<String, Object> attributes;
        Reading(String state, Map<String, Object> attributes) { this.state = state; this.attributes = attributes; }
    }

    /** What the chips look like, parsed from settings. Replaced as a whole. */
    private static final class Look {
        int columns = 2;
        int chipWidth = 360;
        int minHeight = 0;
        int valueLines = 2;
        int nameLines = 1;
        int textScale = 100;
        int opacity = 75;
        int inset = 24;
        int redraw = 30;
        boolean fitContent;
        boolean hideNames;
        boolean mono;
        boolean always;
        String anchor = OverlaySpec.BOTTOM;

        /** Everything that changes the overlay's size or look. */
        String signature() {
            return anchor + "|" + inset + "|" + columns + "|" + chipWidth + "|" + minHeight + "|" + valueLines + "|" + nameLines
                    + "|" + textScale + "|" + opacity + "|" + fitContent + "|" + hideNames + "|" + mono;
        }
    }

    /** A chip ready to draw. */
    private static final class Card {
        final String name;
        final String value;
        final String icon;
        final int accent;
        Card(String name, String value, String icon, int accent) { this.name = name; this.value = value; this.icon = icon; this.accent = accent; }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private PluginHost host;
    private List<Chip> chips = new ArrayList<>();
    private final Map<String, Reading> readings = new HashMap<>();
    private final Set<String> subscribed = new HashSet<>();
    private Look look = new Look();
    private boolean screensaver;
    private boolean shownNow;
    private String shownSignature = "";
    private Runnable pendingShow;
    private Runnable poll;
    private String lastStatus = "";
    private int eventsSeen;
    private int generation;
    private int created;
    private int destroyed;

    // Touched on the Android main thread only.
    private LinearLayout root;
    private Context context;
    private KsTheme theme;
    private volatile List<Card> drawn = new ArrayList<>();
    private volatile Look drawnLook = new Look();

    @Override
    public synchronized void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        try {
            host.subscribe("screensaver.state");
            host.subscribe("screensaver.view");
            host.executeCommand("isScreensaverActive", new HashMap<String, Object>(), (ok, data, error) -> {
                if (ok && data instanceof Boolean) setScreensaver((Boolean) data);
            });
        } catch (UnsupportedOperationException olderHost) {
            screensaver = true;
        }
        configure(settings);
        poll = new Runnable() {
            @Override public void run() {
                PluginHost live;
                synchronized (GlancePlusPlugin.this) { live = host; }
                if (live == null) return;
                try {
                    live.executeCommand("isScreensaverActive", new HashMap<String, Object>(), (ok, data, error) -> {
                        if (ok && data instanceof Boolean) setScreensaver((Boolean) data);
                    });
                } catch (RuntimeException ignored) {}
                int every;
                synchronized (GlancePlusPlugin.this) { every = look.redraw; if (every > 0) redraw(); }
                main.postDelayed(this, every > 0 ? every * 1000L : 30000L);
            }
        };
        main.postDelayed(poll, 30000);
    }

    @Override
    public synchronized void configure(Map<String, Object> settings) {
        look = parseLook(settings);
        chips = parseChips(settings.get("chips"));
        Set<String> wanted = new HashSet<>();
        for (Chip chip : chips) wanted.add(chip.entity);
        for (String id : new ArrayList<>(subscribed)) {
            if (!wanted.contains(id)) { host.unsubscribe("ha.entity." + id); subscribed.remove(id); readings.remove(id); }
        }
        for (String id : wanted) {
            if (subscribed.size() >= 16) break;
            if (subscribed.add(id)) host.subscribe("ha.entity." + id);
        }
        refresh();
    }

    @Override
    public void execute(String command, Map<String, Object> arguments) {}

    @Override
    public synchronized void onEvent(String event, Map<String, Object> payload) {
        eventsSeen++;
        if (event.equals("ks.screensaver.state")) {
            Object active = payload.get("active");
            setScreensaver(Boolean.TRUE.equals(active));
            return;
        }
        if (event.equals("ks.screensaver.view")) {
            // The screensaver changed what it draws, for example a new slide. Draw the chips on top again.
            if (look.redraw > 0) redraw();
            return;
        }
        if (!event.startsWith("ks.ha.entity.")) return;
        String id = event.substring("ks.ha.entity.".length());
        if (!subscribed.contains(id)) return;
        Object state = payload.get("state");
        Object status = payload.get("status");
        String text = state instanceof String ? (String) state : status instanceof String && !"available".equals(status) ? (String) status : "";
        Object attributes = payload.get("attributes");
        readings.put(id, new Reading(text, attributes instanceof Map ? castMap(attributes) : new HashMap<String, Object>()));
        refresh();
    }

    @Override
    public synchronized void stop() {
        if (poll != null) main.removeCallbacks(poll);
        if (pendingShow != null) main.removeCallbacks(pendingShow);
        host = null;
        shownNow = false;
    }

    /** Draws the overlay again, in case the screensaver now covers it. */
    private synchronized void redraw() {
        if (!shownNow) return;
        shownSignature = "";
        lastStatus = "";
        refresh();
    }

    private synchronized void refreshLater() { lastStatus = ""; refresh(); }

    private synchronized void setScreensaver(boolean active) {
        screensaver = active;
        refresh();
    }

    /**
     * Shows, replaces or hides the overlay to match the settings, the screensaver and the entity states. The overlay's
     * size follows its content, and KS only measures it when the view is created, so a change that could resize it
     * replaces the overlay instead of editing it in place. Changes are coalesced so a burst makes one replacement.
     */
    private void refresh() {
        PluginHost current = host;
        if (current == null) return;
        List<Card> cards = cards();
        boolean show = !cards.isEmpty() && (look.always || screensaver);
        report(current, cards.size(), show);
        if (!show) {
            if (pendingShow != null) { main.removeCallbacks(pendingShow); pendingShow = null; }
            if (shownNow) {
                shownNow = false;
                shownSignature = "";
                try { current.hideOverlay(OVERLAY); } catch (UnsupportedOperationException ignored) {}
            }
            return;
        }
        StringBuilder signature = new StringBuilder(look.signature());
        for (Card card : cards) signature.append('\n').append(card.name).append('|').append(card.value).append('|').append(card.icon).append('|').append(card.accent);
        if (shownNow && signature.toString().equals(shownSignature)) return;
        shownNow = true;
        shownSignature = signature.toString();
        drawn = cards;
        drawnLook = look;
        if (pendingShow != null) main.removeCallbacks(pendingShow);
        final Look snapshotLook = look;
        pendingShow = () -> {
            PluginHost live;
            synchronized (GlancePlusPlugin.this) { pendingShow = null; live = host; }
            if (live == null) return;
            OverlaySpec spec = OverlaySpec.at(snapshotLook.anchor).inset(snapshotLook.inset).onTop(true).touchable(false).closeOnBack(false);
            final int mine;
            synchronized (GlancePlusPlugin.this) { mine = ++generation; }
            try {
                live.showOverlay(OVERLAY, spec, new OverlayFactory() {
                    @Override public View create(Context c, KsTheme t) {
                        context = c;
                        theme = t;
                        root = new LinearLayout(c);
                        root.setOrientation(LinearLayout.VERTICAL);
                        build();
                        synchronized (GlancePlusPlugin.this) { created++; lastStatus = ""; }
                        return root;
                    }
                    @Override public void onThemeChanged(View view, KsTheme t) { theme = t; build(); }
                    @Override public void onDestroy(View view) {
                        if (view == root) { root = null; context = null; theme = null; }
                        synchronized (GlancePlusPlugin.this) {
                            destroyed++;
                            // KS removed the current overlay by itself (for example when the screensaver view changed):
                            // forget it so the next refresh draws it again. A replaced overlay is not the current one.
                            if (mine == generation) {
                                shownNow = false;
                                shownSignature = "";
                                main.postDelayed(new Runnable() { @Override public void run() { refreshLater(); } }, 1000);
                            }
                        }
                    }
                });
            } catch (UnsupportedOperationException olderHost) {
                live.status("Glance Plus needs a Kiosk Satellite with native overlays.", true);
            }
        };
        main.postDelayed(pendingShow, 300);
    }

    /** One line in the plugin's Plugin Manager entry, so a missing chip can be traced without a debugger. */
    private void report(PluginHost current, int ready, boolean show) {
        String text = chips.size() + " chips set, " + ready + " with a value · screensaver " + (screensaver ? "active" : "idle")
                + " · show: " + (look.always ? "always" : "screensaver only") + " · overlay " + (show ? "on" : "off")
                + " · " + eventsSeen + " events · drawn " + created + ", removed " + destroyed;
        if (text.equals(lastStatus)) return;
        lastStatus = text;
        try { current.status(text, false); } catch (RuntimeException ignored) {}
    }

    private List<Card> cards() {
        List<Card> result = new ArrayList<>();
        for (Chip chip : chips) {
            Reading reading = readings.get(chip.entity);
            if (reading == null || GlanceFormat.blank(reading.state)) continue;
            String value = GlanceFormat.value(reading.state, reading.attributes);
            result.add(new Card(GlanceFormat.name(chip.entity, reading.attributes, chip.name), value,
                    GlanceFormat.icon(chip.entity, reading.attributes, chip.icon), GlanceFormat.accent(chip.entity, reading.state)));
        }
        return result;
    }

    // ---- drawing, on the main thread ----

    private void build() {
        if (root == null || context == null || theme == null) return;
        Look l = drawnLook;
        float scale = l.textScale / 100f;
        root.removeAllViews();
        int gap = theme.px(10 * scale);
        int gravity = l.anchor.endsWith("left") || l.anchor.equals("left") ? Gravity.START
                : l.anchor.endsWith("right") || l.anchor.equals("right") ? Gravity.END : Gravity.CENTER_HORIZONTAL;
        root.setGravity(gravity);
        List<Card> cards = drawn;
        int columns = Math.max(1, Math.min(l.columns, cards.size()));
        for (int i = 0; i < cards.size(); i += columns) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) rowParams.topMargin = gap;
            root.addView(row, rowParams);
            for (int j = i; j < Math.min(i + columns, cards.size()); j++) {
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(l.fitContent ? ViewGroup.LayoutParams.WRAP_CONTENT : theme.px(l.chipWidth * scale),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                if (j > i) p.leftMargin = gap;
                row.addView(chip(cards.get(j), l, scale), p);
            }
        }
    }

    private View chip(Card card, Look l, float scale) {
        LinearLayout chip = new LinearLayout(context);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(theme.px(12 * scale), theme.px(10 * scale), theme.px(20 * scale), theme.px(10 * scale));
        chip.setMinimumHeight(theme.px(l.minHeight * scale));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(Color.argb(Math.round(255 * l.opacity / 100f), 30, 30, 30));
        pill.setCornerRadius(theme.px(32 * scale));
        chip.setBackground(pill);

        int circleSize = theme.px(44 * scale);
        FrameLayout circle = new FrameLayout(context);
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(card.accent != 0 && !l.mono ? card.accent : 0x40FFFFFF);
        circle.setBackground(disc);
        Drawable glyph = null;
        try { glyph = theme.icon(card.icon, "onSurface"); } catch (RuntimeException | LinkageError missing) { glyph = null; }
        if (glyph != null) {
            glyph.setTint(Color.WHITE);
            ImageView image = new ImageView(context);
            image.setImageDrawable(glyph);
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(theme.px(24 * scale), theme.px(24 * scale), Gravity.CENTER);
            circle.addView(image, ip);
        }
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(circleSize, circleSize);
        chip.addView(circle, cp);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(l.fitContent ? ViewGroup.LayoutParams.WRAP_CONTENT : 0, ViewGroup.LayoutParams.WRAP_CONTENT, l.fitContent ? 0f : 1f);
        tp.leftMargin = theme.px(12 * scale);
        int textWidth = theme.px((l.chipWidth - 76) * scale);
        if (!l.hideNames) {
            TextView name = new TextView(context);
            theme.styleText(name, 12 * scale, 500, "onSurface");
            name.setTextColor(0xB3FFFFFF);
            name.setText(card.name);
            name.setMaxLines(l.nameLines);
            name.setEllipsize(TextUtils.TruncateAt.END);
            if (l.fitContent) name.setMaxWidth(textWidth);
            texts.addView(name);
        }
        TextView value = new TextView(context);
        theme.styleText(value, (l.hideNames ? 22 : 18) * scale, 700, "onSurface");
        value.setTextColor(Color.WHITE);
        value.setText(card.value);
        value.setMaxLines(l.valueLines);
        value.setEllipsize(TextUtils.TruncateAt.END);
        if (l.fitContent) value.setMaxWidth(textWidth);
        texts.addView(value);
        chip.addView(texts, tp);
        return chip;
    }

    // ---- settings ----

    private static Look parseLook(Map<String, Object> s) {
        Look l = new Look();
        l.columns = GlanceFormat.clamp(s.get("columns"), 1, 4, 2);
        l.chipWidth = GlanceFormat.clamp(s.get("chipWidth"), 120, 1000, 360);
        l.minHeight = GlanceFormat.clamp(s.get("minHeight"), 0, 400, 0);
        l.valueLines = GlanceFormat.clamp(s.get("valueLines"), 1, 8, 2);
        l.nameLines = GlanceFormat.clamp(s.get("nameLines"), 1, 3, 1);
        l.textScale = GlanceFormat.clamp(s.get("textScale"), 50, 250, 100);
        l.opacity = GlanceFormat.clamp(s.get("opacity"), 20, 100, 75);
        l.inset = GlanceFormat.clamp(s.get("inset"), 0, 400, 24);
        l.redraw = GlanceFormat.clamp(s.get("redraw"), 0, 300, 30);
        l.fitContent = Boolean.TRUE.equals(s.get("fitContent"));
        l.hideNames = Boolean.TRUE.equals(s.get("hideNames"));
        l.mono = Boolean.TRUE.equals(s.get("monochromeIcons"));
        l.always = "Always".equals(s.get("showOn"));
        Object position = s.get("position");
        l.anchor = GlanceFormat.anchor(position instanceof String ? (String) position : null);
        return l;
    }

    private static List<Chip> parseChips(Object raw) {
        List<Chip> result = new ArrayList<>();
        if (!(raw instanceof List)) return result;
        for (Object item : (List<?>) raw) {
            if (!(item instanceof Map) || result.size() >= MAX_CHIPS) continue;
            Map<String, Object> row = castMap(item);
            Object entity = row.get("entity");
            if (!(entity instanceof String) || ((String) entity).isEmpty()) continue;
            Object name = row.get("name");
            Object icon = row.get("icon");
            result.add(new Chip((String) entity, name instanceof String ? (String) name : "", icon instanceof String ? (String) icon : ""));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) copy.put(String.valueOf(entry.getKey()), entry.getValue());
        return copy;
    }
}
