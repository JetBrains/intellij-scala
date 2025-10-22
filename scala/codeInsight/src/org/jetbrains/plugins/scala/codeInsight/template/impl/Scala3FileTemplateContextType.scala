package org.jetbrains.plugins.scala.codeInsight.template.impl

import com.intellij.codeInsight.template.{FileTypeBasedContextType, TemplateActionContext, TemplateContextType}
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighterFactory
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.{Scala3Language, ScalaFileType, ScalaLanguage}

final class Scala3FileTemplateContextType extends FileTypeBasedContextType(
  Scala3Language.INSTANCE.getDisplayName,
  ScalaFileType.INSTANCE
)  {

  override def createHighlighter(): SyntaxHighlighter =
    ScalaSyntaxHighlighterFactory.createScalaSyntaxHighlighter(null, null, Scala3Language.INSTANCE)
}