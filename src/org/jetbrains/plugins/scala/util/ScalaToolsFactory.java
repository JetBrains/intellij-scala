package org.jetbrains.plugins.scala.util;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.codeInsight.completion.CompletionData;
import com.intellij.ide.structureView.StructureViewBuilder;
import org.jetbrains.plugins.scala.lang.surroundWith.SurroundDescriptors;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Author: Ilya Sergey
 * Date: 09.10.2006
 * Time: 21:23:48
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

  public abstract PsiFile createJavaView(FileViewProvider viewProvider);

  public abstract CompletionData createScalaCompletionData();

//  public abstract StructureViewBuilder createStructureViewBuilder(PsiFile psiFile);
}