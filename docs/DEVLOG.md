# 开发日志

## 2026-11-05 / P0 技术验证（主窗口正交摄像机 + 2D 渲染）

### 目标

按 `初版开发计划书.txt` 的 P0：在真实 Minecraft 1.21.1 世界中接管主窗口摄像机，
实现正交侧视与可玩的 2D/2.5D 观感，为 P1（R/Tab 平面切换）打基础。

### 环境（已固定）

| 用途 | 路径 |
|---|---|
| JDK 21（Temurin 21.0.13+9，SHA256 与 Adoptium 官方一致） | `<JDK 21 目录>` |
| Gradle 用户目录 / 发行版 9.2.1 | `<GRADLE_USER_HOME>` |
| NeoForge MDK 模板 | `<NeoForge MDK 模板>` |
| 隔离测试实例（NeoForge 21.1.265） | `<隔离测试实例>` |
| 仓库 | `<仓库目录>` |

网络：`maven.neoforged.net` 仅能经本机代理 `<本机代理>`；Mojang/GitHub/Gradle 官方源直连可用。

### 结论先行

1. **主窗口接管可行**：`Camera.setup` + `GameRenderer.getProjectionMatrix` 两个 mixin 即可把主窗口
   换成侧视正交摄像机；不需要 WindowX 的独立窗口。
2. **WindowX 正交语义**：`CameraOptions.fov` 在 ORTHOGRAPHIC 下是**正交半高（方块）**，
   直接写进 `Matrix4f.ortho(-w, w, -h, h, 0.05, far)`。我们已把该语义沿用到主窗口投影。
3. **WindowX 会清天空像素**：`CameraSkyMask` 用深度纹理把“远平面”像素 alpha 置 0。
   自定义叠加层若不写深度会被整体抹掉——这是早期 2D 全黑的根因之一。
4. **两趟渲染不可在关卡渲染中途执行**：在 `RenderLevelStageEvent` 里递归调用
   `LevelRenderer.renderLevel` 会让游戏卡死（未响应），分层视差因此挂起。

### 当前实现

**模式**：R 键循环。默认 `原版 ↔ 2D`；`enableIsometric = true` 时加入 2.5D 彩蛋。

**摄像机（两种模式共用）**
- `CameraMixin`：每帧 TAIL 写入我们的位姿；`detached = true` 强制玩家模型渲染。
- `CameraController`：侧视方向吸附 90°；2.5D 下俯 12° 并沿视线做方块检测防埋入。
- `GameRendererMixin`：返回正交投影；2D 下近平面 = `distance - sliceHalfThickness`，
  切掉相机与玩家之间的方块；2.5D 近平面 0.05。
- 摄像机模式取消手持物品（`RenderHandEvent`）、准星（`RenderGuiLayerEvent`）、视角摇晃（`bobView`）。

**2D 渲染（方案 A）**
- 背景：普通正交世界渲染，原版天空盒/昼夜保留；雾由 `RenderFog` 接管，默认 96→448 格，
  雾色固定为当前天空色（入水不再变色）。
- 前景：`CameraRenderHandler` 在 `AFTER_TRANSLUCENT_BLOCKS` 于玩家所在轴绘制
  **方块朝向相机的那一面**的贴图（`BakedModel.getQuads`），空气格留空透出背景；
  光照 = `max(方块光, 天光 × 太阳系数)`，环境底光 0.35（泰拉瑞亚式）。
- 实体只保留与平面距离 ≤ `sliceHalfThickness` 的。

**2.5D（彩蛋）**
- 侧视正交 + 下俯 12° + 抬高相机；普通 3D 世界渲染；默认关闭。

### 配置（`config/stevetomadeline-client.toml`）

| 键 | 默认 | 说明 |
|---|---|---|
| `cameraDistance` | 8.0 | 相机水平距离（0.5–128） |
| `orthoHalfHeight` | 10.0 | 正交半高（方块） |
| `sliceHalfThickness` | 1.0 | 2D 切片半厚度 / 实体过滤范围 |
| `fogStart` / `fogEnd` | 96 / 448 | 摄像机模式雾距离 |
| `cameraFov` | 0.0 | >0 时改为透视（实验，非目标方案） |
| `enableIsometric` | false | 2.5D 彩蛋 |
| `layeredParallax` | false | 分层视差（实验，当前会卡死，已挂起） |
| `parallaxStrength` | 0.0 | 运动伪视差（已否定，默认关闭） |

**写入配置注意**：必须 UTF-8 **无 BOM**，否则 NeoForge 解析失败并重建默认配置。

### 构建 / 部署

```powershell
$env:JAVA_HOME='<JDK 21 目录>'
$env:GRADLE_USER_HOME='<GRADLE_USER_HOME>'
cd <仓库目录>
.\gradlew.bat build --console=plain
# 产物 build/libs/stevetomadeline-0.1.0.jar，复制到实例 mods/ 并先删旧版本
```

### 已知问题 / 挂起项

1. **分层视差**：运动伪视差观感错误已弃用；真正的分层视差（滞后相机 + 离屏远景）在
   `RenderLevelStageEvent` 中递归渲染会卡死。下一步按“WindowX 相机纹理”方案：
   用第二个 WindowX camera binding 渲染滞后视角，读取 `CameraTexture.getColorTextureId()` 合成。
2. **2.5D 相机仍可能切进方块**：射线检测已加，但正交切片边缘仍会漏出远景；待视差/遮挡方案统一处理。
3. **树叶/藤蔓**：前景层用 alpha 混合而非 alpha 裁剪，边缘略软。
4. `WindowXCameraBridge` 已不再创建独立窗口，WindowX 目前只作为依赖保留。

### 下一步（P1）

1. Tab 顺时针旋转 90°：朝向吸附、锚点重建、摄像机同步。
2. 坐标锁定：进入模式时记录锚点轴坐标，移动只在平面内生效。
3. R 退出无损恢复：原版摄像机、移动、渲染全部还原，位置不重置。
4. 冲刺/爬墙等不可中断状态下的转向缓存。
5. 之后再做：分层视差（WindowX 纹理方案）、玛德琳渲染、Celeste 资源加载。

### 提交记录（P0 阶段）

```
128bd8c chore: initialize NeoForge 1.21.1 project skeleton with WindowX dependency
4925f4c docs(agents): record proxy routing for NeoForged maven
c217761 feat(p0): add WindowX orthographic camera prototype bound to R
b3370c3 fix(p0): ortho half-height config, camera collision clamp and live sliders
eddd421 refactor(config): register camera options as CLIENT config
41b5c90 feat(render): add 2D slice mode and 2.5D tilted mode with R cycling
...
9c5a0ff fix(mixin): make CameraPoseAccess a pure accessor interface
```

（完整历史见 `git log`。）
