package io.siggi.anvilregionformat;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

class AnvilFile implements Closeable {
    private final AnvilCoordinate coordinate;
    private final File parent;
    private final File file;
    private final RandomAccessFile raf;
    private final InputStream rafIn;
    private final OutputStream rafOut;
    private final int[] offsets = new int[1024];
    private final int[] sizes = new int[1024];
    private final int[] editTimes = new int[1024];
    private boolean[] usedSectors = new boolean[0];
    private final int minX;
    private final int minZ;

    AnvilFile(AnvilCoordinate coordinate, File parent, File file) throws IOException {
        this.coordinate = coordinate;
        this.parent = parent;
        this.file = file;
        this.raf = new RandomAccessFile(file, "rwd");
        this.rafIn = new RAFInputStream(raf);
        this.rafOut = new RAFOutputStream(raf);
        setUsed(0, 2, true);
        if (raf.length() < 8192L) {
            raf.setLength(8192L);
        } else {
            for (int i = 0; i < 1024; i++) {
                offsets[i] = (raf.read() << 16) | (raf.read() << 8) | raf.read();
                sizes[i] = raf.read();
                setUsed(offsets[i], sizes[i], true);
            }
            for (int i = 0; i < 1024; i++) {
                editTimes[i] = raf.readInt();
            }
        }
        this.minX = coordinate.x << 5;
        this.minZ = coordinate.z << 5;
    }

    public AnvilCoordinate getCoordinate() {
        return coordinate;
    }

    private void setUsed(int offset, int count, boolean used) {
        if (count == 0)
            return;
        int minimumSize = offset + count;
        if (usedSectors.length < minimumSize) {
            usedSectors = Arrays.copyOf(usedSectors, minimumSize);
        }
        for (int i = 0; i < count; i++) {
            usedSectors[offset + i] = used;
        }
    }

    private boolean isSectorUsed(int index) {
        return index < usedSectors.length && usedSectors[index];
    }

    private int findFreeSpace(int sectorCount) {
        if (sectorCount <= 0) return 0;
        int offset = 0;
        int firstFree = 0;
        int freeCount = 0;
        while (true) {
            if (isSectorUsed(offset)) {
                freeCount = 0;
            } else {
                if (freeCount == 0) {
                    firstFree = offset;
                }
                freeCount += 1;
                if (freeCount >= sectorCount)
                    return firstFree;
            }
            offset += 1;
        }
    }

    private int sizeToSectorCount(int size) {
        return (size + 4095) / 4096;
    }

    private int getOffset(ChunkCoordinate coordinate) {
        int x = coordinate.x & 0x1F;
        int z = coordinate.z & 0x1F;
        return x + (32 * z);
    }

    public ChunkData read(ChunkCoordinate coordinate) throws IOException {
        int offset = getOffset(coordinate);
        int readFrom = offsets[offset];
        int sectorCount = sizes[offset];
        if (readFrom == 0 || sectorCount == 0) {
            return null;
        }
        raf.seek(((long) readFrom) * 4096L);
        int length = (raf.read() << 24) | (raf.read() << 16) | (raf.read() << 8) | raf.read();
        int compressionType = raf.read();
        boolean external = false;
        if ((compressionType & 0x80) != 0) {
            external = true;
            compressionType = compressionType & 0x7f;
        }
        length -= 1;
        byte[] chunkData;
        if (external) {
            try (FileInputStream in = new FileInputStream(getExternalFile(coordinate))) {
                chunkData = AnvilUtil.readFully(in);
            }
        } else {
            chunkData = new byte[length];
            AnvilUtil.readFully(rafIn, chunkData);
        }
        return new ChunkData(chunkData, compressionType, editTimes[offset]);
    }

    public void write(ChunkCoordinate coordinate, ChunkData data) throws IOException {
        int offset = getOffset(coordinate);
        int oldSector = offsets[offset];
        int oldSize = sizes[offset];
        if (data == null) {
            if (oldSector != 0 && oldSize != 0) {
                setUsed(oldSector, oldSize, false);
            }
            offsets[offset] = 0;
            sizes[offset] = 0;
            editTimes[offset] = 0;
            raf.seek(4 * offset);
            raf.writeInt(0);
            raf.seek((4 * offset) + 4096);
            raf.writeInt(0);
            getExternalFile(coordinate).delete();
        } else {
            boolean external = false;
            int newSize = sizeToSectorCount(data.data.length + 5);
            if (newSize >= 256) {
                newSize = 1;
                external = true;
            }
            int newSector = findFreeSpace(newSize);
            if (oldSector != 0 && oldSize != 0) {
                setUsed(oldSector, oldSize, false);
            }
            setUsed(newSector, newSize, true);
            long writeTo = ((long) newSector) * 4096L;
            if (raf.length() < writeTo)
                raf.setLength(writeTo);
            raf.seek(writeTo);
            if (external) {
                raf.writeInt(1);
                raf.write(0x80 | data.compressionType);
                try (FileOutputStream out = new FileOutputStream(getExternalFile(coordinate))) {
                    out.write(data.data);
                }
            } else {
                getExternalFile(coordinate).delete();
                raf.writeInt(data.data.length + 1);
                raf.write(data.compressionType);
                raf.write(data.data);
            }
            long currentLength = raf.length();
            long mod4096 = (currentLength % 4096L);
            if (mod4096 != 0L) {
                long newLength = currentLength + (4096L - mod4096);
                raf.setLength(newLength);
            }
            raf.seek(4 * offset);
            raf.write((newSector >> 16) & 0xff);
            raf.write((newSector >> 8) & 0xff);
            raf.write(newSector & 0xff);
            raf.write(newSize);
            raf.seek((4 * offset) + 4096);
            raf.writeInt(data.editTime);
            offsets[offset] = newSector;
            sizes[offset] = newSize;
            editTimes[offset] = data.editTime;
        }
    }

    private File getExternalFile(ChunkCoordinate coordinate) {
        return new File(parent, "c." + coordinate.x + "." + coordinate.z + ".mcc");
    }

    @Override
    public void close() throws IOException {
        try {
            raf.close();
        } catch (Exception e) {
        }
    }

    public <T extends Collection<ChunkCoordinate>> T getChunks(T coordinates, boolean onlyExisting) {
        for (int z = 0; z < 32; z++) {
            for (int x = 0; x < 32; x++) {
                ChunkCoordinate coordinate = new ChunkCoordinate(minX + x, minZ + z);
                if (!onlyExisting || offsets[getOffset(coordinate)] != 0) {
                    coordinates.add(coordinate);
                }
            }
        }
        return coordinates;
    }

    public void eraseFreeSpace() throws IOException {
        SpaceList usedSpace = new SpaceList();
        usedSpace.add(0, 8192L);
        for (ChunkCoordinate chunk : getChunks(new ArrayList<>(), true)) {
            int sector = offsets[getOffset(chunk)];
            long start = (sector) * 4096L;
            raf.seek(start);
            int length = raf.readInt();
            usedSpace.add(start, length + 4);
        }
        SpaceList freeSpace = usedSpace.flip();
        for (Space space : freeSpace.getSpaces(new ArrayList<>())) {
            if (space.length == -1L) {
                long endOfMeaningfulData = space.offset;
                long endOfFile = endOfMeaningfulData;
                long mod4096 = endOfMeaningfulData % 4096L;
                if (mod4096 != 0L) {
                    endOfFile = endOfMeaningfulData + (4096L - mod4096);
                }
                raf.setLength(endOfFile);
                raf.seek(endOfMeaningfulData);
                AnvilUtil.writeZeroes(rafOut, endOfFile - endOfMeaningfulData);
            } else {
                raf.seek(space.offset);
                AnvilUtil.writeZeroes(rafOut, space.length);
            }
        }
    }
}
