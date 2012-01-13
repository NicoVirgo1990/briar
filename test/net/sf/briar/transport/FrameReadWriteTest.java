package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;
import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.Mac;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class FrameReadWriteTest extends BriarTestCase {

	private final CryptoComponent crypto;
	private final Cipher tagCipher, frameCipher;
	private final Random random;
	private final byte[] outSecret;
	private final ErasableKey tagKey, frameKey, macKey;
	private final Mac mac;

	public FrameReadWriteTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		crypto = i.getInstance(CryptoComponent.class);
		tagCipher = crypto.getTagCipher();
		frameCipher = crypto.getFrameCipher();
		random = new Random();
		// Since we're sending frames to ourselves, we only need outgoing keys
		outSecret = new byte[32];
		random.nextBytes(outSecret);
		tagKey = crypto.deriveTagKey(outSecret, true);
		frameKey = crypto.deriveFrameKey(outSecret, true);
		macKey = crypto.deriveMacKey(outSecret, true);
		mac = crypto.getMac();
	}

	@Test
	public void testInitiatorWriteAndRead() throws Exception {
		testWriteAndRead(true);
	}

	@Test
	public void testResponderWriteAndRead() throws Exception {
		testWriteAndRead(false);
	}

	private void testWriteAndRead(boolean initiator) throws Exception {
		// Encode the tag
		byte[] tag = new byte[TAG_LENGTH];
		TagEncoder.encodeTag(tag, 0, tagCipher, tagKey);
		// Generate two random frames
		byte[] frame = new byte[12345];
		random.nextBytes(frame);
		byte[] frame1 = new byte[321];
		random.nextBytes(frame1);
		// Copy the keys - the copies will be erased
		ErasableKey frameCopy = frameKey.copy();
		ErasableKey tagCopy = tagKey.copy();
		ErasableKey macCopy = macKey.copy();
		// Write the frames
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionEncrypter encrypter = new ConnectionEncrypterImpl(out,
				Long.MAX_VALUE, tagCipher, frameCipher, tagCopy, frameCopy,
				false);
		ConnectionWriter writer = new ConnectionWriterImpl(encrypter, mac,
				macCopy);
		OutputStream out1 = writer.getOutputStream();
		out1.write(frame);
		out1.flush();
		out1.write(frame1);
		out1.flush();
		// Read the tag back
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		byte[] recoveredTag = new byte[TAG_LENGTH];
		assertEquals(TAG_LENGTH, in.read(recoveredTag));
		assertArrayEquals(tag, recoveredTag);
		assertTrue(TagEncoder.validateTag(tag, 0, tagCipher, tagKey));
		// Read the frames back
		FrameSource decrypter = new ConnectionDecrypter(in, tagCipher,
				frameCipher, tagKey, frameKey, mac.getMacLength(), false);
		ConnectionReader reader = new ConnectionReaderImpl(decrypter, mac,
				macKey);
		InputStream in1 = reader.getInputStream();
		byte[] recovered = new byte[frame.length];
		int offset = 0;
		while(offset < recovered.length) {
			int read = in1.read(recovered, offset, recovered.length - offset);
			if(read == -1) break;
			offset += read;
		}
		assertEquals(recovered.length, offset);
		assertArrayEquals(frame, recovered);
		byte[] recovered1 = new byte[frame1.length];
		offset = 0;
		while(offset < recovered1.length) {
			int read = in1.read(recovered1, offset, recovered1.length - offset);
			if(read == -1) break;
			offset += read;
		}
		assertEquals(recovered1.length, offset);
		assertArrayEquals(frame1, recovered1);
	}
}
