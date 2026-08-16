package com.createsentryarm.content;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * 单种弹药（按物品 ID）的完整射击参数，全部可由数据包配置：
 * {@code data/createsentryarm/ammo/<name>.json}。
 * <p>
 * 完整字段示例：
 * <pre>
 * {
 *   "item": "minecraft:arrow",
 *   "range": 20,
 *   "range_high": 40,
 *   "allow_high_arc": true,
 *   "speed": 1.6,
 *   "gravity": 0.05,
 *   "drag": 0.99,
 *   "no_gravity": false,
 *   "lead": 1.0,
 *   "aim_height": 1.0,
 *   "solver_tolerance": 0.10,
 *   "verify_tolerance": 2.0,
 *   "damage": 2.0,
 *   "cooldown": 15,
 *   "spread": 0.0,
 *   "turn_speed": 1.5
 * }
 * </pre>
 * <ul>
 * <li>索敌：{@code range} 平射模式索敌射程；{@code range_high} 高抛模式索敌射程
 *     （不写时与 range 一致；两者不写时自动取 {@link #flatFireMaxRange 平射最大射程}）；
 *     {@code allow_high_arc} 是否允许高抛弹道（false = 反转也只平射，默认 true）；
 *     {@code lead} 目标移动提前量系数（0 = 不预判）；{@code aim_height} 索敌部位高度
 *     （相对目标身高比例，1.0 = 头部/眼睛，0.5 = 身体中部）。</li>
 * <li>弹道：{@code speed} 出膛速度、{@code gravity} 重力（0 或 no_gravity=true 为直线）、
 *     {@code drag} 每刻速度衰减、{@code spread} 散布。</li>
 * <li>解算：{@code solver_tolerance} 弹道解算速度容差（默认 0.10）；
 *     {@code verify_tolerance} 闭环校验距离，弹道须接近瞄准点该格数内才发射，
 *     0 或负数禁用校验（默认 2.0）。</li>
 * <li>其他：{@code damage} 直接伤害覆盖（箭基伤，不写沿用默认）；
 *     {@code cooldown} 射击冷却（不写沿用武器默认：弓 20 / 弩 25 / 药水 20-28 /
 *     土豆炮取 Create 注册表装填时间）；{@code turn_speed} 机械臂瞄准转向速度倍率。</li>
 * </ul>
 * 弹道方式（平射/高抛）由机械臂旋转方向决定：正转平射，反转高抛
 * （见 AttackArmBlockEntity#isHighArc）；{@code allow_high_arc} 为 false 的弹药不受影响，
 * 始终平射。
 *
 * @param range           平射模式的可用索敌射程（格）
 * @param rangeHigh       高抛模式的可用索敌射程（格）
 * @param allowHighArc    是否允许高抛弹道（false = 始终平射）
 * @param speed           出膛速度
 * @param gravity         弹道重力（0 = 直线飞行）
 * @param drag            每刻速度衰减系数
 * @param lead            目标移动提前量系数（0 = 不预判）
 * @param aimHeight       索敌部位：相对目标身高的比例，>= 1 视为头部（眼睛位置）
 * @param spread          散布
 * @param turnSpeed       机械臂瞄准该弹药时的转向速度倍率
 * @param solverTolerance 弹道解算容差（所需初速相对出膛速度的偏差比例）
 * @param verifyTolerance 闭环校验距离（格），<= 0 禁用
 * @param damage          直接伤害覆盖（null = 沿用默认）
 * @param cooldown        射击冷却覆盖（null = 沿用武器默认）
 */
public record AmmoData(ResourceLocation itemId, double range, double rangeHigh, boolean allowHighArc,
                       double speed, double gravity, double drag,
                       double lead, double aimHeight, double spread, double turnSpeed,
                       double solverTolerance, double verifyTolerance,
                       @Nullable Double damage, @Nullable Integer cooldown) {

    public static final double DEFAULT_RANGE = 64.0;
    public static final double DEFAULT_SPEED = 1.6;
    public static final double DEFAULT_GRAVITY = 0.05;
    public static final double DEFAULT_DRAG = 0.99;
    public static final double DEFAULT_LEAD = 1.0;
    public static final double DEFAULT_AIM_HEIGHT = 1.0;
    public static final double DEFAULT_SOLVER_TOLERANCE = 0.10;
    public static final double DEFAULT_VERIFY_TOLERANCE = 2.0;

    public int cooldown(int weaponDefault) {
        return cooldown != null ? Mth.clamp(cooldown, 1, 600) : weaponDefault;
    }

    public double damageOr(double fallback) {
        return damage != null ? damage : fallback;
    }

    /**
     * 当前机械臂旋转模式下的实际索敌射程：禁用高抛的弹药始终用平射射程。
     */
    public double effectiveRange(boolean armHighArc) {
        return allowHighArc && armHighArc ? rangeHigh : range;
    }

    /**
     * 平射（直射）最大射程：以仰角上限 30° 发射，按逐刻阻力+重力模拟，
     * 弹体落回发射高度时飞过的水平距离。无重力弹体视为不受限（256 格，
     * 实际仍受机械臂滑条上限约束）。
     */
    public static double flatFireMaxRange(double speed, double gravity, double drag) {
        if (gravity <= 1.0e-6) {
            return 256.0;
        }
        double rad = Math.toRadians(30.0);
        double vx = speed * Math.cos(rad);
        double vy = speed * Math.sin(rad);
        double x = 0.0;
        double y = 0.0;
        for (int t = 0; t < 600; t++) {
            x += vx;
            y += vy;
            vx *= drag;
            vy = vy * drag - gravity;
            if (y < 0.0) {
                break;
            }
        }
        return Mth.clamp(x, 4.0, 256.0);
    }

    /**
     * 内置弹药条目：射程自动取平射最大射程，允许高抛，其余使用通用默认值。
     */
    public static AmmoData direct(ResourceLocation itemId, double speed, double gravity, double drag) {
        return of(itemId, speed, gravity, drag,
                flatFireMaxRange(speed, gravity, drag), flatFireMaxRange(speed, gravity, drag), true,
                DEFAULT_LEAD, DEFAULT_AIM_HEIGHT, 0.0);
    }

    /**
     * 内置条目（含双射程/高抛开关/索敌部位与散布）。
     */
    public static AmmoData of(ResourceLocation itemId, double speed, double gravity, double drag,
                              double range, double rangeHigh, boolean allowHighArc,
                              double lead, double aimHeight, double spread) {
        return new AmmoData(itemId, range, rangeHigh, allowHighArc, speed, gravity, drag,
                lead, aimHeight, spread, 1.0, DEFAULT_SOLVER_TOLERANCE, DEFAULT_VERIFY_TOLERANCE, null, null);
    }

    public static AmmoData fromJson(JsonObject json, ResourceLocation itemId) {
        double speed = optDouble(json, "speed", DEFAULT_SPEED);
        double gravity = json.has("no_gravity") && json.get("no_gravity").getAsBoolean()
                ? 0.0 : optDouble(json, "gravity", DEFAULT_GRAVITY);
        double drag = optDouble(json, "drag", DEFAULT_DRAG);
        double autoRange = flatFireMaxRange(speed, gravity, drag);
        double range = json.has("range")
                ? Mth.clamp(optDouble(json, "range", DEFAULT_RANGE), 1.0, 256.0)
                : autoRange;
        double rangeHigh = json.has("range_high")
                ? Mth.clamp(optDouble(json, "range_high", range), 1.0, 256.0)
                : range;
        boolean allowHighArc = !json.has("allow_high_arc") || json.get("allow_high_arc").getAsBoolean();
        double lead = optDouble(json, "lead", DEFAULT_LEAD);
        double aimHeight = Mth.clamp(optDouble(json, "aim_height", DEFAULT_AIM_HEIGHT), 0.05, 2.0);
        double spread = optDouble(json, "spread", 0.0);
        double turnSpeed = optDouble(json, "turn_speed", 1.0);
        double solverTolerance = Mth.clamp(optDouble(json, "solver_tolerance", DEFAULT_SOLVER_TOLERANCE), 0.01, 0.5);
        double verifyTolerance = Mth.clamp(optDouble(json, "verify_tolerance", DEFAULT_VERIFY_TOLERANCE), -1.0, 16.0);
        Double damage = json.has("damage") ? json.get("damage").getAsDouble() : null;
        Integer cooldown = json.has("cooldown") ? Mth.clamp(json.get("cooldown").getAsInt(), 1, 600) : null;
        return new AmmoData(itemId, range, rangeHigh, allowHighArc, speed, gravity, drag,
                lead, aimHeight, spread, turnSpeed, solverTolerance, verifyTolerance, damage, cooldown);
    }

    private static double optDouble(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }
}
