package com.gnottero.blossom.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
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
 * Gli eventi che nascono da un pacchetto del client: salti, sneak, sprint, click, oggetti lasciati
 * cadere, ricette scelte, comandi scritti.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImpl_blossomEventsMixin
{
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handlePlayerInput", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;setLastClientInput(Lnet/minecraft/world/entity/player/Input;)V"))
    private void blossom$onInput(ServerboundPlayerInputPacket packet, CallbackInfo ci)
    {
        Input input = packet.input();
        boolean wasSneaking = player.isShiftKeyDown();
        if (wasSneaking != input.shift())
        {
            Fire.player(input.shift() ? Event.PLAYER_STARTS_SNEAKING : Event.PLAYER_STOPS_SNEAKING, player);
        }
        if (Event.PLAYER_RIDES.isListened()
                && (input.jump() || input.shift() || input.forward() || input.backward() || input.left() || input.right()))
        {
            Fire.mountControls(player,
                    input.left() == input.right() ? 0 : (input.left() ? -1 : 1),
                    input.forward() == input.backward() ? 0 : (input.forward() ? 1 : -1),
                    input.jump(), input.shift());
        }
    }

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;jumpFromGround()V"))
    private void blossom$onJump(ServerboundMovePlayerPacket packet, CallbackInfo ci)
    {
        Fire.player(Event.PLAYER_JUMPS, player);
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

    @Inject(method = "handlePlayerCommand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;setSprinting(Z)V", ordinal = 0))
    private void blossom$onStartSprinting(ServerboundPlayerCommandPacket packet, CallbackInfo ci)
    {
        Fire.player(Event.PLAYER_STARTS_SPRINTING, player);
    }

    @Inject(method = "handlePlayerCommand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;setSprinting(Z)V", ordinal = 1))
    private void blossom$onStopSprinting(ServerboundPlayerCommandPacket packet, CallbackInfo ci)
    {
        Fire.player(Event.PLAYER_STOPS_SPRINTING, player);
    }

    @Inject(method = "handlePlayerCommand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;isSleeping()Z", shift = At.Shift.BEFORE))
    private void blossom$onWakeUp(ServerboundPlayerCommandPacket packet, CallbackInfo ci)
    {
        Fire.player(player.isSleeping() ? Event.PLAYER_WAKES_UP : Event.PLAYER_ESCAPES_SLEEP, player);
    }

    @Inject(method = "handlePlayerCommand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;tryToStartFallFlying()Z", shift = At.Shift.BEFORE))
    private void blossom$onElytra(ServerboundPlayerCommandPacket packet, CallbackInfo ci)
    {
        Fire.player(Event.PLAYER_DEPLOYS_ELYTRA, player);
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

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"))
    private void blossom$onSwitchSlot(ServerboundSetCarriedItemPacket packet, CallbackInfo ci)
    {
        if (Event.PLAYER_SWITCHES_SLOT.isListened() && player.level().getServer() != null
                && player.level().getServer().isSameThread())
        {
            Fire.slotSwitch(player, player.getInventory().getSelectedSlot(), packet.getSlot());
        }
    }

    @Inject(method = "handleAnimate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;resetLastActionTime()V", shift = At.Shift.BEFORE))
    private void blossom$onSwing(ServerboundSwingPacket packet, CallbackInfo ci)
    {
        if (!player.swinging)
        {
            Fire.handAction(Event.PLAYER_SWINGS_HAND, player, packet.getHand());
        }
    }

    @Inject(method = "lambda$handleChatCommand$0", at = @At("HEAD"))
    private void blossom$onCommand(ServerboundChatCommandPacket packet, CallbackInfo ci)
    {
        if (Event.PLAYER_COMMAND.isListened())
        {
            Event.PLAYER_COMMAND.fireForPlayer(player, () -> java.util.List.of(
                    new com.gnottero.blossom.value.EntityValue(player), StringValue.of(packet.command())));
        }
    }
}
