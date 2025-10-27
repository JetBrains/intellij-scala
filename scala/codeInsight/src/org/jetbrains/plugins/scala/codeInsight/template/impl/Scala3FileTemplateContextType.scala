package org.jetbrains.plugins.scala.codeInsight.template.impl

import com.intellij.codeInsight.template.{FileTypeBasedContextType, TemplateActionContext}
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighterFactory
import org.jetbrains.plugins.scala.{Scala3Language, ScalaFileType}

final class Scala3FileTemplateContextType extends FileTypeBasedContextType(
  Scala3Language.INSTANCE.getDisplayName,
  ScalaFileType.INSTANCE
)  {

  override def createHighlighter(): SyntaxHighlighter =
    ScalaSyntaxHighlighterFactory.createScalaSyntaxHighlighter(null, null, Scala3Language.INSTANCE)

  override def isInContext(templateActionContext: TemplateActionContext): Boolean =
    templateActionContext.getFile.getLanguage == Scala3Language.INSTANCE
}