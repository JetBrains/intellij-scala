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

/**
 * Probes to monitor monitor some aspects of thread life cycle:
 * where and when threads are created (instance of {@link Thread} or its subclass),
 * started ({@link Thread#start()}), and their names changed ({@link Thread#setName(String)})
 * For detail, see <a href="//www.yourkit.com/docs/java/help/threads_probe.jsp" target="_blank">Help</a>.
 */
public final class Threads {
  public static final String TABLE_NAME = "Thread";
  /**
   * Mapping between Thread objects and row index in T_THREAD table
   */
  private static final ObjectRowIndexMap<Object> ourThread2RowIndex = new ObjectRowIndexMap<Object>();

  private static final ResourceTable<Object> T_THREAD = new ResourceTable<Object>(
    Threads.class,
    TABLE_NAME,
    "Name" // resource name column
  );

  private static final class ThreadCreateTable extends Table {
    public ThreadCreateTable() {
      super(T_THREAD, "Create", Table.MASK_FOR_POINT_EVENTS);
    }
  }
  private static final ThreadCreateTable T_CREATE = new ThreadCreateTable();

  private static final class ThreadNameHistoryTable extends Table {
    private final StringColumn myName = new StringColumn("Name");

    public ThreadNameHistoryTable() {
      super(T_THREAD, "Set Name", Table.MASK_FOR_POINT_EVENTS);
    }
  }
  private static final ThreadNameHistoryTable T_NAME = new ThreadNameHistoryTable();

  private static final class ThreadStartTable extends Table {
    public ThreadStartTable() {
      super(T_THREAD, "Start", Table.MASK_FOR_POINT_EVENTS);
    }
  }
  private static final ThreadStartTable T_START = new ThreadStartTable();

  private static final class ThreadRunTable extends Table {
    public ThreadRunTable() {
      super(
        T_THREAD,
        "Run",
        LASTING_EVENTS | RECORD_THREAD // don't record stack trace - it's always <Thread class>.run()
      );
    }
  }
  private static final ThreadRunTable T_RUN = new ThreadRunTable();

  @MethodPattern("java.lang.Thread:<init>(*)")
  public static final class Thread_constructor_Probe {
    public static int onEnter(@This final Object thread) {
      if (!JVM.isLivePhase()) {
        return Table.NO_ROW;
      }

      final int existingRowIndex = ourThread2RowIndex.get(thread);
      if (!Table.shouldIgnoreRow(existingRowIndex)) {
        // skip nested constructor call
        return Table.NO_ROW;
      }

      final int rowIndex = T_THREAD.createRow();

      // remember now to detect nested constructor calls
      ourThread2RowIndex.put(thread, rowIndex);

      return rowIndex;
    }

    public static void onExit(
      @This final Object thread,
      @OnEnterResult final int rowIndex
    ) {
      if (Table.shouldIgnoreRow(rowIndex)) {
        return;
      }

      String name = null;
      try {
        name = ((Thread)thread).getName();
        //noinspection ConstantConditions
        if (name != null && name.startsWith("YJPAgent-")) {
          // skip profiler internal threads

          T_THREAD.deleteRow(rowIndex);
          ourThread2RowIndex.remove(thread);
          return;
        }
      }
      catch (final Throwable ignored) {
      }

      // "Create" event
      T_CREATE.createRow(rowIndex);

      T_THREAD.setResourceObject(rowIndex, thread);
      // set current thread name
      T_THREAD.setResourceName(rowIndex, name);
    }
  }

  @MethodPattern("java.lang.Thread:start()")
  public static final class Thread_start_Probe {
    public static void onEnter(@This final Object thread) {
      if (!JVM.isLivePhase()) {
        return;
      }

      final int threadRowIndex = ourThread2RowIndex.get(thread);
      T_START.createRow(threadRowIndex);
    }
  }

  @MethodPattern(
    {
      "java.lang.*:run()", // must explicitly add Thread and other core classes not covered with "*"
      "*:run() void"
    }
  )
  public static final class Thread_run_Probe {
    public static int onEnter(@This final Thread thread) {
      if (!JVM.isLivePhase()) {
        return Table.NO_ROW;
      }

      final int threadRowIndex = ourThread2RowIndex.get(thread);
      return T_RUN.createRow(threadRowIndex);
    }

    public static void onExit(
      @OnEnterResult final int runRow,
      @ThrownException  final Throwable e
    ) {
      T_RUN.closeRow(runRow, e);
    }
  }

  @MethodPattern("java.lang.Thread:setName(String)")
  public static final class Thread_setName_Probe {
    public static void onReturn(
      @This final Object thread,
      @Param(1) final String name
    ) {
      if (!JVM.isLivePhase()) {
        return;
      }

      final int rowIndex = ourThread2RowIndex.get(thread);
      if (Table.shouldIgnoreRow(rowIndex)) {
        // thread has not been created yet, or the event was cleared
        return;
      }

      // create name history record
      final int historyRowIndex = T_NAME.createRow(rowIndex);
      T_NAME.myName.setValue(historyRowIndex, name);

      // update current thread name
      T_THREAD.setResourceName(rowIndex, name);
    }
  }
}
