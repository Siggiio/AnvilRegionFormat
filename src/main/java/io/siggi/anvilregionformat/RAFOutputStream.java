package io.siggi.anvilregionformat;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

class RAFOutputStream extends OutputStream {

	private final RandomAccessFile raf;

	RAFOutputStream(RandomAccessFile raf) {
		this.raf = raf;
	}

	@Override
	public void write(int value) throws IOException {
		raf.write(value);
	}

	@Override
	public void write(byte[] b) throws IOException {
		raf.write(b);
	}

	@Override
	public void write(byte[] b, int offset, int length) throws IOException {
		raf.write(b, offset, length);
	}
}
