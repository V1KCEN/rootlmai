package com.example.examplemod.rootimpact;

import com.example.examplemod.RootImpactConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * HUD-оверлей для отображения текущего веса игрока.
 * <p>
 * Позиция и масштаб настраиваются через {@link RootImpactConfig}:
 * <ul>
 *   <li>{@code hudX} — отступ от правого края экрана (пиксели)</li>
 *   <li>{@code hudY} — отступ от верхнего края экрана (пиксели)</li>
 *   <li>{@code hudScale} — масштаб текста (1.0 = 100%)</li>
 * </ul>
 * Изменения применяются без перезапуска игры.
 * <p>
 * Цвет текста меняется в зависимости от заполненности веса:
 * — зелёный (0-40%)
 * — жёлтый (41-65%)
 * — оранжевый (66-80%)
 * — красный (81-95%)
 * — тёмно-красный (96-100%+)
 */
@EventBusSubscriber(modid = "examplemod", value = Dist.CLIENT)
public class WeightHudOverlay {

    private static final int TEXT_COLOR_GREEN = 0xFF55FF55;
    private static final int TEXT_COLOR_YELLOW = 0xFFFFFF55;
    private static final int TEXT_COLOR_ORANGE = 0xFFFFAA00;
    private static final int TEXT_COLOR_RED = 0xFFFF5555;
    private static final int TEXT_COLOR_DARK_RED = 0xFFAA0000;

    /**
     * Рендерит текст с текущим весом после отрисовки всего GUI.
     * Координаты и масштаб читаются из конфига каждый кадр,
     * поэтому изменения применяются без перезапуска.
     */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        if (mc.options.hideGui) return;

        double currentWeight = PlayerWeightHandler.getCachedWeight(player);
        double maxWeight = RootImpactConfig.MAX_WEIGHT.getAsDouble();
        double ratio = currentWeight / maxWeight;

        int color = getColorForRatio(ratio);
        String text = String.format("%.1f / %.0f", currentWeight, maxWeight);

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        // Читаем конфиг каждый кадр — изменения применяются без перезапуска
        int hudX = RootImpactConfig.HUD_X.get();
        int hudY = RootImpactConfig.HUD_Y.get();
        double hudScale = RootImpactConfig.HUD_SCALE.get();

        int textWidth = mc.font.width(text);
        int x = screenWidth - textWidth - hudX;
        int y = hudY;

        // Сохраняем и применяем масштаб через матрицу GuiGraphics
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale((float) hudScale, (float) hudScale, 1.0F);
        pose.translate(-x, -y, 0);

        guiGraphics.drawString(mc.font, Component.literal(text), x, y, color, true);

        pose.popPose();
    }

    /**
     * Возвращает цвет текста в зависимости от процента заполненности веса.
     *
     * @param ratio отношение текущего веса к максимальному (0.0 – 1.0+)
     * @return цвет в формате ARGB
     */
    private static int getColorForRatio(double ratio) {
        if (ratio <= 0.40) return TEXT_COLOR_GREEN;
        if (ratio <= 0.65) return TEXT_COLOR_YELLOW;
        if (ratio <= 0.80) return TEXT_COLOR_ORANGE;
        if (ratio <= 0.95) return TEXT_COLOR_RED;
        return TEXT_COLOR_DARK_RED;
    }
}
