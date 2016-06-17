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

public final class TestNG {
  public static final String TABLE_NAME = "TestNG Method";

  private static final class TestsTable extends Table {
    private final ClassNameColumn myTestClass = new ClassNameColumn("Test Class");
    private final StringColumn myMethod = new StringColumn("Method");

    public TestsTable() {
      super(TestNG.class, TABLE_NAME, Table.MASK_FOR_LASTING_EVENTS);
    }
  }
  private static final TestsTable T_TESTS = new TestsTable();

  @MethodPattern({
    "*test*:@org.testng.annotations.Test *(*)",
    "*Test*:@org.testng.annotations.Test *(*)"
  })
  public static final class Test_Probe {
    public static int onEnter(
      @ClassRef final Class testClass,
      @MethodName final String methodName
    ) {
      final int rowIndex = T_TESTS.createRow();
      T_TESTS.myMethod.setValue(rowIndex, methodName);
      T_TESTS.myTestClass.setValue(rowIndex, testClass);
      return rowIndex;
    }

    public static void onExit(
      @OnEnterResult final int rowIndex,
      @ThrownException  final Throwable e
    ) {
      T_TESTS.closeRow(rowIndex, e);
    }
  }
}
