package io.siggi.anvilregionformat;

import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;

class SpaceList {
	private final TreeSet<Space> spaces = new TreeSet<>((a, b) -> {
		if (a.offset < b.offset) {
			return -1;
		} else if (a.offset > b.offset) {
			return 1;
		} else {
			return 0;
		}
	});

	public SpaceList() {
	}

	public void add(long offset, long length) {
		Space newSpace = new Space(offset, length);
		Space previousSpace = spaces.floor(newSpace);
		if (previousSpace != null && (previousSpace.length == -1L || previousSpace.offset + previousSpace.length >= offset)) {
			spaces.remove(previousSpace);
			if (previousSpace.length == -1L || newSpace.length == -1L) {
				newSpace = new Space(previousSpace.offset, -1L);
			} else {
				long end = Math.max(previousSpace.offset + previousSpace.length, newSpace.offset + newSpace.length);
				newSpace = new Space(previousSpace.offset, end - previousSpace.offset);
			}
		}
		Space nextSpace = spaces.ceiling(newSpace);
		if (nextSpace != null && newSpace.offset + newSpace.length >= nextSpace.offset) {
			spaces.remove(nextSpace);
			if (newSpace.length == -1L || nextSpace.length == -1L) {
				newSpace = new Space(newSpace.offset, -1L);
			} else {
				long end = Math.max(newSpace.offset + newSpace.length, nextSpace.offset + nextSpace.length);
				newSpace = new Space(newSpace.offset, end - newSpace.offset);
			}
		}
		spaces.add(newSpace);
	}

	public SpaceList flip() {
		SpaceList newSpaceList = new SpaceList();
		if (spaces.size() == 0) {
			newSpaceList.add(0, -1L);
		} else {
			long previousOffset = 0L;
			for (Iterator<Space> it = spaces.iterator(); it.hasNext(); ) {
				Space space = it.next();
				if (space.offset > previousOffset) {
					newSpaceList.add(previousOffset, space.offset - previousOffset);
				}
				if (space.length == -1L)
					break;
				previousOffset = space.offset + space.length;
				if (!it.hasNext()) {
					newSpaceList.add(previousOffset, -1L);
				}
			}
		}
		return newSpaceList;
	}

	public <T extends Collection<Space>> T getSpaces(T spaces) {
		spaces.addAll(this.spaces);
		return spaces;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (Space space : spaces) {
			if (sb.length() != 0) {
				sb.append("\n");
			}
			sb.append(space.offset).append(" -> ").append(space.length == -1 ? "forever" : Long.toString(space.offset + space.length));
		}
		return sb.toString();
	}
}
