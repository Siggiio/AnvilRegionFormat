package io.siggi.anvilregionformat;

import java.util.Objects;

public final class ChunkCoordinate {
	public final int x;
	public final int z;
	public ChunkCoordinate(int x, int z) {
		this.x = x;
		this.z = z;
	}

	public AnvilCoordinate toAnvilCoordinate() {
		return new AnvilCoordinate(x >> 5, z >> 5);
	}

	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (!(other instanceof ChunkCoordinate))
			return false;
		ChunkCoordinate o = (ChunkCoordinate) other;
		return x == o.x && z == o.z;
	}

	@Override
	public int hashCode() {
		return Objects.hash(x, z);
	}
}
