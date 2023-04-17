package io.siggi.anvilregionformat;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class AnvilRegion implements Closeable {
    private final File root;
    private final List<AnvilFile> files = new LinkedList<>();
    private final int maxCache;
    private boolean closed = false;

    /**
     * Open an AnvilRegion (which may or may not yet exist on disk).
     *
     * @param f A directory containing mca files, or an empty directory to start writing mca files into.
     * @return An AnvilRegion
     */
    public static AnvilRegion open(File f) {
        return open(f, 64);
    }

    /**
     * Open an AnvilRegion (which may or may not yet exist on disk).
     *
     * @param f        A directory containing mca files, or an empty directory to start writing mca files into.
     * @param maxCache The maximum number of mca files to keep an open file descriptor to at a time.
     * @return An AnvilRegion
     */
    public static AnvilRegion open(File f, int maxCache) {
        return new AnvilRegion(f, maxCache);
    }

    private AnvilRegion(File f, int maxCache) {
        this.root = f;
        this.maxCache = maxCache;
        if (!root.exists()) root.mkdirs();
    }

    private AnvilFile getAnvilFile(AnvilCoordinate coordinate, boolean create) throws IOException {
        if (closed) {
            throw new IOException("Already closed");
        }
        boolean first = true;
        for (Iterator<AnvilFile> it = files.iterator(); it.hasNext(); ) {
            AnvilFile thisFile = it.next();
            if (thisFile.getCoordinate().equals(coordinate)) {
                if (!first) {
                    it.remove();
                    files.add(0, thisFile);
                }
                return thisFile;
            }
            first = false;
        }
        File file = getFile(coordinate);
        if (!file.exists() && !create) return null;
        AnvilFile newFile = new AnvilFile(coordinate, root, file);
        files.add(0, newFile);
        while (files.size() > maxCache) {
            AnvilFile remove = files.remove(files.size() - 1);
            remove.close();
        }
        return newFile;
    }

    private File getFile(AnvilCoordinate coordinate) {
        return new File(root, "r." + coordinate.x + "." + coordinate.z + ".mca");
    }

    /**
     * Read ChunkData from a coordinate.
     *
     * @param coordinate the coordinates to read from
     * @return ChunkData at the specified coordinates or null if it does not exist
     * @throws IOException if an IO error occurs
     */
    public ChunkData read(ChunkCoordinate coordinate) throws IOException {
        AnvilFile anvilFile = getAnvilFile(coordinate.toAnvilCoordinate(), false);
        if (anvilFile == null)
            return null;
        return anvilFile.read(coordinate);
    }

    /**
     * Write ChunkData to a coordinate.
     *
     * @param coordinate the coordinates to write to
     * @param data       the data to write to the specified coordinates, or null to delete the data at those coordinates
     * @throws IOException if an IO error occurs
     */
    public void write(ChunkCoordinate coordinate, ChunkData data) throws IOException {
        getAnvilFile(coordinate.toAnvilCoordinate(), true).write(coordinate, data);
    }

    /**
     * Close this AnvilRegion.
     *
     * @throws IOException if an IO error occurs
     */
    @Override
    public void close() throws IOException {
        closed = true;
        for (AnvilFile file : files) {
            try {
                file.close();
            } catch (Exception e) {
            }
        }
        files.clear();
    }

    /**
     * Get all AnvilCoordinates that exist in this AnvilRegion.
     *
     * @param coordinates an empty collection to add coordinates to
     * @param <T>         the type of collection to add coordinates to
     * @return the collection
     */
    public <T extends Collection<AnvilCoordinate>> T getRegions(T coordinates) {
        for (File file : root.listFiles()) {
            if (file.isDirectory())
                continue;
            String name = file.getName();
            if (!name.startsWith("r.") || !name.endsWith(".mca"))
                continue;
            String[] parts = name.split("\\.");
            if (parts.length != 4)
                continue;
            try {
                coordinates.add(new AnvilCoordinate(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
            } catch (Exception e) {
            }
        }
        return coordinates;
    }

    /**
     * Get all ChunkCoordinates that exist in an AnvilCoordinate.
     *
     * @param anvilCoordinate the anvil coordinate to get a list of chunks in
     * @param coordinates     an empty collection to add coordinates to
     * @param onlyExisting    true if you want to get only coordinates that have data written to them
     * @param <T>             the type of collection to add coordinates to
     * @return the collection
     */
    public <T extends Collection<ChunkCoordinate>> T getChunks(AnvilCoordinate anvilCoordinate, T coordinates, boolean onlyExisting) throws IOException {
        AnvilFile anvilFile = getAnvilFile(anvilCoordinate, !onlyExisting);
        if (anvilFile == null)
            return coordinates;
        return anvilFile.getChunks(coordinates, onlyExisting);
    }

    /**
     * For a specified AnvilCoordinate, erase and re-write the data to disk. This may reduce the file size and improve
     * compression. Doing this as a routine maintenance action is not recommended, it should only be used to prepare
     * to archive your data.
     *
     * @param coordinate the anvil coordinate to compact the data file of
     * @throws IOException if an IO error occurs
     */
    public void compact(AnvilCoordinate coordinate) throws IOException {
        Map<ChunkCoordinate, ChunkData> chunkData = new HashMap<>();
        AnvilFile anvilFile = getAnvilFile(coordinate, false);
        if (anvilFile == null)
            return;
        ArrayList<ChunkCoordinate> chunks = anvilFile.getChunks(new ArrayList<>(), true);
        for (ChunkCoordinate chunk : chunks) {
            chunkData.put(chunk, anvilFile.read(chunk));
            anvilFile.write(chunk, null);
        }
        anvilFile.eraseFreeSpace();
        for (ChunkCoordinate chunk : chunks) {
            anvilFile.write(chunk, chunkData.get(chunk));
        }
    }

    /**
     * Write zeros over all free space in the AnvilRegion. This may make the file compress better in an archive. Doing
     * this as a routine maintenance action is not recommended, it should only be used to prepare to archive your data.
     *
     * @throws IOException if an IO error occurs
     */
    public void eraseFreeSpace() throws IOException {
        for (AnvilCoordinate coordinate : getRegions(new ArrayList<>())) {
            eraseFreeSpace(coordinate);
        }
    }

    /**
     * Write zeros over all free space at an AnvilCoordinate. This may make the file compress better in an archive. Doing
     * this as a routine maintenance action is not recommended, it should only be used to prepare to archive your data.
     *
     * @throws IOException if an IO error occurs
     */
    public void eraseFreeSpace(AnvilCoordinate coordinate) throws IOException {
        AnvilFile anvilFile = getAnvilFile(coordinate, false);
        if (anvilFile != null)
            anvilFile.eraseFreeSpace();
    }
}
