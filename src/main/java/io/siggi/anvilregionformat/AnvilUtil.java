package io.siggi.anvilregionformat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Adler32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;

public class AnvilUtil {
	static byte[] readFully(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		copy(in, out);
		return out.toByteArray();
	}

	static void readFully(InputStream in, byte[] data) throws IOException {
		int read = 0;
		int c;
		while (read < data.length) {
			c = in.read(data, read, data.length - read);
			if (c == -1)
				throw new EOFException();
			read += c;
		}
	}

	static void copy(InputStream in, OutputStream out) throws IOException {
		byte[] b = new byte[4096];
		int c;
		while ((c = in.read(b, 0, b.length)) != -1) {
			out.write(b, 0, c);
		}
	}

	static void writeZeroes(OutputStream out, long count) throws IOException {
		byte[] data = new byte[4096];
		while (count > 0L) {
			int write = (int) Math.min(count, (long) data.length);
			out.write(data, 0, write);
			count -= (long) write;
		}
	}

	public static byte[] convertGzipToZlib(byte[] data) {
		if (data == null)
			throw new NullPointerException();
		try {
			long adler32;
			{
				Adler32 adler32Checksum = new Adler32();
				try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
					byte[] b = new byte[4096];
					int c;
					while ((c = in.read(b, 0, b.length)) != -1) {
						adler32Checksum.update(b, 0, c);
					}
				}
				adler32 = adler32Checksum.getValue();
			}
			int beginningOfData;
			int dataLength;
			if (data[3] == (byte) 0x0) {
				beginningOfData = 10;
				dataLength = data.length - 8 - beginningOfData;
			} else if (data[3] == (byte) 0x08) {
				beginningOfData = -1;
				for (int i = 10; i < data.length; i++) {
					if (data[i] == (byte) 0x0) {
						beginningOfData = i + 1;
						break;
					}
				}
				if (beginningOfData == -1) {
					throw new RuntimeException("Could not find beginning of gzip data.");
				}
				dataLength = data.length - 8 - beginningOfData;
			} else {
				throw new RuntimeException("Unsupported Gzip data");
			}
			byte[] newData = new byte[dataLength + 6];
			System.arraycopy(data, beginningOfData, newData, 2, dataLength);
			newData[0] = (byte) 0x78;
			newData[1] = (byte) 0x9C;
			newData[newData.length - 4] = (byte) ((adler32 >> 24) & 0xff);
			newData[newData.length - 3] = (byte) ((adler32 >> 16) & 0xff);
			newData[newData.length - 2] = (byte) ((adler32 >> 8) & 0xff);
			newData[newData.length - 1] = (byte) (adler32 & 0xff);
			return newData;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static byte[] zlibCompress(byte[] data) {
		if (data == null)
			throw new NullPointerException();
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (DeflaterOutputStream deflaterStream = new DeflaterOutputStream(out, new Deflater(Deflater.DEFAULT_COMPRESSION, false))) {
				deflaterStream.write(data);
			}
			return out.toByteArray();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
