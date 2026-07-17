# CrossAnywhere Fabric

[English](README.md)

CrossAnywhere 的 Fabric 服务端移植版，目标版本为 Minecraft 26.2、Fabric Loader 0.19.3、Fabric API 0.154.2 和 Java 25。客户端无需安装本 Mod。

## 功能

- 个人与全局传送点，保留位置、朝向和描述
- TPA、TPAHere、接受、拒绝、取消和直传允许列表
- `/back` 与死亡位置记录
- 世界白名单、跨维度控制、分类冷却
- 经验与物品消耗，支持 Custom Model Data
- 危险位置确认与附近安全位置搜索
- 使用异步区块加载的随机传送 `/rtp`、`/tpr`、`/r`
- 中英文消息、可点击列表和 TPA 按钮
- MCDR STP JSON 导入
- `/ca`、`/stp` 及原 Paper 版快捷命令

## 构建

```powershell
.\gradlew.bat build
```

构建产物位于 `build/libs/`。

## 安装

1. 在 Minecraft 26.2 服务端安装 Fabric Loader 和 Fabric API。
2. 将 CrossAnywhere JAR 放入服务端的 `mods/` 目录。
3. 启动一次服务端以生成配置文件。
4. 按需修改 `config/crossanywherefabric/` 下的文件，然后执行 `/ca reload` 或重启服务端。

## 数据目录

服务端首次启动后会创建：

```text
config/crossanywherefabric/
  config.yml
  messages_en_US.yml
  messages_zh_CN.yml
  personal_waypoints.json
  global_waypoints.json
  tpa_allowlist.json
```

传送点和 TPA 允许列表沿用 Paper 版 JSON 结构。迁移已有服务器时，可将以下文件从 `plugins/CrossAnywhere/` 复制到 `config/crossanywherefabric/`：

- `personal_waypoints.json`
- `global_waypoints.json`
- `tpa_allowlist.json`
- `stp_uuid_map.json`、`stp_world_map.json`（使用 STP 导入时）

默认配置兼容 Paper 世界名 `world`、`world_nether`、`world_the_end`，内部会映射到 Fabric 的原版维度 ID。

## 随机传送

随机传送以玩家当前位置为中心，在配置的最小和最大半径之间按面积均匀取样：

```yaml
cooldown:
  rtp: 60

random_teleport:
  enabled: true
  min_radius: 500
  max_radius: 5000
  max_attempts: 8
```

候选区块通过 Minecraft 原生异步 future 逐个加载。插件使用高度图直接定位地表，在加载前排除重复坐标和世界边界外坐标，并限制每名玩家同时只能执行一个搜索任务。

## 权限

Fabric 版使用 Fabric Permission API。Paper 节点 `crossanywhere.xxx` 对应 Fabric 标识 `crossanywhere:xxx`，层级中的点改为 `/`，例如：

```text
crossanywhere.admin                 -> crossanywhere:admin
crossanywhere.personal.tp           -> crossanywhere:personal/tp
crossanywhere.tpa.allowlist         -> crossanywhere:tpa/allowlist
crossanywhere.cooldown.bypass       -> crossanywhere:cooldown/bypass
crossanywhere.rtp                   -> crossanywhere:rtp
```

未安装权限管理 Mod 时，个人传送点、列表、TPA、允许列表、`/back` 和随机传送默认开放；全局传送点管理、直接 `/tp`、跨维度与各类绕过权限默认要求管理员等级。

## 主要命令

命令语义与 Paper 版一致：

```text
/ca setp|setg [-f] <name> [描述...]
/ca tpp|tpg <name>
/ca delp|delg <name>
/ca list|listp|listg
/ca descp|descg <name> <描述...>
/ca tp|tphere <player>
/ca tpa|tpahere <player>
/ca accept|deny [player]
/ca tpaallow|tpadisallow <player>
/ca tpaallowlist
/ca back
/rtp | /tpr | /r
/ca confirm|cancelconfirm
/ca reload
/ca importstp [file] [--include-back] [--offline-uuid|--raw-uuid|--auto-uuid] [--clear]
```
