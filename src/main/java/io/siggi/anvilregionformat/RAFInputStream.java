package io.siggi.anvilregionformat;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

class RAFInputStream extends InputStream {

	private final RandomAccessFile raf;

	RAFInputStream(RandomAccessFile raf) {
		this.raf = raf;
	}

	@Override
	public int read() throws IOException {
		return raf.read();
	}

	@Override
	public int read(byte[] b) throws IOException {
		return raf.read(b);
	}

	@Override
	public int read(byte[] b, int offset, int length) throws IOException {
		return raf.read(b, offset, length);
	}
}
