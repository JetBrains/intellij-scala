package org.jetbrains.plugins.scala
package annotator

import java.awt.Color
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import lang.psi.api.statements.params.ScParameter
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import lang.formatting.settings.ScalaCodeStyleSettings
import com.intellij.openapi.util.TextRange
import lang.psi.api.expr._
import lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.extensions._
import lang.psi.types.nonvalue.Parameter
import lang.psi.ScalaPsiUtil

/**
 * Pavel Fatin
 */

object ByNameParameter extends AnnotatorPart[ScExpression] {
  private val Foreground = new Color(128, 128, 128)

  def kind = classOf[ScExpression]

  def annotate(exp: ScExpression, holder: AnnotationHolder, typeAware: Boolean) {
    val settings = CodeStyleSettingsManager.getSettings(exp.getProject)
            .getCustomSettings(classOf[ScalaCodeStyleSettings])

    if(!settings.SHOW_ARGUMENTS_TO_BY_NAME_PARAMETERS) return

    if(!settings.INCLUDE_BLOCK_EXPRESSIONS && exp.isInstanceOf[ScBlockExpr]) return

    val parameter = ScalaPsiUtil.parameterOf(exp)//.orElse(conversionParameterOf(exp))

    parameter.filter(_.isByName).foreach { p =>
      val attributes = new TextAttributes()
      attributes.setForegroundColor(Foreground)

      val ranges = if(settings.INCLUDE_LITERALS) Seq(exp.getTextRange) else nonLiteralRangesIn(exp)

      ranges.foreach { r =>
        val annotation = holder.createInfoAnnotation(r, null)
        annotation.setEnforcedTextAttributes(attributes)
      }
    }
  }

  private def nonLiteralRangesIn(exp: ScExpression): Seq[TextRange] = {
    val literalRanges = exp.depthFirst.filterByType(classOf[ScLiteral]).map(_.getTextRange).toList
    val literalIndices = literalRanges.flatMap(r => List(r.getStartOffset, r.getEndOffset))
    val allIndices = exp.getTextRange.getStartOffset :: literalIndices ::: exp.getTextRange.getEndOffset :: Nil
    allIndices.grouped(2).map(it => new TextRange(it(0), it(1))).filterNot(_.isEmpty).toList
  }
}