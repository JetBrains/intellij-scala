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

package org.jetbrains.plugins.scala.components;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.fileTemplates.impl.FileTemplateImpl;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeView;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;

/**
 * @author Ilya.Sergey
 */
public class ScalaFileCreator implements ApplicationComponent {


  public static final String SCALA_FILE_TEMPLATE = ScalaBundle.message("new.scala.template");

  @NonNls
  @NotNull
  public String getComponentName() {
    return ScalaComponents.SCALA_FILE_CREATOR;
  }


  private void registerScalaScriptTemplate() {
    String templateText = ScalaBundle.message("template.file.text");
    if (FileTemplateManager.getInstance().getTemplate(SCALA_FILE_TEMPLATE) == null) {
      FileTemplate scalaTemplate = FileTemplateManager.getInstance().addTemplate(SCALA_FILE_TEMPLATE,
          ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension());
      ((FileTemplateImpl) scalaTemplate).setInternal(true);
      scalaTemplate.setText(templateText);
    }
  }


  public void initComponent() {
    removeComponent();
    FileTemplateManager fileTemplateManager = FileTemplateManager.getInstance();
    FileTemplate scalaTemplate = fileTemplateManager.getTemplate(SCALA_FILE_TEMPLATE);
    if (scalaTemplate == null) {
      registerScalaScriptTemplate();
    }
  }

  public void disposeComponent() {
    removeComponent();
  }


  private void removeComponent() {
    FileTemplate scalaTemplate = FileTemplateManager.getInstance().getTemplate(SCALA_FILE_TEMPLATE);
    if (scalaTemplate != null)
      FileTemplateManager.getInstance().removeTemplate(scalaTemplate, false);
  }

/*
  private String getPackage() {
    IdeView view = (IdeView)DataManager.getInstance().getDataContext().getData(DataConstantsEx.IDE_VIEW);
    String name = view.getOrChooseDirectory().getName();
    return name == null ? "bugaga" : name;
  }
*/


}
