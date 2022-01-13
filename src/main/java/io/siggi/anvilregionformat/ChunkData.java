package io.siggi.anvilregionformat;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class ChunkData {
	final byte[] data;
	final int compressionType;
	final int editTime;

	ChunkData(byte[] data, int compressionType, int editTime) {
		this.data = data;
		this.compressionType = compressionType;
		this.editTime = editTime;
	}

	public static ChunkData create(byte[] data) {
		return create(data, (int) (System.currentTimeMillis() / 1000L));
	}

	public static ChunkData create(byte[] data, int editTime) {
		int compressionType = 3;
		if (data[0] == (byte) 0x1f && data[1] == (byte) 0x8b) {
			compressionType = 1;
		} else if (data[0] == (byte) 0x78) {
			compressionType = 2;
		}
		return create(data, compressionType, editTime);
	}

	public static ChunkData create(byte[] data, int compressionType, int editTime) {
		if (data == null)
			throw new NullPointerException();
		return new ChunkData(Arrays.copyOf(data, data.length), compressionType, editTime);
	}

	public byte[] getRawData() {
		return Arrays.copyOf(data, data.length);
	}

	public byte[] getData() {
		try {
			switch (compressionType) {
				case 1:
					try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
						return AnvilUtil.readFully(in);
					}
				case 2:
					try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(data), new Inflater(false))) {
						return AnvilUtil.readFully(in);
					}
				case 3:
					return getRawData();
				default:
					throw new RuntimeException("Unsupported compression type " + compressionType);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public int getCompressionType() {
		return compressionType;
	}
}
