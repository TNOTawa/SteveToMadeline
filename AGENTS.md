# AGENTS.md

> **This file is a map, not a manual.**
> Keep it short, stable, project-specific, and mechanically actionable.
> Put branch-specific knowledge behind pointers; put enforceable rules into tests, CI, linters, architecture checks, or tool permissions.

## Sources of truth

- The current task/spec describes intended behavior.
- Existing code and tests describe current behavior and compatibility obligations.
- Project docs describe architecture, data semantics, and rationale.
- Local/nested `AGENTS.md` files may add subtree-specific facts and commands.
- If these sources conflict, do not silently pick the easiest one. Follow an explicitly documented authority rule; otherwise surface the conflict as a decision.

## Default work protocol

1. **Investigate** — inspect the relevant implementation, tests, docs, and existing call sites before assuming behavior.
2. **Verify need** — establish whether a code change is actually required. A verified **no-change** result is a valid successful outcome.
3. **Resolve uncertainty** — look up facts yourself. Ask only about consequential decisions that remain unresolved after investigation.
4. **Change narrowly** — make the smallest coherent change; reuse existing abstractions and single sources of truth.
5. **Verify independently** — run focused checks while developing, then the project QA gate before completion.
6. **Report evidence** — state what changed, what was run, what passed, what could not be run, and any remaining risk.

## Engineering invariants

- Preserve user data, backward compatibility, and stable identities unless the task explicitly changes them.
- Confirm unfamiliar APIs, units, encodings, indexes, lifetimes, and boundary semantics from code/docs/tests or a minimal experiment.
- Keep dependency direction and architecture boundaries intact.
- Prefer deterministic behavior for generators, compilers, serializers, exporters, and reports when the domain allows it.
- Treat tests, golden files, quality thresholds, and CI rules as part of the verification system. Changes to them require task intent and explicit justification.
- Prefer rules that can be mechanically checked over rules that merely ask the Agent to “be careful”.
- An inability to run a required check is a reported limitation, not an implicit pass.

## Read on demand

- **Material product/architecture ambiguity remains, or the user asks to stress-test a plan / invokes a grilling workflow**  
  → read `docs/agents/DECISIONS.md`.
- **Adding/changing tests, QA, coverage, mutation testing, golden files, or deciding whether work is complete**  
  → read `docs/agents/QUALITY.md`.
- **Changing CI, permissions, sandboxes, architecture checks, agent infrastructure, or instruction layout**  
  → read `docs/agents/HARNESS.md`.
- **Need the evidence behind these rules**  
  → read `docs/agents/RESEARCH.md`.
- **Working inside a subtree with its own `AGENTS.md`**  
  → read that file and treat it as the local delta; do not duplicate root guidance into it.

## 本机开发规范（强制）

- **临时文件用后即销毁**：下载包、解压中间目录、构建中转文件、调试输出用完立刻删除，不留在磁盘上。
- **不占用 C 盘**：JDK、Gradle、依赖缓存、下载、临时目录一律放在 D:/F:；任何工具默认写 C 盘时要显式改到 D:/F:。
- **能在工作区内完成的绝不离开工作区**：源码、脚本、测试数据、生成的报告默认落在仓库内；只有明确约定的外部设施（见下）才写到仓库外。
- **隔离 Minecraft 实例的 mods 目录只放最新构建产物**：每次更新先删旧版本再放新包，不堆砌历史 jar 或备份。
- **网络**：`maven.neoforged.net` 直连被阻断，只能经本机代理 `<本机代理>`；Mojang、GitHub、Gradle 官方源均可直连。`<GRADLE_USER_HOME>\gradle.properties` 已把 Mojang 域写入 nonProxyHosts，代理只承担 NeoForged 流量。
- **已固定的外部设施**（不得随意改路径）：
  - JDK 21：`<JDK 21 目录>`
  - Gradle 用户目录 / 发行版：`<GRADLE_USER_HOME>`
  - NeoForge MDK 模板：`<NeoForge MDK 模板>`
  - 隔离测试实例：`<隔离测试实例>`

## Project map

- **Current phase:** P0 技术验证完成（主窗口正交摄像机 + 2D 渲染），P1 待开始；进度与结论见 [docs/DEVLOG.md](docs/DEVLOG.md)
- **Project:** SteveToMadeline
- **Purpose:** 在 Minecraft 1.21.1 NeoForge 中以 WindowX 为前置实现 Celeste 风格二维横版移动、平面切换与玛德琳表现
- **Main entry point:** `src/main/java/...`（NeoForge mod 主类）
- **Architecture map:** 见 `初版开发计划书.txt` 第 5 节模块表
- **Build/setup:** `gradlew build`（JDK 21 + Gradle 用户目录 `<GRADLE_USER_HOME>`）
- **Fast relevant tests:** `gradlew test`（P2 起才有物理测试）
- **Full QA command:** `gradlew build`（P2 引入物理测试后替换为统一入口）
- **Release/build artifact:** `build/libs/stevetomadeline-*.jar`
- **Critical data/compatibility notes:** 锁定 [windowx-2.2.0-Beta2-1.21.1-NeoForge.jar](<windowx-2.2.0-Beta2-1.21.1-NeoForge.jar>)（SHA256 `145BE1D2E2341162453BE84E93E8D44FC122EE6614FA538F9F65AFED7ABB5A5C`）；依赖 NeoForge `[21.1.234,)`、MC `[1.21.1]`

## Completion

Completion is based on **observable evidence**, not Agent confidence.

A normal completion report should be compact and factual, for example:

```text
Changed: ...
Focused checks: ...
Full QA: ...
Golden/determinism: ...
Not run / remaining risk: ...
```
