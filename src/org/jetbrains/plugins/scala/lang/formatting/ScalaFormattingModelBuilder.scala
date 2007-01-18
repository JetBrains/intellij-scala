package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.FormattingModelProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;

sealed class ScalaFormattingModelBuilder extends FormattingModelBuilder {

  def createModel(element: PsiElement, settings: CodeStyleSettings) = {
    FormattingModelProvider.createFormattingModelForPsiFile(element.getContainingFile(),
            new ScalaBlock(null, element.getNode(), null, null, null, settings), settings);
  }

}