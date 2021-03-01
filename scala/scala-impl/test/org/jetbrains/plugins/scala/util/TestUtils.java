/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.ThreadTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Ilya.Sergey
 */
public class TestUtils {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.scala.util.TestUtils");

  public static final String CARET_MARKER = "<caret>";
  public static final String BEGIN_MARKER = "<begin>";
  public static final String END_MARKER = "<end>";

  private static String TEST_DATA_PATH = null;

  @NotNull
  public static String getTestDataPath() {
    if (TEST_DATA_PATH == null) {
      try {
        URL resource = TestUtils.class.getClassLoader().getResource("testdata");
        TEST_DATA_PATH = resource == null ?
                find("scala/scala-impl", "testdata").getAbsolutePath() :
                new File(resource.toURI()).getPath().replace(File.separatorChar, '/');
      } catch (URISyntaxException | IOException e) {
        LOG.error(e);
        throw new RuntimeException(e);
        // just rethrowing here because that's a clearer way to make tests fail than some NPE somewhere else
      }
    }

    return TEST_DATA_PATH;
  }

  public static String findCommunityRoot() throws IOException {
    // <community-root>/scala/scala-impl/testdata/
    String testDataPath = getTestDataPath();
    return java.nio.file.Paths.get(testDataPath, "..", "..", "..").normalize().toString() + "/";
  }

  @NotNull
  public static String findTestDataDir(String pathname) throws IOException {
    return findTestDataDir(new File(pathname), "testdata");
  }

  private static File find(String pathname, String child) throws IOException {
    File file = new File("community/" + pathname, child);
    return file.exists() ? file : new File(findTestDataDir(pathname));
  }

  /** Go upwards to find testdata, because when running test from IDEA, the launching dir might be some subdirectory. */
  @NotNull
  private static String findTestDataDir(File parent, String child) throws IOException {
    File testData = new File(parent, child).getCanonicalFile();

    if (testData.exists()) {
      return testData.getCanonicalPath();
    } else {
      File newParent = parent.getCanonicalFile().getParentFile();
      if (newParent == null) throw new RuntimeException("no testdata directory found");
      else return findTestDataDir(newParent, child);
    }
  }

  public static String removeBeginMarker(String text) {
    int index = text.indexOf(BEGIN_MARKER);
    return text.substring(0, index) + text.substring(index + BEGIN_MARKER.length());
  }

  public static String removeEndMarker(String text) {
    int index = text.indexOf(END_MARKER);
    return text.substring(0, index) + text.substring(index + END_MARKER.length());
  }

  public static List<String> readInput(String filePath) throws IOException {
    return readInput(new File(filePath), null);
  }

  public static List<String> readInput(File file, @Nullable String encoding) throws IOException {
    String content = new String(FileUtil.loadFileText(file, encoding));
    Assert.assertNotNull(content);

    List<String> input = new ArrayList<>();

    int separatorIndex;
    content = StringUtil.replace(content, "\r", ""); // for MACs

    // Adding input  before -----
    while ((separatorIndex = content.indexOf("-----")) >= 0) {
      input.add(content.substring(0, separatorIndex - 1));
      content = content.substring(separatorIndex);
      while (StringUtil.startsWithChar(content, '-')) {
        content = content.substring(1);
      }
      if (StringUtil.startsWithChar(content, '\n')) {
        content = content.substring(1);
      }
    }
    // Result - after -----
    if (content.endsWith("\n")) {
      content = content.substring(0, content.length() - 1);
    }
    input.add(content);

    Assert.assertTrue("No data found in source file", input.size() > 0);

    return input;
  }


  public static void disableTimerThread() {
    ThreadTracker.longRunningThreadCreated(UnloadAwareDisposable.scalaPluginDisposable(), "Timer");
    ThreadTracker.longRunningThreadCreated(UnloadAwareDisposable.scalaPluginDisposable(), "BaseDataReader");
    ThreadTracker.longRunningThreadCreated(UnloadAwareDisposable.scalaPluginDisposable(), "ProcessWaitFor");
  }
}
