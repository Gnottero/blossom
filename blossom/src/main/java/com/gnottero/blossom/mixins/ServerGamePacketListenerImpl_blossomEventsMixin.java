package com.gnottero.blossom.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

import com.gnottero.blossom.event.Event;
import com.gnottero.blossom.event.Fire;
import blossom.value.StringValue;

/**
 * The events born from a client packet: jumps, sneaking, sprinting, clicks, dropped items, chosen
 * recipes, typed commands.
 *
 * <p>Nearly all of them are hooked with a {@code @Redirect} on the call that carries the action out,
 * and not with an {@code @Inject} beside it: it is the only shape in which "the handler said no"
 * really means the game does not do it, instead of an event that watches and lies about being
 * cancellable.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImpl_blossomEventsMixin
{
    @Shadow
    public ServerPlayer player;

    /**
     * {@code player_rides}: the commands a mounted player gives to their mount.
     *
     * <p>Cancelling replaces them with an input that asks for nothing, keeping sneaking and
     * sprinting, which belong to the player and not to the mount.
     */
    @Redirect(method = "handlePlayerInput", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;setLastClientInput(Lnet/minecraft/world/entity/player/Input;)V"))
    private void blossom$onMountControls(ServerPlayer target, Input input)
    {
        if (Event.PLAYER_RIDES.isListened()
                && (input.jump() || input.shift() || input.forward() || input.backward() || input.left()
                        || input.right())
                && Fire.mountControls(player,
                        input.left() == input.right() ? 0 : (input.left() ? -1 : 1),
                        input.forward() == input.backward() ? 0 : (input.forward() ? 1 : -1),
                        input.jump(), input.shift()))
        {
            target.setLastClientInput(new Input(false, false, false, false, false, input.shift(), input.sprint()));
            return;
        }
        target.setLastClientInput(input);
    }

    /** {@code player_starts_sneaking} and {@code player_stops_sneaking}: cancelling keeps the state. */
    @Redirect(method = "handlePlayerInput", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;setShiftKeyDown(Z)V"))
    private void blossom$onSneak(ServerPlayer target, boolean sneaking)
    {
        if (target.isShiftKeyDown() != sneaking
                && Fire.player(sneaking ? Event.PLAYER_STARTS_SNEAKING : Event.PLAYER_STOPS_SNEAKING, player))
        {
            return;
        }
        target.setShiftKeyDown(sneaking);
    }

    @Redirect(method = "handleMovePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;jumpFromGround()V"))
    private void blossom$onJump(ServerPlayer target)
    {
        if (!Fire.player(Event.PLAYER_JUMPS, player))
        {
            target.jumpFromGround();
        }
    }

    @Inject(method = "handlePlayerAction", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;drop(Z)V", ordinal = 0, shift = At.Shift.BEFORE))
    private void blossom$onDropItem(ServerboundPlayerActionPacket packet, CallbackInfo ci)
    {
        if (Fire.player(Event.PLAYER_DROPS_ITEM, player))
        {
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerAction", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;drop(Z)V", ordinal = 1, shift = At.Shift.BEFORE))
    private void blossom$onDropStack(ServerboundPlayerActionPacket packet, CallbackInfo ci)
    {
        if (Fire.player(Event.PLAYER_DROPS_STACK, player))
        {
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerAction", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;",
            ordinal = 1, shift = At.Shift.BEFORE))
    private void blossom$onSwapHands(ServerboundPlayerActionPacket packet, CallbackInfo ci)
    {
        if (Fire.player(Event.PLAYER_SWAPS_HANDS, player))
        {
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerAction", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayerGameMode;handleBlockBreakAction(Lnet/minecraft/core/BlockPos;Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket$Action;Lnet/minecraft/core/Direction;II)V",
            shift = At.Shift.BEFORE))
    private void blossom$onClickBlock(ServerboundPlayerActionPacket packet, CallbackInfo ci)
    {
        if (packet.getAction() == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                && Fire.blockClick(Event.PLAYER_CLICKS_BLOCK, player, packet.getPos(), packet.getDirection()))
        {
            ci.cancel();
        }
    }

    @Redirect(method = "handlePlayerAction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;releaseUsingItem()V"))
    private void blossom$onReleaseItem(ServerPlayer target)
    {
        if (!Event.PLAYER_RELEASES_ITEM.isListened())
        {
            target.releaseUsingItem();
            return;
        }
        InteractionHand hand = target.getUsedItemHand();
        ItemStack stack = target.getUseItem().copy();
        target.releaseUsingItem();
        Fire.item(Event.PLAYER_RELEASES_ITEM, player, hand, stack);
    }

    @Inject(method = "handleUseItemOn", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayerGameMode;useItemOn(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private void blossom$onRightClickBlock(ServerboundUseItemOnPacket packet, CallbackInfo ci)
    {
        if (Fire.blockHitWithItem(Event.PLAYER_RIGHT_CLICKS_BLOCK, player, packet.getHand(), packet.getHitResult()))
        {
            ci.cancel();
        }
    }

    @Inject(method = "handleUseItem", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;resetLastActionTime()V"))
    private void blossom$onUseItem(ServerboundUseItemPacket packet, CallbackInfo ci)
    {
        if (Event.PLAYER_USES_ITEM.isListened())
        {
            InteractionHand hand = packet.getHand();
            if (Fire.item(Event.PLAYER_USES_ITEM, player, hand, player.getItemInHand(hand).copy()))
            {
                ci.cancel();
            }
        }
    }

    @Redirect(method = "handlePlayerCommand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;setSprinting(Z)V", ordinal = 0))
    private void blossom$onStartSprinting(ServerPlayer target, boolean sprinting)
    {
        if (!Fire.player(Event.PLAYER_STARTS_SPRINTING, player))
        {
            target.setSprinting(sprinting);
        }
    }

    @Redirect(method = "handlePlayerCommand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;setSprinting(Z)V", ordinal = 1))
    private void blossom$onStopSprinting(ServerPlayer target, boolean sprinting)
    {
        if (!Fire.player(Event.PLAYER_STOPS_SPRINTING, player))
        {
            target.setSprinting(sprinting);
        }
    }

    /**
     * {@code player_escapes_sleep}: the key pressed while not asleep. Waking up for real is the
     * redirect below.
     *
     * <p>Cancelling drops the leave-bed action, and the honest version of that sentence is that
     * today it changes nothing anybody can see: in this branch the game does nothing, so there is
     * nothing to stop. The hook is real, the branch is empty.
     */
    @Inject(method = "handlePlayerCommand", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;isSleeping()Z", shift = At.Shift.BEFORE))
    private void blossom$onEscapeSleep(ServerboundPlayerCommandPacket packet, CallbackInfo ci)
    {
        if (!player.isSleeping() && Fire.player(Event.PLAYER_ESCAPES_SLEEP, player))
        {
            ci.cancel();
        }
    }

    @Redirect(method = "handlePlayerCommand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;stopSleepInBed(ZZ)V"))
    private void blossom$onWakeUp(ServerPlayer target, boolean wakeImmediately, boolean updateLevelForSleepingPlayers)
    {
        if (!Fire.player(Event.PLAYER_WAKES_UP, player))
        {
            target.stopSleepInBed(wakeImmediately, updateLevelForSleepingPlayers);
        }
    }

    /** Cancelling answers "not flying", which is what makes the game call {@code stopFallFlying}. */
    @Redirect(method = "handlePlayerCommand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;tryToStartFallFlying()Z"))
    private boolean blossom$onElytra(ServerPlayer target)
    {
        return !Fire.player(Event.PLAYER_DEPLOYS_ELYTRA, player) && target.tryToStartFallFlying();
    }

    @Inject(method = "handlePlaceRecipe", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;resetLastActionTime()V"))
    private void blossom$onChooseRecipe(ServerboundPlaceRecipePacket packet, CallbackInfo ci)
    {
        if (!Event.PLAYER_CHOOSES_RECIPE.isListened())
        {
            return;
        }
        RecipeManager.ServerDisplayInfo display = player.level().getServer().getRecipeManager()
                .getRecipeFromDisplay(packet.recipe());
        if (display == null)
        {
            return;
        }
        if (Fire.recipe(player, display.parent().id().identifier(), packet.useMaxItems()))
        {
            ci.cancel();
        }
    }

    /**
     * {@code player_switches_slot}: cancelling keeps the old slot.
     *
     * <p>The client has already moved its own selection when the packet leaves, so it has to be
     * told to move it back: without the packet the two sides disagree until the next switch.
     */
    @Inject(method = "handleSetCarriedItem", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V", shift = At.Shift.BEFORE))
    private void blossom$onSwitchSlot(ServerboundSetCarriedItemPacket packet, CallbackInfo ci)
    {
        int from = player.getInventory().getSelectedSlot();
        if (Fire.slotSwitch(player, from, packet.getSlot()))
        {
            player.connection.send(new ClientboundSetHeldSlotPacket(from));
            ci.cancel();
        }
    }

    @Redirect(method = "handleAnimate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private void blossom$onSwing(ServerPlayer target, InteractionHand hand)
    {
        if (!target.swinging && Fire.handAction(Event.PLAYER_SWINGS_HAND, player, hand))
        {
            return;
        }
        target.swing(hand);
    }

    /**
     * {@code player_command}: the body that runs a command typed in chat, before it runs.
     *
     * <p>The signature has already been checked here, which is the part that cannot be undone; the
     * command itself has not started, so cancelling really does stop it.
     */
    @Inject(method = "lambda$handleChatCommand$0", cancellable = true, at = @At("HEAD"))
    private void blossom$onCommand(ServerboundChatCommandPacket packet, CallbackInfo ci)
    {
        if (Event.PLAYER_COMMAND.isListened()
                && Event.PLAYER_COMMAND.fireForPlayer(player, () -> java.util.List.of(
                        new com.gnottero.blossom.value.EntityValue(player), StringValue.of(packet.command()))))
        {
            ci.cancel();
        }
    }
}
