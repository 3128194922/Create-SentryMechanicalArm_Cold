package com.createsentryarm.compat;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;

/**
 * 运行时兼容层：在不引入编译依赖的情况下，与 Create-Biotech 模组的
 * BioPackagerContraptionDamageTracker 交互，使得攻击机械臂在生物动态结构中
 * 击杀的生物能被 BioPackager 正确捕获。
 */
public final class BiotechCompat {
    private static final String BIOTECH_MODID = "create_biotech";
    private static final String DAMAGE_TRACKER_CLASS =
            "com.nobodiiiii.createbiotech.content.biopackager.BioPackagerContraptionDamageTracker";

    private static Boolean loaded;

    private BiotechCompat() {}

    public static boolean isBiotechLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded(BIOTECH_MODID);
        }
        return loaded;
    }

    /**
     * 在执行攻击前推送伤害上下文，使得 BioPackager 能识别攻击来源为当前动态结构。
     */
    public static boolean pushDamageContext(LivingEntity target, AbstractContraptionEntity contraption) {
        if (!isBiotechLoaded()) return false;
        try {
            Class<?> trackerClass = Class.forName(DAMAGE_TRACKER_CLASS);
            var method = trackerClass.getMethod("pushDamageContext",
                    net.minecraft.world.entity.Entity.class,
                    AbstractContraptionEntity.class);
            return (boolean) method.invoke(null, target, contraption);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 攻击完成后弹出伤害上下文。
     */
    public static void popDamageContext() {
        if (!isBiotechLoaded()) return;
        try {
            Class<?> trackerClass = Class.forName(DAMAGE_TRACKER_CLASS);
            var method = trackerClass.getMethod("popDamageContext");
            method.invoke(null);
        } catch (Exception ignored) {
        }
    }
}
