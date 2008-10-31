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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Created by IntelliJ IDEA.
 * User: Ilya.Sergey
 */
public class TestUtils {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.scala.util.TestUtils");

  public static final String CARET_MARKER = "<caret>";
  public static final String BEGIN_MARKER = "<begin>";
  public static final String END_MARKER = "<end>";

  public static PsiFile createPseudoPhysicalFile(final Project project, final String text, int i) throws IncorrectOperationException {
    String TEMP_FILE = project.getProjectFilePath() + "temp" + i + ".scala";
    return PsiFileFactory.getInstance(project).createFileFromText(
        TEMP_FILE,
        FileTypeManager.getInstance().getFileTypeByFileName(TEMP_FILE),
        text,
        LocalTimeCounter.currentTime(),
        true);
  }


  public static PsiFile createPseudoPhysicalScalaFile(final Project project, final String text) throws IncorrectOperationException {
    String TEMP_FILE = project.getProjectFilePath() + "temp.scala";
    return PsiFileFactory.getInstance(project).createFileFromText(
        TEMP_FILE,
        FileTypeManager.getInstance().getFileTypeByFileName(TEMP_FILE),
        text,
        LocalTimeCounter.currentTime(),
        true);
  }

  private static String TEST_DATA_PATH = null;

  public static String getTestDataPath() {
    if (TEST_DATA_PATH == null) {
      ClassLoader loader = TestUtils.class.getClassLoader();
      URL resource = loader.getResource("testdata");
      try {
        TEST_DATA_PATH = new File(resource.toURI()).getPath().replace(File.separatorChar, '/');
      } catch (URISyntaxException e) {
        LOG.error(e);
        return null;
      }
    }

    return TEST_DATA_PATH;
  }


  public static String getMockJdk() {
    return getTestDataPath() + "/mockJDK";
  }

  public static String getMockScalaLib() {
    return getTestDataPath() + "/mockScalaLib/scala.jar";
  }

  public static String getMockScalaSrc() {
    return getTestDataPath() + "/mockScalaLib/scala-src.jar";
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
}
