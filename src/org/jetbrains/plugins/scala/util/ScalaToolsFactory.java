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

import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.surroundWith.SurroundDescriptors;

/**
 * @author ilyas
 */
public abstract class ScalaToolsFactory implements ApplicationComponent {

  public static ScalaToolsFactory getInstance() {
    return ApplicationManager.getApplication().getComponent(ScalaToolsFactory.class);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "scala.ScalaToolsFactory";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public abstract ParserDefinition createScalaParserDefinition();

  public abstract FoldingBuilder createScalaFoldingBuilder();

  public abstract SurroundDescriptors createSurroundDescriptors();


  public abstract FormattingModelBuilder createScalaFormattingModelBuilder();

  public abstract StructureViewBuilder createStructureViewBuilder(PsiFile psiFile);

  public abstract FindUsagesProvider createFindUsagesProvider();

}