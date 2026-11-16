package com.pockyl.handson_delight.client;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.handson_delight.station.StationRecipes;

/**
 * A stack clicked onto a station's opening when its slots have no place for it, every one holding other food: the library would
 * refuse it, so it is offered to the logic, which takes it in when the food still makes a dish ({@link StationRecipes#offer}).
 */
final class FullZone {
    private FullZone() {
    }

    /** Offers {@code stack} on the cursor to {@code group} when the group takes it but has no place for it; true when sent. */
    static boolean offer(StationView view, String group, ItemStack stack, int button) {
        if (!isFull(view, group, stack)) {
            return false;
        }
        CompoundTag data = new CompoundTag();
        data.putBoolean(StationRecipes.ONE, button == 1);
        view.sendAction(StationRecipes.ACTION_OFFER, data);
        return true;
    }

    /** Whether {@code group} takes {@code stack} and every slot holds something it cannot join. */
    static boolean isFull(StationView view, String group, ItemStack stack) {
        StationSchema.SlotGroup slots = view.state().schema().slotGroups().get(group);
        if (slots == null || stack.isEmpty() || !slots.rules().accepts().test(stack)) {
            return false;
        }
        for (int i = 0; i < slots.size(); i++) {
            ItemStack there = view.state().getItem(slots.ref(i));
            if (there.isEmpty() || ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < slots.rules().limitFor(there)) {
                return false;
            }
        }
        return true;
    }
}
