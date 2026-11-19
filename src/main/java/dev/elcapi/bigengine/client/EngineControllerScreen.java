package dev.elcapi.bigengine.client;

import dev.elcapi.bigengine.BigEngine;
import dev.elcapi.bigengine.content.blockentity.EngineControllerBlockEntity;
import dev.elcapi.bigengine.engine.EngineSettings;
import dev.elcapi.bigengine.network.EngineControlPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Tuning screen of the Engine Control Unit. Shift-click +/- for bigger steps. */
public class EngineControllerScreen extends Screen {
    private static final String P = "gui." + BigEngine.MOD_ID + ".";
    private static final int W = 300, H = 236, PER_PAGE = 4;

    private final BlockPos pos;
    private int left, top;
    private int page;
    private final List<Runnable> labelUpdaters = new ArrayList<>();
    private final List<ValueLabel> valueLabels = new ArrayList<>();

    private record ValueLabel(int x, int y, Supplier<Component> text) {}

    public EngineControllerScreen(BlockPos pos) {
        super(Component.translatable(P + "title"));
        this.pos = pos;
    }

    @Nullable
    private EngineControllerBlockEntity be() {
        if (minecraft == null || minecraft.level == null) return null;
        return minecraft.level.getBlockEntity(pos) instanceof EngineControllerBlockEntity e ? e : null;
    }

    private int setting(int key) {
        EngineControllerBlockEntity e = be();
        return e == null ? 0 : e.settings.get(key);
    }

    private void send(int action, int key, int value) {
        PacketDistributor.sendToServer(new EngineControlPayload(pos, action, key, value));
    }

    private void set(int key, int value) {
        EngineControllerBlockEntity e = be();
        if (e != null) e.settings.set(key, value, 256); // optimistic local update, server clamps for real
        send(EngineControlPayload.SET, key, value);
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        labelUpdaters.clear();
        valueLabels.clear();

        int y = top + 64;
        stepper(y, "throttle", EngineSettings.THROTTLE, 5, v -> v + " %");
        stepper(y += 16, "idle", EngineSettings.IDLE_RPM, 2, v -> v + " RPM");
        stepper(y += 16, "max_rpm", EngineSettings.MAX_RPM, 4, v -> v + " RPM");
        stepper(y += 16, "fuel_limit", EngineSettings.FUEL_LIMIT, 5, v -> v + " %");
        stepper(y += 16, "afr", EngineSettings.AFR, 1, v -> String.format(Locale.ROOT, "%.1f : 1", v / 10.0));
        stepper(y += 16, "ignition", EngineSettings.IGNITION, 1, v -> v + "° BTDC");
        toggle(left + 8, y += 18, 140, () -> Component.translatable(P + "redstone", onOff(setting(EngineSettings.REDSTONE) != 0)),
                () -> set(EngineSettings.REDSTONE, setting(EngineSettings.REDSTONE) == 0 ? 1 : 0));
        toggle(left + 8, y += 20, 140, () -> Component.translatable(P + "direction",
                        Component.translatable(P + (setting(EngineSettings.REVERSE) != 0 ? "reverse" : "forward"))),
                () -> set(EngineSettings.REVERSE, setting(EngineSettings.REVERSE) == 0 ? 1 : 0));

        // big buttons
        int by = top + H - 24;
        toggle(left + 8, by, 70, () -> {
            EngineControllerBlockEntity e = be();
            return Component.translatable(P + (e != null && e.isRunning() ? "stop" : "start"));
        }, () -> {
            EngineControllerBlockEntity e = be();
            send(e != null && e.isRunning() ? EngineControllerBlockEntity.ACTION_STOP : EngineControllerBlockEntity.ACTION_START, 0, 0);
        });
        toggle(left + 82, by, 96, () -> {
            EngineControllerBlockEntity e = be();
            return Component.translatable(P + (e != null && e.isAssembled() ? "disassemble" : "assemble"));
        }, () -> {
            EngineControllerBlockEntity e = be();
            send(e != null && e.isAssembled() ? EngineControllerBlockEntity.ACTION_DISASSEMBLE : EngineControllerBlockEntity.ACTION_ASSEMBLE, 0, 0);
        });
        toggle(left + 182, by, 110, () -> Component.translatable(P + "auto_time"),
                () -> send(EngineControllerBlockEntity.ACTION_AUTO_TIME, 0, 0));

        // cylinders
        int cx = left + 156;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> page = Math.max(0, page - 1))
                .bounds(cx, top + 48, 14, 12).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            EngineControllerBlockEntity e = be();
            int pages = e == null ? 1 : Math.max(1, (e.getCylinderCount() + PER_PAGE - 1) / PER_PAGE);
            page = Math.min(pages - 1, page + 1);
        }).bounds(left + W - 22, top + 48, 14, 12).build());
        for (int row = 0; row < PER_PAGE; row++) {
            final int r = row;
            int ry = top + 64 + row * 36;
            toggle(cx, ry + 10, 30, () -> Component.translatable(P + (setting(cylKey(r, EngineSettings.CYL_ENABLED)) != 0 ? "on" : "off")),
                    () -> { if (cylExists(r)) set(cylKey(r, EngineSettings.CYL_ENABLED), setting(cylKey(r, EngineSettings.CYL_ENABLED)) == 0 ? 1 : 0); });
            toggle(cx + 32, ry + 10, 44, () -> Component.translatable(P + "turn", setting(cylKey(r, EngineSettings.CYL_SECOND_TURN)) != 0 ? 2 : 1),
                    () -> { if (cylExists(r)) set(cylKey(r, EngineSettings.CYL_SECOND_TURN), setting(cylKey(r, EngineSettings.CYL_SECOND_TURN)) == 0 ? 1 : 0); });
            addRenderableWidget(Button.builder(Component.literal("-"), b -> { if (cylExists(r)) set(cylKey(r, EngineSettings.CYL_TRIM), setting(cylKey(r, EngineSettings.CYL_TRIM)) - step(1)); })
                    .bounds(cx + 78, ry + 10, 12, 12).build());
            addRenderableWidget(Button.builder(Component.literal("+"), b -> { if (cylExists(r)) set(cylKey(r, EngineSettings.CYL_TRIM), setting(cylKey(r, EngineSettings.CYL_TRIM)) + step(1)); })
                    .bounds(cx + 124, ry + 10, 12, 12).build());
            valueLabels.add(new ValueLabel(cx + 107, ry + 12, () -> cylExists(r)
                    ? Component.literal(String.format(Locale.ROOT, "%+d°", setting(cylKey(r, EngineSettings.CYL_TRIM)))) : Component.empty()));
        }
    }

    private int cylIndex(int row) {
        return page * PER_PAGE + row;
    }

    private int cylKey(int row, int field) {
        return EngineSettings.CYL_BASE + cylIndex(row) * EngineSettings.CYL_STRIDE + field;
    }

    private boolean cylExists(int row) {
        EngineControllerBlockEntity e = be();
        return e != null && cylIndex(row) < Math.min(e.getCylinderCount(), EngineSettings.MAX_CYLINDERS);
    }

    private static int step(int base) {
        return hasShiftDown() ? base * 10 : base;
    }

    private static Component onOff(boolean b) {
        return Component.translatable(P + (b ? "on" : "off"));
    }

    private void stepper(int y, String name, int key, int stepSize, java.util.function.IntFunction<String> fmt) {
        valueLabels.add(new ValueLabel(left + 8, y + 2, () -> Component.translatable(P + name)));
        addRenderableWidget(Button.builder(Component.literal("-"), b -> set(key, setting(key) - step(stepSize))).bounds(left + 74, y, 12, 12).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> set(key, setting(key) + step(stepSize))).bounds(left + 136, y, 12, 12).build());
        valueLabels.add(new ValueLabel(left + 111, y + 2, () -> Component.literal(fmt.apply(setting(key)))));
    }

    private void toggle(int x, int y, int w, Supplier<Component> label, Runnable action) {
        Button b = addRenderableWidget(Button.builder(label.get(), btn -> action.run()).bounds(x, y, w, 14).build());
        labelUpdaters.add(() -> b.setMessage(label.get()));
    }

    @Override
    public void tick() {
        super.tick();
        if (be() == null) {
            onClose();
            return;
        }
        labelUpdaters.forEach(Runnable::run);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        EngineControllerBlockEntity e = be();
        if (e == null) return;

        g.drawString(font, title, left + 8, top + 6, 0xFFE0A040);
        Component status = Component.translatable("status." + BigEngine.MOD_ID + "." + e.getStatus().name().toLowerCase(Locale.ROOT));
        g.drawString(font, status, left + W - 8 - font.width(status), top + 6,
                e.getStatus() == EngineControllerBlockEntity.Status.RUNNING ? 0xFF55FF55 : 0xFFFFDD55);

        double rpm = e.getRpm();
        float su = (float) (e.getCapacityPerRpm() * Math.min(256, rpm));
        g.drawString(font, Component.translatable(P + "readout1", fmt0(rpm), fmt0(su), fmt0(e.getLoad() * 100)), left + 8, top + 20, 0xFFFFFFFF);
        g.drawString(font, Component.translatable(P + "readout2", fmt0(e.getTemperature()), fmt0(e.getAvgOil() * 100),
                fmt0(e.getFuelStored()), fmt0(e.getBalance() * 100)), left + 8, top + 31, 0xFFCCCCCC);
        Component err = e.getStructureError();
        if (err != null) g.drawString(font, err.copy().withStyle(ChatFormatting.RED), left + 8, top + 42, 0xFFFF5555);
        else if (e.isKnocking()) g.drawString(font, Component.translatable(P + "knock").withStyle(ChatFormatting.GOLD), left + 8, top + 42, 0xFFFFAA00);

        // cylinder panel
        int cx = left + 156;
        int pages = Math.max(1, (e.getCylinderCount() + PER_PAGE - 1) / PER_PAGE);
        Component header = Component.translatable(P + "cylinders", e.getCylinderCount(), page + 1, pages);
        g.drawString(font, header, cx + 18, top + 50, 0xFFE0A040);
        for (int row = 0; row < PER_PAGE; row++) {
            int i = cylIndex(row);
            if (i >= e.getCylinderCount()) continue;
            int ry = top + 64 + row * 36;
            g.drawString(font, Component.translatable(P + "cylinder", i + 1, e.getCylinderPhase(i),
                    fmt0(e.getCylinderOil(i) * 100)), cx, ry, 0xFFDDDDDD);
        }

        for (ValueLabel v : valueLabels) {
            Component t = v.text.get();
            int x = v.x;
            // centre the numeric labels between their -/+ buttons
            if (v.x != left + 8) x = v.x - font.width(t) / 2;
            g.drawString(font, t, x, v.y, 0xFFFFFFFF);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + W, top + H, 0xE0202428);
        g.renderOutline(left, top, W, H, 0xFF8A6A3A);
        g.fill(left + 152, top + 46, left + 153, top + H - 30, 0xFF5A4A2A);
    }

    private static String fmt0(double d) {
        return String.format(Locale.ROOT, "%.0f", d);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
