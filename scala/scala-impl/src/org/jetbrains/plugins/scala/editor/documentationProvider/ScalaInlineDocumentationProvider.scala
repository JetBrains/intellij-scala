package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.codeInsight.documentation.render.PsiCommentInlineDocumentation
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.{InlineDocumentation, InlineDocumentationProvider}
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

import java.util

//noinspection UnstableApiUsage
final class ScalaInlineDocumentationProvider extends InlineDocumentationProvider {
  // The platform's compatibility provider collects comments through ScalaDocumentationProvider.collectDocComments.
  override def inlineDocumentationItems(file: PsiFile): util.Collection[InlineDocumentation] =
    util.Collections.emptyList()

  override def findInlineDocumentation(file: PsiFile, textRange: TextRange): InlineDocumentation = file match {
    case _: ScalaFile =>
      val comment = PsiTreeUtil.getParentOfType(file.findElementAt(textRange.getStartOffset), classOf[ScDocComment], false)
      if (comment != null && comment.getTextRange == textRange)
        new PsiCommentInlineDocumentation(comment)
      else
        null
    case _ => null
  }
}
