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
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.ScalaLanguage;
import org.jetbrains.plugins.scala.ScalaLoader;

public abstract class ScalaFileSetTestCase extends FileSetTestCase {
  @NonNls
  protected final static String TEMP_FILE = "temp.scala";


  public ScalaFileSetTestCase(String path) {
    super(path);
  }

  protected CommonCodeStyleSettings getSettings() {
      return CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(ScalaLanguage.INSTANCE);
  }

  protected void setSettings() {
      getSettings().getIndentOptions().INDENT_SIZE = 2;
      getSettings().getIndentOptions().CONTINUATION_INDENT_SIZE = 2;
      getSettings().getIndentOptions().TAB_SIZE = 2;
  }

  protected void setUp(Project project) {
    super.setUp(project);
    ScalaLoader.loadScala();
    setSettings();
  }

  protected void tearDown(Project project) {
    super.tearDown(project);
  }
}
