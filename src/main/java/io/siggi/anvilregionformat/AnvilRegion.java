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

	public AnvilRegion(File f) {
		this(f, 64);
	}

	public AnvilRegion(File f, int maxCache) {
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
		AnvilFile newFile = new AnvilFile(coordinate, root, getFile(coordinate));
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

	public ChunkData read(ChunkCoordinate coordinate) throws IOException {
		AnvilFile anvilFile = getAnvilFile(coordinate.toAnvilCoordinate(), false);
		if (anvilFile == null)
			return null;
		return anvilFile.read(coordinate);
	}

	public void write(ChunkCoordinate coordinate, ChunkData data) throws IOException {
		getAnvilFile(coordinate.toAnvilCoordinate(), true).write(coordinate, data);
	}

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

	public <T extends Collection<ChunkCoordinate>> T getChunks(AnvilCoordinate anvilCoordinate, T coordinates, boolean onlyExisting) throws IOException {
		AnvilFile anvilFile = getAnvilFile(anvilCoordinate, !onlyExisting);
		if (anvilFile == null)
			return coordinates;
		return anvilFile.getChunks(coordinates, onlyExisting);
	}

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

	public void eraseFreeSpace() throws IOException {
		for (AnvilCoordinate coordinate : getRegions(new ArrayList<>())) {
			eraseFreeSpace(coordinate);
		}
	}

	public void eraseFreeSpace(AnvilCoordinate coordinate) throws IOException {
		AnvilFile anvilFile = getAnvilFile(coordinate, false);
		if (anvilFile != null)
			anvilFile.eraseFreeSpace();
	}
}
