/*
 * The Alluxio Open Foundation licenses this work under the Apache License, version 2.0
 * (the "License"). You may not use this work except in compliance with the License, which is
 * available at www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied, as more fully set forth in the License.
 *
 * See the NOTICE file distributed with this work for information regarding copyright ownership.
 */

package alluxio.master.journal;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;

import alluxio.Configuration;
import alluxio.ConfigurationTestUtils;
import alluxio.PropertyKey;
import alluxio.master.journal.JournalWriter.EntryOutputStream;
import alluxio.proto.journal.Journal.JournalEntry;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;

/**
 * Unit tests for {@link AsyncJournalWriter}.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({JournalWriter.class, EntryOutputStream.class})
public class AsyncJournalWriterTest {

  private JournalWriter mMockJournalWriter;
  private AsyncJournalWriter mAsyncJournalWriter;

  @After
  public void after() throws Exception {
    ConfigurationTestUtils.resetConfiguration();
  }

  private void setupAsyncJournalWriter(boolean batchingEnabled) throws Exception {
    if (batchingEnabled) {
      Configuration.set(PropertyKey.MASTER_JOURNAL_FLUSH_BATCH_TIME_MS, 500);
    } else {
      Configuration.set(PropertyKey.MASTER_JOURNAL_FLUSH_BATCH_TIME_MS, 0);
    }

    mMockJournalWriter = PowerMockito.mock(JournalWriter.class);
    doNothing().when(mMockJournalWriter).writeEntry(any(JournalEntry.class));
    doNothing().when(mMockJournalWriter).flushEntryStream();
    mAsyncJournalWriter = new AsyncJournalWriter(mMockJournalWriter);
  }

  public void writesAndFlushesInternal(boolean batchingEnabled) throws Exception {
    setupAsyncJournalWriter(batchingEnabled);
    int entries = 5;

    for (int i = 0; i < entries; i++) {
      long flushCounter = mAsyncJournalWriter.appendEntry(JournalEntry.getDefaultInstance());
      // Assuming the flush counter starts from 0.
      Assert.assertEquals(i + 1, flushCounter);
    }

    for (int i = 1; i <= entries; i++) {
      mAsyncJournalWriter.flush(i);
    }
    if (batchingEnabled) {
      Mockito.verify(mMockJournalWriter, atLeastOnce()).flushEntryStream();
    } else {
      Mockito.verify(mMockJournalWriter, times(entries)).flushEntryStream();
    }
  }

  @Test(timeout = 10000)
  public void writesAndFlushes() throws Exception {
    writesAndFlushesInternal(false);
  }

  @Test(timeout = 10000)
  public void writesAndFlushesWithBatching() throws Exception {
    writesAndFlushesInternal(true);
  }

  public void failedWriteInternal(boolean batchingEnabled) throws Exception {
    setupAsyncJournalWriter(batchingEnabled);
    int entries = 5;

    for (int i = 0; i < entries; i++) {
      long flushCounter = mAsyncJournalWriter.appendEntry(JournalEntry.getDefaultInstance());
      // Assuming the flush counter starts from 0
      Assert.assertEquals(i + 1, flushCounter);
    }

    // Start failing journal writes.
    doThrow(new IOException("entry write failed")).when(mMockJournalWriter)
        .writeEntry(any(JournalEntry.class));

    // Flushes should fail.
    for (int i = 1; i <= entries; i++) {
      try {
        mAsyncJournalWriter.flush(1);
        Assert.fail("journal flush should not succeed if journal write fails.");
      } catch (IOException e) {
        // This is expected.
      }
    }

    // Allow journal writes to succeed.
    doNothing().when(mMockJournalWriter).writeEntry(any(JournalEntry.class));

    // Flushes should succeed.
    for (int i = 1; i <= entries; i++) {
      mAsyncJournalWriter.flush(i);
    }
    if (batchingEnabled) {
      Mockito.verify(mMockJournalWriter, atLeastOnce()).flushEntryStream();
    } else {
      Mockito.verify(mMockJournalWriter, times(entries)).flushEntryStream();
    }
  }

  @Test(timeout = 10000)
  public void failedWrite() throws Exception {
    failedWriteInternal(false);
  }

  @Test(timeout = 10000)
  public void failedWriteWithBatching() throws Exception {
    failedWriteInternal(true);
  }

  public void failedFlushInternal(boolean batchingEnabled) throws Exception {
    setupAsyncJournalWriter(batchingEnabled);
    int entries = 5;

    for (int i = 0; i < entries; i++) {
      long flushCounter = mAsyncJournalWriter.appendEntry(JournalEntry.getDefaultInstance());
      // Assuming the flush counter starts from 0
      Assert.assertEquals(i + 1, flushCounter);
    }

    // Start failing journal flushes.
    doThrow(new IOException("flush failed")).when(mMockJournalWriter).flushEntryStream();

    // Flushes should fail.
    for (int i = 1; i <= entries; i++) {
      try {
        mAsyncJournalWriter.flush(1);
        Assert.fail("journal flush should not succeed if journal flush fails.");
      } catch (IOException e) {
        // This is expected.
      }
    }

    // Allow journal flushes to succeed.
    doNothing().when(mMockJournalWriter).flushEntryStream();

    // Flushes should succeed.
    for (int i = 1; i <= entries; i++) {
      mAsyncJournalWriter.flush(i);
    }
    if (batchingEnabled) {
      Mockito.verify(mMockJournalWriter, atLeastOnce()).flushEntryStream();
    } else {
      // The first half of the calls were the failed flush calls.
      Mockito.verify(mMockJournalWriter, times(2 * entries)).flushEntryStream();
    }
  }

  @Test(timeout = 10000)
  public void failedFlush() throws Exception {
    failedFlushInternal(false);
  }

  @Test(timeout = 10000)
  public void failedFlushWithBatching() throws Exception {
    failedFlushInternal(true);
  }
}
