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

package org.jetbrains.plugins.scala.lang.formatter;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.Console;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;
import org.jetbrains.plugins.scala.util.TestUtils;

import java.io.File;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: Ilya.Sergey
 */

public class FormatterTest extends BaseScalaFileSetTestCase {
  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/scala/lang/formatter/data/";

  public FormatterTest() throws IOException {
    super(
      System.getProperty("path") != null ?
      System.getProperty("path") :
      (new File(DATA_PATH)).getCanonicalPath()
    );
  }

  public FormatterTest(String path) {
    super(path);
  }

  protected void performFormatting(final Project project, final PsiFile file) throws IncorrectOperationException {
    TextRange myTextRange = file.getTextRange();
    CodeStyleManager.getInstance(project).reformatText(file, myTextRange.getStartOffset(), myTextRange.getEndOffset());
  }

  public String transform(String testName, String[] data) throws Exception {
    String fileText = data[0];
    final PsiFile psiFile = TestUtils.createPseudoPhysicalScalaFile(getProject(), fileText);
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              performFormatting(getProject(), psiFile);
            } catch (IncorrectOperationException e) {
              e.printStackTrace();
            }
          }
        });
      }
    }, null, null);
    Console.println("------------------------ "+testName+" ------------------------");
    Console.println(psiFile.getText());
    Console.println("");
    return psiFile.getText();
  }

  public static Test suite() throws IOException {
    return new FormatterTest();
  }

}



