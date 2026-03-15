package com.replayplugin.sidecar.model;

import java.util.Objects;

/**
 * Integer block position for occlusion set and iteration.
 */
public final class BlockPos {

    private final int x;
    private final int y;
    private final int z;

    public BlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlockPos blockPos = (BlockPos) o;
        return x == blockPos.x && y == blockPos.y && z == blockPos.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }
}
