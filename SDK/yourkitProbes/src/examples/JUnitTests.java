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

public final class JUnitTests {
  private static final String TEST_SET_UP = "setUp";
  private static final String TEST_RUN = "run";
  private static final String TEST_TEAR_DOWN = "tearDown";

  public static final String TABLE_NAME = "JUnit Test";

  private static final class TestsTable extends Table {
    private final ClassNameColumn myTestClass = new ClassNameColumn("Test Class");
    private final StringColumn myCategory = new StringColumn("Category");
    private final StringColumn myMethod = new StringColumn("Method");
    private final StringColumn myStatus = new StringColumn("Status");

    public TestsTable() {
      super(
        JUnitTests.class,
        TABLE_NAME,
        Table.MASK_FOR_LASTING_EVENTS
      );
    }
  }
  private static final TestsTable T_TESTS = new TestsTable();

  // JUnit 3.8 and older: tests extend TestCase

  @MethodPattern("*Test:setUp()")
  public static final class Old_SetUp_Probe {
    public static int onEnter(
      @ClassRef final Class testClass,
      @MethodName final String methodName
    ) {
      if (!isTestCaseClass(testClass)) {
        return Table.NO_ROW;
      }

      return handleEnter(testClass, TEST_SET_UP, methodName);
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      handleExit(rowIndex, e);
    }
  }

  @MethodPattern("*Test:test*()")
  public static final class Old_Run_Probe {
    public static int onEnter(
      @ClassRef final Class testClass,
      @MethodName final String methodName
    ) {
      if (!isTestCaseClass(testClass)) {
        return Table.NO_ROW;
      }

      return handleEnter(testClass, TEST_RUN, methodName);
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      handleExit(rowIndex, e);
    }
  }

  @MethodPattern("*Test:tearDown()")
  public static final class Old_TearDown_Probe {
    public static int onEnter(
      @ClassRef final Class testClass,
      @MethodName final String methodName
    ) {
      if (!isTestCaseClass(testClass)) {
        return Table.NO_ROW;
      }

      return handleEnter(testClass, TEST_TEAR_DOWN, methodName);
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      handleExit(rowIndex, e);
    }
  }

  private static boolean isTestCaseClass(final Class testClass) {
    try {
      if (testClass.getName().startsWith("junit.framework.")) {
        // do nothing with JUnit internals
        return false;
      }

      for (Class aClass = testClass; aClass != null; aClass = aClass.getSuperclass()) {
        if ("junit.framework.TestCase".equals(aClass.getName())) {
          return true;
        }
      }

      return false;
    }
    catch (final Throwable ignored) {
      return false;
    }
  }

  // JUnit 4.0 and newer: tests are annotated with @Before, @Test and @After

  @MethodPattern("*Test:@org.junit.Before *()")
  public static final class SetUp_Probe {
    public static int onEnter(
      @ClassRef final Class testClass,
      @MethodName final String methodName
    ) {
      return handleEnter(testClass, TEST_SET_UP, methodName);
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      handleExit(rowIndex, e);
    }
  }

  @MethodPattern("*Test:@org.junit.Test *()")
  public static final class Run_Probe {
    public static int onEnter(
      @ClassRef final Class testClass,
      @MethodName final String methodName
    ) {
      return handleEnter(testClass, TEST_RUN, methodName);
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      handleExit(rowIndex, e);
    }
  }

  @MethodPattern("*Test:@org.junit.After *()")
  public static final class TearDown_Probe {
    public static int onEnter(
      @ClassRef final Class testClass,
      @MethodName final String methodName
    ) {
      return handleEnter(testClass, TEST_TEAR_DOWN, methodName);
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      handleExit(rowIndex, e);
    }
  }

  /**
   * Create row
   * @return row index
   */
  private static int handleEnter(
    final Class aClass,
    final String category,
    final String method
  ) {
    final int rowIndex = T_TESTS.createRow();
    T_TESTS.myTestClass.setValue(rowIndex, aClass);
    T_TESTS.myCategory.setValue(rowIndex, category);
    T_TESTS.myMethod.setValue(rowIndex, method);
    T_TESTS.myStatus.setValue(rowIndex, "Running");

    return rowIndex;
  }

  /**
   * Close row
   * @param exception {@code null} if success
   */
  private static void handleExit(
    final int rowIndex,
     final Throwable exception
  ) {
    if (exception == null) {
      T_TESTS.myStatus.setValue(rowIndex, "Passed");
    }
    else {
      T_TESTS.myStatus.setValue(rowIndex, "Failed");
    }

    T_TESTS.closeRow(rowIndex, exception);
  }
}
