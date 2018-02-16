package org.jetbrains.plugins.scala
package annotator

import java.awt.Color

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.settings._

/**
 * Pavel Fatin
 */

object ByNameParameter extends AnnotatorPart[ScExpression] {
  private val Foreground = new Color(128, 128, 128)

  def annotate(exp: ScExpression, holder: AnnotationHolder, typeAware: Boolean) {
    if(!ScalaProjectSettings.getInstance(exp.getProject).isShowArgumentsToByNameParams) return

    if(!ScalaProjectSettings.getInstance(exp.getProject).isIncludeBlockExpressions && exp.isInstanceOf[ScBlockExpr]) return

    val parameter = ScalaPsiUtil.parameterOf(exp)//.orElse(conversionParameterOf(exp))

    parameter.filter(_.isByName).foreach { _ =>
      val attributes = new TextAttributes()
      attributes.setForegroundColor(Foreground)

      val ranges =
        if(ScalaProjectSettings.getInstance(exp.getProject).isIncludeLiterals) Seq(exp.getTextRange)
        else nonLiteralRangesIn(exp)

      ranges.foreach { r =>
        val annotation = holder.createInfoAnnotation(r, "Passed as by-name parameter")
        annotation.setEnforcedTextAttributes(attributes)
      }
    }
  }

  private def nonLiteralRangesIn(exp: ScExpression): Seq[TextRange] = {
    val literalRanges = exp.depthFirst(parent => !parent.isInstanceOf[ScLiteral])
            .filterByType[ScLiteral].map(_.getTextRange).toList
    val literalIndices = literalRanges.flatMap(r => List(r.getStartOffset, r.getEndOffset))
    val allIndices = exp.getTextRange.getStartOffset :: literalIndices ::: exp.getTextRange.getEndOffset :: Nil
    allIndices.grouped(2).map(it => new TextRange(it.head, it(1))).filterNot(_.isEmpty).toList
  }
}
