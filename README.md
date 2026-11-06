# SteveToMadeline

> Minecraft 1.21.1 · NeoForge · 把方块世界变成《蔚蓝》风格的二维横版平台跳跃

[![Build](https://github.com/TNOTawa/SteveToMadeline/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/TNOTawa/SteveToMadeline/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/TNOTawa/SteveToMadeline?label=release)](https://github.com/TNOTawa/SteveToMadeline/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

**SteveToMadeline** 让 Minecraft 拥有 Celeste 式的二维横版操作：按 `R` 进入横版模式，
`Tab` 旋转运动平面，用跑、跳、冲刺、墙跳、攀爬和游泳穿越原版地形。角色可从本机 Celeste
安装读取图集与音效；缺少游戏资源时使用原创占位角色并静音。

> [!IMPORTANT]
> 本模组是**纯许愿式编程**作品：目标与需求由作者提出，主要代码由 AI 编程助手完成。
> 项目已结束，**后续不接受反馈，也不提供维护、兼容性更新或问题解答**。
> 欢迎使用你手里的 AI 继续完善；本项目以 **MIT** 协议开源，你可以自由修改并发布自己的版本。

---

## 目录

- [项目状态](#项目状态)
- [功能](#功能)
- [环境要求](#环境要求)
- [安装](#安装)
- [快速上手](#快速上手)
- [操作](#操作)
- [设置界面](#设置界面)
- [配置文件](#配置文件)
- [原版 Celeste 资源](#原版-celeste-资源)
- [已知限制](#已知限制)
- [从源码构建](#从源码构建)
- [项目结构](#项目结构)
- [开发文档](#开发文档)
- [许可与第三方声明](#许可与第三方声明)
- [关于本项目](#关于本项目)
- [English](#english)

## 项目状态

- 最新版本：**v0.1.0**（见 [Releases](https://github.com/TNOTawa/SteveToMadeline/releases/latest)）
- 目标平台：Minecraft **1.21.1** + NeoForge **21.1.234+**，Java 21
- 开发阶段：P0–P3 已实现，P1/P2 经作者实机验收；原计划 P4 的外部键位导入取消，改为游戏内设置；
  P5（社区 MOD 兼容）与 P6（完整联机稳定性）未实现
- 维护状态：**已完结，不维护**；欢迎 fork 后继续开发

> 本项目不是 Celeste 的完整复刻，而是在 Minecraft 静态方块世界中的动作与表现适配。
> 它不会启动 Celeste，也不会运行 Everest MOD。

## 功能

### 二维视角与运动平面

- **R 进入 / 退出**：进入时将玩家 X/Z 吸附到当前方块中心，Y 不变；退出恢复原版视角，
  不重置位置、不重建实体。
- **Tab 顺时针旋转 90°**：围绕玩家重建运动平面，摄像机以缓动过渡；空中转向会转换水平速度，
  并检查新平面的碰撞。转向中最多缓存一次输入。
- **四方向平面**：朝南/北时沿 X 轴横移，朝东/西时沿 Z 轴横移；鼠标转动不会偷偷改变运动平面。
- 进入和转向由服务端确认，失败时原子取消并提示，不会穿墙或抬升玩家。

### Celeste 物理

- 独立的 Celeste 状态机，每个 Minecraft 逻辑 tick 执行 **3 个 1/60 秒子步**；
  16 Celeste px = 1 格，站姿碰撞箱 0.5 × 0.6875 × 0.5 格（宽 × 高 × 深）。
- 世界碰撞仍使用 Minecraft 的三维方块形状：半砖、楼梯、低天花板和墙角按实际形状检测。
- 跑动、可变高度跳跃、土狼时间、跳跃缓冲、落地恢复。
- 八方向冲刺、冲刺冻结与瞄准缓冲、次数恢复、落地/入水恢复规则。
- 贴墙滑行、墙跳、抓墙、攀爬、体力消耗与耗尽脱离。
- 游泳：水、岩浆和气泡柱在模式内统一作为游泳区域；支持水下移动、近水面浮起、
  水面跳跃、抓墙上岸和水下冲刺。模式内屏蔽原版溺水、岩浆流体灼烧、浮力与游泳姿态。
- 进阶技巧走同一套状态机规则，包括 Super、Hyper、延迟 Hyper、Wavedash、Ultra、
  反向起跳、Wallbounce、墙速保留、墙角/顶角修正、半体力 wallboost、兔子跳与 Demo Dash。

### 联机与生命周期

- **服务端权威 + 客户端预测/重放**：客户端每个逻辑 tick 发送三份按时间边界采样的 60 Hz 输入，
  服务端确认完整状态，客户端纠正并重放未确认输入。客户端与服务端需安装**同一版本**。
- 死亡/重生、传送和跨维度保留横版模式与朝向，并在新位置重建平面。
- 乘坐、睡眠、创造飞行、鞘翅、旁观和激流期间由原版接管移动，结束后恢复横版物理。
- 服务器未安装本模组时，按 R 会提示服务器不支持，而不是静默失败。
- 其他玩家能看到你的碰撞箱尺寸与横版模式状态；本模组不接管远端玩家的运动。

### 角色与音画

- 从本机 Celeste 安装读取 `Content/Graphics/Atlases/Gameplay.meta`、对应数据页和
  `Content/Graphics/Sprites.xml`，动态生成玩家贴图，支持站立、跑、跳、冲刺、攀爬、游泳等动画。
- 头发摆动、冲刺颜色变化、低体力闪红、汗滴、烟尘、冲刺残影、手套和待机小动作。
- 读取本机 Celeste 的 FMOD bank 与 64 位 FMOD 1.x 运行库，播放原版动作音效。
- **不随模组分发任何 Celeste 商业美术、音频或字体**；缺少安装时使用代码生成的原创占位角色。
- 音效仅支持 Windows x64，且需要本机安装中可用的 64 位 FMOD 库（例如 Everest 的
  `lib64-win-x64`）；缺少时静音并显示原因。

### 输入

- 键盘/鼠标与手柄各自 **4 个键位槽**，每个动作可绑定多个键；支持鼠标按键与组合键修饰。
- 手柄基于 GLFW 原生接口：标准映射手柄使用 A/B/X/Y、LB/RB、双摇杆、扳机和方向键；
  未识别设备回退到原始按钮/轴，可在设置页直接录入。
- 摇杆死区可调（5%–90%，默认 30%），按下与释放使用不同阈值避免抖动；支持热插拔。
- 抓墙支持**按住 / 反转 / 切换**三种模式。
- 手柄只驱动横版模式的动作与 R/Tab 快捷操作；原版模式行走、视角、菜单和背包仍用键盘/鼠标。

### 摄像机与画面

- 默认固定侧视 + 连续景深：整片世界使用同一透视投影，远处地形自然缩小。
- **镜头避障**：中心和八角射线检测墙面，自动收近/恢复；忽略玻璃、草、花、火把等装饰或透明方块，
  半砖、楼梯按真实占用形状判断。
- **狭窄空间淡出**：空间不足 3 格时保留镜头距离，被切去的前景在 0.25 秒内透明淡出，
  离开后淡入；保留地形仍写入深度，避免看到岩体内部。
- **保守地形裁切（ADAPTIVE）**：只在材料、体积、垂直连续性等条件都满足时把区域视为自然地形；
  洞穴、混合建筑、浮空结构和薄墙优先保留。它是启发式规则，不是建筑语义识别。
- 雾、天空与平面外实体默认保留原版行为；旧版纯 2D 覆盖可在实验选项中开启。
- 可选 **WindowX**：仅实验性的旧 `LAYERED` 离屏远景需要 WindowX 2.2.0-Beta2；
  未安装时自动回退 `DEPTH`，不影响其他功能。

### 设置与指示器

- **F8** 或模组列表中的 Config 打开自定义设置界面。
- 可分别编辑键盘/鼠标、手柄、角色、音画、指示器和相机配置。
- 左下角体力条与冲刺次数默认隐藏，可分别开启。
- 首启动迁移旧 `options.txt` 单键设置；配置文件版本 3，旧版本键位会保留或拆分迁移。

## 环境要求

| 项目 | 要求 |
|---|---|
| Minecraft | **1.21.1**（Java Edition） |
| 模组加载器 | **NeoForge 21.1.234 或更新**（开发/测试使用 21.1.265） |
| Java | **21** |
| 侧 | 客户端 + 服务端（单人世界使用内置服务端） |
| 可选 | Celeste 本机安装（角色/音效）、WindowX 2.2.0-Beta2（实验性 `LAYERED` 远景） |

多人游戏时，客户端与服务端必须安装**同一版本**的 SteveToMadeline。如果服务器未安装本模组、
或双方版本不一致，按 R 会提示服务器不支持，而不是静默失败。

## 安装

1. 安装 Minecraft 1.21.1 与 NeoForge 21.1.234+。
2. 从 [Releases](https://github.com/TNOTawa/SteveToMadeline/releases/latest) 下载
   `stevetomadeline-0.1.0.jar`，放入 `mods/`。
3. 启动游戏；单人存档无需额外配置。
4. 多人服务器：把同一个 JAR 放入服务端 `mods/` 后重启。
5. 可选：在 F8 →「角色」中选择 Celeste 安装目录，或让模组自动检测，以启用玛德琳图集与音效。
6. 可选：安装 WindowX 2.2.0-Beta2 后，实验性的 `LAYERED` 远景才会启用。

模组不会自动下载或分发 Celeste 资源；你需要自行购买并安装 Celeste 才能使用原版角色与音效。

## 快速上手

1. 进入世界，水平看向一个方向。
2. 按 **R** 进入横版模式；玩家会被吸附到当前方块中心。
3. 用 **A / D** 横移，**空格** 跳跃，**X** 冲刺，**左 Shift** 抓墙。
4. 按 **Tab** 顺时针旋转运动平面，从另一个方向观察世界。
5. 按 **R** 退出，回到原版第一人称视角。
6. 按 **F8** 打开设置，调整键位、手柄、角色、音画和相机。

## 操作

| 动作 | 默认键盘/鼠标 | 默认手柄 |
|---|---|---|
| 向左移动 / 冲刺瞄准 | `A` | 左摇杆 ← / 方向键 ← |
| 向右移动 / 冲刺瞄准 | `D` | 左摇杆 → / 方向键 → |
| 向上攀爬 / 冲刺瞄准 | `W` | 左摇杆 ↑ / 方向键 ↑ |
| 向下攀爬 / 快速下落 / 冲刺瞄准 | `S` | 左摇杆 ↓ / 方向键 ↓ |
| 跳跃 / 墙跳 | `空格`、`C` | `A` |
| 冲刺 | `X` | `X` |
| 抓墙 | `左 Shift`、`Z`、`V` | `RT`（右扳机） |
| 蹲伏冲刺（Demo Dash） | 未绑定 | 未绑定 |
| 进入 / 退出横版模式 | `R` | `Start` |
| 顺时针切换运动平面 | `Tab` | `LB` |
| 打开设置 | `F8` | 默认仅键盘/鼠标 |

- 每个动作在键盘/鼠标与手柄两组中各有 4 个槽位，可在 F8 设置中分别重绑、清除和恢复默认。
- 同一设备族内会检测冲突；模式切换（R/Tab）优先于其他动作。
- `Tab` 与原版玩家列表同键时，只在横版模式隐藏玩家列表；原版界面、聊天和 GUI 中的行为不变。
- `R` / `Tab` 只在实际游戏界面中生效，打开 GUI、聊天或失焦时不会误触发。

## 设置界面

按 **F8**，或在 NeoForge 模组列表中点 Config：

| 页签 | 内容 |
|---|---|
| 键盘 / 鼠标 | 4 槽位键位、抓取模式（按住/反转/切换）、冲突提示、清除与恢复默认 |
| 手柄 | 启用手柄、摇杆死区、重新扫描、设备与映射状态、当前按下状态、手柄键位 |
| 角色 | Celeste 安装目录、浏览、自动检测、保存并加载、显示/隐藏角色、加载状态 |
| 音画 | 原版音效开关、音量、视觉特效开关 |
| 指示器 | 体力条、冲刺次数（默认均关闭） |
| 相机与配置 | 景深、切片、雾、镜头避障、狭窄空间淡出、实验性渲染选项 |

设置会保存到 `config/stevetomadeline-controls.json`（键位/呈现选项）和
`config/stevetomadeline-client.toml`（相机/渲染配置）。

## 配置文件

### 客户端配置

文件：`config/stevetomadeline-client.toml`

| 键 | 默认 | 说明 |
|---|---|---|
| `celesteModeEnabled` | `true` | 横版模式总开关 |
| `entryOrientation` | `FACING_RIGHT` | 进入时的屏幕右侧规则；可选 `FACING_LEFT`、`CAMERA_FORWARD`、`SOUTH`、`WEST`、`NORTH`、`EAST` |
| `turnDuration` | `0.3` | Tab 转向缓动秒数（0.1–1.0） |
| `orthoHalfHeight` | `10.0` | 玩家平面视图半高（方块，2–30） |
| `sliceHalfThickness` | `1.0` | 玩家平面切片半厚（0.25–2.5） |
| `depthDistance` | `32.0` | 连续景深参考距离（16–256）；越小远景越小 |
| `cameraCollision` | `true` | 镜头自动避障 |
| `experimentalNearWallSlice` | `true` | 狭窄空间保留镜头并淡出前景 |
| `legacySkyFog` | `false` | 旧版纯 2D 天空/雾覆盖 |
| `hideOffPlaneEntities` | `false` | 隐藏平面外实体 |
| `foregroundSlice` | `false` | 旧版前景切片 |
| `fogStart` / `fogEnd` | `96` / `448` | 摄像机模式雾距离 |
| `experimentalRendering` | `false` | 总实验开关；关闭时忽略下列实验项但保留配置值 |
| `cameraDistance` | `8.0` | [实验] 相机站位距离（0.5–128） |
| `layeredParallax` | `true` | [实验] 深度效果开关 |
| `parallaxMode` | `DEPTH` | [实验] `DEPTH` / `LAYERED`；`LAYERED` 需要 WindowX |
| `terrainClipping` | `ADAPTIVE` | [实验] `ADAPTIVE` / `SLICE` / `NONE`；仅在 `foregroundSlice = true` 时生效 |
| `parallaxRatio` | `0.4` | [实验] 仅 `LAYERED`：远景水平跟随比例（0.2–0.8） |
| `parallaxStrength` | `0.0` | [实验] 旧速度伪视差强度（0–8） |
| `enableIsometric` | `false` | [实验] 2.5D 彩蛋视角 |
| `cameraFov` | `0.0` | [实验] 手动 FOV（0 为自动） |

### 服务端配置

由服务端（单人世界为内置服务端）持久化：

| 键 | 默认 | 说明 |
|---|---|---|
| `maxDashes` | `1` | 最大冲刺次数（1–2） |
| `fallDamage` | `true` | 保留原版摔落伤害；制作平台跳跃地图时可关闭 |

### 键位文件

`config/stevetomadeline-controls.json` 保存键位、手柄设置、抓取模式、音画选项、指示器和角色目录。
文件带版本号（当前 3），采用原子写入；旧版本会保留键盘绑定并补入手柄默认值。一般建议用 F8 界面修改，
不需要手工编辑。

## 原版 Celeste 资源

角色与音效是**可选**功能：

- 自动检测会扫描各磁盘下的 Steam 库（`SteamLibrary`、`Steam`、`steam/steam`）以及
  `ProgramFiles(x86)/Steam`、`ProgramFiles/Steam` 的 `libraryfolders.vdf`。
- 也可在 F8 →「角色」手动选择目录，或设置环境变量 `CELESTE_PATH`。
- 角色需要 `Content/Graphics/Atlases/Gameplay.meta`、对应数据页和 `Content/Graphics/Sprites.xml`。
- 音效需要 `Content/FMOD/Desktop` 中的 bank，以及可加载的 64 位 FMOD 1.x 库；当前仅支持 Windows x64。
- 缺少资源时：角色使用原创占位图，音效静音并显示原因；物理和画面不受影响。

仓库与发布 JAR **不包含** Celeste 的图集、音频、字体或其他商业资源。相关文件只在运行时从
你合法安装的本地副本读取，不会上传或再分发。Celeste 是 Maddy Makes Games 的作品；
本项目与 Maddy Makes Games、Extremal 无关。

## 已知限制

- **不是完整 Celeste 移植**：当前只针对 Minecraft 静态方块地形适配；不包含移动平台、Holdable、
  Dream Dash、特殊机关、关卡剧情、死亡过场和完整桌面版音画逐帧一致。
- **渲染/Mixin 兼容性**：会修改摄像机、投影、玩家物理、碰撞箱和渲染阶段；与光影包、Fabulous 图形、
  第三方摄像机/渲染模组组合可能冲突，未做系统兼容测试。
- **多人**：需要同版本的客户端和服务端；远端玩家运动不被接管；复杂延迟、丢包和跨维度场景仍需实测。
- **伤害判定**：站姿/蹲姿受伤框会改变近战与部分飞行物的命中范围；直接使用 `getBoundingBox()`
  的第三方模组不在兼容范围内。
- **手柄**：代码已接入并自动识别设备，但作者没有实体手柄，实际键位手感和热插拔仍需自行验证。
- **地形裁切**：`ADAPTIVE` 是保守启发式；纯天然材料堆成的厚实建筑仍可能被当作地形，
  混合区域也可能保留过多并遮挡玩家。遇到问题可改用 `SLICE` 或 `NONE`。
- **实验选项**：2.5D、旧天空/雾、旧分层视差等默认关闭，未经过完整实机验收。
- **音效平台**：仅 Windows x64；其他系统只影响音效，不影响移动与渲染。
- **WindowX**：仅实验性 `LAYERED` 远景使用锁定的 2.2.0-Beta2 构建；未安装时回退 `DEPTH`。
- 项目已结束，不保证未来 Minecraft/NeoForge 版本的兼容性。

## 从源码构建

需要 JDK 21（通过 `JAVA_HOME` 指定）：

```bash
git clone https://github.com/TNOTawa/SteveToMadeline.git
cd SteveToMadeline
./gradlew build
```

Windows：

```powershell
.\gradlew.bat build
```

产物：`build/libs/stevetomadeline-0.1.0.jar`。

- `./gradlew test` 运行自动测试（v0.1.0 共 156 项，覆盖物理、输入、碰撞、资源、渲染回归）。
- `./gradlew build` 是完整 QA 入口，包含编译、测试和打包。
- 开发运行需要显式加 `-PwithWindowX` 才会把 WindowX 加入运行时依赖；正常构建不加载 WindowX。
- `libs/windowx-2.2.0-Beta2-1.21.1-NeoForge.jar` 是可选第三方编译依赖，详见
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 项目结构

| 路径 | 内容 |
|---|---|
| `core/physics` | Celeste 物理状态机、参数、服务端权威模拟 |
| `core/plane` | 运动平面、朝向、转向动画与生命周期 |
| `core/mode` | 服务端平面进入/转向校验 |
| `client/render` | 玛德琳渲染、动画、头发、占位角色 |
| `client/assets` | 本机 Celeste 安装检测、图集解析 |
| `client/audio` | FMOD bank 与动作音效 |
| `client/input` | 键位、手柄、输入时间线、F8 设置界面 |
| `client`（根） | 摄像机、投影、前景裁切、镜头避障、HUD |
| `mixin` | 对 Minecraft 摄像机/渲染/玩家/流体的注入 |
| `network` | 平面与物理输入/快照 payload |
| `src/test` | JUnit 测试与参考轨迹 |
| `tools/reference` | 从未修改的 desk-madeline 源码生成对照轨迹 |
| `docs` | 开发日志、阶段计划、实现与验收说明 |

## 开发文档

- [开发日志](docs/DEVLOG.md)
- [P1 计划](docs/P1_PLAN.md)
- [P2 计划](docs/P2_PLAN.md)
- [P2/P3 实现与验收](docs/P2_IMPLEMENTATION.md)
- [音画与抓取模式实现](docs/PRESENTATION_IMPLEMENTATION.md)
- [第三方声明](THIRD_PARTY_NOTICES.md)
- [AGENTS.md](AGENTS.md)（Agent 工作约定）

## 许可与第三方声明

本项目原创代码以 **MIT License** 发布，见 [LICENSE](LICENSE)。

- **desk-madeline**（solstice23，MIT）：动作常量、Normal/Climb/Dash 结构、输入规则与表现层参考。
- **Celeste 公开源码样本**（Noel Berry，MIT）：游泳参数与水面判定参考；不覆盖商业游戏资源。
- **WindowX**（XuTrain）：可选第三方编译依赖；仓库内含未修改的第三方 JAR，权利归其作者，
  不属本项目 MIT 授权范围。
- 本项目**不包含** Celeste、desk-madeline、Cateline 或其他第三方模组的美术、音频和字体资源。

完整归属、许可证文本与“不包含商业资源”的说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
如果你想发布自己的修改版，请保留 MIT 许可证和第三方声明，并自行确认你新增内容的授权。

## 关于本项目

这是一次**纯许愿式编程**的实践：作者把想要的效果描述出来，由 AI 编程助手完成主要实现、
调试和文档。它已经结束，不再接受 issue、反馈或功能请求，也不会保证后续维护。

但它以 **MIT** 协议开源。你可以：

- 自由阅读、修改、分发本项目；
- 用你手里的 AI 继续完善它；
- 发布自己的版本，或把它移植到新的 Minecraft/NeoForge 版本；
- 在保留 MIT 许可证与第三方声明的前提下商用或再发布。

Fork 与修改版由各自的发布者负责，与原作者的“已完结、不维护”声明无关。

## English

**SteveToMadeline** is a Minecraft **1.21.1 / NeoForge** mod that turns the game into a
**Celeste-style 2D side-scrolling platformer**. Press **R** to enter the side-scrolling mode,
**Tab** to rotate the movement plane, and use Celeste-inspired running, jumping, dashing,
wall jumps, climbing, and swimming through vanilla terrain.

- Server-authoritative 60 Hz physics with client prediction; client and server need the same version.
- Keyboard/mouse and native GLFW gamepad support with four binding slots per action.
- Optional local Celeste installation for Madeline sprites, hair, particles, and FMOD sound effects;
  no commercial assets are bundled. A generated placeholder character is used otherwise.
- Camera collision avoidance, near-wall foreground fade, adaptive terrain clipping, and optional
  WindowX-based layered parallax (legacy/experimental).
- In-game settings via **F8**; client config in `config/stevetomadeline-client.toml`,
  bindings in `config/stevetomadeline-controls.json`.

Requires Minecraft 1.21.1, NeoForge 21.1.234+, and Java 21. Install the JAR in `mods/` on both
client and server. This is not a complete Celeste port and does not include Celeste's commercial
assets.

**License:** MIT (original code). See [LICENSE](LICENSE) and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

**Status:** This is a wish-driven, AI-assisted hobby project. It is finished and
**unmaintained**; no feedback, support, or compatibility updates will be provided. You are welcome
to fork it, improve it with your own AI tools, and publish your own version under the MIT License.
