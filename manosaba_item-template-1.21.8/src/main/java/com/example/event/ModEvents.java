// Copyright (c) 2026 Manosaba Item Team. All rights reserved.
package com.example.event;

import net.minecraft.ChatFormatting;
import com.example.component.ModDataComponents;
import com.example.component.SketchbookDrawingData;
import com.example.component.SketchbookPage;
import com.example.effect.ModEffects;
import com.example.item.ModItems;
import com.example.item.custom.AnanBookItem;
import com.example.item.custom.BloodiedBrokenBottleItem;
import com.example.item.custom.BrokenBottleItem;
import com.example.network.BottleKeyPressPayload;
import com.example.network.BottleStruggleEndPayload;
import com.example.network.BottleStruggleStartPayload;
import com.example.network.BottleStruggleUpdatePayload;
import com.example.network.SketchbookDisplayPayload;
import com.example.network.SketchbookEditPayload;
import com.example.network.SketchbookPayload;
import com.example.network.SketchbookUpdatePayload;
import com.example.network.RevolverActionPayload;
import com.example.network.RevolverStatusPayload;
import com.example.item.custom.RevolverItem;
import com.example.item.custom.RevolverSounds;
import com.example.item.custom.RotaryMagazineItem;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.InteractionHand;

public final class ModEvents {
    private static final int MAX_PAGES = 100;
    private static final int MAX_PAGE_NAME_LENGTH = 32;
    private static final int MAX_COLORS = 20;
    private static final ResourceLocation DIZZINESS_EFFECT = ResourceLocation.fromNamespaceAndPath("mano_effect", "dizziness");
    private static final Map<UUID, ItemStack> PREVIOUS_MAIN_HAND = new HashMap<>();
    private static final Map<UUID, ItemStack> PREVIOUS_BLOODIED_MAIN_HAND = new HashMap<>();
    private static final Map<UUID, RevolverReload> REVOLVER_RELOADS = new HashMap<>();
    private static final Map<UUID, Long> REVOLVER_CHECKS = new HashMap<>();

    private ModEvents() {}

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (ModPlayerState.golduck.containsKey(sender.getUUID())) {
                sender.displayClientMessage(Component.translatable("message.manosaba_item.mouth_full")
                    .withStyle(ChatFormatting.YELLOW), true);
                return false;
            }
            if (ModEffects.hasSleepingEffect(sender)) {
                sender.displayClientMessage(Component.translatable("message.manosaba_item.sleeping_cannot_chat")
                    .withStyle(ChatFormatting.YELLOW), true);
                return false;
            }
            return true;
        });

        PayloadTypeRegistry.playC2S().register(SketchbookPayload.ID, SketchbookPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SketchbookUpdatePayload.ID, SketchbookUpdatePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SketchbookDisplayPayload.ID, SketchbookDisplayPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SketchbookEditPayload.ID, SketchbookEditPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RevolverActionPayload.ID, RevolverActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(RevolverStatusPayload.ID, RevolverStatusPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(BottleKeyPressPayload.ID, BottleKeyPressPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(BottleStruggleStartPayload.ID, BottleStruggleStartPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(BottleStruggleUpdatePayload.ID, BottleStruggleUpdatePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(BottleStruggleEndPayload.ID, BottleStruggleEndPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SketchbookPayload.ID, (payload, context) ->
            context.server().execute(() -> beginQuickSketchbookEdit(context.player(), payload.getText())));
        ServerPlayNetworking.registerGlobalReceiver(BottleKeyPressPayload.ID, (payload, context) ->
            context.server().execute(() -> handleBottleKeyPress(context.player(), payload.isAttacker())));
        ServerPlayNetworking.registerGlobalReceiver(SketchbookUpdatePayload.ID, (payload, context) ->
            context.server().execute(() -> updateSketchbook(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(SketchbookDisplayPayload.ID, (payload, context) ->
            context.server().execute(() -> displaySketchbookPage(context.player(), payload.getPageIndex())));
        ServerPlayNetworking.registerGlobalReceiver(RevolverActionPayload.ID, (payload, context) ->
            context.server().execute(() -> handleRevolverAction(context.player(), payload.action())));

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                notifyAndRemoveStruggles(player.getUUID(), player.getServer());
                clearPlayerState(player.getUUID());
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getEntity() instanceof Player player) {
                ItemStack main = player.getMainHandItem();
                ItemStack off = player.getOffhandItem();
                if (main.is(ModItems.GOLDEN_SWORD)) replaceInventoryStack(player, main);
                else if (off.is(ModItems.GOLDEN_SWORD)) replaceInventoryStack(player, off);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerLevel level : server.getAllLevels()) cleanupTextDisplays(level, level.getGameTime());
            for (ServerPlayer player : server.getPlayerList().getPlayers()) tickPlayer(player);
            tickBottleStruggles(server);
            tickBottleBreakParticles(server);
        });
    }

    private static void updateSketchbook(ServerPlayer player, SketchbookUpdatePayload payload) {
        ItemStack stack = heldSketchbook(player);
        if (stack == null || !validSketchbookUpdate(payload)) return;
        List<SketchbookPage> pages = List.copyOf(payload.pages());
        stack.set(ModDataComponents.SKETCHBOOK_PAGES, pages);
        stack.set(ModDataComponents.SKETCHBOOK_COLORS, List.copyOf(payload.colors()));
        stack.set(ModDataComponents.SKETCHBOOK_PREV_COLOR, payload.prevColor());
        stack.set(ModDataComponents.SKETCHBOOK_DISPLAY_PAGE, payload.displayPage());
        if (payload.displayPage() >= 0) {
            selectSketchbookHotbarSlot(player, stack);
            openSketchbook(stack, payload.displayPage());
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    private static void activateSketchbook(ServerPlayer player) {
        ItemStack stack = heldSketchbook(player);
        if (stack == null) return;
        selectSketchbookHotbarSlot(player, stack);
        int displayPage = stack.getOrDefault(ModDataComponents.SKETCHBOOK_DISPLAY_PAGE, -1);
        openSketchbook(stack, displayPage);
    }

    private static void beginQuickSketchbookEdit(ServerPlayer player, String rawText) {
        ItemStack stack = heldSketchbook(player);
        if (stack == null || rawText == null || rawText.isBlank()) return;
        String text = limitCodePoints(rawText.strip(), SketchbookDrawingData.MAX_TEXT_LENGTH);
        selectSketchbookHotbarSlot(player, stack);
        List<SketchbookPage> pages = new ArrayList<>(stack.getOrDefault(ModDataComponents.SKETCHBOOK_PAGES, List.of()));
        if (pages.size() >= MAX_PAGES) return;
        String name = nextPageName(pages);
        pages.add(new SketchbookPage(name, "drawing", "", new byte[0]));
        int pageIndex = pages.size() - 1;
        stack.set(ModDataComponents.SKETCHBOOK_PAGES, List.copyOf(pages));
        openSketchbook(stack, stack.getOrDefault(ModDataComponents.SKETCHBOOK_DISPLAY_PAGE, -1));
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        ServerPlayNetworking.send(player, new SketchbookEditPayload(pageIndex, text));
    }

    private static String nextPageName(List<SketchbookPage> pages) {
        int number = 1;
        while (true) {
            String candidate = "未命名的页面" + number++;
            if (pages.stream().noneMatch(page -> candidate.equals(page.getName()))) return candidate;
        }
    }

    private static String limitCodePoints(String text, int max) {
        int count = text.codePointCount(0, text.length());
        return count <= max ? text : text.substring(0, text.offsetByCodePoints(0, max));
    }

    private static void openSketchbook(ItemStack stack, int displayPage) {
        stack.set(ModDataComponents.SKETCHBOOK_OPEN, true);
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
            new CustomModelData(List.of(), List.of(), List.of("1"), List.of()));
        stack.set(ModDataComponents.SKETCHBOOK_DISPLAY_PAGE, displayPage);
    }

    private static boolean validSketchbookUpdate(SketchbookUpdatePayload payload) {
        if (payload.pages() == null || payload.pages().size() > MAX_PAGES || payload.colors() == null
            || payload.colors().size() > MAX_COLORS) return false;
        for (SketchbookPage page : payload.pages()) if (!validPage(page)) return false;
        int display = payload.displayPage();
        return display == -1 || display >= 0 && display < payload.pages().size()
            && "drawing".equals(payload.pages().get(display).getMode());
    }

    private static boolean validPage(SketchbookPage page) {
        if (page == null || page.getName() == null || page.getName().isBlank() || page.getName().length() > MAX_PAGE_NAME_LENGTH
            || page.getMode() == null || page.getTextContent() == null || page.getDrawingData() == null) return false;
        if ("writing".equals(page.getMode())) return countTextUnits(page.getTextContent()) <= 60 && page.getDrawingData().length == 0;
        if (!"drawing".equals(page.getMode()) || !page.getTextContent().isEmpty()) return false;
        return SketchbookDrawingData.isValid(page.getDrawingData());
    }

    private static int countTextUnits(String text) {
        return text.codePoints().map(codePoint -> isCjk(codePoint) ? 2 : 1).sum();
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
            || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL
            || codePoint >= 0xFF66 && codePoint <= 0xFF9D;
    }

    private static void displaySketchbookPage(ServerPlayer player, int pageIndex) {
        ItemStack stack = heldSketchbook(player);
        if (stack == null) return;
        List<SketchbookPage> pages = stack.getOrDefault(ModDataComponents.SKETCHBOOK_PAGES, List.of());
        if (pageIndex < 0 || pageIndex >= pages.size()) return;
        SketchbookPage page = pages.get(pageIndex);
        if (!validPage(page)) return;
        if ("drawing".equals(page.getMode())) {
            selectSketchbookHotbarSlot(player, stack);
            openSketchbook(stack, pageIndex);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        display.setText(Component.literal(page.getTextContent()));
        display.setPos(player.getX(), player.getY() + 2.0, player.getZ());
        display.setNoGravity(true);
        display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        display.setTextOpacity((byte) -1);
        long duration = Math.max(100, Math.min(600, 100 + page.getTextContent().length() * 5L));
        long end = level.getGameTime() + duration;
        display.addTag("manosaba_sketchbook_" + end + "_" + (end - 20));
        display.addTag("manosaba_follow_" + player.getUUID());
        level.addFreshEntity(display);
    }

    private static void selectSketchbookHotbarSlot(ServerPlayer player, ItemStack stack) {
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot) != stack) continue;
            player.getInventory().setSelectedSlot(slot);
            player.connection.send(new ClientboundSetHeldSlotPacket(slot));
            return;
        }
    }

    private static ItemStack heldOpenSketchbook(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof AnanBookItem && AnanBookItem.isOpen(main)) return main;
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof AnanBookItem && AnanBookItem.isOpen(off)) return off;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof AnanBookItem && AnanBookItem.isOpen(stack)) return stack;
        }
        return null;
    }

    private static ItemStack heldSketchbook(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof AnanBookItem) return main;
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof AnanBookItem) return off;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof AnanBookItem) return stack;
        }
        return null;
    }

    private static void tickPlayer(ServerPlayer player) {
        long time = player.level().getGameTime(); UUID id = player.getUUID();
        tickBottleSwitches(player, id);
        tickBottleStabDamage(player, id, time);
        tickPoison(player, id, time);
        tickTldjm(player, id, time);
        tickGolduck(player, id, time);
        tickSleeping(player, id, time);
        tickZhangXuefeng(player, id);
        tickRevolver(player, id);
    }

    private static void handleRevolverAction(ServerPlayer player, int action) {
        ItemStack gun = player.getMainHandItem();
        UUID id = player.getUUID();
        boolean fillingMagazine = gun.is(ModItems.ROTARY_MAGAZINE);
        if (!gun.is(ModItems.ZUOLUN) && !fillingMagazine) return;
        if (action == 1) {
            if (fillingMagazine) return;
            REVOLVER_CHECKS.put(id, player.level().getGameTime() + 20);
            return;
        }
        if (action != 0) return;
        if (REVOLVER_RELOADS.remove(id) != null) {
            RevolverItem.setReloading(player, false);
            player.stopUsingItem();
            ServerPlayNetworking.send(player, new RevolverStatusPayload(false, 0, false));
            return;
        }
        if (!fillingMagazine && RevolverItem.ammo(gun) >= RevolverItem.CAPACITY) return;
        ItemStack magazine = findMagazine(player, true);
        ItemStack bullet = findItem(player, ModItems.REVOLVER_BULLET);
        if (fillingMagazine) {
            magazine = ItemStack.EMPTY;
            bullet = findItemExcludingSlot(player, ModItems.REVOLVER_BULLET,
                player.getInventory().findSlotMatchingItem(gun));
        } else if (magazine.isEmpty() && bullet.isEmpty()) magazine = findMagazine(player, false);
        if (magazine.isEmpty() && bullet.isEmpty()) return;
        boolean useMagazine = !magazine.isEmpty();
        int sourceSlot = useMagazine ? player.getInventory().findSlotMatchingItem(magazine)
            : player.getInventory().findSlotMatchingItem(bullet);
        int targetSlot = fillingMagazine ? player.getInventory().findSlotMatchingItem(gun) : -1;
        REVOLVER_RELOADS.put(id, new RevolverReload(player.level().getGameTime(), useMagazine, fillingMagazine,
            sourceSlot, targetSlot, gun.getOrDefault(ModDataComponents.REVOLVER_CYLINDER_EJECTED, false)));
        RevolverItem.setReloading(player, true);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        ServerPlayNetworking.send(player, new RevolverStatusPayload(true, useMagazine ? 40 : 30, useMagazine));
    }

    private static ItemStack findMagazine(ServerPlayer player, boolean minimumTwo) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.ROTARY_MAGAZINE)
                && stack.getOrDefault(ModDataComponents.MAGAZINE_AMMO, 0) >= (minimumTwo ? 2 : 1)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findItem(ServerPlayer player, net.minecraft.world.item.Item item) {
        return findItemExcludingSlot(player, item, -1);
    }

    private static ItemStack findItemExcludingSlot(ServerPlayer player, net.minecraft.world.item.Item item, int excludedSlot) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
            if (slot != excludedSlot && player.getInventory().getItem(slot).is(item)) return player.getInventory().getItem(slot);
        return ItemStack.EMPTY;
    }

    private static void tickRevolver(ServerPlayer player, UUID id) {
        long time = player.level().getGameTime();
        Long check = REVOLVER_CHECKS.get(id);
        if (check != null && time >= check) {
            REVOLVER_CHECKS.remove(id);
            int count = RevolverItem.ammo(player.getMainHandItem());
            player.displayClientMessage(Component.literal("剩余子弹 : " + count + " / 6"), true);
        }
        RevolverReload reload = REVOLVER_RELOADS.get(id);
        if (reload == null) return;
        ItemStack gun = player.getMainHandItem();
        boolean fillingMagazine = reload.targetMagazine;
        if ((!fillingMagazine && !gun.is(ModItems.ZUOLUN)) || (fillingMagazine && !gun.is(ModItems.ROTARY_MAGAZINE))) {
            REVOLVER_RELOADS.remove(id);
            RevolverItem.setReloading(player, false);
            player.stopUsingItem();
            ServerPlayNetworking.send(player, new RevolverStatusPayload(false, 0, false));
            return;
        }
        player.setSprinting(false);
        player.zza *= 0.2F;
        player.xxa *= 0.2F;
        player.setDeltaMovement(player.getDeltaMovement().multiply(0.2, 1.0, 0.2));
        if (fillingMagazine) {
            tickMagazineReload(player, id, reload, gun, time);
            return;
        }
        if (!reload.ejected && time - reload.started >= 10) {
            int ammo = RevolverItem.ammo(gun), shells = RevolverItem.shells(gun);
            for (int i = 0; i < ammo; i++) dropRevolverModel(player, ModItems.REVOLVER_BULLET, "revolver_bullet_3d");
            for (int i = 0; i < shells; i++) dropRevolverModel(player, ModItems.SHELL_CASING, "shell_casing_3d");
            gun.set(ModDataComponents.REVOLVER_AMMO, 0);
            gun.set(ModDataComponents.REVOLVER_SHELLS, 0);
            gun.set(ModDataComponents.REVOLVER_CYLINDER_EJECTED, true);
            reload.ejected = true;
        }
        if (reload.magazine) {
            ServerPlayNetworking.send(player, new RevolverStatusPayload(true,
                Math.max(0, 40 - (int) (time - reload.started)), true));
            if (time - reload.started < 40) return;
            if (!reload.loaded) {
                ItemStack mag = player.getInventory().getItem(reload.sourceSlot);
                if (mag.is(ModItems.ROTARY_MAGAZINE)) {
                    int space = 6 - RevolverItem.ammo(gun);
                    int available = mag.getOrDefault(ModDataComponents.MAGAZINE_AMMO, 0);
                    int moved = Math.min(space, available);
                    gun.set(ModDataComponents.REVOLVER_AMMO, RevolverItem.ammo(gun) + moved);
                    mag.set(ModDataComponents.MAGAZINE_AMMO, available - moved);
                    RotaryMagazineItem.updateModel(mag);
                    reload.loaded = true;
                    gun.set(ModDataComponents.REVOLVER_CYLINDER_EJECTED, false);
                    RevolverSounds.play(player.level(), player, "reload_quick", 3.0, 1.0F, false);
                    RevolverSounds.play(player.level(), player, "reload_over" + (player.level().random.nextBoolean() ? 1 : 2),
                        2.0, 1.0F, false);
                }
            }
        } else {
            long elapsed = time - reload.started;
            ServerPlayNetworking.send(player, new RevolverStatusPayload(true, Math.max(0, 30 - (int) elapsed), false));
            if (elapsed < 30) return;
            ItemStack bullets = player.getInventory().getItem(reload.sourceSlot);
            if (!bullets.is(ModItems.REVOLVER_BULLET) || RevolverItem.ammo(gun) >= 6) {
                REVOLVER_RELOADS.remove(id);
                RevolverItem.setReloading(player, false);
                player.stopUsingItem();
                ServerPlayNetworking.send(player, new RevolverStatusPayload(false, 0, false));
                return;
            }
            bullets.shrink(1);
            gun.set(ModDataComponents.REVOLVER_AMMO, RevolverItem.ammo(gun) + 1);
            gun.set(ModDataComponents.REVOLVER_CYLINDER_EJECTED, false);
            reload.started = time;
            RevolverSounds.play(player.level(), player, "reload_lower", 3.0, 1.0F, false);
            if (bullets.isEmpty() || RevolverItem.ammo(gun) >= 6) {
                RevolverSounds.play(player.level(), player, "reload_over" + (player.level().random.nextBoolean() ? 1 : 2),
                    2.0, 1.0F, false);
            }
            if (bullets.isEmpty() || RevolverItem.ammo(gun) >= 6) {
                REVOLVER_RELOADS.remove(id);
                RevolverItem.setReloading(player, false);
                player.stopUsingItem();
                ServerPlayNetworking.send(player, new RevolverStatusPayload(false, 0, false));
            }
        }
        if (reload.magazine && reload.loaded) {
            ServerPlayNetworking.send(player, new RevolverStatusPayload(false, 0, true));
            REVOLVER_RELOADS.remove(id);
            RevolverItem.setReloading(player, false);
            player.stopUsingItem();
        }
        player.getInventory().setChanged();
    }

    private static void dropRevolverModel(ServerPlayer player, Item item, String model) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
            new CustomModelData(List.of(), List.of(), List.of(model), List.of()));
        net.minecraft.world.entity.item.ItemEntity entity = new net.minecraft.world.entity.item.ItemEntity(
            player.level(), player.getX(), player.getY() + 0.25, player.getZ(), stack);
        entity.setInvisible(true);
        entity.addTag("manosaba_revolver_pickup");
        entity.setDeltaMovement((player.level().random.nextDouble() - 0.5) * 0.12, 0.12,
            (player.level().random.nextDouble() - 0.5) * 0.12);
        player.level().addFreshEntity(entity);
        Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, player.level());
        display.setPos(entity.getX(), entity.getY(), entity.getZ());
        display.getSlot(0).set(stack.copy());
        display.addTag("manosaba_revolver_display_" + entity.getId());
        player.level().addFreshEntity(display);
    }

    private static void tickMagazineReload(ServerPlayer player, UUID id, RevolverReload reload, ItemStack target,
                                            long time) {
        long elapsed = time - reload.started;
        ServerPlayNetworking.send(player, new RevolverStatusPayload(true, Math.max(0, 30 - (int) elapsed), false));
        if (elapsed < 30) return;
        ItemStack bullets = player.getInventory().getItem(reload.sourceSlot);
        target = player.getInventory().getItem(reload.targetSlot);
        int loaded = target.getOrDefault(ModDataComponents.MAGAZINE_AMMO, 0);
        if (!bullets.is(ModItems.REVOLVER_BULLET) || loaded >= RevolverItem.CAPACITY) {
            RevolverSounds.play(player.level(), player, "reload_over" + (player.level().random.nextBoolean() ? 1 : 2),
                2.0, 1.0F, false);
            REVOLVER_RELOADS.remove(id);
            RevolverItem.setReloading(player, false);
            player.stopUsingItem();
            ServerPlayNetworking.send(player, new RevolverStatusPayload(false, 0, false));
            return;
        }
        bullets.shrink(1);
        target.set(ModDataComponents.MAGAZINE_AMMO, loaded + 1);
        RotaryMagazineItem.updateModel(target);
        reload.started = time;
        RevolverSounds.play(player.level(), player, "reload_lower", 3.0, 1.0F, false);
        if (bullets.isEmpty() || loaded + 1 >= RevolverItem.CAPACITY) {
            RevolverSounds.play(player.level(), player, "reload_over" + (player.level().random.nextBoolean() ? 1 : 2),
                2.0, 1.0F, false);
            REVOLVER_RELOADS.remove(id);
            RevolverItem.setReloading(player, false);
            player.stopUsingItem();
            ServerPlayNetworking.send(player, new RevolverStatusPayload(false, 0, false));
        }
        player.getInventory().setChanged();
    }

    private static final class RevolverReload {
        private long started;
        private final boolean magazine;
        private final boolean targetMagazine;
        private final int sourceSlot;
        private final int targetSlot;
        private boolean ejected;
        private boolean loaded;
        private RevolverReload(long started, boolean magazine, boolean targetMagazine, int sourceSlot, int targetSlot,
                               boolean ejected) {
            this.started = started; this.magazine = magazine; this.targetMagazine = targetMagazine;
            this.sourceSlot = sourceSlot; this.targetSlot = targetSlot; this.ejected = ejected;
        }
    }

    private static void tickBottleSwitches(ServerPlayer player, UUID id) {
        ItemStack current = player.getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack previous = PREVIOUS_MAIN_HAND.put(id, current.copy());
        if (previous != null && !previous.is(ModItems.BROKEN_BOTTLE) && current.is(ModItems.BROKEN_BOTTLE))
            BrokenBottleItem.onSwitchToEvent(player);
        previous = PREVIOUS_BLOODIED_MAIN_HAND.put(id, current.copy());
        if (previous != null && !previous.is(ModItems.BLOODIED_BROKEN_BOTTLE) && current.is(ModItems.BLOODIED_BROKEN_BOTTLE))
            BloodiedBrokenBottleItem.onSwitchToEvent(player);
    }

    private static void tickPoison(ServerPlayer player, UUID id, long time) {
        ModPlayerState.PoisonState state = ModPlayerState.poison.get(id); if (state == null) return;
        long elapsed = time - state.eatTime;
        if (elapsed >= 1200 && !state.stage1) {
            state.stage1 = true;
            applyDizziness(player, 2400);
            player.displayClientMessage(Component.translatable("message.manosaba_item.poison_stage_1")
                .withStyle(ChatFormatting.YELLOW), false);
        }
        if (elapsed >= 1800 && !state.stage2) {
            state.stage2 = true;
            player.displayClientMessage(Component.translatable("message.manosaba_item.poison_stage_2")
                .withStyle(ChatFormatting.RED), false);
        }
        if (elapsed >= 3000 && !state.stage3) {
            state.stage3 = true;
            player.displayClientMessage(Component.translatable("message.manosaba_item.poison_stage_3")
                .withStyle(ChatFormatting.RED), false);
        }
        if (state.stage3 && elapsed < 3600) ((ServerLevel) player.level()).sendParticles(new DustParticleOptions(0xCC0000, 1.2F),
            player.getX(), player.getY() + 1.5, player.getZ(), 3, .2, .1, .2, .01);
        if (elapsed >= 3600) {
            player.displayClientMessage(Component.translatable("message.manosaba_item.poison_death")
                .withStyle(ChatFormatting.RED), false);
            player.kill((ServerLevel) player.level());
            ModPlayerState.poison.remove(id);
        }
    }

    private static void tickTldjm(ServerPlayer player, UUID id, long time) {
        ModPlayerState.TldjmState state = ModPlayerState.tldjm.get(id);
        if (state != null && time >= state.deathTime) { player.kill((ServerLevel) player.level()); ModPlayerState.tldjm.remove(id); }
    }

    private static void tickGolduck(ServerPlayer player, UUID id, long time) {
        ModPlayerState.GolduckState state = ModPlayerState.golduck.get(id); if (state == null) return;
        if (time - state.eatTime >= 3600) ModPlayerState.golduck.remove(id);
        else if (player.getFoodData().getFoodLevel() < 20) { player.getFoodData().setFoodLevel(20); player.getFoodData().setSaturation(20); }
    }

    private static void tickSleeping(ServerPlayer player, UUID id, long time) {
        ModPlayerState.SleepingPillsState state = ModPlayerState.sleepingPills.get(id); if (state == null) return;
        long elapsed = time - state.eatTime;
        if (elapsed >= 200 && !state.stage1) {
            state.stage1 = true;
            player.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(ModEffects.DIZZINESS), 400, 0, false, false, true));
        }
        if (elapsed >= 600 && !state.stage2) {
            state.stage2 = true;
            player.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(ModEffects.SLEEPING), 2400, 100, false, false, true));
        }
        if (elapsed >= 600) {
            state.locked = true;
        }
        if (state.locked) {
            player.setPose(Pose.SLEEPING); player.xxa = 0; player.zza = 0;
            player.setDeltaMovement(0, Math.min(player.getDeltaMovement().y, 0), 0); player.setSprinting(false);
        }
        if (elapsed >= 3000 && state.locked) {
            player.setPose(Pose.STANDING); player.removeEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(ModEffects.SLEEPING));
            ModPlayerState.sleepingPills.remove(id);
        }
    }

    private static void tickZhangXuefeng(ServerPlayer player, UUID id) {
        ModPlayerState.ZhangXuefengState state = ModPlayerState.zhangXuefeng.get(id); if (state == null) return;
        double dx = player.getX() - state.lastX, dz = player.getZ() - state.lastZ;
        state.distance += Math.sqrt(dx * dx + dz * dz); state.lastX = player.getX(); state.lastZ = player.getZ();
        if (state.distance >= 100) {
            player.displayClientMessage(Component.translatable("message.manosaba_item.running_death")
                .withStyle(ChatFormatting.RED), false);
            player.kill((ServerLevel) player.level());
            ModPlayerState.zhangXuefeng.remove(id);
        }
    }

    private static void tickBottleStabDamage(ServerPlayer player, UUID id, long time) {
        ModPlayerState.BottleStabDamageState state = ModPlayerState.bottleStabDamage.get(id); if (state == null) return;
        if (time - state.startTime >= 600) { ModPlayerState.bottleStabDamage.remove(id); return; }
        if (time - state.lastDamageTime >= 60) {
            state.lastDamageTime = time;
            hurtWithoutKilling(player, 2.0F);
        }
    }

    private static void handleBottleKeyPress(ServerPlayer player, boolean claimedAttacker) {
        UUID id = player.getUUID();
        ModPlayerState.BottleStruggleState state = ModPlayerState.bottleStruggle.get(id);
        boolean attacker = state != null;
        if (state == null) state = ModPlayerState.bottleStruggle.values().stream().filter(value -> value.victimId.equals(id)).findFirst().orElse(null);
        if (state == null || state.finished || claimedAttacker != attacker) return;
        long tick = player.level().getGameTime();
        if (attacker && state.lastAttackerPressTick != tick) { state.lastAttackerPressTick = tick; state.attackerPresses++; }
        if (!attacker && state.lastVictimPressTick != tick) { state.lastVictimPressTick = tick; state.victimPresses++; }
    }

    private static void tickBottleStruggles(MinecraftServer server) {
        long time = server.overworld().getGameTime(); List<UUID> remove = new ArrayList<>();
        for (Map.Entry<UUID, ModPlayerState.BottleStruggleState> entry : ModPlayerState.bottleStruggle.entrySet()) {
            UUID attackerId = entry.getKey(); ModPlayerState.BottleStruggleState state = entry.getValue();
            ServerPlayer attacker = server.getPlayerList().getPlayer(attackerId);
            ServerPlayer victim = server.getPlayerList().getPlayer(state.victimId);
            if (state.finished || attacker == null || victim == null || !attacker.isAlive() || !victim.isAlive()) {
                sendStruggleEnd(attacker, victim, state.reachedThreshold); remove.add(attackerId); continue;
            }
            if (Double.isNaN(state.attackerStartY)) {
                state.attackerStartX = attacker.getX(); state.attackerStartY = attacker.getY(); state.attackerStartZ = attacker.getZ();
                state.victimStartX = victim.getX(); state.victimStartY = victim.getY(); state.victimStartZ = victim.getZ();
            }
            lockPlayer(attacker, state.attackerStartX, state.attackerStartY, state.attackerStartZ);
            lockPlayer(victim, state.victimStartX, state.victimStartY, state.victimStartZ);
            forceLookAt(attacker, victim); forceLookAt(victim, attacker);
            float progress = (float) (0.55 + (state.attackerPresses * 1.5 - state.victimPresses) / 100.0);
            if (progress >= 1.01F) {
                BrokenBottleItem.onStruggleAttackerWin(attacker, victim, attacker.level());
                victim.kill(attacker.level());
                sendStruggleEnd(attacker, victim, true);
                remove.add(attackerId);
                continue;
            }
            boolean damagePhase = progress >= .7F;
            if (damagePhase && !state.reachedThreshold) state.lastDamageTime = time;
            state.reachedThreshold = damagePhase;
            int damageInterval = Math.max(10, 60 - Math.round((progress - .7F) / .3F * 50));
            if (damagePhase && time - state.lastDamageTime >= damageInterval) {
                hurtWithoutKilling(victim, 2.0F);
                state.lastDamageTime = time;
            }
            BottleStruggleUpdatePayload update = new BottleStruggleUpdatePayload(Math.clamp(progress, 0.0F, 1.0F),
                state.attackerPresses, state.victimPresses, state.reachedThreshold);
            ServerPlayNetworking.send(attacker, update); ServerPlayNetworking.send(victim, update);
            if (time - state.startTime >= 600) {
                if (state.reachedThreshold) BrokenBottleItem.onStruggleAttackerWin(attacker, victim, attacker.level());
                else BrokenBottleItem.onStruggleFail(attacker, victim);
                sendStruggleEnd(attacker, victim, state.reachedThreshold); remove.add(attackerId);
            }
        }
        remove.forEach(ModPlayerState.bottleStruggle::remove);
    }

    private static void notifyAndRemoveStruggles(UUID playerId, MinecraftServer server) {
        if (server == null) return;
        for (Map.Entry<UUID, ModPlayerState.BottleStruggleState> entry : ModPlayerState.bottleStruggle.entrySet()) {
            ModPlayerState.BottleStruggleState state = entry.getValue();
            if (entry.getKey().equals(playerId) || state.victimId.equals(playerId)) {
                sendStruggleEnd(server.getPlayerList().getPlayer(entry.getKey()), server.getPlayerList().getPlayer(state.victimId), state.reachedThreshold);
                ModPlayerState.bottleStruggle.remove(entry.getKey());
            }
        }
    }

    private static void sendStruggleEnd(ServerPlayer attacker, ServerPlayer victim, boolean attackerWon) {
        BottleStruggleEndPayload payload = new BottleStruggleEndPayload(attackerWon);
        if (attacker != null) ServerPlayNetworking.send(attacker, payload);
        if (victim != null) ServerPlayNetworking.send(victim, payload);
    }

    private static void applyDizziness(ServerPlayer player, int duration) {
        BuiltInRegistries.MOB_EFFECT.get(DIZZINESS_EFFECT).ifPresent(effect ->
            player.addEffect(new MobEffectInstance(effect, duration, 0, false, false, true)));
    }

    private static void hurtWithoutKilling(Player player, float amount) {
        if (player.getHealth() > 1.0F) {
            player.hurt(player.damageSources().generic(), Math.min(amount, player.getHealth() - 1.0F));
        }
    }

    private static void lockPlayer(ServerPlayer player, double x, double y, double z) {
        player.xxa = 0; player.zza = 0; player.setSprinting(false);
        player.setDeltaMovement(0, Math.min(player.getDeltaMovement().y, 0), 0);
        if (player.distanceToSqr(x, y, z) > .01) player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
    }

    private static void forceLookAt(ServerPlayer player, ServerPlayer target) {
        double dx = target.getX() - player.getX(), dy = target.getEyeY() - player.getEyeY(), dz = target.getZ() - player.getZ();
        float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI - 90);
        float pitch = (float) (-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI);
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, pitch);
    }

    private static void cleanupTextDisplays(ServerLevel level, long time) {
        List<Display.TextDisplay> remove = new ArrayList<>();
        for (var entity : level.getAllEntities()) if (entity instanceof Display.TextDisplay display) {
            for (String tag : display.getTags()) if (tag.startsWith("manosaba_follow_")) {
                try {
                    Player target = level.getPlayerByUUID(UUID.fromString(tag.substring("manosaba_follow_".length())));
                    if (target != null && target.isAlive()) display.setPos(target.getX(), target.getY() + 2, target.getZ());
                } catch (IllegalArgumentException ignored) {}
            }
            for (String tag : display.getTags()) if (tag.startsWith("manosaba_sketchbook_")) {
                String[] parts = tag.substring("manosaba_sketchbook_".length()).split("_");
                if (parts.length < 2) break;
                try {
                    long end = Long.parseLong(parts[0]), fade = Long.parseLong(parts[1]);
                    if (time >= end) remove.add(display);
                    else if (time >= fade) display.setTextOpacity((byte) Math.clamp((int) ((end - time) * 255 / Math.max(1, end - fade)), 0, 255));
                } catch (NumberFormatException ignored) {}
                break;
            }
        }
        remove.forEach(Display.TextDisplay::discard);
        for (var entity : level.getAllEntities()) if (entity instanceof Display.ItemDisplay display) {
            for (String tag : display.getTags()) if (tag.startsWith("manosaba_revolver_display_")) {
                try {
                    int itemId = Integer.parseInt(tag.substring("manosaba_revolver_display_".length()));
                    var pickup = level.getEntity(itemId);
                    if (pickup == null || !pickup.isAlive()) display.discard();
                    else display.setPos(pickup.getX(), pickup.getY(), pickup.getZ());
                } catch (NumberFormatException ignored) { display.discard(); }
                break;
            }
        }
    }

    private static void tickBottleBreakParticles(MinecraftServer server) {
        List<Long> remove = new ArrayList<>();
        for (Map.Entry<Long, ModPlayerState.BottleBreakParticle> entry : ModPlayerState.bottleBreakParticles.entrySet()) {
            ModPlayerState.BottleBreakParticle particle = entry.getValue(); ServerLevel level = server.getLevel(particle.dimension);
            if (level == null || level.getGameTime() >= particle.expiryTime) { remove.add(entry.getKey()); continue; }
            int color = switch (particle.particleType) { case 0 -> 0x3333FF; case 1 -> 0x990000; case 2 -> 0xAAAAAA; default -> -1; };
            if (color != -1) level.sendParticles(new DustParticleOptions(color, 1.5F), particle.x, particle.y + .2, particle.z, 3, .3, .1, .3, .02);
        }
        remove.forEach(ModPlayerState.bottleBreakParticles::remove);
    }

    private static void replaceInventoryStack(Player player, ItemStack stack) {
        int slot = player.getInventory().findSlotMatchingItem(stack);
        if (slot >= 0) player.getInventory().setItem(slot, new ItemStack(ModItems.BLOOD_GOLDEN_SWORD, stack.getCount()));
    }
    private static void clearPlayerState(UUID id) {
        ModPlayerState.poison.remove(id); ModPlayerState.tldjm.remove(id); ModPlayerState.golduck.remove(id);
        ModPlayerState.sleepingPills.remove(id); ModPlayerState.zhangXuefeng.remove(id); ModPlayerState.bottleStabDamage.remove(id);
        PREVIOUS_MAIN_HAND.remove(id); PREVIOUS_BLOODIED_MAIN_HAND.remove(id);
        REVOLVER_RELOADS.remove(id); REVOLVER_CHECKS.remove(id); RevolverItem.clearReloading(id);
    }
}
