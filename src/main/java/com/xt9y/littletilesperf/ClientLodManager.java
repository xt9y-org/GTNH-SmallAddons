package com.xt9y.littletilesperf;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import com.creativemd.littletiles.common.tileentity.TileEntityLittleTiles;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Optional conservative distant LOD for static LittleTiles block meshing.
 *
 * An Angelica chunk worker requests the LOD mesh during construction. Client
 * ticks detect camera distance-band crossings and invalidate the affected
 * chunk meshes, returning full detail before the player approaches a build.
 * Never issues GL operations, never touches NBT or server-side world state.
 */
@SideOnly(Side.CLIENT)
public final class ClientLodManager {

    public static final ClientLodManager INSTANCE = new ClientLodManager();

    // At 20 ticks/s, revisit up to 512 registered tile entities per tick.
    // At most 2 region rebuild requests/tick to prevent update storms.
    private static final int SCAN_BUDGET = 512;
    private static final int REBUILD_BUDGET = 2;

    private final ConcurrentHashMap<TileEntityLittleTiles, State> tracked =
        new ConcurrentHashMap<TileEntityLittleTiles, State>();
    private Iterator<Map.Entry<TileEntityLittleTiles, State>> iterator;

    private volatile double cameraX, cameraY, cameraZ;
    private volatile boolean hasCamera;
    private World previousWorld;

    private ClientLodManager() {}

    /**
     * Called on the Angelica chunk-build thread. Does not access Minecraft
     * client world state directly, only the latest published camera coordinates.
     */
    public boolean wantsDistantMesh(TileEntityLittleTiles te) {
        if (!PerfSettings.distantLod || !hasCamera || te.getWorldObj() == null) return false;

        boolean far = farEnough(te, PerfSettings.distantLodDistanceBlocks);
        State state = tracked.get(te);
        if (state == null) {
            State fresh = new State(far);
            State existing = tracked.putIfAbsent(te, fresh);
            state = existing == null ? fresh : existing;
        }
        // The rebuilt mesh is now compiled from this target LOD.
        state.compiledFar = state.requestedFar;
        return state.requestedFar;
    }

    public void untrack(TileEntityLittleTiles te) {
        tracked.remove(te);
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getMinecraft();
        World world = client.theWorld;
        EntityPlayer player = client.thePlayer;
        if (world != previousWorld) {
            tracked.clear();
            iterator = null;
            previousWorld = world;
        }
        if (world == null || player == null || !PerfSettings.distantLod) {
            hasCamera = false;
            if (!PerfSettings.distantLod) {
                tracked.clear();
                iterator = null;
            }
            return;
        }
        cameraX = player.posX;
        cameraY = player.posY;
        cameraZ = player.posZ;
        hasCamera = true;

        int rebuilds = 0;
        for (int scan = 0; scan < SCAN_BUDGET; scan++) {
            if (iterator == null || !iterator.hasNext()) iterator = tracked.entrySet().iterator();
            if (!iterator.hasNext()) break;
            Map.Entry<TileEntityLittleTiles, State> entry = iterator.next();
            TileEntityLittleTiles te = entry.getKey();
            State state = entry.getValue();
            if (te.isInvalid() || te.getWorldObj() != world) {
                tracked.remove(te);
                continue;
            }
            // Hysteresis: switch to far beyond D+16, near below D-16.
            double distance = PerfSettings.distantLodDistanceBlocks;
            boolean target = state.requestedFar;
            if (target && !farEnough(te, Math.max(16, distance - 16))) target = false;
            else if (!target && farEnough(te, distance + 16)) target = true;

            if (target != state.requestedFar && rebuilds < REBUILD_BUDGET) {
                state.requestedFar = target;
                world.markBlockRangeForRenderUpdate(
                    te.xCoord, te.yCoord, te.zCoord, te.xCoord, te.yCoord, te.zCoord);
                rebuilds++;
            }
        }
    }

    private boolean farEnough(TileEntityLittleTiles te, double distance) {
        double dx = (te.xCoord + 0.5) - cameraX;
        double dy = (te.yCoord + 0.5) - cameraY;
        double dz = (te.zCoord + 0.5) - cameraZ;
        return dx * dx + dy * dy + dz * dz > distance * distance;
    }

    private static final class State {
        private volatile boolean requestedFar;
        private volatile boolean compiledFar;

        private State(boolean far) {
            requestedFar = far;
            compiledFar = far;
        }
    }
}
