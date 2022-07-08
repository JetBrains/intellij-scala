package org.jetbrains.plugins.scala
package annotator

import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.settings._

import java.awt.Color

object ByNameParameter extends AnnotatorPart[ScExpression] {

  private val Foreground = new Color(128, 128, 128)

  override def annotate(exp: ScExpression, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
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
        holder.createInfoAnnotation(r, ScalaBundle.message("passed.as.by.name.parameter"), Some(attributes), fixes = Nil)
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
