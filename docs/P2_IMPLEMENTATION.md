# P2/P3 移植状态与验收

状态：2026-11-05 用户实机确认运动机制和手感通过。音画与抓取模式扩展见
[PRESENTATION_IMPLEMENTATION.md](PRESENTATION_IMPLEMENTATION.md)，该扩展仍待实机验收。
实现依据为 `solstice23/desk-madeline` 的提交 `8404a89e2a90cbafe35a97ef40f640083afd3c4c`，
参考了 `Player.cs`、`KeyBindings.cs`、`IntegerAxis.cs`、`CelesteAtlas.cs`、`SpriteBank.cs`、
`HairMeta.cs` 与 `PetWindow.cs`。它是 Minecraft 静态方块世界中的 Normal/Climb/Dash 适配，
**不宣称与原游戏或 desk-madeline 全状态、逐帧、音画完全一致**。

## 世界尺度与判定

- 唯一尺度：16 Celeste px = 1 Minecraft 格。每 Minecraft 逻辑 tick 执行三个 1/60 秒子步，
  保留 float 计时器、按轴整数像素取整与子像素余量；Minecraft 三维碰撞形状用于每个像素步的障碍查询。
- 站立实体碰撞箱 X/Y/Z = 0.5/0.6875/0.5 格（8×11×8 px）；蹲姿及 Demo Dash
  X/Y/Z = 0.5/0.375/0.5 格（8×6×8 px）。实体尺寸、眼高、服务端碰撞和客户端预测同步切换。
- 站立受伤判定 8×9×8 px（Y = 0.5625 格），蹲姿 8×4×8 px（Y = 0.25 格）。
  与各自碰撞箱顶部对齐，底部排除 2 px；近战 `getHitbox` 和通用 `ProjectileUtil`
  的射线/飞行物窄相检测使用受伤判定，方块碰撞仍使用完整实体碰撞箱。
  部分模组直接使用 `getBoundingBox()` 作伤害检测，不在这层兼容范围内。
- R 进入检查吸附路径，手动退出按原版站立/蹲伏/爬行姿态顺序检查空间，全部受阻时保留模式。
  死亡、传送和不支持状态沿用原有退出策略。Tab 转向保持 P1 的锚点与缓动规则。

## 动作与输入

- 基础跑跳、变高跳、土狼时间、缓冲、抓墙/贴墙滑行/墙跳、体力与冲刺次数；冲刺冻结、
  延迟取瞄准、下斜冲落地转换、Super/Hyper、Wavedash、Ultra、Wallbounce、墙速保留、
  墙角和顶角修正、半体力 wallboost 返还与中性跳均走普通状态机条件，不另加专用技巧模式。
- 客户端每 20 Hz 逻辑 tick 送三份按时间边界采样的 60 Hz 输入。按键逐个记录边沿：
  同一动作的一个键持续按住、第二个键新按下仍可触发；GUI/失焦清空待处理边沿。
  服务端每 tick 最多消费一个带 epoch/sequence 的输入包，回传完整状态；客户端重放未确认输入。
  网络断流最多保留方向 5 tick。协议版本为 4，需同版本客户端与服务端。
- 游戏内按 **F8** 或模组列表打开设置；每个动作四个键位，可混用键盘、鼠标与手柄，
  可分别清除、恢复默认并显示冲突。R/Tab 可在同一界面重绑，模式操作优先。
  键盘默认 A/D、W/S、空格/C 跳跃、X 冲刺、左 Shift/Z/V 抓墙、R 进入退出、Tab 切平面；
  手柄默认左摇杆/方向键移动、A 跳跃、X 冲刺、RT 抓墙、Start 进出横版模式、LB 顺转平面；
  Demo Dash 默认未绑定。手柄只驱动横版模式动作与 R/Tab 快捷操作，原版模式行走、视角和
  界面导航仍用键盘/鼠标。设置页手柄分页可开关手柄、调整摇杆死区并查看设备与当前输入。
  新配置在实例 `config/stevetomadeline-controls.json` 原子写入（版本 2）；旧版本 1 只在
  空槽位补入手柄默认值。首次启动迁移旧 `options.txt` 单键设置。
  原版键位映射现只保留 F8 设置入口；不读取 Celeste `settings.celeste`。

## P3 角色

- 客户端在后台从本机 Celeste 安装目录的 `Content/Graphics/Atlases/Gameplay.meta`、
  对应 `.data` 页和 `Content/Graphics/Sprites.xml` 解码玩家帧、动画顺序与头发元数据；
  使用动态纹理渲染站立、跑、跳、冲、攀爬等动作，头发随冲刺次数变色与摆动。
  可在设置界面选目录、自动检测、重载或关闭角色替换。
- 缺少安装时使用本模组代码生成的原创中性占位角色。JAR 不含 Celeste 原版贴图、音频或
  desk-madeline 的其他美术；参考 MIT 许可证位于 `META-INF/licenses/desk-madeline.txt`。

## 验证与边界

`gradlew build --offline --no-daemon --console=plain`：**81 项自动测试通过**，0 失败、0 跳过。
`tools/reference/README.md` 记录了直接从未修改的参考 `Player.cs` 生成 17 个场景、
2,905 帧对照数据的过程；Java 逐帧检查位置、速度、动作、体力、冲刺、碰撞姿态和七个计时器。
首轮对照发现并修复了跳跃缓冲最后一帧先递减再判断的问题；所有 17 场景的对照测试已单独通过。
其余单元测试
包含物理状态复制/包往返、Super/Hyper/Ultra、蹲姿上墙输入、咖啡跳所需的多键连续按下与墙跳窗口、
半体力 wallboost、兔子跳、反向技巧、碰撞尺寸/受伤框、图集解析与头发模拟。
本机 Celeste 安装的真实图集/XML 加载用例已执行；JAR 内容与中英文语言键另做静态核查。

未验证：实际 Minecraft 运行期 Mixin 注入、特殊方块/动态实体碰撞、网络延迟、
自定义碰撞盒的各类伤害源、视角转向时的精细输入时序、动画画面及高刷新率手感。
**上述测试不能证明和 desk-madeline 全部功能逐帧等价。** 当前不包含桌面窗口移动平台、
Celeste 特殊机关、Holdable、DreamDash 以及完整角色死亡/过场演出。
常见动作音效与粒子已在后续音画扩展中接入，详见上方链接。
原版及参考项目的蹲姿与受伤框尺寸已经移植，但 Minecraft 的地形与实体交互不可能仅凭
参数相等就自动达到原版同等手感。

用户实机检查顺序：F3+B 对比站/蹲碰撞箱，半格低洞蹲冲；跑跳与中性墙跳；
Super/Hyper/延迟 Hyper、Wavedash/Ultra、Wallbounce、角落修正；两个跳跃键的连续墙跳；
半体力返还；F8 重绑、重启持久化、资源目录切换；R/Tab、死亡/传送与多人观察者尺寸。
如有偏差，请记录按键序列、帧数、初始地形/坐标和实际表现，以便与参考逐帧比对。

以下为原 P2/P3 批次的历史产物记录，后续崩溃修复和音画扩展已替换：
`build/libs/stevetomadeline-0.1.0.jar` SHA256：
`1B45BDFF3D841DCB51291EF35154847E43D32C9D14856B6A8545D951B58EB860`。
没有部署到隔离实例；其旧 JAR 的哈希为
`B3CB861DE888C226EE2F8B63A9B59CEC6B3A32FBD03376A1DB9481FA0B887477`。
本机自动审批拒绝删除本轮临时参考克隆与对照编译输出（`blocked by policy`），
未使用替代删除方式绕过限制。压缩测试基准是有意保留的源码测试资源。
