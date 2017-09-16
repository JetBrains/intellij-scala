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

package org.jetbrains.plugins.scala.testcases;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightIdeaTestCase;
import junit.framework.TestSuite;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.FileScanner;
import org.jetbrains.plugins.scala.SlowTests;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 *
 * @author oleg
 * @date Oct 11, 2006
 */
public abstract class FileSetTestCase extends TestSuite {
  @NonNls
  protected static final String TEST_FILE_PATTERN = "(.*)\\.test";
  private File[] myFiles;
  private Project myProject;
  protected Project getProject() {
    return myProject;
  }

  public FileSetTestCase(String path) {
    List<File> myFileList;
    try {
      myFileList = FileScanner.scan(path, getSearchPattern(), false);
    } catch (FileNotFoundException e) {
      myFileList = new ArrayList<>();
    }
    myFiles = myFileList.toArray(new File[myFileList.size()]);
    addAllTests();
  }

  protected void setUp(Project project) {
      myProject = project;
  }

  protected void tearDown(Project project) {
    myProject = null;
  }

  private void addAllTests() {
    for (File f : myFiles) {
      if (f.isFile()) {
        addFileTest(f);
      }
    }
  }

  public String getName() {
    return getClass().getName();
  }

  public String getSearchPattern() {
    return TEST_FILE_PATTERN;
  }

  protected void addFileTest(File file) {
    if (!StringUtil.startsWithChar(file.getName(), '_') &&
            !"CVS".equals(file.getName())) {
      final ActualTest t = new ActualTest(file);
      addTest(t);
    }
  }

  protected abstract void runTest(final File file) throws Throwable;

  private class ActualTest extends LightIdeaTestCase {
    private File myTestFile;

    @NotNull
    @Override
    protected String getTestName(boolean lowercaseFirstLetter) {
      return "";
    }

    public ActualTest(File testFile) {
      myTestFile = testFile;
    }

    protected void setUp() throws Exception {
      super.setUp();
      FileSetTestCase.this.setUp(getProject());
      TestUtils.disableTimerThread();
    }

    protected void tearDown() throws Exception {
      FileSetTestCase.this.tearDown(getProject());
      try {
        super.tearDown();
      }
      catch (IllegalArgumentException ignore) {
      }
    }

    protected void runTest() throws Throwable {
      FileSetTestCase.this.runTest(myTestFile);
    }

    public int countTestCases() {
      return 1;
    }

    public String toString() {
      return myTestFile.getAbsolutePath() + " ";
    }

    protected void resetAllFields() {
      // Do nothing otherwise myTestFile will be nulled out before getName() is called.
    }

    public String getName() {
      return myTestFile.getAbsolutePath();
    }
  }

}
