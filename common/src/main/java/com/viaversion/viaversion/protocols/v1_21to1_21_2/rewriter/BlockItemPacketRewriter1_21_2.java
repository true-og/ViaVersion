/*
 * This file is part of ViaVersion - https://github.com/ViaVersion/ViaVersion
 * Copyright (C) 2016-2024 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.viaversion.viaversion.protocols.v1_21to1_21_2.rewriter;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.StringTag;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.data.FullMappings;
import com.viaversion.viaversion.api.data.MappingData;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.minecraft.Holder;
import com.viaversion.viaversion.api.minecraft.HolderSet;
import com.viaversion.viaversion.api.minecraft.Particle;
import com.viaversion.viaversion.api.minecraft.SoundEvent;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataContainer;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataKey;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.minecraft.item.data.Consumable1_21_2;
import com.viaversion.viaversion.api.minecraft.item.data.DamageResistant;
import com.viaversion.viaversion.api.minecraft.item.data.FoodProperties1_20_5;
import com.viaversion.viaversion.api.minecraft.item.data.FoodProperties1_21_2;
import com.viaversion.viaversion.api.minecraft.item.data.Instrument1_20_5;
import com.viaversion.viaversion.api.minecraft.item.data.Instrument1_21_2;
import com.viaversion.viaversion.api.minecraft.item.data.PotionEffect;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.chunk.ChunkType1_20_2;
import com.viaversion.viaversion.api.type.types.version.Types1_21;
import com.viaversion.viaversion.api.type.types.version.Types1_21_2;
import com.viaversion.viaversion.protocols.v1_20_5to1_21.packet.ClientboundPacket1_21;
import com.viaversion.viaversion.protocols.v1_20_5to1_21.packet.ClientboundPackets1_21;
import com.viaversion.viaversion.protocols.v1_21to1_21_2.Protocol1_21To1_21_2;
import com.viaversion.viaversion.protocols.v1_21to1_21_2.packet.ClientboundPackets1_21_2;
import com.viaversion.viaversion.protocols.v1_21to1_21_2.packet.ServerboundPacket1_21_2;
import com.viaversion.viaversion.protocols.v1_21to1_21_2.packet.ServerboundPackets1_21_2;
import com.viaversion.viaversion.rewriter.BlockRewriter;
import com.viaversion.viaversion.rewriter.SoundRewriter;
import com.viaversion.viaversion.rewriter.StructuredItemRewriter;
import com.viaversion.viaversion.util.Key;
import com.viaversion.viaversion.util.TagUtil;
import com.viaversion.viaversion.util.Unit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class BlockItemPacketRewriter1_21_2 extends StructuredItemRewriter<ClientboundPacket1_21, ServerboundPacket1_21_2, Protocol1_21To1_21_2> {

    private static final int RECIPE_NOTIFICATION_FLAG = 1 << 0;
    private static final int RECIPE_HIGHLIGHT_FLAG = 1 << 1;
    private static final int RECIPE_INIT = 0;
    private static final int RECIPE_ADD = 1;
    private static final int RECIPE_REMOVE = 2;

    public BlockItemPacketRewriter1_21_2(final Protocol1_21To1_21_2 protocol) {
        super(protocol,
            Types1_21.ITEM, Types1_21.ITEM_ARRAY, Types1_21_2.ITEM, Types1_21_2.ITEM_ARRAY,
            Types1_21.ITEM_COST, Types1_21.OPTIONAL_ITEM_COST, Types1_21_2.ITEM_COST, Types1_21_2.OPTIONAL_ITEM_COST,
            Types1_21.PARTICLE, Types1_21_2.PARTICLE
        );
    }

    @Override
    public void registerPackets() {
        final BlockRewriter<ClientboundPacket1_21> blockRewriter = BlockRewriter.for1_20_2(protocol);
        blockRewriter.registerBlockEvent(ClientboundPackets1_21.BLOCK_EVENT);
        blockRewriter.registerBlockUpdate(ClientboundPackets1_21.BLOCK_UPDATE);
        blockRewriter.registerSectionBlocksUpdate1_20(ClientboundPackets1_21.SECTION_BLOCKS_UPDATE);
        blockRewriter.registerLevelEvent1_21(ClientboundPackets1_21.LEVEL_EVENT, 2001);
        blockRewriter.registerLevelChunk1_19(ClientboundPackets1_21.LEVEL_CHUNK_WITH_LIGHT, ChunkType1_20_2::new);
        blockRewriter.registerBlockEntityData(ClientboundPackets1_21.BLOCK_ENTITY_DATA);

        registerAdvancements1_20_3(ClientboundPackets1_21.UPDATE_ADVANCEMENTS);
        registerSetEquipment(ClientboundPackets1_21.SET_EQUIPMENT);
        registerMerchantOffers1_20_5(ClientboundPackets1_21.MERCHANT_OFFERS);
        registerSetCreativeModeSlot(ServerboundPackets1_21_2.SET_CREATIVE_MODE_SLOT);
        registerLevelParticles1_20_5(ClientboundPackets1_21.LEVEL_PARTICLES);

        protocol.registerClientbound(ClientboundPackets1_21.COOLDOWN, wrapper -> {
            final MappingData mappingData = protocol.getMappingData();
            final int itemId = wrapper.read(Types.VAR_INT);
            final int mappedItemId = mappingData.getNewItemId(itemId);
            wrapper.write(Types.STRING, mappingData.getFullItemMappings().mappedIdentifier(mappedItemId));
        });

        protocol.registerClientbound(ClientboundPackets1_21.CONTAINER_SET_CONTENT, wrapper -> {
            updateContainerId(wrapper);
            wrapper.passthrough(Types.VAR_INT); // State id
            final Item[] items = wrapper.read(itemArrayType());
            wrapper.write(mappedItemArrayType(), items);
            for (int i = 0; i < items.length; i++) {
                items[i] = handleItemToClient(wrapper.user(), items[i]);
            }
            passthroughClientboundItem(wrapper);
        });
        protocol.registerClientbound(ClientboundPackets1_21.CONTAINER_SET_SLOT, wrapper -> {
            updateContainerId(wrapper);
            final int containerId = wrapper.get(Types.VAR_INT, 0);
            if (containerId == -1) { // cursor item
                wrapper.setPacketType(ClientboundPackets1_21_2.SET_CURSOR_ITEM);
                wrapper.resetReader();
                wrapper.read(Types.VAR_INT); // container id
                wrapper.read(Types.VAR_INT); // State id
                wrapper.read(Types.SHORT); // Slot id
            } else if (containerId == -2) { // cursor item
                wrapper.setPacketType(ClientboundPackets1_21_2.SET_PLAYER_INVENTORY);
                wrapper.resetReader();
                wrapper.read(Types.VAR_INT); // container id
                wrapper.read(Types.VAR_INT); // State id
                wrapper.write(Types.VAR_INT, (int) wrapper.read(Types.SHORT)); // Slot id
            } else {
                wrapper.passthrough(Types.VAR_INT); // State id
                wrapper.passthrough(Types.SHORT); // Slot id
            }
            passthroughClientboundItem(wrapper);
        });
        protocol.registerClientbound(ClientboundPackets1_21.CONTAINER_CLOSE, this::updateContainerId);
        protocol.registerClientbound(ClientboundPackets1_21.CONTAINER_SET_DATA, this::updateContainerId);
        protocol.registerClientbound(ClientboundPackets1_21.HORSE_SCREEN_OPEN, this::updateContainerId);
        protocol.registerClientbound(ClientboundPackets1_21.SET_CARRIED_ITEM, ClientboundPackets1_21_2.SET_HELD_SLOT);
        protocol.registerServerbound(ServerboundPackets1_21_2.CONTAINER_CLOSE, this::updateContainerIdServerbound);
        protocol.registerServerbound(ServerboundPackets1_21_2.CONTAINER_CLICK, wrapper -> {
            updateContainerIdServerbound(wrapper);
            wrapper.passthrough(Types.VAR_INT); // State id
            wrapper.passthrough(Types.SHORT); // Slot
            wrapper.passthrough(Types.BYTE); // Button
            wrapper.passthrough(Types.VAR_INT); // Mode
            final int length = wrapper.passthrough(Types.VAR_INT);
            for (int i = 0; i < length; i++) {
                wrapper.passthrough(Types.SHORT); // Slot
                passthroughServerboundItem(wrapper);
            }
            passthroughServerboundItem(wrapper);
        });
        protocol.registerClientbound(ClientboundPackets1_21.PLACE_GHOST_RECIPE, wrapper -> {
            this.updateContainerId(wrapper);

            final String recipeKey = wrapper.read(Types.STRING);
            final RecipeRewriter1_21_2.Recipe recipe = wrapper.user().get(RecipeRewriter1_21_2.class).recipe(recipeKey);
            if (recipe == null) {
                wrapper.cancel();
                return;
            }

            wrapper.write(Types.VAR_INT, recipe.recipeDisplayId());
            recipe.writeRecipeDisplay(wrapper);
        });
        protocol.registerServerbound(ServerboundPackets1_21_2.PLACE_RECIPE, wrapper -> {
            this.updateContainerIdServerbound(wrapper);
            convertServerboundRecipeDisplayId(wrapper);
        });
        protocol.registerServerbound(ServerboundPackets1_21_2.RECIPE_BOOK_SEEN_RECIPE, this::convertServerboundRecipeDisplayId);

        protocol.registerServerbound(ServerboundPackets1_21_2.USE_ITEM_ON, wrapper -> {
            wrapper.passthrough(Types.VAR_INT); // Hand
            wrapper.passthrough(Types.BLOCK_POSITION1_14); // Block position
            wrapper.passthrough(Types.VAR_INT); // Direction
            wrapper.passthrough(Types.FLOAT); // X
            wrapper.passthrough(Types.FLOAT); // Y
            wrapper.passthrough(Types.FLOAT); // Z
            wrapper.passthrough(Types.BOOLEAN); // Inside
            wrapper.read(Types.BOOLEAN); // World border hit
        });

        protocol.registerClientbound(ClientboundPackets1_21.EXPLODE, wrapper -> {
            final int centerX = (int) Math.floor(wrapper.passthrough(Types.DOUBLE)); // Center X
            final int centerY = (int) Math.floor(wrapper.passthrough(Types.DOUBLE)); // Center Y
            final int centerZ = (int) Math.floor(wrapper.passthrough(Types.DOUBLE)); // Center Z

            final float power = wrapper.read(Types.FLOAT); // Power
            final List<BlockPosition> affectedBlocks = new ArrayList<>();
            final int blocks = wrapper.read(Types.VAR_INT);
            for (int i = 0; i < blocks; i++) {
                final int x = centerX + wrapper.read(Types.BYTE); // Relative X
                final int y = centerY + wrapper.read(Types.BYTE); // Relative Y
                final int z = centerZ + wrapper.read(Types.BYTE); // Relative Z
                affectedBlocks.add(new BlockPosition(x, y, z));
            }

            final float knockbackX = wrapper.read(Types.FLOAT);
            final float knockbackY = wrapper.read(Types.FLOAT);
            final float knockbackZ = wrapper.read(Types.FLOAT);
            if (knockbackX != 0 || knockbackY != 0 || knockbackZ != 0) {
                wrapper.write(Types.BOOLEAN, true);
                wrapper.write(Types.DOUBLE, (double) knockbackX);
                wrapper.write(Types.DOUBLE, (double) knockbackY);
                wrapper.write(Types.DOUBLE, (double) knockbackZ);
            } else {
                wrapper.write(Types.BOOLEAN, false);
            }

            final int blockInteractionMode = wrapper.read(Types.VAR_INT);
            if (blockInteractionMode == 1 || blockInteractionMode == 2) {
                for (final BlockPosition affectedBlock : affectedBlocks) {
                    final PacketWrapper blockUpdate = PacketWrapper.create(ClientboundPackets1_21_2.BLOCK_UPDATE, wrapper.user());
                    blockUpdate.write(Types.BLOCK_POSITION1_14, affectedBlock); // position
                    blockUpdate.write(Types.VAR_INT, 0); // block state
                    blockUpdate.send(Protocol1_21To1_21_2.class);
                }
            }

            final Particle smallExplosionParticle = wrapper.read(Types1_21.PARTICLE);
            final Particle largeExplosionParticle = wrapper.read(Types1_21.PARTICLE);
            if (power >= 2.0F && blockInteractionMode != 0) {
                rewriteParticle(wrapper.user(), largeExplosionParticle);
                wrapper.write(Types1_21_2.PARTICLE, largeExplosionParticle);
            } else {
                rewriteParticle(wrapper.user(), smallExplosionParticle);
                wrapper.write(Types1_21_2.PARTICLE, smallExplosionParticle);
            }

            new SoundRewriter<>(protocol).soundHolderHandler().handle(wrapper);
        });

        protocol.registerClientbound(ClientboundPackets1_21.UPDATE_RECIPES, wrapper -> {
            final FullMappings recipeSerializerMappings = protocol.getMappingData().getRecipeSerializerMappings();
            final RecipeRewriter1_21_2 rewriter = new RecipeRewriter1_21_2(protocol);
            wrapper.user().put(rewriter);

            final int size = wrapper.read(Types.VAR_INT);
            for (int i = 0; i < size; i++) {
                final String recipeIdentifier = wrapper.read(Types.STRING);
                final int serializerTypeId = wrapper.read(Types.VAR_INT);
                final String serializerTypeIdentifier = recipeSerializerMappings.identifier(serializerTypeId);
                rewriter.setCurrentRecipeIdentifier(recipeIdentifier);
                rewriter.handleRecipeType(wrapper, serializerTypeIdentifier);
            }
            rewriter.finalizeRecipes();

            // These are used for client predictions, such smithing or furnace inputs.
            // Other recipes will be written in RECIPE/RECIPE_BOOK_ADD
            rewriter.writeUpdateRecipeInputs(wrapper);
        });

        protocol.registerClientbound(ClientboundPackets1_21.RECIPE, ClientboundPackets1_21_2.RECIPE_BOOK_ADD, wrapper -> {
            final int state = wrapper.passthrough(Types.VAR_INT);

            // Pairs of open + filtering for: Crafting, furnace, blast furnace, smoker
            final PacketWrapper settingsPacket = wrapper.create(ClientboundPackets1_21_2.RECIPE_BOOK_SETTINGS);
            for (int i = 0; i < 4 * 2; i++) {
                settingsPacket.write(Types.BOOLEAN, wrapper.read(Types.BOOLEAN));
            }
            settingsPacket.send(Protocol1_21To1_21_2.class);

            // Read recipe keys from the packet
            final String[] recipes = wrapper.read(Types.STRING_ARRAY);
            Set<String> toHighlight = Set.of();
            if (state == RECIPE_INIT) {
                final String[] highlightRecipes = wrapper.read(Types.STRING_ARRAY);
                toHighlight = Set.of(highlightRecipes);
            }

            final RecipeRewriter1_21_2 recipeRewriter = wrapper.user().get(RecipeRewriter1_21_2.class);
            if (recipeRewriter == null) {
                protocol.getLogger().severe("Recipes not yet sent for recipe add packet");
                wrapper.cancel();
                return;
            }

            wrapper.clearPacket();

            if (state == RECIPE_REMOVE) {
                wrapper.setPacketType(ClientboundPackets1_21_2.RECIPE_BOOK_REMOVE);

                final int[] ids = new int[recipes.length];
                for (int i = 0; i < recipes.length; i++) {
                    final String recipeKey = recipes[i];
                    final RecipeRewriter1_21_2.Recipe recipe = recipeRewriter.recipe(recipeKey);
                    if (recipe == null) {
                        protocol.getLogger().severe("Recipe not found for key " + recipeKey);
                        wrapper.cancel();
                        return;
                    }

                    ids[i] = recipe.index();
                }

                wrapper.write(Types.VAR_INT_ARRAY_PRIMITIVE, ids);
                return;
            }

            // Add or init recipes
            int size = recipes.length;
            wrapper.write(Types.VAR_INT, size);
            for (final String recipeKey : recipes) {
                final RecipeRewriter1_21_2.Recipe recipe = recipeRewriter.recipe(recipeKey);
                if (recipe == null) {
                    // Stonecutting and smithing recipes, or bad data
                    size--;
                    continue;
                }

                wrapper.write(Types.VAR_INT, recipe.index()); // Display ID, just an arbitrary index as determined by the server

                wrapper.write(Types.VAR_INT, recipe.recipeDisplayId());
                recipe.writeRecipeDisplay(wrapper);

                wrapper.write(Types.OPTIONAL_VAR_INT, recipe.group() != -1 ? recipe.group() : null);
                wrapper.write(Types.VAR_INT, recipe.category());

                final Item[][] ingredients = recipe.ingredients();
                if (ingredients != null) {
                    wrapper.write(Types.BOOLEAN, true);

                    // Why are some of these empty? Who knows, but they can't be
                    final List<HolderSet> filteredIngredients = Arrays.stream(ingredients).filter(ingredient -> ingredient.length > 0).map(recipeRewriter::toHolderSet).toList();
                    wrapper.write(Types.VAR_INT, filteredIngredients.size());
                    for (final HolderSet ingredient : filteredIngredients) {
                        wrapper.write(Types.HOLDER_SET, ingredient);
                    }
                } else {
                    wrapper.write(Types.BOOLEAN, false);
                }

                byte flags = 0;
                if (state == RECIPE_ADD) {
                    if (recipe.showNotification()) {
                        flags |= RECIPE_NOTIFICATION_FLAG;
                    }
                    flags |= RECIPE_HIGHLIGHT_FLAG;
                } else if (toHighlight.contains(recipeKey)) {
                    flags |= RECIPE_HIGHLIGHT_FLAG;
                }
                wrapper.write(Types.BYTE, flags);
            }

            // Update final size
            wrapper.set(Types.VAR_INT, 0, size);

            wrapper.write(Types.BOOLEAN, state == RECIPE_INIT); // Replace
        });
    }

    private void convertServerboundRecipeDisplayId(final PacketWrapper wrapper) {
        final int recipeDisplayId = wrapper.read(Types.VAR_INT);
        final RecipeRewriter1_21_2.Recipe recipe = wrapper.user().get(RecipeRewriter1_21_2.class).recipe(recipeDisplayId);
        if (recipe == null) {
            wrapper.cancel();
            return;
        }

        wrapper.write(Types.STRING, recipe.identifier());
    }

    @Override
    public void rewriteParticle(final UserConnection connection, final Particle particle) {
        super.rewriteParticle(connection, particle);

        final String identifier = protocol.getMappingData().getParticleMappings().mappedIdentifier(particle.id());
        if (identifier.equals("minecraft:dust_color_transition")) {
            floatsToARGB(particle, 0);
            floatsToARGB(particle, 1);
        } else if (identifier.equals("minecraft:dust")) {
            floatsToARGB(particle, 0);
        }
    }

    private void floatsToARGB(final Particle particle, final int fromIndex) {
        final Particle.ParticleData<Float> r = particle.removeArgument(fromIndex);
        final Particle.ParticleData<Float> g = particle.removeArgument(fromIndex);
        final Particle.ParticleData<Float> b = particle.removeArgument(fromIndex);
        final int rgb = 255 << 24 | (int) (r.getValue() * 255) << 16 | (int) (g.getValue() * 255) << 8 | (int) (b.getValue() * 255);
        particle.add(fromIndex, Types.INT, rgb);
    }

    @Override
    public Item handleItemToClient(final UserConnection connection, final Item item) {
        super.handleItemToClient(connection, item);
        updateItemData(item);
        return item;
    }

    @Override
    public Item handleItemToServer(final UserConnection connection, final Item item) {
        super.handleItemToServer(connection, item);
        downgradeItemData(item);
        return item;
    }

    private void updateContainerId(final PacketWrapper wrapper) {
        // Container id handling was always a bit whack with most reading them as unsigned bytes, some as bytes, some already as var ints.
        // In VV they're generally read as unsigned bytes to not have to look the type up every time, but we need to make sure they're
        // properly converted to ints when used
        final short containerId = wrapper.read(Types.UNSIGNED_BYTE);
        final int intId = (byte) containerId;
        wrapper.write(Types.VAR_INT, intId);
    }

    private void updateContainerIdServerbound(final PacketWrapper wrapper) {
        final int containerId = wrapper.read(Types.VAR_INT);
        wrapper.write(Types.UNSIGNED_BYTE, (short) containerId);
    }

    public static void updateItemData(final Item item) {
        final StructuredDataContainer dataContainer = item.dataContainer();
        dataContainer.replace(StructuredDataKey.INSTRUMENT1_20_5, StructuredDataKey.INSTRUMENT1_21_2, instrument -> {
            if (instrument.hasId()) {
                return Holder.of(instrument.id());
            }
            final Instrument1_20_5 value = instrument.value();
            return Holder.of(new Instrument1_21_2(value.soundEvent(), value.useDuration() / 20F, value.range(), new StringTag("")));
        });
        dataContainer.replace(StructuredDataKey.FOOD1_21, StructuredDataKey.FOOD1_21_2, food -> {
            final Holder<SoundEvent> sound = Holder.of(new SoundEvent("minecraft:entity.generic.eat", null));
            final Consumable1_21_2.ConsumeEffect<?>[] consumeEffects = new Consumable1_21_2.ConsumeEffect[food.possibleEffects().length];
            for (int i = 0; i < consumeEffects.length; i++) {
                final FoodProperties1_20_5.FoodEffect effect = food.possibleEffects()[i];
                final Consumable1_21_2.ApplyStatusEffects applyStatusEffects = new Consumable1_21_2.ApplyStatusEffects(new PotionEffect[]{effect.effect()}, effect.probability());
                consumeEffects[i] = new Consumable1_21_2.ConsumeEffect<>(0 /* add status effect */, Consumable1_21_2.ApplyStatusEffects.TYPE, applyStatusEffects);
            }
            dataContainer.set(StructuredDataKey.CONSUMABLE1_21_2, new Consumable1_21_2(food.eatSeconds(), 1 /* eat */, sound, true, consumeEffects));
            if (food.usingConvertsTo() != null) {
                dataContainer.set(StructuredDataKey.USE_REMAINDER, food.usingConvertsTo());
            }
            return new FoodProperties1_21_2(food.nutrition(), food.saturationModifier(), food.canAlwaysEat());
        });
        dataContainer.replaceKey(StructuredDataKey.CONTAINER1_21, StructuredDataKey.CONTAINER1_21_2);
        dataContainer.replaceKey(StructuredDataKey.CHARGED_PROJECTILES1_21, StructuredDataKey.CHARGED_PROJECTILES1_21_2);
        dataContainer.replaceKey(StructuredDataKey.BUNDLE_CONTENTS1_21, StructuredDataKey.BUNDLE_CONTENTS1_21_2);
        dataContainer.replaceKey(StructuredDataKey.POTION_CONTENTS1_20_5, StructuredDataKey.POTION_CONTENTS1_21_2);
        dataContainer.replace(StructuredDataKey.FIRE_RESISTANT, StructuredDataKey.DAMAGE_RESISTANT, fireResistant -> new DamageResistant("minecraft:is_fire"));
        dataContainer.replace(StructuredDataKey.LOCK, lock -> {
            final CompoundTag predicateTag = new CompoundTag();
            final CompoundTag itemComponentsTag = new CompoundTag();
            predicateTag.put("components", itemComponentsTag);
            itemComponentsTag.put("custom_name", lock);
            return predicateTag;
        });
    }

    public static void downgradeItemData(final Item item) {
        final StructuredDataContainer dataContainer = item.dataContainer();
        dataContainer.replace(StructuredDataKey.LOCK, StructuredDataKey.LOCK, lock -> {
            final CompoundTag predicateTag = (CompoundTag) lock;
            final CompoundTag itemComponentsTag = predicateTag.getCompoundTag("components");
            if (itemComponentsTag != null) {
                return TagUtil.getNamespacedStringTag(itemComponentsTag, "custom_name");
            }
            return null;
        });
        dataContainer.replace(StructuredDataKey.INSTRUMENT1_21_2, StructuredDataKey.INSTRUMENT1_20_5, instrument -> {
            if (instrument.hasId()) {
                return Holder.of(instrument.id());
            }
            final Instrument1_21_2 value = instrument.value();
            return Holder.of(new Instrument1_20_5(value.soundEvent(), (int) (value.useDuration() * 20), value.range()));
        });
        dataContainer.replace(StructuredDataKey.FOOD1_21_2, StructuredDataKey.FOOD1_21, food -> {
            final Consumable1_21_2 consumableData = dataContainer.get(StructuredDataKey.CONSUMABLE1_21_2);
            final Item useRemainderData = dataContainer.get(StructuredDataKey.USE_REMAINDER);
            final float eatSeconds = consumableData != null ? consumableData.consumeSeconds() : 1.6F;
            final List<FoodProperties1_20_5.FoodEffect> foodEffects = new ArrayList<>();
            if (consumableData != null) {
                for (Consumable1_21_2.ConsumeEffect<?> consumeEffect : consumableData.consumeEffects()) {
                    if (consumeEffect.value() instanceof Consumable1_21_2.ApplyStatusEffects applyStatusEffects) {
                        for (PotionEffect effect : applyStatusEffects.effects()) {
                            foodEffects.add(new FoodProperties1_20_5.FoodEffect(effect, applyStatusEffects.probability()));
                        }
                    }
                }
            }
            return new FoodProperties1_20_5(food.nutrition(), food.saturationModifier(), food.canAlwaysEat(), eatSeconds, useRemainderData, foodEffects.toArray(new FoodProperties1_20_5.FoodEffect[0]));
        });
        dataContainer.replaceKey(StructuredDataKey.CONTAINER1_21_2, StructuredDataKey.CONTAINER1_21);
        dataContainer.replaceKey(StructuredDataKey.CHARGED_PROJECTILES1_21_2, StructuredDataKey.CHARGED_PROJECTILES1_21);
        dataContainer.replaceKey(StructuredDataKey.BUNDLE_CONTENTS1_21_2, StructuredDataKey.BUNDLE_CONTENTS1_21);
        dataContainer.replaceKey(StructuredDataKey.POTION_CONTENTS1_21_2, StructuredDataKey.POTION_CONTENTS1_20_5);
        dataContainer.replace(StructuredDataKey.DAMAGE_RESISTANT, StructuredDataKey.FIRE_RESISTANT, damageResistant -> {
            if (Key.stripMinecraftNamespace(damageResistant.typesTagKey()).equals("is_fire")) {
                return Unit.INSTANCE;
            }
            return null;
        });
        dataContainer.remove(StructuredDataKey.REPAIRABLE);
        dataContainer.remove(StructuredDataKey.ENCHANTABLE);
        dataContainer.remove(StructuredDataKey.CONSUMABLE1_21_2);
        dataContainer.remove(StructuredDataKey.USE_REMAINDER);
        dataContainer.remove(StructuredDataKey.USE_COOLDOWN);
        dataContainer.remove(StructuredDataKey.ITEM_MODEL);
        dataContainer.remove(StructuredDataKey.EQUIPPABLE);
        dataContainer.remove(StructuredDataKey.GLIDER);
        dataContainer.remove(StructuredDataKey.TOOLTIP_STYLE);
        dataContainer.remove(StructuredDataKey.DEATH_PROTECTION);
    }
}