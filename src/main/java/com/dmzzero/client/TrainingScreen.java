package com.dmzzero.client;

import com.dmzzero.chamber.TrainingMath;
import com.dmzzero.chamber.TrainingText;
import com.dmzzero.network.TrainingMenuS2C;
import com.dragonminez.client.util.LocalizationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

// the time chamber training menu: a search box over the opponents you can refight, each with a Fight button that
// asks how many. the search stays when the server sends the menu again (after Clear)
public final class TrainingScreen extends Screen {
    private static final int ROW = 24;
    private static final int TOP = 66;
    private static final int BUTTON_W = 56;
    private static final int BUTTON_H = 18;
    private static final int GOLD = 0xFFD700;
    private static final int WHITE = 0xFFFFFF;
    private static final int MUTED = 0xB0B0B0;
    private static final int HEADER = 0x30FFFFFF;
    private static final int ROW_EVEN = 0x60000000;
    private static final int ROW_ODD = 0x40000000;
    private static final Function<String, Component> READABLE = LocalizationUtil::localizedOrReadable;

    private TrainingMenuS2C menu;
    private List<Row> rows = List.of();
    private String query = "";
    private EditBox search;
    private boolean typing = true;
    private int scroll;

    private TrainingMenuS2C.Foe picking;
    private EditBox howMany;

    // a section header, an opponent, or a line saying what will show up there
    private record Row(Component text, Component detail, TrainingMenuS2C.Foe foe, boolean header) {
    }

    private TrainingScreen(TrainingMenuS2C menu) {
        super(Component.translatableWithFallback("gui.dmzzero.training.title", "Time Chamber Training"));
        this.menu = menu;
        this.rows = rows(menu, "");
    }

    /** Opens the menu, or refreshes it in place when it's already open. */
    public static void show(TrainingMenuS2C menu) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TrainingScreen open) {
            open.menu = menu;
            open.rows = rows(menu, open.query);
            open.scroll = Math.min(open.scroll, open.maxScroll());
            open.refresh();
        } else {
            mc.setScreen(new TrainingScreen(menu));
        }
    }

    static List<Row> rows(TrainingMenuS2C menu, String query) {
        List<Row> out = new ArrayList<>();
        var shown = TrainingMath.filter(menu.sections(), TrainingMenuS2C.Section::foes,
                s -> TrainingText.header(s, s.foes().size(), READABLE).getString(),
                f -> TrainingText.line(f, READABLE).getString(), query);
        boolean blank = query == null || query.isBlank();
        if (blank && menu.sections().stream().noneMatch(s -> s.kind() != TrainingMenuS2C.MOBS)) {
            out.add(new Row(Component.translatableWithFallback("gui.dmzzero.training.no_bosses", "No saga bosses yet"),
                    Component.translatableWithFallback("gui.dmzzero.training.no_bosses_hint", "Finish saga quests to fight their bosses here."), null, true));
        }
        for (var s : shown) {
            out.add(new Row(TrainingText.header(s.section(), s.foes().size(), READABLE), Component.empty(), null, true));
            for (TrainingMenuS2C.Foe f : s.foes()) out.add(new Row(TrainingText.name(f), TrainingText.detail(f, READABLE), f, false));
            if (blank && s.foes().isEmpty() && s.section().kind() == TrainingMenuS2C.MOBS) {
                out.add(new Row(Component.empty(), Component.translatableWithFallback("gui.dmzzero.training.no_mobs",
                        "Whatever you beat from now on shows up here."), null, false));
            }
        }
        return out;
    }

    private void refresh() {
        typing = search != null && search.isFocused();
        rebuildWidgets();
    }

    private void searched(String value) {
        if (value.equals(query)) return;
        query = value;
        scroll = 0;
        rows = rows(menu, query);
        refresh();
    }

    private int listWidth() {
        return Math.min(width - 16, 460);
    }

    private int left() {
        return (width - listWidth()) / 2;
    }

    private int visibleRows() {
        return Math.max(1, (height - TOP - 36) / ROW);
    }

    private int maxScroll() {
        return Math.max(0, rows.size() - visibleRows());
    }

    @Override
    protected void init() {
        if (picking != null) {
            initPrompt();
            return;
        }
        int left = left(), right = left + listWidth();
        if (search == null) {
            search = new EditBox(font, left, 42, listWidth(), 16, Component.translatableWithFallback("gui.dmzzero.training.search", "Search"));
            search.setMaxLength(64);
            search.setHint(Component.translatableWithFallback("gui.dmzzero.training.search_hint", "Search by name, saga or quest"));
            search.setValue(query);
            search.setResponder(this::searched);
        }
        search.setX(left);
        search.setWidth(listWidth());
        addRenderableWidget(search);
        if (typing) setFocused(search);
        if (menu.out() > 0) {
            addRenderableWidget(Button.builder(Component.translatableWithFallback("gui.dmzzero.training.clear", "Send away"), b -> send("dmzzero training clear"))
                    .bounds(right - 80, 22, 80, 16).build());
        }
        int last = Math.min(rows.size(), scroll + visibleRows());
        for (int i = scroll; i < last; i++) {
            TrainingMenuS2C.Foe foe = rows.get(i).foe();
            if (foe == null) continue;
            int y = TOP + (i - scroll) * ROW + (ROW - BUTTON_H) / 2;
            addRenderableWidget(Button.builder(Component.translatableWithFallback("gui.dmzzero.training.fight", "Fight"), b -> fight(foe))
                    .bounds(right - BUTTON_W - 4, y, BUTTON_W, BUTTON_H).build());
        }
        addRenderableWidget(Button.builder(Component.translatableWithFallback("gui.dmzzero.training.close", "Close"), b -> onClose())
                .bounds(width / 2 - 40, height - 26, 80, 20).build());
    }

    private int room() {
        return Math.max(0, menu.max() - menu.out());
    }

    // Fight asks how many first; the box starts at the quest's count, cut to the room left
    private void fight(TrainingMenuS2C.Foe foe) {
        picking = foe;
        howMany = new EditBox(font, 0, 0, 60, 18, Component.translatableWithFallback("gui.dmzzero.training.how_many", "How many?"));
        howMany.setMaxLength(3);
        howMany.setFilter(s -> s.chars().allMatch(Character::isDigit));
        howMany.setValue(String.valueOf(Mth.clamp(foe.count(), 1, Math.max(1, room()))));
        typing = false;
        rebuildWidgets();
        howMany.moveCursorToEnd();
    }

    // more than the limit isn't asked again: the server puts out as many as there's room for
    private void spawn() {
        if (picking == null) return;
        int wanted;
        try {
            wanted = Math.max(1, Integer.parseInt(howMany.getValue()));
        } catch (NumberFormatException e) {
            wanted = 1;
        }
        String id = picking.id();
        onClose();
        send("dmzzero training fight " + wanted + " " + id);
    }

    private void back() {
        picking = null;
        howMany = null;
        typing = true;
        rebuildWidgets();
    }

    private void initPrompt() {
        int cx = width / 2, cy = height / 2;
        howMany.setX(cx - 30);
        howMany.setY(cy - 6);
        addRenderableWidget(howMany);
        setFocused(howMany);
        addRenderableWidget(Button.builder(Component.translatableWithFallback("gui.dmzzero.training.spawn", "Fight"), b -> spawn())
                .bounds(cx - 84, cy + 20, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatableWithFallback("gui.dmzzero.training.back", "Back"), b -> back())
                .bounds(cx + 4, cy + 20, 80, 20).build());
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (picking != null) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                spawn();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                back();
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private static void send(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.connection.sendCommand(command);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (picking != null) return true;
        int next = Mth.clamp(scroll - (int) Math.signum(delta) * 3, 0, maxScroll());
        if (next != scroll) {
            scroll = next;
            refresh();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        if (picking != null) {
            renderPrompt(g);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        int left = left(), width = listWidth();
        g.drawCenteredString(font, title, this.width / 2, 8, GOLD);
        g.drawString(font, TrainingText.out(menu.out(), menu.max()), left, 26, WHITE);
        int last = Math.min(rows.size(), scroll + visibleRows());
        for (int i = scroll; i < last; i++) {
            Row row = rows.get(i);
            int y = TOP + (i - scroll) * ROW;
            g.fill(left, y + 1, left + width, y + ROW - 1, row.header() ? HEADER : (i % 2 == 0 ? ROW_EVEN : ROW_ODD));
            int textW = width - 12 - (row.foe() == null ? 0 : BUTTON_W + 4);
            boolean twoLines = !row.detail().getString().isEmpty() && !row.text().getString().isEmpty();
            if (!row.text().getString().isEmpty()) {
                g.drawString(font, font.plainSubstrByWidth(row.text().getString(), textW), left + 6, twoLines ? y + 3 : y + 8, row.header() ? GOLD : WHITE);
            }
            if (!row.detail().getString().isEmpty()) {
                g.drawString(font, font.plainSubstrByWidth(row.detail().getString(), textW), left + 6, twoLines ? y + 13 : y + 8, MUTED);
            }
        }
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatableWithFallback("gui.dmzzero.training.no_match", "Nothing matches \"%s\".", query.trim()),
                    this.width / 2, TOP + 10, MUTED);
        }
        if (maxScroll() > 0) {
            g.drawCenteredString(font, Component.translatableWithFallback("gui.dmzzero.training.scroll", "%s to %s of %s, scroll for more",
                    scroll + 1, last, rows.size()), this.width / 2, height - 38, MUTED);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    // laid out like vanilla's confirm screens: the list steps aside, a question and its buttons sit on the plain background
    private void renderPrompt(GuiGraphics g) {
        int cx = width / 2, cy = height / 2;
        g.drawCenteredString(font, title, cx, 8, GOLD);
        Component ask = Component.translatableWithFallback("gui.dmzzero.training.how_many_of", "How many %s?", Component.translatable(picking.entity()));
        g.drawCenteredString(font, font.plainSubstrByWidth(ask.getString(), width - 16), cx, cy - 36, WHITE);
        g.drawCenteredString(font, Component.translatableWithFallback("gui.dmzzero.training.room", "Room for %s more (limit %s)", room(), menu.max()),
                cx, cy - 22, MUTED);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** For the autotest: types {@code query} (null leaves it) and says what the menu shows. */
    public static String probe(String query) {
        if (!(Minecraft.getInstance().screen instanceof TrainingScreen open) || open.search == null) return "no menu";
        if (query != null) open.search.setValue(query);
        StringBuilder titles = new StringBuilder();
        for (Row row : open.rows) titles.append(titles.length() == 0 ? "" : "|").append(row.text().getString());
        int foes = open.menu.sections().stream().mapToInt(s -> s.foes().size()).sum();
        return "out=" + open.menu.out() + " foes=" + foes + " rows=" + open.rows.size() + " search=" + open.search.isFocused()
                + " query=" + open.query + " titles=" + titles;
    }
}
