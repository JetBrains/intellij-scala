package org.jetbrains.plugins.scala.lang.refactoring;

import org.jetbrains.plugins.scala.util.TestUtils;

import java.io.File;

public class JavaTestUtil {
  static String getJavaTestDataPath() {
    return new File(new File(TestUtils.getTestDataPath()).getParentFile().getParentFile(), "testdata").getPath();
  }
}
