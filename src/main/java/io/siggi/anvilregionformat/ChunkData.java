package io.siggi.anvilregionformat;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class ChunkData {
    public static final int COMPRESSION_TYPE_GZIP = 1;
    public static final int COMPRESSION_TYPE_ZLIB = 2;
    public static final int COMPRESSION_TYPE_NONE = 3;

    final byte[] data;
    final int compressionType;
    final int editTime;

    ChunkData(byte[] data, int compressionType, int editTime) {
        this.data = data;
        this.compressionType = compressionType;
        this.editTime = editTime;
    }

    /**
     * Create ChunkData with a byte array. The compression type will be automatically detected based on the header
     * in the byte array.
     *
     * @param data the data
     * @return a ChunkData
     */
    public static ChunkData create(byte[] data) {
        return create(data, (int) (System.currentTimeMillis() / 1000L));
    }

    /**
     * Create ChunkData with a byte array and custom edit time. The compression type will be automatically detected
     * based on the header in the byte array.
     *
     * @param data     the data
     * @param editTime the time the data was edited in seconds since 1970, see {@link #getEditTime()} for more info
     * @return a ChunkData
     */
    public static ChunkData create(byte[] data, int editTime) {
        int compressionType = COMPRESSION_TYPE_NONE;
        if (data.length >= 2 && data[0] == (byte) 0x1f && data[1] == (byte) 0x8b) {
            compressionType = COMPRESSION_TYPE_GZIP;
        } else if (AnvilUtil.detectZlibHeader(data)) {
            compressionType = COMPRESSION_TYPE_ZLIB;
        }
        return create(data, compressionType, editTime);
    }

    /**
     * Create ChunkData with a byte array, custom compression type and custom edit time.
     *
     * @param data            the data
     * @param compressionType the type of compression used
     * @param editTime        the time the data was edited in seconds since 1970, see {@link #getEditTime()} for more info
     * @return a ChunkData
     */
    public static ChunkData create(byte[] data, int compressionType, int editTime) {
        if (data == null)
            throw new NullPointerException();
        return new ChunkData(Arrays.copyOf(data, data.length), compressionType, editTime);
    }

    /**
     * Get the raw data stored in this chunk
     *
     * @return the raw data
     */
    public byte[] getData() {
        return Arrays.copyOf(data, data.length);
    }

    /**
     * Get the decompressed data stored in this chunk. This method takes the raw data, decompresses it, and returns it.
     *
     * @return the decompressed data
     */
    public byte[] getDecompressedData() {
        try {
            switch (compressionType) {
                case COMPRESSION_TYPE_GZIP:
                    try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
                        return AnvilUtil.readFully(in);
                    }
                case COMPRESSION_TYPE_ZLIB:
                    try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(data), new Inflater(false))) {
                        return AnvilUtil.readFully(in);
                    }
                case COMPRESSION_TYPE_NONE:
                    return getData();
                default:
                    throw new RuntimeException("Unsupported compression type " + compressionType);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the type of compression used in this chunk.
     *
     * @return the type of compression used.
     */
    public int getCompressionType() {
        return compressionType;
    }

    /**
     * Get the time this data was edited. This is in the form of number of seconds since the beginning of 1970. However,
     * since it is a 32 bit signed integer, it does roll over in 2038. After 2038, you may consider this the number of
     * seconds since February 7th, 2106 at 06:28:16 UTC. As a result, the time edited is not a 100% reliable method of
     * telling how old a piece of data is.
     *
     * @return the time this data was edited.
     */
    public int getEditTime() {
        return editTime;
    }
}
