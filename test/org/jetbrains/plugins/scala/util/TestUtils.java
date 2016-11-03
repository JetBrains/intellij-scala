/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.testFramework.ThreadTracker;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.Console;
import org.jetbrains.plugins.scala.debugger.DebuggerTestUtil$;
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


  public static PsiFile createPseudoPhysicalScalaFile(final Project project, final String text) throws IncorrectOperationException {
    String TEMP_FILE = project.getBaseDir() + "temp.scala";
    return PsiFileFactory.getInstance(project).createFileFromText(
        TEMP_FILE,
        FileTypeManager.getInstance().getFileTypeByFileName(TEMP_FILE),
        text,
        LocalTimeCounter.currentTime(),
        true);
  }

  private static String TEST_DATA_PATH = null;

  public static String getTestName(String name, boolean lowercaseFirstLetter) {
    Assert.assertTrue(name.startsWith("test"));
    name = name.substring("test".length());
    if (lowercaseFirstLetter) {
      name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
    return name;
  }



  public static String getTestDataPath() {
    if (TEST_DATA_PATH == null) {
      ClassLoader loader = TestUtils.class.getClassLoader();
      URL resource = loader.getResource("testdata");
      try {
        // jzaugg: this logic was added to stay backwards compatible. Does anyone/anything
        // rely on this working from the working directory one level higher than "./scala-plugin"? If not,
        // we can just simplify to use `f2`
        File f1 = new File("scala-plugin", "testdata");
        if (f1.exists()) {
          TEST_DATA_PATH = f1.getAbsolutePath();
        } else {
          File f2 = new File("testdata");
          TEST_DATA_PATH = f2.getAbsolutePath();
        }
        if (resource != null) {
          TEST_DATA_PATH = new File(resource.toURI()).getPath().replace(File.separatorChar, '/');
        }
      } catch (URISyntaxException e) {
        LOG.error(e);
        return null;
      }
    }

    return TEST_DATA_PATH;
  }


  public static String getDefaultJdk() {
    String path = DebuggerTestUtil$.MODULE$.discoverJDK18().get();
    VfsRootAccess.allowRootAccess(path);
    return path;
  }

  public enum ScalaSdkVersion {
    _2_10("2.10", "2.10.6"), _2_11("2.11", "2.11.7"), _2_11_8("2.11", "2.11.8"),
    _2_12_OLD("2.12", "2.12.0-M4"), _2_12("2.12", "2.12.0-M5");
    private String major;
    private String minor;

    ScalaSdkVersion(String major, String minor) {
      this.major = major;
      this.minor = minor;
    }

    public String getMinor() {
      return minor;
    }

    public String getMajor() {
      return major;
    }
  }

  public static final ScalaSdkVersion DEFAULT_SCALA_SDK_VERSION = ScalaSdkVersion._2_10;

  public static String getScalaLibraryPath() {
    return getScalaLibraryPath(DEFAULT_SCALA_SDK_VERSION);
  }

  public static String getScalaLibrarySrc() {
    return getScalaLibrarySrc(DEFAULT_SCALA_SDK_VERSION);
  }

  public static String getMockScalazLib(ScalaSdkVersion version) {
    String major = version.getMajor();
    String dirName = "/org.scalaz/scalaz-core_" + major + "/bundles/";
    String fileName = "scalaz-core_" + major + "-7.1.0.jar";
    return getIvyCachePath() + dirName + fileName;
  }

  public static String getMockSprayLib(ScalaSdkVersion version) {
    return getIvyCachePath() + "/io.spray/spray-routing_2.11/bundles/spray-routing_2.11-1.3.1.jar" ;
  }

  public static String getMockSlickLib(ScalaSdkVersion version) {
    return getIvyCachePath() + "/com.typesafe.slick/slick_2.11/bundles/slick_2.11-3.1.0.jar" ;
  }

  public static String getSpecs2Lib(ScalaSdkVersion version) {
    return getIvyCachePath() + "/org.specs2/specs2_2.11/jars/specs2_2.11-2.4.15.jar" ;
  }

  public static String getScalacheckLib(ScalaSdkVersion version) {
    return getIvyCachePath() + "/org.scalacheck/scalacheck_2.11/jars/scalacheck_2.11-1.12.5.jar" ;
  }

  public static String getPostgresLib(ScalaSdkVersion version) {
    return getIvyCachePath() +  "/com.wda.sdbc/postgresql_2.11/jars/postgresql_2.11-0.5.jar" ;
  }

  public static String getCatsLib(ScalaSdkVersion version) {
    return getIvyCachePath() + "/org.typelevel/cats-core_2.11/jars/cats-core_2.11-0.4.0.jar" ;
  }

  public static String getScalaLibrarySrc(ScalaSdkVersion version) {
    String fileName = "scala-library-" + version.getMinor() + "-sources.jar";
    return getIvyCachePath() + "/org.scala-lang/scala-library/srcs/" + fileName;
  }

  public static String getIvyCachePath() {
    String homePath = System.getProperty("user.home") + "/.ivy2/cache";
    String ivyCachePath = System.getProperty("sbt.ivy.home");
    String result = ivyCachePath != null ? ivyCachePath + "/cache" : homePath;
    return result.replace("\\", "/");
  }

  public static String getScalaLibraryPath(ScalaSdkVersion version) {
    String fileName = "scala-library-" + version.getMinor() + ".jar";
    return getIvyCachePath() + "/org.scala-lang/scala-library/jars/" + fileName;
  }

  public static String getScalaCompilerPath(ScalaSdkVersion version) {
    String fileName = "scala-compiler-" + version.getMinor() + ".jar";
    return getIvyCachePath() + "/org.scala-lang/scala-compiler/jars/" + fileName;
  }

  public static String getScalaReflectPath(ScalaSdkVersion version) {
    String fileName = "scala-reflect-" + version.getMinor() + ".jar";
    return getIvyCachePath() + "/org.scala-lang/scala-reflect/jars/" + fileName;
  }
  
  public static String removeCaretMarker(String text) {
    int index = text.indexOf(CARET_MARKER);
    return text.substring(0, index) + text.substring(index + CARET_MARKER.length());
  }

  public static String removeBeginMarker(String text) {
    int index = text.indexOf(BEGIN_MARKER);
    return text.substring(0, index) + text.substring(index + BEGIN_MARKER.length());
  }

  public static String removeEndMarker(String text) {
    int index = text.indexOf(END_MARKER);
    return text.substring(0, index) + text.substring(index + END_MARKER.length());
  }

  private static final long ETALON_TIMING = 438;

  public static final boolean COVERAGE_ENABLED_BUILD = "true".equals(System.getProperty("idea.coverage.enabled.build"));

  public static void assertTiming(String message, long expected, long actual) {
    if (COVERAGE_ENABLED_BUILD) return;
    long expectedOnMyMachine = expected * Timings.MACHINE_TIMING / ETALON_TIMING;
    final double acceptableChangeFactor = 1.1;

    // Allow 10% more in case of test machine is busy.
    // For faster machines (expectedOnMyMachine < expected) allow nonlinear performance rating:
    // just perform better than acceptable expected
    if (actual > expectedOnMyMachine * acceptableChangeFactor &&
        (expectedOnMyMachine > expected || actual > expected * acceptableChangeFactor)) {
      int percentage = (int)(((float)100 * (actual - expectedOnMyMachine)) / expectedOnMyMachine);
      Assert.fail(message + ". Operation took " + percentage + "% longer than expected. Expected on my machine: " + expectedOnMyMachine +
                  ". Actual: " + actual + ". Expected on Etalon machine: " + expected + "; Actual on Etalon: " +
                  (actual * ETALON_TIMING / Timings.MACHINE_TIMING));
    }
    else {
      int percentage = (int)(((float)100 * (actual - expectedOnMyMachine)) / expectedOnMyMachine);
      Console.println(message + ". Operation took " + percentage + "% longer than expected. Expected on my machine: " +
                         expectedOnMyMachine + ". Actual: " + actual + ". Expected on Etalon machine: " + expected +
                         "; Actual on Etalon: " + (actual * ETALON_TIMING / Timings.MACHINE_TIMING));
    }
  }

  public static void assertTiming(String message, long expected, @NotNull Runnable actionToMeasure) {
    assertTiming(message, expected, 4, actionToMeasure);
  }

  public static void assertTiming(String message, long expected, int attempts, @NotNull Runnable actionToMeasure) {
    while (true) {
      attempts--;
      long start = System.currentTimeMillis();
      actionToMeasure.run();
      long finish = System.currentTimeMillis();
      try {
        assertTiming(message, expected, finish - start);
        break;
      }
      catch (AssertionError e) {
        if (attempts == 0) throw e;
        System.gc();
        System.gc();
        System.gc();
      }
    }
  }
  
  public static List<String> readInput(String filePath) throws IOException {
    String content = new String(FileUtil.loadFileText(new File(filePath)));
    Assert.assertNotNull(content);

    List<String> input = new ArrayList<String>();

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
    Assert.assertNotNull("Test output points to null", input.size() > 1);

    return input;
  }

  public static void setLanguageLevel(Project project, LanguageLevel level) {
    LanguageLevelProjectExtension.getInstance(project).setLanguageLevel(level);
  }


  public static void disableTimerThread() {
    ThreadTracker.longRunningThreadCreated(ApplicationManager.getApplication(), "Timer");
    ThreadTracker.longRunningThreadCreated(ApplicationManager.getApplication(), "BaseDataReader");
    ThreadTracker.longRunningThreadCreated(ApplicationManager.getApplication(), "ProcessWaitFor");
  }
}
