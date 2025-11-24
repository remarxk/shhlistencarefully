package com.remarxk.shhlistencarefully;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

@EventBusSubscriber
public class Listen {
    private static final List<SoundInfo> soundInfos = new ArrayList<>();

    // 缓存
    private static final Stack<SoundInfo> soundInfoPool = new Stack<>();

    private static final ResourceLocation ECHO_TEX =
            ResourceLocation.tryBuild(ShhListencarefully.MODID, "textures/gui/sound_gui.png");

    private static boolean listening = false;
    private static boolean inListen = false;
    private static long startListenTime = 0;

    private static final int FRAME_WIDTH = 32;
    private static final int FRAME_HEIGHT = 32;
    private static final int FRAME_COUNT = 5;
    private static final int FRAME_DURATION = 10; // 每帧持续 tick 数

    private static int tick = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event){
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            listening = false;
            inListen = false;
            return;
        }

        LocalPlayer player = mc.player;

        if (player.isCrouching()) {
            if (!listening){
                startListenTime = mc.level.getGameTime();
                listening = true;
            }

            if (mc.level.getGameTime() - startListenTime >= Config.TIME.get()){
                inListen = true;
            }
        } else {
            listening = false;
            inListen = false;
        }
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundSourceEvent event){
        if (!inListen){
            return;
        }

        if (soundInfos.size() >= Config.MAX_NUM.get()){
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null){
            return;
        }

        Vec3 playerPos = mc.player.getPosition(mc.getTimer().getGameTimeDeltaPartialTick(true));

        SoundInstance sound = event.getSound();

        double x = sound.getX();
        double y = sound.getY();
        double z = sound.getZ();

        double dis = Config.MAX_DISTANCE.get();
        if (playerPos.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()) >= dis * dis) {
            return;
        }

        SoundSource source = sound.getSource();
        if (!(source == SoundSource.HOSTILE || source == SoundSource.NEUTRAL || source == SoundSource.PLAYERS)) {
            return;
        }

        long currentTime = mc.level.getGameTime();
        for (SoundInfo existing : soundInfos) {
            if (existing.pos.distanceToSqr(x, y ,z) < 1.0d && currentTime - existing.time < 10) {
                return;
            }
        }

        SoundInfo soundInfo;
        if (!soundInfoPool.empty()) {
            soundInfo = soundInfoPool.pop();
        }
        else {
            soundInfo = new SoundInfo();
        }

        soundInfo.pos = new Vec3(x, y, z);
        soundInfo.time = mc.level.getGameTime();
        soundInfos.add(soundInfo);
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        Vec3 playerPos = mc.player.getPosition(event.getPartialTick().getGameTimeDeltaPartialTick(true));

        GuiGraphics guiGraphics = event.getGuiGraphics();

        // 当前帧索引
        int frameIndex = (tick / FRAME_DURATION) % FRAME_COUNT;

        float minScale = 0.1f;
        float maxScale = 1f;
        double maxDis = Config.MAX_DISTANCE.get();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        PoseStack poseStack = guiGraphics.pose();

        // 遍历所有实体
        for (int i = soundInfos.size() - 1; i >= 0; i--) {
            SoundInfo info = soundInfos.get(i);

            // 世界坐标 → 屏幕坐标
            Vector3f screen = worldToScreen(info.pos.x, info.pos.y, info.pos.z);

            // 不可见（背后）
            if (Float.isNaN(screen.x))
                continue;

            poseStack.pushPose();

            int x = (int)screen.x;
            int y = (int)screen.y;

            int drawX = - FRAME_WIDTH / 2; // 左移半宽
            int drawY = - FRAME_HEIGHT / 2; // 上移半高

            double distance = playerPos.distanceTo(info.pos);
            float dynamicScale;
            if (distance <= 0.1d) {
                dynamicScale = maxScale;
            } else {
                dynamicScale = minScale + (maxScale - minScale) * (float) Math.pow(1 - distance / maxDis, 2);
            }

            poseStack.translate(x, y, 0);
            poseStack.scale(dynamicScale, dynamicScale, 1.0f);

            // 绘制当前帧
            guiGraphics.blit(
                    ECHO_TEX,
                    drawX, drawY,
                    frameIndex * FRAME_WIDTH, 0,
                    FRAME_WIDTH, FRAME_HEIGHT,
                    FRAME_WIDTH * FRAME_COUNT, FRAME_HEIGHT
            );

            poseStack.popPose();

            if (mc.level.getGameTime() - info.time > 20) {
                soundInfoPool.push(soundInfos.remove(i));
            }
        }

        RenderSystem.disableBlend();

        tick++;
    }

    public static Vector3f worldToScreen(double worldX, double worldY, double worldZ) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();

        // ====== 1. 世界坐标 → 相机局部坐标 ======
        Vec3 cam = camera.getPosition();

        float x = (float)(worldX - cam.x);
        float y = (float)(worldY - cam.y);
        float z = (float)(worldZ - cam.z);

        // ====== 2. 相机旋转（四元数） ======
        Quaternionf rotation = camera.rotation().conjugate(new Quaternionf());

        Vector3f local = new Vector3f(x, y, z);
        rotation.transform(local); // 应用相机旋转

        // ====== 3. 获取 Projection 矩阵 ======
        Matrix4f projection = mc.gameRenderer.getProjectionMatrix(mc.options.fov().get());

        // 推入裁剪空间
        Vector4f clip = new Vector4f(local.x, local.y, local.z, 1.0f);
        clip.mul(projection);

        // 在背后（W < 0） → 不可见
        if (clip.w <= 0.0f) {
            return new Vector3f(Float.NaN, Float.NaN, -1);
        }

        // ====== 4. 归一化坐标 (NDC) ======
        clip.div(clip.w);

        // ====== 5. NDC → 屏幕坐标 ======
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        float sx = (clip.x * 0.5f + 0.5f) * sw;
        float sy = (clip.y * -0.5f + 0.5f) * sh;

        return new Vector3f(sx, sy, clip.z);
    }

    static class SoundInfo {
        public long time;
        public Vec3 pos;
    }
}
