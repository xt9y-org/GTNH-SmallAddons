package com.xt9y.features.xtprofile;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

final class XTProfileMachineResolver {

    private static final long RESCAN_TICKS = 100;
    private static final Map<World, WorldCache> CACHES = new WeakHashMap<>();

    static TileEntity resolve(TileEntity target) {
        if (!(target instanceof IGregTechTileEntity) || target.getWorldObj() == null) return target;

        IMetaTileEntity meta = ((IGregTechTileEntity) target).getMetaTileEntity();
        if (meta instanceof MTEMultiBlockBase) return target;

        TileEntity directController = watcherController(meta);
        if (directController != null) return directController;

        World world = target.getWorldObj();
        WorldCache cache;
        synchronized (CACHES) {
            cache = CACHES.get(world);
            if (cache == null) {
                cache = new WorldCache();
                CACHES.put(world, cache);
            }
        }

        String targetKey = key(target);
        TileEntity cached = dereference(cache.controllers.get(targetKey));
        if (cached != null) return cached;

        long tick = world.getTotalWorldTime();
        if (!cache.scanned || tick < cache.lastScanTick || tick - cache.lastScanTick >= RESCAN_TICKS) {
            rebuild(world, cache, tick);
            cached = dereference(cache.controllers.get(targetKey));
            if (cached != null) return cached;
        }

        return target;
    }

    static void clear() {
        synchronized (CACHES) {
            CACHES.clear();
        }
    }

    static <T> T firstInstance(Iterable<?> values, Class<T> type) {
        if (values == null || type == null) return null;
        for (Object value : values) {
            if (type.isInstance(value)) return type.cast(value);
        }
        return null;
    }

    private static TileEntity watcherController(IMetaTileEntity meta) {
        if (meta == null) return null;

        Class<?> type = meta.getClass();
        while (type != null && IMetaTileEntity.class.isAssignableFrom(type)) {
            try {
                Field field = type.getDeclaredField("watchers");
                if (Iterable.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object value = field.get(meta);
                    if (value instanceof Iterable) {
                        MTEMultiBlockBase controller = firstInstance((Iterable<?>) value, MTEMultiBlockBase.class);
                        TileEntity controllerTile = controllerTile(controller);
                        if (controllerTile != null) return controllerTile;
                    }
                }
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException ignored) {}
            type = type.getSuperclass();
        }
        return null;
    }

    private static TileEntity controllerTile(MTEMultiBlockBase controller) {
        if (controller == null) return null;
        IGregTechTileEntity base = controller.getBaseMetaTileEntity();
        if (!(base instanceof TileEntity)) return null;
        TileEntity tile = (TileEntity) base;
        return tile.isInvalid() ? null : tile;
    }

    private static void rebuild(World world, WorldCache cache, long tick) {
        cache.controllers.clear();
        cache.lastScanTick = tick;
        cache.scanned = true;

        for (Object object : world.loadedTileEntityList) {
            if (!(object instanceof TileEntity) || !(object instanceof IGregTechTileEntity)) continue;

            TileEntity tile = (TileEntity) object;
            IGregTechTileEntity gregTech = (IGregTechTileEntity) object;
            IMetaTileEntity meta = gregTech.getMetaTileEntity();
            if (!(meta instanceof MTEMultiBlockBase)) continue;

            indexController((MTEMultiBlockBase) meta, tile, cache.controllers);
        }
    }

    private static void indexController(MTEMultiBlockBase controller, TileEntity controllerTile,
        Map<String, WeakReference<TileEntity>> controllers) {
        Set<IMetaTileEntity> indexed = java.util.Collections.newSetFromMap(new IdentityHashMap<IMetaTileEntity, Boolean>());

        Class<?> type = controller.getClass();
        while (type != null && MTEMultiBlockBase.class.isAssignableFrom(type)) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !Iterable.class.isAssignableFrom(field.getType())) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                    Object value = field.get(controller);
                    if (!(value instanceof Iterable)) continue;

                    for (Object member : (Iterable<?>) value) {
                        if (!(member instanceof IMetaTileEntity)) continue;
                        IMetaTileEntity hatch = (IMetaTileEntity) member;
                        if (!indexed.add(hatch)) continue;

                        IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
                        if (base instanceof TileEntity) {
                            controllers.put(key((TileEntity) base), new WeakReference<>(controllerTile));
                        }
                    }
                } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException ignored) {}
            }
            type = type.getSuperclass();
        }
    }

    private static TileEntity dereference(WeakReference<TileEntity> reference) {
        if (reference == null) return null;
        TileEntity tile = reference.get();
        return tile == null || tile.isInvalid() ? null : tile;
    }

    private static String key(TileEntity tile) {
        int dimension = tile.getWorldObj() == null ? 0 : tile.getWorldObj().provider.dimensionId;
        return dimension + ":" + tile.xCoord + ":" + tile.yCoord + ":" + tile.zCoord;
    }

    private XTProfileMachineResolver() {}

    private static final class WorldCache {

        final Map<String, WeakReference<TileEntity>> controllers = new HashMap<>();
        long lastScanTick;
        boolean scanned;
    }
}
