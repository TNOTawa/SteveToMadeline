package com.stevetomadeline.client.windowx;

import com.stevetomadeline.mixin.WindowXRenderAccess;
import com.xutrain.windowx.camera.CameraRegistry;
import com.xutrain.windowx.camera.CameraRenderManager;
import com.xutrain.windowx.camera.CameraTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Headless binding for the pinned WindowX 2.2.0-Beta2 renderer. */
public final class WindowXCameraBridge {
    public static final String CAMERA_ID = "stevetomadeline:parallax";
    private static final String BINDING_ID = "stevetomadeline:parallax-background";
    private static final Object OWNER = new Object();
    private static CameraRenderManager.CameraBinding binding;
    private static CameraRegistry.Pose pose;
    private static long frame;
    private static long publishedFrame = -1;
    private static int publishedTexture;

    private WindowXCameraBridge() {
    }

    public static boolean isRendering() {
        return CAMERA_ID.equals(CameraRenderManager.getCurrentCameraId());
    }

    public static boolean isAnyRendering() {
        return CameraRenderManager.isRendering();
    }

    public static float renderDistance() {
        return CameraRenderManager.getCurrentRenderBlocks();
    }

    public static int prepare(Vec3 position, float yaw, int width, int height, float halfHeight,
                              float renderDistance, boolean invalidate) {
        CameraRenderManager manager = CameraRenderManager.getInstance();
        if (binding == null || WindowXRenderAccess.stevetomadeline$bindings().get(BINDING_ID) != binding) {
            stop();
            manager.setWindowCamera("", BINDING_ID, CAMERA_ID, "fit");
            binding = WindowXRenderAccess.stevetomadeline$bindings().get(BINDING_ID);
            binding.sky = true;
            binding.clouds = false;
            binding.weather = false;
            binding.fog = true;
            CameraRegistry.update(OWNER, CAMERA_ID, partialTick -> pose);
        }
        pose = new CameraRegistry.Pose(position, yaw, 0.0f, new Quaternionf());
        manager.changeCamera("", BINDING_ID, CAMERA_ID, 0.0, 0.0,
                halfHeight, width, height, 1, renderDistance, "ortho");
        CameraTexture texture = binding.texture;
        int result = !invalidate && publishedFrame == frame && texture != null && texture.isValid()
                && texture.getWidth() == width && texture.getHeight() == height
                && texture.getColorTextureId() == publishedTexture ? publishedTexture : 0;
        frame++;
        return result;
    }

    /** updateWin runs only after CameraTexture.publish has completed successfully. */
    public static void onPublished(CameraRenderManager.CameraBinding rendered) {
        if (rendered == binding && rendered.texture != null && rendered.texture.isValid()) {
            publishedFrame = frame;
            publishedTexture = rendered.texture.getColorTextureId();
        }
    }

    public static void stop() {
        CameraRegistry.unregister(OWNER);
        if (binding != null) {
            // Public removal matches native windows; a headless binding has none.
            if (WindowXRenderAccess.stevetomadeline$bindings().remove(BINDING_ID, binding)) {
                WindowXRenderAccess.stevetomadeline$destroyBinding(binding);
            }
            binding = null;
        }
        pose = null;
        frame = 0;
        publishedFrame = -1;
        publishedTexture = 0;
    }
}
