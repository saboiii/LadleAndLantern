package com.ladleandlantern.core.menu;

import com.ladleandlantern.core.block.entity.SmallStorageBlockEntity;

import com.ladleandlantern.core.registry.MenuTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

public class StorageMenu extends AbstractContainerMenu {
    private final Container container;

    public StorageMenu(int id, Inventory inv, SmallStorageBlockEntity storage) {
        this(id, inv, storage, storage::accepts);
    }

    public static StorageMenu client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new StorageMenu(id, inv, new SimpleContainer(SmallStorageBlockEntity.SIZE), s -> true);
    }

    private StorageMenu(int id, Inventory inv, Container container, Predicate<ItemStack> filter) {
        super(MenuTypeRegistry.STORAGE.get(), id);
        this.container = container;
        container.startOpen(inv.player);
        for (int i = 0; i < SmallStorageBlockEntity.SIZE; i++) {
            addSlot(new Slot(container, i, 8 + i * 18, 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return filter.test(stack);
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 49 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 107));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int size = SmallStorageBlockEntity.SIZE;
        if (index < size) {
            if (!moveItemStackTo(stack, size, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, size, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
