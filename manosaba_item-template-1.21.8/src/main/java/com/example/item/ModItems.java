// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.item;

import com.example.item.custom.*;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;

import java.util.function.Function;

public final class ModItems {
    public static final ModItems INSTANCE = new ModItems();

    private ModItems() {
    }

    private static <T extends Item> T registerItem(String name, Function<Item.Properties, T> factory) {
        ResourceKey<Item> key = ResourceKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("manosaba_item", name)
        );
        T item = factory.apply(new Item.Properties().setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    private static FoodProperties defaultFood(int nutrition, float saturation) {
        return new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build();
    }

    private static Consumable eatConsumable() {
        return Consumable.builder().consumeSeconds(1.6F).animation(ItemUseAnimation.EAT).build();
    }

    private static Consumable potionDrinkConsumable() {
        return Consumable.builder().consumeSeconds(1.6F).animation(ItemUseAnimation.DRINK)
            .sound(SoundEvents.GENERIC_DRINK).build();
    }

    private static Item.Properties unbreakable(Item.Properties properties) {
        return properties.component(DataComponents.UNBREAKABLE, Unit.INSTANCE);
    }

    public static final Item ANAN_BOOK = registerItem("anan_book", p -> new AnanBookItem(p.stacksTo(1)));
    public static final Item BANDAGE = registerItem("bandage", p -> new BandageItem(p.stacksTo(16)));
    public static final Item BLOOD_NANOKA_HAIRBAND = registerItem("blood_nanoka_hairband",
        p -> new BloodNanokaHairbandItem(p.stacksTo(1)));
    public static final Item BLOOD_GOLDEN_SWORD = registerItem("blood_golden_sword",
        p -> new BloodyKnightSwordItem(5, -2.4F, unbreakable(p.stacksTo(1))));
    public static final Item BROKEN_BOTTLE = registerItem("broken_bottle", p -> new BrokenBottleItem(p.stacksTo(1)));
    public static final Item BLOODIED_BROKEN_BOTTLE = registerItem("bloodied_broken_bottle",
        p -> new BloodiedBrokenBottleItem(p.stacksTo(1)));
    public static final Item DEAD_BODIES_LAY_EVERYWHERE = registerItem("dead_bodies_lay_everywhere",
        p -> new CustomSwordItem(100, -2.4F, unbreakable(p.stacksTo(1))));
    public static final Item DIAMOND_SWORD = registerItem("diamond_sword",
        p -> new CustomSwordItem(10000, -2.4F, unbreakable(p.stacksTo(1))));
    public static final Item EMPTY_JAR = registerItem("empty_jar", p -> new EmptyJarItem(p.stacksTo(64)));
    public static final Item EMPTY_GLASS_BOTTLE = registerItem("empty_glass_bottle", p -> new Item(p.stacksTo(64)));
    public static final Item FEATHER_FAN = registerItem("feather_fan", p -> new FeatherFanItem(p.stacksTo(1)));
    public static final Item GOLDEN_HOE = registerItem("golden_hoe",
        p -> new BurnRodItem(unbreakable(p.stacksTo(1)).fireResistant()));
    public static final Item GOLDEN_SWORD = registerItem("golden_sword",
        p -> new CustomSwordItem(5, -2.4F, unbreakable(p.stacksTo(1))));
    public static final Item GOLDUCK = registerItem("golduck",
        p -> new GolduckItem(p.stacksTo(64).food(defaultFood(6, 1.2F), eatConsumable())));
    public static final Item HIRO_PEN = registerItem("hiro_pen", p -> new Item(p.stacksTo(1)));
    public static final Item IRON_HOE = registerItem("iron_hoe",
        p -> new CooledBurnRodItem(unbreakable(p.stacksTo(1)).fireResistant()));
    public static final Item JAR = registerItem("jar",
        p -> new JarItem(p.stacksTo(64).food(defaultFood(5, 0.6F), eatConsumable())));
    public static final Item JIAN = registerItem("jian", p -> new CustomArrowItem(p.stacksTo(64)));
    public static final Item JIAN_YI_CHANG_MAO = registerItem("jian_yi_chang_mao",
        p -> new SimpleSpearItem(p.durability(1)));
    public static final Item KNIFE = registerItem("knife",
        p -> new CustomSwordItem(5, -1.0F, unbreakable(p.stacksTo(1))));
    public static final Item NETHERITE_SWORD = registerItem("netherite_sword",
        p -> new CustomSwordItem(10, -2.4F, unbreakable(p.stacksTo(1))));
    public static final Item POISON = registerItem("poison", p -> new PoisonItem(p.stacksTo(1)
        .component(DataComponents.CONSUMABLE, potionDrinkConsumable()).craftRemainder(EMPTY_GLASS_BOTTLE)));
    public static final Item SLEEPING_PILLS = registerItem("sleeping_pills", p -> new SleepingPillsItem(p.stacksTo(1)
        .component(DataComponents.CONSUMABLE, potionDrinkConsumable()).craftRemainder(EMPTY_GLASS_BOTTLE)));
    public static final Item SPLINT = registerItem("splint", p -> new Item(p.stacksTo(64)));
    public static final Item SYRINGE = registerItem("syringe", p -> new Item(p.stacksTo(2)));
    public static final Item YUKIS_SEXUAL_FLUIDS = registerItem("yukis_sexual_fluids", p -> new TldjmItem(p.stacksTo(1)
        .component(DataComponents.CONSUMABLE, potionDrinkConsumable()).craftRemainder(EMPTY_GLASS_BOTTLE)));
    public static final Item WOODEN_SWORD = registerItem("wooden_sword",
        p -> new WoodenSwordItem(unbreakable(p.stacksTo(1))));
    public static final Item NANOKA_HAIRBAND = registerItem("nanoka_hairband", p -> new HairRibbonItem(p.stacksTo(1)));
    public static final Item REVOLVER_BULLET = registerItem("revolver_bullet", p -> new Item(p.stacksTo(64)));
    public static final Item ROTARY_MAGAZINE = registerItem("rotary_magazine", p -> new RotaryMagazineItem(p.stacksTo(16)));
    public static final Item SHELL_CASING = registerItem("shell_casing", p -> new Item(p.stacksTo(64)));
    public static final Item ZHANG_XUEFENG = registerItem("zhang_xuefeng",
        p -> new ZhangXuefengItem(p.stacksTo(1).food(defaultFood(10, 1.0F), eatConsumable())));
    public static final Item ZONGZI = registerItem("zongzi",
        p -> new Item(p.stacksTo(1).food(defaultFood(15, 1.2F), eatConsumable())));
    public static final Item ZUOLUN = registerItem("zuolun", p -> new RevolverItem(p.stacksTo(1)));
    public static final Item POISONED_FOOD = registerItem("poisoned_food",
        p -> new PoisonedFoodItem(p.stacksTo(64).food(defaultFood(4, 0.4F), eatConsumable())));
    public static final Item SYRINGE_UNKNOWN_ONE = registerItem("syringe_unknown_one",
        p -> new SyringeItem(p.stacksTo(1), SyringeType.TLDJM));
    public static final Item SYRINGE_UNKNOWN_TWO = registerItem("syringe_unknown_two",
        p -> new SyringeItem(p.stacksTo(1), SyringeType.SLEEPING));
    public static final Item SYRINGE_UNKNOWN_THREE = registerItem("syringe_unknown_three",
        p -> new SyringeItem(p.stacksTo(1), SyringeType.POISON));
    public static final Item SYRINGE_TLDJM = registerItem("syringe_tldjm",
        p -> new SyringeItem(p.stacksTo(1), SyringeType.TLDJM));
    public static final Item SYRINGE_SLEEPING = registerItem("syringe_sleeping",
        p -> new SyringeItem(p.stacksTo(1), SyringeType.SLEEPING));
    public static final Item SYRINGE_POISON = registerItem("syringe_poison",
        p -> new SyringeItem(p.stacksTo(1), SyringeType.POISON));

    public static void init() {
    }

    public Item getANAN_BOOK() { return ANAN_BOOK; }
    public Item getBANDAGE() { return BANDAGE; }
    public Item getBLOOD_NANOKA_HAIRBAND() { return BLOOD_NANOKA_HAIRBAND; }
    public Item getBLOOD_GOLDEN_SWORD() { return BLOOD_GOLDEN_SWORD; }
    public Item getBROKEN_BOTTLE() { return BROKEN_BOTTLE; }
    public Item getBLOODIED_BROKEN_BOTTLE() { return BLOODIED_BROKEN_BOTTLE; }
    public Item getDEAD_BODIES_LAY_EVERYWHERE() { return DEAD_BODIES_LAY_EVERYWHERE; }
    public Item getDIAMOND_SWORD() { return DIAMOND_SWORD; }
    public Item getEMPTY_JAR() { return EMPTY_JAR; }
    public Item getEMPTY_GLASS_BOTTLE() { return EMPTY_GLASS_BOTTLE; }
    public Item getFEATHER_FAN() { return FEATHER_FAN; }
    public Item getGOLDEN_HOE() { return GOLDEN_HOE; }
    public Item getGOLDEN_SWORD() { return GOLDEN_SWORD; }
    public Item getGOLDUCK() { return GOLDUCK; }
    public Item getHIRO_PEN() { return HIRO_PEN; }
    public Item getIRON_HOE() { return IRON_HOE; }
    public Item getJAR() { return JAR; }
    public Item getJIAN() { return JIAN; }
    public Item getJIAN_YI_CHANG_MAO() { return JIAN_YI_CHANG_MAO; }
    public Item getKNIFE() { return KNIFE; }
    public Item getNETHERITE_SWORD() { return NETHERITE_SWORD; }
    public Item getPOISON() { return POISON; }
    public Item getSLEEPING_PILLS() { return SLEEPING_PILLS; }
    public Item getSPLINT() { return SPLINT; }
    public Item getSYRINGE() { return SYRINGE; }
    public Item getYUKIS_SEXUAL_FLUIDS() { return YUKIS_SEXUAL_FLUIDS; }
    public Item getWOODEN_SWORD() { return WOODEN_SWORD; }
    public Item getNANOKA_HAIRBAND() { return NANOKA_HAIRBAND; }
    public Item getZHANG_XUEFENG() { return ZHANG_XUEFENG; }
    public Item getZONGZI() { return ZONGZI; }
    public Item getZUOLUN() { return ZUOLUN; }
    public Item getPOISONED_FOOD() { return POISONED_FOOD; }
    public Item getSYRINGE_UNKNOWN_ONE() { return SYRINGE_UNKNOWN_ONE; }
    public Item getSYRINGE_UNKNOWN_TWO() { return SYRINGE_UNKNOWN_TWO; }
    public Item getSYRINGE_UNKNOWN_THREE() { return SYRINGE_UNKNOWN_THREE; }
    public Item getSYRINGE_TLDJM() { return SYRINGE_TLDJM; }
    public Item getSYRINGE_SLEEPING() { return SYRINGE_SLEEPING; }
    public Item getSYRINGE_POISON() { return SYRINGE_POISON; }
}
