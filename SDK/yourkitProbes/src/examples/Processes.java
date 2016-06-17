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

import java.io.*;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;

public final class Processes {
  public static final String TABLE_NAME = "Process";

  private static final boolean IS_WINDOWS = System.getProperty("os.name").startsWith("Windows");

  /**
   * Mapping between tracked object (process or stream) to row index in T_PROCESS table
   */
  private static final ObjectRowIndexMap<Object> ourObject2ProcessRowIndex = new ObjectRowIndexMap<Object>();
  private static final HashSet<Integer> ourUnclosedRowIndices = new HashSet<Integer>();
  /**
   * Remember stderr streams to distinct them from stdout streams
   */
  private static final ObjectSet<Object> ourErrorStreams = new ObjectSet<Object>();

  private static final class ProcessTable extends Table {
    private final StringColumn myCommand = new StringColumn("Command");
    private final StringColumn myDirectory = new StringColumn("Directory");
    private final StringColumn myEnvironment = new StringColumn("Environment");
    private final IntColumn myPID = new IntColumn("PID");

    public ProcessTable() {
      super(Processes.class, TABLE_NAME, Table.MASK_FOR_POINT_EVENTS);
    }
  }
  private static final ProcessTable T_PROCESS = new ProcessTable();

  private static final class IOOperationTable extends Table {
    private final IntColumn myBytes = new IntColumn("Bytes");

    public IOOperationTable(final String title) {
      super(T_PROCESS, title, Table.MASK_FOR_LASTING_EVENTS);
    }
  }
  private static final IOOperationTable T_STDOUT = new IOOperationTable("Output Read");
  private static final IOOperationTable T_STDERR = new IOOperationTable("Error Read");
  private static final IOOperationTable T_STDIN = new IOOperationTable("Input Write");

  private static final class ExitTable extends Table {
    private final StringColumn myExitCode = new StringColumn("Exit Code"); // store as string to avoid redundant min-max columns in UI

    public ExitTable() {
      super(T_PROCESS, "Exit", Table.MASK_FOR_POINT_EVENTS);
    }
  }
  private static final ExitTable T_EXIT = new ExitTable();

  static {
    if (IS_WINDOWS) {
      startProcessExitListenerThread();
    }
  }

  private static void startProcessExitListenerThread() {
    // A daemon thread to asynchronously monitor exited processes.
    // We need it to handle processes for which waitFor() is not used.
    final Thread thread = new Thread(
      new Runnable() {
        @Override
        public void run() {
          //noinspection InfiniteLoopStatement
          while (true) {
            try {
              try {
                Thread.sleep(1000);
              }
              catch (final InterruptedException ignored) {
                continue;
              }

              final int[] exitCodes = JVM.getExitedProcesses();
              if (exitCodes == null) {
                continue;
              }

              // if any waitFor() are waiting for this thread, give them chance to trigger first
              try {
                Thread.sleep(100);
              }
              catch (final InterruptedException ignored) {
                continue;
              }

              for (int i = 0; i < exitCodes.length; i += 2) {
                final int rowIndex = exitCodes[i];
                final int exitCode = exitCodes[i+1];

                handleProcessExit(rowIndex, exitCode);
              }
            }
            catch (final Throwable e) {
              Callback.messageToLogFile(Callback.getExceptionStackTrace(e));
            }
          }
        }
      },
      "YJP-Process-Exit-Listener"
    );
    thread.setDaemon(true);
    thread.start();
  }

  private static void handleProcessExit(final int processRow, final int exitCode) {
    if (Table.shouldIgnoreRow(processRow)) {
      return;
    }

    synchronized (ourUnclosedRowIndices) {
      if (!ourUnclosedRowIndices.remove(processRow)) {
        // the row has already been closed
        return;
      }
    }

    final int exitRow = T_EXIT.createRow(processRow);
    T_EXIT.myExitCode.setValue(exitRow, String.valueOf(exitCode));
  }

  /**
   * Associate input streams with process
   */
  @MethodPattern(
    {
      "java.lang.ProcessImpl:getInputStream()",
      "java.lang.UNIXProcess:getInputStream()"
    }
  )
  public static final class ProcessImpl_getInputStream_Probe {
    public static void onReturn(
      @ReturnValue  final InputStream stream,
      @This final Process process
    ) {
      final int rowIndex = ourObject2ProcessRowIndex.get(process);
      if (Table.shouldIgnoreRow(rowIndex)) {
        return;
      }

      final InputStream inputStream = getProperInputStream(stream);
      if (inputStream == null) {
        return;
      }

      ourObject2ProcessRowIndex.put(inputStream, rowIndex);
    }
  }

  /**
   * Associate error streams with process
   */
  @MethodPattern(
    {
      "java.lang.ProcessImpl:getErrorStream()",
      "java.lang.UNIXProcess:getErrorStream()"
    }
  )
  public static final class ProcessImpl_getErrorStream_Probe {
    public static void onReturn(
      @ReturnValue  final InputStream stream,
      @This final Process process
    ) {
      final int rowIndex = ourObject2ProcessRowIndex.get(process);
      if (Table.shouldIgnoreRow(rowIndex)) {
        return;
      }

      final InputStream errorStream = getProperInputStream(stream);
      if (errorStream == null) {
        return;
      }

      ourObject2ProcessRowIndex.put(errorStream, rowIndex);
      ourErrorStreams.add(errorStream);
    }
  }

  /**
   * Associate output streams with process
   */
  @MethodPattern(
    {
      "java.lang.ProcessImpl:getOutputStream()",
      "java.lang.UNIXProcess:getOutputStream()"
    }
  )
  public static final class ProcessImpl_getOutputStream_Probe {
    public static void onReturn(
      @ReturnValue  final OutputStream stream,
      @This final Process process
    ) {
      final int rowIndex = ourObject2ProcessRowIndex.get(process);
      if (Table.shouldIgnoreRow(rowIndex)) {
        return;
      }

      final FileOutputStream outputStream = getFileOutputStream(stream);
      if (outputStream == null) {
        return;
      }

      ourObject2ProcessRowIndex.put(outputStream, rowIndex);
    }
  }

  @MethodPattern("java.lang.UNIXProcess:waitForProcessExit(int)")
  public static final class UnixProcess_waitForProcessExit_Probe {
    public static void onReturn(
      @ReturnValue final int exitCode,
      @This final Process process
    ) {
      final int rowIndex = ourObject2ProcessRowIndex.get(process);
      handleProcessExit(rowIndex, exitCode);
    }
  }

  @MethodPattern("java.lang.ProcessImpl:start(String[], java.util.Map, String, *)")
  public static final class ProcessImpl_start_Probe {
    public static void onReturn(
      @ReturnValue  final Process process,
      @Param(1) final String[] cmd,
      @Param(2) final Map<String, String> env,
      @Param(3) final String dir
    ) {
      if (process == null) {
        return;
      }

      final int rowIndex = T_PROCESS.createRow();
      if (Table.shouldIgnoreRow(rowIndex)) {
        return;
      }

      synchronized (ourUnclosedRowIndices) {
        ourObject2ProcessRowIndex.put(process, rowIndex);
        ourUnclosedRowIndices.add(rowIndex);
      }

      T_PROCESS.myCommand.setValue(rowIndex, Callback.getCommandLineSpaceSeparated(cmd));
      T_PROCESS.myDirectory.setValue(rowIndex, dir);
      T_PROCESS.myEnvironment.setValue(rowIndex, getEnvironmentAsString(env));
      T_PROCESS.myPID.setValue(rowIndex, getPID(process));

      // ensure that we record the process exit code
      if (IS_WINDOWS) {
        JVM.registerProcessExitListener(process, rowIndex);
      }
    }
  }

  @MethodPattern(
    {
      "java.lang.ProcessImpl:waitFor()",
      "java.lang.UNIXProcess:waitFor()"
    }
  )
  public static final class ProcessImpl_waitFor_Probe {
    public static void onReturn(
      @This final Process process,
      @ReturnValue final int exitCode
    ) {
      final int rowIndex = ourObject2ProcessRowIndex.get(process);
      handleProcessExit(rowIndex, exitCode);
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
      return processWriteEnterImpl(fileOutputStream, bytesWritten);
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      processWriteExitImpl(rowIndex, e);
    }
  }

  @MethodPattern("java.io.FileOutputStream:write(int)")
  public static final class FileOutputStream_write_Probe {
    public static int onEnter(@This final FileOutputStream fileOutputStream) {
      return processWriteEnterImpl(fileOutputStream, 1 /* one byte to be written */);
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      processWriteExitImpl(rowIndex, e);
    }
  }

  private static int processWriteEnterImpl(final FileOutputStream fileOutputStream, final int bytesWritten) {
    final int rowIndex = ourObject2ProcessRowIndex.get(fileOutputStream);
    if (Table.shouldIgnoreRow(rowIndex)) {
      return Table.NO_ROW;
    }

    final int writeRowIndex = T_STDIN.createRow(rowIndex);
    T_STDIN.myBytes.setValueIfPositive(writeRowIndex, bytesWritten);
    return writeRowIndex;
  }

  private static void processWriteExitImpl(
    final int rowIndex,
    @ThrownException  final Throwable e
  ) {
    T_STDIN.closeRow(rowIndex, e);
  }

  @MethodPattern(
    {
      "java.io.FileInputStream:read()",
      "java.lang.ProcessInputStream:read()"
    }
  )
  public static final class InputStream_read_Probe {
    public static int onEnter(@This final InputStream inputStream) {
      return processReadEnterImpl(inputStream);
    }

    public static void onExit(
      @This final InputStream inputStream,
      @OnEnterResult final int readRowIndex,
      @ReturnValue final int returnValue,
      @ThrownException  final Throwable e
    ) {
      // non negative return value means that a byte has been successfully read
      final int readBytes = returnValue >= 0 ? 1 : 0;
      processReadExitImpl(inputStream, readRowIndex, readBytes, e);
    }
  }

  @MethodPattern(
    {
      "java.io.FileInputStream:readBytes(byte[], int, int)",
      // The following is for IBM Java on UNIX:
      "java.lang.ProcessInputStream:read(byte[], int, int)",
      "java.lang.ProcessInputStream:read(byte[])"
    }
  )
  public static final class InputStream_readBytes_Probe {
    public static int onEnter(@This final InputStream inputStream) {
      return processReadEnterImpl(inputStream);
    }

    public static void onExit(
      @This final InputStream inputStream,
      @OnEnterResult final int readRowIndex,
      @ReturnValue final int bytesRead,
      @ThrownException  final Throwable e
    ) {
      processReadExitImpl(inputStream, readRowIndex, bytesRead, e);
    }
  }

  /**
   * Record individual read event
   */
  private static int processReadEnterImpl(final InputStream inputStream) {
    final int rowIndex = ourObject2ProcessRowIndex.get(inputStream);
    if (Table.shouldIgnoreRow(rowIndex)) {
      return Table.NO_ROW;
    }

    final IOOperationTable readTable = ourErrorStreams.contains(inputStream) ? T_STDERR : T_STDOUT;

    return readTable.createRow(rowIndex);
  }

  private static void processReadExitImpl(
    final InputStream inputStream,
    final int readRowIndex,
    final int bytesRead,
    @ThrownException  final Throwable e
  ) {
    if (Table.shouldIgnoreRow(readRowIndex)) {
      return;
    }

    final IOOperationTable readTable = ourErrorStreams.contains(inputStream) ? T_STDERR : T_STDOUT;

    readTable.myBytes.setValueIfPositive(readRowIndex, bytesRead);
    readTable.closeRow(readRowIndex, e);
  }

  private static FileOutputStream getFileOutputStream(final OutputStream stream) {
    if (stream instanceof BufferedOutputStream) {
      try {
        return Callback.getFieldObjectValue(stream, "out", OutputStream.class);
      }
      catch (final Throwable ignored) {
        return null;
      }
    }

    if (stream instanceof FileOutputStream) {
      return (FileOutputStream)stream;
    }

    return null;
  }

  private static InputStream getProperInputStream( final InputStream stream) {
    if (stream instanceof BufferedInputStream) {
      try {
        return Callback.getFieldObjectValue(stream, "in", InputStream.class);
      }
      catch (final Throwable ignored) {
        return null;
      }
    }

    if (stream instanceof FileInputStream) {
      return stream;
    }

    if (
      stream != null &&
      // cannot use instanceof for this non-public IBM Java class
      "java.lang.ProcessInputStream".equals(stream.getClass().getName())
    ) {
      return stream;
    }

    return null;
  }

  static String getEnvironmentAsString(final Map<String, String> env) {
    if (env == null) {
      return "";
    }

    final StringBuilder sb = new StringBuilder();
    for (final Map.Entry<String, String> e : env.entrySet()) {
      sb.append(e.getKey());
      sb.append('=');
      sb.append(e.getValue());
      sb.append('\n');
    }

    return sb.toString();
  }

  private static int getPID(final Process process) {
    if (IS_WINDOWS) {
      try {
        final long handle = Callback.getFieldLongValue(process.getClass(), process, "handle");
        return Callback.getProcessID(handle);
      }
      catch (final Throwable ignored) {
      }
    }
    else {
      try {
        final Field f = process.getClass().getDeclaredField("pid");
        f.setAccessible(true);
        return f.getInt(process);
      }
      catch (final Throwable ignored) {
      }
    }

    // unknown
    return 0;
  }
}
