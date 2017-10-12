/*
  Copyright (c) 2003-2016, YourKit
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are met:

  * Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.

  * Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.

  * Neither the name of YourKit nor the
    names of its contributors may be used to endorse or promote products
    derived from this software without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY YOURKIT "AS IS" AND ANY
  EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
  DISCLAIMED. IN NO EVENT SHALL YOURKIT BE LIABLE FOR ANY
  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package examples;

import com.yourkit.probes.*;
import com.yourkit.runtime.Callback;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

public final class Files {
  public static final String TABLE_NAME = "File";

  public static final String SYSTEM_IN_PSEUDO_PATH = "<System.in>";
  public static final String SYSTEM_OUT_PSEUDO_PATH = "<System.out>";
  public static final String SYSTEM_ERR_PSEUDO_PATH = "<System.err>";

  /**
   * Mapping between file and row index in T_FILE table
   */
  private static final MasterResourceRegistry<Object> ourFiles = new MasterResourceRegistry<Object>(
    Files.class,
    TABLE_NAME,
    null, // no additional description for a file
    "Path"
  ) {
        @Override
    protected String retrieveResourceName(final Object resource) {
      return retrieveFilePath(resource);
    }
  };

  private static final TableIntBytes T_READ = new TableIntBytes(ourFiles, "Read", Table.MASK_FOR_LASTING_EVENTS);

  private static final TableIntBytes T_WRITE = new TableIntBytes(ourFiles, "Write", Table.MASK_FOR_LASTING_EVENTS);

  private static final TableLongBytes T_CHANNEL_READ = new TableLongBytes(ourFiles, "Channel Read", Table.MASK_FOR_LASTING_EVENTS);

  private static final TableLongBytes T_CHANNEL_WRITE = new TableLongBytes(ourFiles, "Channel Write", Table.MASK_FOR_LASTING_EVENTS);

  @MethodPattern(
    {
      "java.io.FileOutputStream:open(String)",
      "java.io.FileOutputStream:open(String, boolean)"
    }
  )
  public static final class FileOutputStream_open_Probe {
    public static long onEnter() {
      return ourFiles.openOnEnter();
    }

    public static void onExit(
      @This final FileOutputStream stream,
      @Param(1) final String path,
      @OnEnterResult final long resourceID,
      @ThrownException  final Throwable e
    ) {
      ourFiles.openOnExit(
        resourceID,
        path,
        stream,
        e,
        FailedEventPolicy.RECORD
      );
    }
  }

  @MethodPattern("java.io.FileOutputStream:close()")
  public static final class FileOutputStream_close_Probe {
    public static int onEnter(@This final FileOutputStream stream) {
      return ourFiles.closeOnEnter(stream);
    }

    public static void onExit(
      @OnEnterResult final int row,
      @ThrownException  final Throwable e
    ) {
      ourFiles.closeOnExit(row, e);
    }
  }

  @MethodPattern(
    {
      "java.io.FileOutputStream:writeBytes(byte[], int, int)",
      "java.io.FileOutputStream:writeBytes(byte[], int, int, boolean)"
    }
  )
  public static final class FileOutputStream_writeBytes_Probe {
    public static int onEnter(
      @This final FileOutputStream fileOutputStream,
      @Param(3) final int bytesWritten
    ) {
      return writeOnEnter(fileOutputStream, bytesWritten);
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      writeOnExit(rowIndex, e);
    }
  }

  @MethodPattern("java.io.FileOutputStream:write(int)")
  public static final class FileOutputStream_write_Probe {
    public static int onEnter(@This final FileOutputStream fileOutputStream) {
      return writeOnEnter(fileOutputStream, 1 /* one byte to be written */);
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      writeOnExit(rowIndex, e);
    }
  }

  @MethodPattern("java.io.FileInputStream:open(String)")
  public static final class FileInputStream_open_Probe {
    public static long onEnter() {
      return ourFiles.openOnEnter();
    }

    public static void onExit(
      @This final FileInputStream stream,
      @Param(1) final String path,
      @OnEnterResult final long resourceID,
      @ThrownException  final Throwable e
    ) {
      ourFiles.openOnExit(
        resourceID,
        path,
        stream,
        e,
        FailedEventPolicy.RECORD
      );
    }
  }

  @MethodPattern("java.io.FileInputStream:close()")
  public static final class FileInputStream_close_Probe {
    public static int onEnter(@This final FileInputStream stream) {
      return ourFiles.closeOnEnter(stream);
    }

    public static void onExit(
      @ThrownException  final Throwable e,
      @OnEnterResult final int row
    ) {
      ourFiles.closeOnExit(row, e);
    }
  }

  @MethodPattern("java.io.FileInputStream:read()")
  public static final class FileInputStream_read_Probe {
    public static int onEnter(@This final FileInputStream fileInputStream) {
      return readOnEnter(fileInputStream);
    }

    public static void onExit(
      @OnEnterResult final int readRowIndex,
      @ReturnValue final int returnValue,
      @ThrownException  final Throwable e
    ) {
      // non negative return value means that a byte has been successfully read
      final int readBytes = returnValue >= 0 ? 1 : 0;
      readOnExit(readRowIndex, readBytes, e);
    }
  }

  @MethodPattern("java.io.FileInputStream:readBytes(byte[], int, int)")
  public static final class FileInputStream_readBytes_Probe {
    public static int onEnter(@This final FileInputStream fileInputStream) {
      return readOnEnter(fileInputStream);
    }

    public static void onExit(
      @OnEnterResult final int readRowIndex,
      @ReturnValue final int bytesRead,
      @ThrownException  final Throwable e
    ) {
      readOnExit(readRowIndex, bytesRead, e);
    }
  }

  @MethodPattern(
    {
      "java.io.RandomAccessFile:getChannel()",
      "java.io.FileInputStream:getChannel()",
      "java.io.FileOutputStream:getChannel()"
    }
  )
  public static final class GetChannel_Probe {
    public static void onReturn(
      @ReturnValue  final FileChannel fileChannel,
      @This final Object file
    ) {
      // associate channel with file resource
      ourFiles.mapAlias(file, fileChannel);
    }
  }

  @MethodPattern(
    {
      "sun.nio.ch.FileChannelImpl:write(java.nio.ByteBuffer) int",
      "sun.nio.ch.FileChannelImpl:write(java.nio.ByteBuffer, long) int",
      "sun.nio.ch.FileChannelImpl:write(java.nio.ByteBuffer[], int, int) long",
      "sun.nio.ch.FileChannelImpl:transferFrom(java.nio.channels.ReadableByteChannel, long, long)"
    }
  )
  public static final class FileChannel_write_Probe {
    public static int onEnter(
      @This final FileChannel fileChannel
    ) {
      return channelWriteOnEnter(fileChannel);
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ReturnValue final long bytesWritten,
      @ThrownException  final Throwable e
    ) {
      channelWriteOnExit(rowIndex, bytesWritten, e);
    }
  }

  @MethodPattern(
    {
      "sun.nio.ch.FileChannelImpl:read(java.nio.ByteBuffer) int",
      "sun.nio.ch.FileChannelImpl:read(java.nio.ByteBuffer, long) int",
      "sun.nio.ch.FileChannelImpl:read(java.nio.ByteBuffer[], int, int) long",
      "sun.nio.ch.FileChannelImpl:transferTo(long, long, java.nio.channels.WritableByteChannel)"
    }
  )
  public static final class FileChannel_read_Probe {
    public static int onEnter(
      @This final FileChannel fileChannel
    ) {
      return channelReadOnEnter(fileChannel);
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ReturnValue final long bytesRead,
      @ThrownException  final Throwable e
    ) {
      channelReadOnExit(rowIndex, bytesRead, e);
    }
  }

  @MethodPattern("java.io.RandomAccessFile:open(String, int)")
  public static final class RandomAccessFile_open_Probe {
    public static long onEnter() {
      return ourFiles.openOnEnter();
    }

    public static void onExit(
      @This final RandomAccessFile file,
      @Param(1) final String path,
      @OnEnterResult final long resourceID,
      @ThrownException  final Throwable e
    ) {
      ourFiles.openOnExit(
        resourceID,
        path,
        file,
        e,
        FailedEventPolicy.RECORD
      );
    }
  }

  @MethodPattern("java.io.RandomAccessFile:close()")
  public static final class RandomAccessFile_close_Probe {
    public static int onEnter(@This final RandomAccessFile file) {
      return ourFiles.closeOnEnter(file);
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      ourFiles.closeOnExit(rowIndex, e);
    }
  }

  @MethodPattern("java.io.RandomAccessFile:writeBytes(byte[], int, int)")
  public static final class RandomAccessFile_writeBytes_Probe {
    public static int onEnter(
      @This final RandomAccessFile randomAccessFile,
      @Param(3) final int bytesWritten
    ) {
      return readOnEnter(randomAccessFile, bytesWritten);
    }

    public static void onExit(
      @OnEnterResult final int writeRowIndex,
      @ThrownException  final Throwable e
    ) {
      writeOnExit(writeRowIndex, e);
    }
  }

  @MethodPattern("java.io.RandomAccessFile:write(int)")
  public static final class RandomAccessFile_write_Probe {
    public static int onEnter(@This final RandomAccessFile randomAccessFile) {
      return readOnEnter(randomAccessFile, 1 /* one byte to write */);
    }

    public static void onExit(
      @OnEnterResult final int writeRowIndex,
      @ThrownException  final Throwable e
    ) {
      writeOnExit(writeRowIndex, e);
    }
  }

  @MethodPattern("java.io.RandomAccessFile:read()")
  public static final class RandomAccessFile_read_Probe {
    public static int onEnter(@This final RandomAccessFile randomAccessFile) {
      return readOnEnter(randomAccessFile);
    }

    public static void onExit(
      @OnEnterResult final int readRowIndex,
      @ReturnValue final int returnValue,
      @ThrownException  final Throwable e
    ) {
      // non negative return value means that a byte has been successfully read
      final int readBytes = returnValue >= 0 ? 1 : 0;
      readOnExit(readRowIndex, readBytes, e);
    }
  }

  @MethodPattern("java.io.RandomAccessFile:readBytes(byte[], int, int)")
  public static final class RandomAccessFile_readBytes_Probe {
    public static int onEnter(@This final RandomAccessFile randomAccessFile) {
      return readOnEnter(randomAccessFile);
    }

    public static void onExit(
      @OnEnterResult final int readRowIndex,
      @ReturnValue final int bytesRead,
      @ThrownException  final Throwable e
    ) {
      readOnExit(readRowIndex, bytesRead, e);
    }
  }

    private static String retrieveFilePath(final Object file) {
    // Try to get the file path
    {
      String path = null;
      try {
        path = Callback.getFieldObjectValue(file, "path", String.class);
      }
      catch (final Throwable ignored) {
      }

      if (path != null) {
        return path;
      }
    }

    // Match the standard streams
    {
      Object fileDescriptor = null;
      try {
        fileDescriptor = Callback.getFieldObjectValue(file, "fd", FileDescriptor.class);
      }
      catch (final Throwable ignored) {
      }

      if (fileDescriptor == FileDescriptor.in) {
        return SYSTEM_IN_PSEUDO_PATH;
      }

      if (fileDescriptor == FileDescriptor.out) {
        return SYSTEM_OUT_PSEUDO_PATH;
      }

      if (fileDescriptor == FileDescriptor.err) {
        return SYSTEM_ERR_PSEUDO_PATH;
      }
    }

    // Name cannot be retrieved
    return null;
  }

  private static int getOrCreateFileRowByChannel(final FileChannel channel) {
    int fileRow = ourFiles.get(channel); // get by alias
    if (!Table.shouldIgnoreRow(fileRow)) {
      return fileRow;
    }

    Object file = null;
    try {
      file = Callback.getFieldObjectValue(channel, "parent", Object.class);
    }
    catch (final Exception ignored) {
    }

    if (
      !(file instanceof FileInputStream) &&
      !(file instanceof FileOutputStream) &&
      !(file instanceof RandomAccessFile)
    ) {
      return Table.NO_ROW;
    }

    fileRow = ourFiles.getOrCreate(file);
    if (!Table.shouldIgnoreRow(fileRow)) {
      // associate channel with file resource
      ourFiles.mapAlias(file, channel);
    }

    return fileRow;
  }

  // Record individual I/O events for streams and random access files

  private static int readOnEnter(final FileInputStream fileInputStream) {
    final int streamRowIndex = ourFiles.getOrCreate(fileInputStream);
    return T_READ.createRow(streamRowIndex);
  }

  private static int readOnEnter(final RandomAccessFile randomAccessFile) {
    final int fileRowIndex = ourFiles.getOrCreate(randomAccessFile);
    return T_READ.createRow(fileRowIndex);
  }

  private static void readOnExit(
    final int readRowIndex,
    final int bytesRead,
    @ThrownException  final Throwable e
  ) {
    T_READ.setBytes(readRowIndex, bytesRead);
    T_READ.closeRow(readRowIndex, e);
  }

  private static int writeOnEnter(
    final FileOutputStream fileOutputStream,
    final int bytesWritten
  ) {
    final int streamRowIndex = ourFiles.getOrCreate(fileOutputStream);

    final int writeRowIndex = T_WRITE.createRow(streamRowIndex);
    T_WRITE.setBytes(writeRowIndex, bytesWritten);
    return writeRowIndex;
  }

  private static int readOnEnter(
    final RandomAccessFile randomAccessFile,
    final int bytesWritten
  ) {
    final int fileRowIndex = ourFiles.getOrCreate(randomAccessFile);

    final int writeRowIndex = T_WRITE.createRow(fileRowIndex);
    T_WRITE.setBytes(writeRowIndex, bytesWritten);
    return writeRowIndex;
  }

  private static void writeOnExit(
    final int writeRowIndex,
    @ThrownException  final Throwable e
  ) {
    T_WRITE.closeRow(writeRowIndex, e);
  }

  // Record individual I/O events for channels

  private static int channelReadOnEnter(final FileChannel fileChannel) {
    return T_CHANNEL_READ.createRow(getOrCreateFileRowByChannel(fileChannel));
  }

  private static void channelReadOnExit(
    final int rowIndex,
    final long bytesRead,
    @ThrownException  final Throwable e
  ) {
    T_CHANNEL_READ.setBytes(rowIndex, bytesRead);
    T_CHANNEL_READ.closeRow(rowIndex, e);
  }

  private static int channelWriteOnEnter(final FileChannel fileChannel) {
    return T_CHANNEL_WRITE.createRow(getOrCreateFileRowByChannel(fileChannel));
  }

  private static void channelWriteOnExit(
    final int rowIndex,
    final long bytesWritten,
    @ThrownException  final Throwable e
  ) {
    T_CHANNEL_WRITE.setBytes(rowIndex, bytesWritten);
    T_CHANNEL_WRITE.closeRow(rowIndex, e);
  }
}
