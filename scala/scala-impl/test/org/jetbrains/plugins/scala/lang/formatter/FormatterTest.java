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

package org.jetbrains.plugins.scala.lang.formatter;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

import java.util.Collections;

/**
 * Created by IntelliJ IDEA.
 * User: Ilya.Sergey
 */

@RunWith(AllTests.class)
public class FormatterTest extends ScalaFileSetTestCase {

  protected FormatterTest(@NotNull @NonNls String path) {
    super(path);
  }

  public static Test suite() {
    return new FormatterTest("/formatter/data/");
  }

  @Override
  protected void setSettings(@NotNull Project project) {
    super.setSettings(project);
    getScalaSettings(project).USE_SCALADOC2_FORMATTING = true;
  }

  @NotNull
  @Override
  protected String transform(@NotNull String testName,
                             @NotNull String fileText,
                             @NotNull Project project) {
    final PsiFile psiFile = createLightFile(fileText, project);
    Runnable runnable = () -> ApplicationManager.getApplication().runWriteAction(() -> {
          try {
            CodeStyleManager.getInstance(project)
                    .reformatText(psiFile, Collections.singletonList(psiFile.getTextRange()));
          } catch (IncorrectOperationException e) {
            e.printStackTrace();
          }
        }
    );
    CommandProcessor.getInstance().executeCommand(project, runnable, null, null);
    return psiFile.getText();
  }
}



