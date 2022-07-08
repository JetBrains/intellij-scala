package org.jetbrains.plugins.scala
package codeInsight
package hint

import com.intellij.codeInsight.hint.DeclarationRangeHandler
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}

final class ScTemplateDeclarationRangeHandler extends DeclarationRangeHandler[ScTemplateDefinition] {

  import ScTemplateDeclarationRangeHandler._

  override def getDeclarationRange(template: ScTemplateDefinition): TextRange =
    template match {
      case definition: ScTypeDefinition =>
        val startOffset = template.getModifierList.getTextRange match {
          case null => definition.getTextOffset
          case range => range.getStartOffset
        }

        new TextRange(startOffset, endOffset(definition))
      case definition: ScNewTemplateDefinition =>
        val startOffset = definition.getTextRange.getStartOffset

        new TextRange(startOffset, endOffset(definition))
    }
}

object ScTemplateDeclarationRangeHandler {

  private def endOffset(definition: ScTemplateDefinition) =
    definition.extendsBlock
      .templateBody
      .fold(definition.getTextRange.getEndOffset) {
        _.getTextRange.getStartOffset
      }
}
