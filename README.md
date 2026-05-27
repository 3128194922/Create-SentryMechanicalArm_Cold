# CreateSentryArm

`Minecraft 1.20.1 Forge` 模组工程。本MOD完全由AI编写，完全抄的Create-SentryMechanicalArm。相关代码遵循代码来源的协议。

## 说明

本项目**完全仿照** `Create-SentryMechanicalArm-1.20.1-0.3.0` 的思路实现，但去掉了第三方枪械依赖，改为围绕 `Create` 与原版武器体系制作攻击机械臂。

## 参考MOD
完全借用代码
https://github.com/Aupoex/Create-SentryMechanicalArm

射箭逻辑
https://github.com/MarkusBordihn/BOs-Easy-NPC

药水抛物线
https://github.com/Mercurows/SuperbWarfare


## 当前实现方向

- 新增 `攻击机械臂`
- 机械臂完全复用 `Create` 机械臂模型与贴图引用
- 不新增机械臂原创素材
- 支持装备：
  - 原版弓
  - 原版弩
  - 烟花弩
  - 喷溅药水
  - 滞留药水
  - `Create` 土豆炮
- 固定方块模式下：
  - 武器装在机械臂上
  - 弹药/药水/火箭从**正下方容器**消耗
- 动态结构模式下：
  - 武器装在机械臂上
  - 弹药从**整个 contraption 任意容器**消耗
- 默认目标规则为：攻击所有可见生物

## 火控

- 火控方块与火控剪贴板实现方式参考 `Create-SentryMechanicalArm-1.20.1-0.3.0`
- 当检测到已安装 `Create-SentryMechanicalArm`（模组 ID: `sentrymechanicalarm`）时：
  - 本模组**不注册**自己的火控方块
  - 本模组**不注册**自己的火控物品

## 依赖

- `Forge 1.20.1`
- `Create 6.0.x`

## 备注

- 本项目不接入额外第三方玩法依赖
- 机械臂贴图/模型复用 `Create` 现有资源路径
- 火控资源与结构实现参考 `Create-SentryMechanicalArm-1.20.1-0.3.0`
