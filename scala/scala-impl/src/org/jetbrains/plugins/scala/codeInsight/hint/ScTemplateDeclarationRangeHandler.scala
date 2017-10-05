package org.jetbrains.plugins.scala.codeInsight.hint

import com.intellij.codeInsight.hint.DeclarationRangeHandler
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}

/**
  * @author Alefas
  * @since 30/06/16
  */
class ScTemplateDeclarationRangeHandler extends DeclarationRangeHandler[ScTemplateDefinition] {
  override def getDeclarationRange(template: ScTemplateDefinition): TextRange = {
    template match {
      case td: ScTypeDefinition =>
        val textRange: TextRange = template.getModifierList.getTextRange
        val startOffset: Int =
          if (textRange != null) textRange.getStartOffset
          else td.getTextOffset
        val endOffset = td.extendsBlock.templateBody match {
          case Some(body) => body.getTextRange.getStartOffset
          case None => td.getTextRange.getEndOffset
        }
        new TextRange(startOffset, endOffset)
      case td: ScNewTemplateDefinition =>
        val startOffset = td.getTextRange.getStartOffset
        val endOffset = td.extendsBlock.templateBody match {
          case Some(body) => body.getTextRange.getStartOffset
          case None => td.getTextRange.getEndOffset
        }
        new TextRange(startOffset, endOffset)
      case _ => template.getTextRange
    }
  }
}
