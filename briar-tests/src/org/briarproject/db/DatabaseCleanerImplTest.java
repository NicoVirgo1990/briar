package org.briarproject.db;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.CountDownLatch;

import org.briarproject.BriarTestCase;
import org.briarproject.api.db.DbException;
import org.briarproject.api.system.Timer;
import org.briarproject.db.DatabaseCleaner.Callback;
import org.briarproject.system.SystemTimer;

import org.junit.Test;

// FIXME: Use a mock timer
public class DatabaseCleanerImplTest extends BriarTestCase {

	@Test
	public void testCleanerRunsPeriodically() throws Exception {
		final CountDownLatch latch = new CountDownLatch(5);
		Callback callback = new Callback() {

			public void checkFreeSpaceAndClean() throws DbException {
				latch.countDown();
			}

			public boolean shouldCheckFreeSpace() {
				return true;
			}
		};
		Timer timer = new SystemTimer();
		DatabaseCleanerImpl cleaner = new DatabaseCleanerImpl(timer);
		// Start the cleaner
		cleaner.startCleaning(callback, 10);
		// The database should be cleaned five times (allow 5s for system load)
		assertTrue(latch.await(5, SECONDS));
		// Stop the cleaner
		cleaner.stopCleaning();
	}

	@Test
	public void testStoppingCleanerWakesItUp() throws Exception {
		final CountDownLatch latch = new CountDownLatch(1);
		Callback callback = new Callback() {

			public void checkFreeSpaceAndClean() throws DbException {
				latch.countDown();
			}

			public boolean shouldCheckFreeSpace() {
				return true;
			}
		};
		Timer timer = new SystemTimer();
		DatabaseCleanerImpl cleaner = new DatabaseCleanerImpl(timer);
		long start = System.currentTimeMillis();
		// Start the cleaner
		cleaner.startCleaning(callback, 10 * 1000);
		// The database should be cleaned once at startup
		assertTrue(latch.await(5, SECONDS));
		// Stop the cleaner (it should be waiting between sweeps)
		cleaner.stopCleaning();
		long end = System.currentTimeMillis();
		// Check that much less than 10 seconds expired
		assertTrue(end - start < 10 * 1000);
	}
}