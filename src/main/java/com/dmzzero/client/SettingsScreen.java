package com.dmzzero.client;

import com.dmzzero.ZeroConfig;
import com.dmzzero.settings.SettingsText;
import com.dmzzero.settings.SettingsTree;
import com.dragonminez.client.util.PanelSkin;
import com.dragonminez.client.util.TextUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// dmz zero's settings, from the Config button in the Mods list. built from dragonminez's own menu pieces and laid
// out like its config menu: names on the left, values on the right. server settings are config/dmzzero/server.toml,
// so they can be changed anywhere except on someone else's server
public final class SettingsScreen extends Screen {
    private static final ResourceLocation MENU_BIG = new ResourceLocation("dragonminez", "textures/gui/menu/menubig.png");
    private static final ResourceLocation BUTTONS = new ResourceLocation("dragonminez", "textures/gui/buttons/characterbuttons.png");
    private static final int ROW = 20;
    private static final int GAP = 6;
    private static final int TAB_H = 16;
    // dmz's menu colors: gold titles, cyan sections, its orange accent for anything changed from the default
    private static final int GOLD = 0xFFD700;
    private static final int CYAN = 0x7CFDD6;
    private static final int WHITE = 0xFFFFFF;
    private static final int CHANGED = 0xFF7722;
    private static final int MUTED = 0xA8A8A8;
    private static final int BAD = 0xFF5555;
    private static final int HEADER = 0x1CFFFFFF;
    private static final int HEADER_HOVER = 0x30FFFFFF;
    private static final int ROW_HOVER = 0x16FFFFFF;
    private static final int FOCUS = 0xC0FFD700;
    private static final int TRACK = 0xFF333333;
    private static final int THUMB = 0xFFAAAAAA;
    // sections opened this session, per tab
    private static final Set<String> OPEN = new HashSet<>();
    private static SettingsScreen open;
    // the dev autotest points here instead of the real cursor, so it never moves the player's mouse
    private static int[] testMouse;

    private final Screen parent;
    private boolean world;
    private boolean clientChanged, worldChanged;
    private String query = "";
    private EditBox search;
    private EditBox typing;
    private SettingsTree.Item typingItem;
    private final List<Row> rows = new ArrayList<>();
    private double scroll;
    private boolean draggingBar;
    private int selected = -1;
    private SettingsTree.Item holding;
    private int holdDir;
    private int holdTicks;
    private int optX, optW, valX, valW, panelY, panelH, listTop, listBottom, tabW, doneY;

    private sealed interface Row permits Header, Setting {
    }

    private record Header(String section, Component name, int count, boolean open) implements Row {
    }

    private record Setting(SettingsTree.Item item) implements Row {
    }

    public SettingsScreen(Screen parent) {
        super(Component.translatableWithFallback("dmzzero.settings.title", "DMZ Zero settings"));
        this.parent = parent;
    }

    /** Puts the Config button in forge's Mods list. Called once from the mod's constructor, on the client only. */
    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SettingsScreen(parent)));
    }

    static boolean worldEditable(Minecraft mc) {
        // on a server in another process the settings are that server's own
        return (mc.getConnection() == null || mc.isLocalServer()) && ZeroConfig.SERVER_SPEC.isLoaded();
    }

    @Override
    protected void init() {
        open = this;
        if (!worldEditable(minecraft)) world = false;
        int total = Math.min(width - 16, 460);
        valW = Mth.clamp(Math.round(total * 0.36f), 104, 160);
        optW = total - GAP - valW;
        optX = (width - total) / 2;
        valX = optX + optW + GAP;
        tabW = Math.min(130, (total - GAP) / 2);
        panelY = TAB_H + 10;
        doneY = height - 24;
        panelH = Math.max(120, doneY - 6 - panelY);
        listTop = panelY + 52;
        listBottom = panelY + panelH - 14;
        search = new EditBox(font, optX + 16, panelY + 37, optW - 32, 10, Component.translatableWithFallback("dmzzero.settings.search", "Search"));
        search.setBordered(false);
        search.setMaxLength(64);
        search.setTextColor(WHITE);
        search.setHint(Component.translatableWithFallback("dmzzero.settings.search", "Search").withStyle(Style.EMPTY.withColor(MUTED)));
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            scroll = 0;
            selected = -1;
            rebuild();
        });
        addRenderableWidget(search);
        typing = null;
        typingItem = null;
        rebuild();
    }

    private List<SettingsTree.Item> items() {
        return SettingsTree.of(world ? ZeroConfig.SERVER_SPEC : ZeroConfig.CLIENT_SPEC);
    }

    private List<SettingsTree.Item> matching() {
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) return items();
        List<SettingsTree.Item> out = new ArrayList<>();
        for (SettingsTree.Item i : items()) {
            if (label(i).getString().toLowerCase(Locale.ROOT).contains(q) || description(i).getString().toLowerCase(Locale.ROOT).contains(q)
                    || section(i.section()).getString().toLowerCase(Locale.ROOT).contains(q)) out.add(i);
        }
        return out;
    }

    // while searching every section with a match is open
    private void rebuild() {
        rows.clear();
        Map<String, List<SettingsTree.Item>> bySection = new LinkedHashMap<>();
        for (SettingsTree.Item i : matching()) bySection.computeIfAbsent(i.section(), s -> new ArrayList<>()).add(i);
        boolean searching = !query.isBlank();
        for (Map.Entry<String, List<SettingsTree.Item>> e : bySection.entrySet()) {
            boolean isOpen = searching || OPEN.contains(openKey(e.getKey()));
            rows.add(new Header(e.getKey(), section(e.getKey()), e.getValue().size(), isOpen));
            if (isOpen) for (SettingsTree.Item i : e.getValue()) rows.add(new Setting(i));
        }
        if (selected >= rows.size()) selected = rows.size() - 1;
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private String openKey(String section) {
        return (world ? "world:" : "client:") + section;
    }

    static MutableComponent label(SettingsTree.Item i) {
        return Component.translatableWithFallback(SettingsText.key(i.path()), SettingsText.label(i.key()));
    }

    // a translation may give its own .tooltip; in english the config comment is the description
    static MutableComponent description(SettingsTree.Item i) {
        return Component.translatableWithFallback(SettingsText.key(i.path()) + ".tooltip", i.comment());
    }

    static MutableComponent section(String section) {
        return Component.translatableWithFallback(SettingsText.key(section), SettingsText.label(section));
    }

    private static boolean isNumber(SettingsTree.Item i) {
        return i.type() == Integer.class || i.type() == Long.class || i.type() == Double.class;
    }

    private static boolean changed(SettingsTree.Item i) {
        return !Objects.equals(i.value().get(), i.value().getDefault());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean atOrPast(Object value, Object bound, int side) {
        if (!(value instanceof Comparable c) || bound == null) return false;
        int cmp = c.compareTo(bound);
        return side < 0 ? cmp <= 0 : cmp >= 0;
    }

    @SuppressWarnings("unchecked")
    private void set(SettingsTree.Item item, Object value) {
        ForgeConfigSpec.ConfigValue<Object> v = (ForgeConfigSpec.ConfigValue<Object>) item.value();
        if (value == null || Objects.equals(v.get(), value)) return;
        v.set(value);
        if (world) worldChanged = true;
        else clientChanged = true;
    }

    private void nudge(SettingsTree.Item item, int dir) {
        set(item, SettingsText.nudge(item.value().get(), item.type(), item.min(), item.max(), item.value().getDefault(), dir));
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    // layout of one row, shared by drawing and clicking
    private int rowY(int index) {
        return listTop + index * ROW - (int) scroll;
    }

    private int optLeft() {
        return optX + 9;
    }

    private int optRight() {
        return optX + optW - 17;
    }

    private int valLeft() {
        return valX + 9;
    }

    private int valRight() {
        return valX + valW - 9;
    }

    private double maxScroll() {
        return Math.max(0, rows.size() * ROW - (listBottom - listTop));
    }

    private int rowAt(double mx, double my) {
        if (my < listTop || my >= listBottom) return -1;
        boolean overOptions = mx >= optX + 5 && mx < optRight() + 2, overValues = mx >= valX + 5 && mx < valX + valW - 5;
        if (!overOptions && !overValues) return -1;
        int i = (int) Math.floor((my - listTop + scroll) / ROW);
        return i >= 0 && i < rows.size() ? i : -1;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        if (testMouse != null) {
            mx = testMouse[0];
            my = testMouse[1];
        }
        renderBackground(g);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        renderTabs(g, mx, my);
        renderPanel(g, optX, optW, Component.literal("DMZ Zero"));
        renderPanel(g, valX, valW, Component.translatableWithFallback("dmzzero.settings.values", "Values"));
        (search.isFocused() ? PanelSkin.FIELD_EDITING : PanelSkin.FIELD).draw(g, optX + 12, panelY + 34, optW - 24, 14);
        int hovered = draggingBar ? -1 : rowAt(mx, my);
        g.enableScissor(optX + 5, listTop, valX + valW - 5, listBottom);
        for (int i = 0; i < rows.size(); i++) {
            int y = rowY(i);
            if (y + ROW <= listTop || y >= listBottom) continue;
            if (rows.get(i) instanceof Header h) renderHeader(g, h, y, i == hovered);
            else if (rows.get(i) instanceof Setting s) renderSetting(g, s.item(), y, i == hovered);
            if (i == selected) {
                g.renderOutline(optLeft() - 1, y, optRight() - optLeft() + 2, ROW - 1, FOCUS);
                g.renderOutline(valLeft() - 1, y, valRight() - valLeft() + 2, ROW - 1, FOCUS);
            }
        }
        g.disableScissor();
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatableWithFallback("dmzzero.settings.noMatch", "No setting matches \"%s\".", query.trim()),
                    optX + optW / 2, listTop + 8, MUTED);
        }
        renderBar(g, mx, my);
        placeTyping();
        boolean overDone = inside(mx, my, width / 2 - 60, doneY, 120, 18);
        (overDone ? PanelSkin.BUTTON_HOVER : PanelSkin.BUTTON).draw(g, width / 2 - 60, doneY, 120, 18);
        TextUtil.drawCenteredStringWithBorder(g, font, Component.translatableWithFallback("gui.done", "Done"), width / 2, doneY + 5, WHITE);
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my, hovered);
    }

    private void renderTabs(GuiGraphics g, int mx, int my) {
        int x1 = width / 2 - tabW - GAP / 2, x2 = width / 2 + GAP / 2;
        renderTab(g, x1, Component.translatableWithFallback("dmzzero.settings.client", "Your settings"), !world, true, inside(mx, my, x1, 6, tabW, TAB_H));
        renderTab(g, x2, Component.translatableWithFallback("dmzzero.settings.server", "Server settings"), world, worldEditable(minecraft),
                inside(mx, my, x2, 6, tabW, TAB_H));
    }

    private void renderTab(GuiGraphics g, int x, Component label, boolean active, boolean enabled, boolean hover) {
        PanelSkin skin = active ? PanelSkin.BUTTON_ACTIVE : enabled && hover ? PanelSkin.BUTTON_HOVER : PanelSkin.BUTTON;
        skin.draw(g, x, 6, tabW, TAB_H);
        TextUtil.drawCenteredStringWithBorder(g, font, label, x + tabW / 2, 10, enabled ? WHITE : 0x808080);
    }

    private void renderPanel(GuiGraphics g, int x, int w, Component title) {
        PanelSkin.PANEL.draw(g, x, panelY, w, panelH);
        g.blit(MENU_BIG, x + (w - 107) / 2, panelY + 10, 142, 22, 107, 21);
        TextUtil.drawCenteredStringWithBorder(g, font, title.copy().withStyle(ChatFormatting.BOLD), x + w / 2, panelY + 17, GOLD);
    }

    private void renderHeader(GuiGraphics g, Header h, int y, boolean hover) {
        g.fill(optLeft(), y + 1, optRight(), y + ROW - 2, hover ? HEADER_HOVER : HEADER);
        g.fill(valLeft(), y + 1, valRight(), y + ROW - 2, hover ? HEADER_HOVER : HEADER);
        // dmz's section arrow, turned down while the section is open
        g.pose().pushPose();
        g.pose().translate(optLeft() + 7, y + 9.5, 0);
        if (h.open()) g.pose().mulPose(Axis.ZP.rotationDegrees(90));
        g.pose().scale(0.6f, 0.6f, 1);
        g.blit(BUTTONS, -4, -7, 20, 0, 8, 14);
        g.pose().popPose();
        String count = String.valueOf(h.count());
        int countW = font.width(count);
        g.drawString(font, fit(h.name().getString(), optRight() - optLeft() - 24 - countW), optLeft() + 14, y + 6, CYAN);
        g.drawString(font, count, optRight() - 4 - countW, y + 6, MUTED);
    }

    private void renderSetting(GuiGraphics g, SettingsTree.Item item, int y, boolean hover) {
        if (hover) {
            g.fill(optLeft(), y + 1, optRight(), y + ROW - 2, ROW_HOVER);
            g.fill(valLeft(), y + 1, valRight(), y + ROW - 2, ROW_HOVER);
        }
        g.drawString(font, fit(label(item).getString(), optRight() - optLeft() - 8), optLeft() + 4, y + 6, WHITE);
        Object value = item.value().get();
        int vy = y + 5, cx = (valLeft() + valRight()) / 2;
        if (value instanceof Boolean on) {
            g.blit(BUTTONS, cx - 10, vy, 122, on ? 0 : 10, 20, 10);
        } else if (isNumber(item)) {
            g.blit(BUTTONS, valLeft() + 3, vy, 142, atOrPast(value, item.min(), -1) ? 10 : 0, 10, 10);
            g.blit(BUTTONS, valRight() - 13, vy, 0, atOrPast(value, item.max(), 1) ? 10 : 0, 10, 10);
            if (item != typingItem) {
                String text = fit(SettingsText.show(value), valRight() - valLeft() - 34);
                g.drawCenteredString(font, text, cx, y + 6, changed(item) ? CHANGED : WHITE);
            } else {
                PanelSkin.FIELD_EDITING.draw(g, valLeft() + 15, y + 3, valRight() - valLeft() - 30, 14);
            }
        } else {
            g.drawCenteredString(font, fit(SettingsText.show(value), valRight() - valLeft() - 8), cx, y + 6, MUTED);
        }
    }

    private void renderBar(GuiGraphics g, int mx, int my) {
        double max = maxScroll();
        if (max <= 0) return;
        int x = optX + optW - 13, view = listBottom - listTop;
        int thumb = Math.max(14, view * view / (rows.size() * ROW));
        int ty = listTop + (int) ((view - thumb) * (scroll / max));
        g.fill(x, listTop, x + 3, listBottom, TRACK);
        boolean active = draggingBar || inside(mx, my, x - 2, ty, 7, thumb);
        g.fill(x, ty, x + 3, ty + thumb, active ? 0xFFFFFFFF : THUMB);
    }

    private void renderTooltip(GuiGraphics g, int mx, int my, int hovered) {
        int x2 = width / 2 + GAP / 2;
        if (!worldEditable(minecraft) && inside(mx, my, x2, 6, tabW, TAB_H)) {
            tooltip(g, mx, my, Component.translatableWithFallback("dmzzero.settings.server", "Server settings"),
                    Component.translatableWithFallback("dmzzero.settings.serverOff",
                            "This server sets them in its own config/dmzzero/server.toml."), List.of());
            return;
        }
        if (hovered < 0 || !(rows.get(hovered) instanceof Setting s)) return;
        SettingsTree.Item i = s.item();
        List<Component> footer = new ArrayList<>();
        if (i.min() != null && i.max() != null) {
            footer.add(Component.translatableWithFallback("dmzzero.settings.range", "From %s to %s",
                    SettingsText.show(i.min()), SettingsText.show(i.max())).withStyle(Style.EMPTY.withColor(MUTED)));
        }
        footer.add(Component.translatableWithFallback("dmzzero.settings.default", "Default: %s", shown(i.value().getDefault()))
                .withStyle(Style.EMPTY.withColor(MUTED)));
        if (isNumber(i)) footer.add(Component.translatableWithFallback("dmzzero.settings.type", "Click the number to type one")
                .withStyle(Style.EMPTY.withColor(MUTED)));
        if (changed(i)) footer.add(Component.translatableWithFallback("dmzzero.settings.reset", "Right-click to reset")
                .withStyle(Style.EMPTY.withColor(CHANGED)));
        tooltip(g, mx, my, label(i), description(i), footer);
    }

    private void tooltip(GuiGraphics g, int mx, int my, Component title, Component text, List<Component> footer) {
        List<Component> lines = new ArrayList<>();
        for (FormattedText line : font.getSplitter().splitLines(text, 200, Style.EMPTY)) lines.add(Component.literal(line.getString()));
        TextUtil.renderAdvancedTooltip(g, font, mx, my, width, height, title.copy().withStyle(Style.EMPTY.withColor(GOLD)), lines, footer, CYAN);
    }

    private Component shown(Object value) {
        if (value instanceof Boolean b) {
            return b ? Component.translatableWithFallback("dmzzero.settings.on", "On") : Component.translatableWithFallback("dmzzero.settings.off", "Off");
        }
        return Component.literal(SettingsText.show(value));
    }

    private String fit(String s, int w) {
        if (w <= 0) return "";
        if (font.width(s) <= w) return s;
        return font.plainSubstrByWidth(s, Math.max(0, w - font.width("..."))) + "...";
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // typing a number: an edit box over the value, moved with the row while it scrolls
    private void startTyping(SettingsTree.Item item) {
        stopTyping(true);
        typingItem = item;
        typing = new EditBox(font, 0, 0, valRight() - valLeft() - 36, 10, label(item));
        typing.setBordered(false);
        typing.setMaxLength(24);
        typing.setValue(SettingsText.show(item.value().get()));
        typing.setResponder(text -> typing.setTextColor(SettingsText.parse(text, item.type(), item.min(), item.max()) == null ? BAD : WHITE));
        addRenderableWidget(typing);
        setFocused(typing);
        placeTyping();
    }

    private void placeTyping() {
        if (typing == null) return;
        int index = -1;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i) instanceof Setting s && s.item() == typingItem) index = i;
        int y = index < 0 ? -1 : rowY(index);
        if (index < 0 || y < listTop || y + ROW > listBottom) {
            stopTyping(true);
            return;
        }
        typing.setX(valLeft() + 18);
        typing.setY(y + 6);
    }

    // keep: a valid number is kept, anything else is dropped
    private void stopTyping(boolean keep) {
        if (typing == null) return;
        if (keep) set(typingItem, SettingsText.parse(typing.getValue(), typingItem.type(), typingItem.min(), typingItem.max()));
        removeWidget(typing);
        typing = null;
        typingItem = null;
    }

    private void toggleSection(Header h) {
        if (!query.isBlank()) return;
        String key = openKey(h.section());
        if (!OPEN.remove(key)) OPEN.add(key);
        rebuild();
    }

    private void activate(int index) {
        if (index < 0 || index >= rows.size()) return;
        click();
        if (rows.get(index) instanceof Header h) toggleSection(h);
        else if (rows.get(index) instanceof Setting s) {
            if (s.item().value().get() instanceof Boolean b) set(s.item(), !b);
            else if (isNumber(s.item())) startTyping(s.item());
        }
    }

    private void select(int index) {
        if (rows.isEmpty()) return;
        selected = Mth.clamp(index, 0, rows.size() - 1);
        int y = selected * ROW;
        int view = listBottom - listTop;
        if (y < scroll) scroll = y;
        else if (y + ROW > scroll + view) scroll = y + ROW - view;
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (typing != null && !inside(mx, my, typing.getX() - 3, typing.getY() - 3, typing.getWidth() + 6, 14)) stopTyping(true);
        if (super.mouseClicked(mx, my, button)) return true;
        search.setFocused(false);
        if (button == 0) {
            int x1 = width / 2 - tabW - GAP / 2, x2 = width / 2 + GAP / 2;
            if (inside(mx, my, x1, 6, tabW, TAB_H)) {
                click();
                show(false);
                return true;
            }
            if (inside(mx, my, x2, 6, tabW, TAB_H) && worldEditable(minecraft)) {
                click();
                show(true);
                return true;
            }
            if (inside(mx, my, width / 2 - 60, doneY, 120, 18)) {
                click();
                onClose();
                return true;
            }
            if (maxScroll() > 0 && inside(mx, my, optX + optW - 15, listTop, 7, listBottom - listTop)) {
                draggingBar = true;
                dragTo(my);
                return true;
            }
        }
        int index = rowAt(mx, my);
        if (index < 0) return false;
        selected = index;
        Row row = rows.get(index);
        if (row instanceof Header h) {
            if (button == 0) {
                click();
                toggleSection(h);
            }
            return true;
        }
        SettingsTree.Item item = ((Setting) row).item();
        if (button == 1) {
            click();
            set(item, item.value().getDefault());
            return true;
        }
        if (button != 0) return false;
        Object value = item.value().get();
        if (value instanceof Boolean b) {
            click();
            set(item, !b);
        } else if (isNumber(item)) {
            int y = rowY(index);
            if (inside(mx, my, valLeft() + 1, y + 3, 14, 14)) hold(item, -1);
            else if (inside(mx, my, valRight() - 15, y + 3, 14, 14)) hold(item, 1);
            else if (mx >= valLeft()) startTyping(item);
        }
        return true;
    }

    private void hold(SettingsTree.Item item, int dir) {
        click();
        nudge(item, dir);
        holding = item;
        holdDir = dir;
        holdTicks = 0;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        holding = null;
        draggingBar = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingBar) {
            dragTo(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    private void dragTo(double my) {
        int view = listBottom - listTop;
        int thumb = Math.max(14, view * view / Math.max(1, rows.size() * ROW));
        double t = (my - listTop - thumb / 2.0) / Math.max(1, view - thumb);
        scroll = Mth.clamp(t, 0, 1) * maxScroll();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (my >= listTop && my < listBottom && mx >= optX && mx < valX + valW) {
            scroll = Mth.clamp(scroll - delta * ROW, 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (typing != null && typing.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                stopTyping(true);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                stopTyping(false);
                return true;
            }
            return typing.keyPressed(key, scan, mods) || true;
        }
        if (search.isFocused()) {
            if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                setFocused(null);
                search.setFocused(false);
                select(0);
                return true;
            }
            return super.keyPressed(key, scan, mods);
        }
        switch (key) {
            case GLFW.GLFW_KEY_UP -> select(selected - 1);
            case GLFW.GLFW_KEY_DOWN -> select(selected + 1);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> activate(selected);
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> adjust(selected, key == GLFW.GLFW_KEY_RIGHT ? 1 : -1);
            case GLFW.GLFW_KEY_DELETE -> {
                if (selected >= 0 && rows.get(selected) instanceof Setting s) set(s.item(), s.item().value().getDefault());
            }
            default -> {
                return super.keyPressed(key, scan, mods);
            }
        }
        return true;
    }

    private void adjust(int index, int dir) {
        if (index < 0 || index >= rows.size() || !(rows.get(index) instanceof Setting s)) return;
        if (s.item().value().get() instanceof Boolean) set(s.item(), dir > 0);
        else if (isNumber(s.item())) nudge(s.item(), dir);
    }

    // typing anywhere searches
    @Override
    public boolean charTyped(char c, int mods) {
        if (typing != null && typing.isFocused()) return typing.charTyped(c, mods);
        if (!search.isFocused() && Character.isLetterOrDigit(c)) {
            setFocused(search);
            search.setFocused(true);
            return search.charTyped(c, mods);
        }
        return super.charTyped(c, mods);
    }

    @Override
    public void tick() {
        search.tick();
        if (typing != null) typing.tick();
        if (holding != null && ++holdTicks > 8 && holdTicks % 2 == 0) nudge(holding, holdDir);
    }

    private void show(boolean worldSettings) {
        if (worldSettings && !worldEditable(minecraft)) return;
        stopTyping(true);
        world = worldSettings;
        scroll = 0;
        selected = -1;
        rebuild();
    }

    @Override
    public void onClose() {
        stopTyping(true);
        save();
        open = null;
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        save();
    }

    private void save() {
        if (clientChanged) ZeroConfig.CLIENT_SPEC.save();
        if (worldChanged && ZeroConfig.SERVER_SPEC.isLoaded()) ZeroConfig.SERVER_SPEC.save();
        clientChanged = worldChanged = false;
    }

    // for the dev autotest

    private static SettingsTree.Item find(String path) {
        if (open == null) return null;
        for (SettingsTree.Item i : open.items()) if (i.path().equals(path)) return i;
        return null;
    }

    /** How many settings the open screen lists under its sections (open or closed), or 0. */
    static int settingRows() {
        return open == null ? 0 : open.matching().size();
    }

    /** Clicks the on/off toggle of the setting at this path on the open screen. */
    static boolean press(String path) {
        SettingsTree.Item i = find(path);
        if (i == null || !(i.value().get() instanceof Boolean b)) return false;
        open.set(i, !b);
        return true;
    }

    /** Types a number into the setting at this path, as the edit box would: out of range or not a number is refused. */
    static boolean type(String path, String text) {
        SettingsTree.Item i = find(path);
        if (i == null || !isNumber(i)) return false;
        open.set(i, SettingsText.parse(text, i.type(), i.min(), i.max()));
        return true;
    }

    /** One click of the setting's - (dir -1) or + (dir 1) button. */
    static boolean nudgeSetting(String path, int dir) {
        SettingsTree.Item i = find(path);
        if (i == null || !isNumber(i)) return false;
        open.nudge(i, dir);
        return true;
    }

    static void searchFor(String text) {
        if (open != null && open.search != null) open.search.setValue(text);
    }

    static void showWorld(boolean worldSettings) {
        if (open != null) open.show(worldSettings);
    }

    static boolean showingWorld() {
        return open != null && open.world;
    }

    static void pointAt(int[] at) {
        testMouse = at;
    }

    /** The middle of a tab, in screen coordinates. */
    static int[] tab(boolean worldTab) {
        if (open == null) return null;
        int x = worldTab ? open.width / 2 + GAP / 2 : open.width / 2 - open.tabW - GAP / 2;
        return new int[]{x + open.tabW / 2, 6 + TAB_H / 2};
    }

    private static int indexOf(String key) {
        if (open == null) return -1;
        for (int i = 0; i < open.rows.size(); i++) {
            Row r = open.rows.get(i);
            if (r instanceof Header h && h.section().equals(key) || r instanceof Setting s && s.item().path().equals(key)) return i;
        }
        return -1;
    }

    // scrolls the row into view first, as a player would
    private static int reveal(String key) {
        int i = indexOf(key);
        if (i < 0) return -1;
        int saved = open.selected;
        open.select(i);
        open.selected = saved;
        return i;
    }

    /** The middle of a section's or setting's name, or null when it isn't listed. */
    static int[] row(String key) {
        int i = reveal(key);
        return i < 0 ? null : new int[]{open.optLeft() + 40, open.rowY(i) + ROW / 2};
    }

    /** A setting's control: -1 the minus button, 0 the value or toggle, 1 the plus button. */
    static int[] control(String path, int which) {
        int i = reveal(path);
        if (i < 0) return null;
        int y = open.rowY(i) + ROW / 2;
        int x = which < 0 ? open.valLeft() + 8 : which > 0 ? open.valRight() - 8 : (open.valLeft() + open.valRight()) / 2;
        return new int[]{x, y};
    }

    static boolean sectionOpen(String section) {
        int i = indexOf(section);
        return i >= 0 && open.rows.get(i) instanceof Header h && h.open();
    }

    static int headers() {
        if (open == null) return 0;
        int n = 0;
        for (Row r : open.rows) if (r instanceof Header) n++;
        return n;
    }

    static int listedRows() {
        return open == null ? 0 : open.rows.size();
    }

    static boolean typingOpen() {
        return open != null && open.typing != null;
    }

    static String selectedKey() {
        if (open == null || open.selected < 0 || open.selected >= open.rows.size()) return null;
        Row r = open.rows.get(open.selected);
        return r instanceof Header h ? h.section() : ((Setting) r).item().path();
    }
}
