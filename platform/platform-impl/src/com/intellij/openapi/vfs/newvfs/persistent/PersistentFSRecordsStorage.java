// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.util.io.CleanableStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@ApiStatus.Internal
public interface PersistentFSRecordsStorage extends IPersistentFSRecordsStorage, CleanableStorage, AutoCloseable {
  int NULL_ID = FSRecords.NULL_FILE_ID;
  int MIN_VALID_ID = NULL_ID + 1;

  /**
   * @return id of newly allocated record
   */
  int allocateRecord() throws IOException;

  void setAttributeRecordId(int fileId, int recordId) throws IOException;

  int getAttributeRecordId(int fileId) throws IOException;

  int getParent(int fileId) throws IOException;

  void setParent(int fileId, int parentId) throws IOException;

  int getNameId(int fileId) throws IOException;

  /** @return previous value of nameId */
  int updateNameId(int fileId, int nameId) throws IOException;

  /**
   * @return true if value is changed, false if not (i.e. new value is actually equal to the old one)
   */
  boolean setFlags(int fileId, int flags) throws IOException;

  //TODO RC: boolean updateFlags(fileId, int maskBits, boolean riseOrClean)

  long getLength(int fileId) throws IOException;


  /**
   * @return true if value is changed, false if not (i.e. new value is actually equal to the old one)
   */
  boolean setLength(int fileId, long length) throws IOException;

  long getTimestamp(int fileId) throws IOException;


  /**
   * @return true if value is changed, false if not (i.e. new value is actually equal to the old one)
   */
  boolean setTimestamp(int fileId, long timestamp) throws IOException;

  int getModCount(int fileId) throws IOException;

  //TODO RC: do we need this method? Record modification is tracked by storage, by actual modification -- there
  //         are (seems to) no way to modify record bypassing it.
  void markRecordAsModified(int fileId) throws IOException;

  int getContentRecordId(int fileId) throws IOException;

  boolean setContentRecordId(int fileId, int recordId) throws IOException;

  @PersistentFS.Attributes
  int getFlags(int fileId) throws IOException;

  /**
   * @throws IndexOutOfBoundsException if fileId is outside of range (0..max] of the fileIds allocated so far
   */
  void cleanRecord(int fileId) throws IOException;

  /* ======================== STORAGE HEADER ============================================================================== */

  long getTimestamp() throws IOException;

  /**
   * @return true if storage was closed properly (i.e. by {@link #close()} in a last session, or false if last session was
   * finished without calling {@link #close()} -- storage content may be inconsistent or corrupted.
   * The property describes events in a _previous_ session, so it is immutable during current session: changes to storage
   * doesn't affect this property's value
   */
  boolean wasClosedProperly() throws IOException;

  int getErrorsAccumulated() throws IOException;

  void setErrorsAccumulated(int errors) throws IOException;

  void setVersion(int version) throws IOException;

  int getVersion() throws IOException;

  /**
   * Additional bit-flags
   * Values are from {@linkplain PersistentFSHeaders.Flags} FLAGS_XXX constants
   */
  int getFlags() throws IOException;

  default boolean getFlag(int flagMask) throws IOException {
    return (getFlags() & flagMask) != 0;
  }

  /**
   * flags values are from {@linkplain PersistentFSHeaders} FLAGS_XXX constants
   *
   * @return true if flags actually changed, or false if current flags are the same as were attempted to set
   * (i.e. current flags already contain all of flagsToAdd and none of flagsToRemove)
   */
  boolean updateFlags(int flagsToAdd,
                      int flagsToRemove) throws IOException;

  int getGlobalModCount();

  @Override
  int recordsCount();

  /** @return max fileId already allocated by this storage */
  int maxAllocatedID();

  /** @return true if fileId is valid for the storage -- points to valid record */
  boolean isValidFileId(int fileId);

  @Override
  boolean isDirty();


  // TODO add a synchronization or requirement to be called on the loading
  @SuppressWarnings("UnusedReturnValue")
  boolean processAllRecords(@NotNull FsRecordProcessor processor) throws IOException;

  @Override
  void force() throws IOException;

  @Override
  void close() throws IOException;

  /** Close the storage and remove all its data files */
  @Override
  void closeAndClean() throws IOException;

  @FunctionalInterface
  interface FsRecordProcessor {
    void process(int fileId, int nameId, int flags, int parentId, int attributeRecordId, int contentId, boolean corrupted);
  }
}
