package io.siggi.anvilregionformat;

import java.util.Objects;

public final class AnvilCoordinate {
    public final int x;
    public final int z;
    public AnvilCoordinate(int x, int z) {
        this.x = x;
        this.z = z;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (!(other instanceof AnvilCoordinate))
            return false;
        AnvilCoordinate o = (AnvilCoordinate) other;
        return x == o.x && z == o.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }
}
