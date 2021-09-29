package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.autoImport.quickFix.ImportImplicitConversionFixes
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolated, ScInterpolatedStringLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedExpressionPrefix

/** see also [[ScStringLiteralAnnotator]] */
object ScInterpolatedStringLiteralAnnotator extends ElementAnnotator[ScInterpolatedStringLiteral] {

  override def annotate(literal: ScInterpolatedStringLiteral, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = literal.reference match {
    case Some(partReference: ScInterpolatedExpressionPrefix) =>
      partReference match {
        case Resolved(resolveResult) =>

          for {
            (reference, call) <- literal.desugaredExpression

            offsetToRange = createOffsetToRangeMap(literal.getInjections.iterator)
              .withDefaultValue(partReference.getTextRange)
          } annotateDesugared(
            reference,
            offsetToRange,
            new AnnotationSession(call.getContainingFile)
          )
        case _ =>
          val annotation = holder.createErrorAnnotation(
            partReference.getTextRange,
            ScalaBundle.message("cannot.resolve.in.StringContext", partReference.refName)
          )
          annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
          ImportImplicitConversionFixes(partReference).foreach(annotation.registerFix)
      }
    case _ =>
  }

  private def annotateDesugared(reference: ScReferenceExpression,
                                offsetToRange: Map[Int, TextRange],
                                session: AnnotationSession)
                               (implicit holder: ScalaAnnotationHolder): Unit =
    ScReferenceAnnotator.annotateReference(reference, inDesugaring = true) {

      new annotationHolder.DelegateAnnotationHolder(session) {

        private val shift = reference.getTextRange.getEndOffset

        override protected def transformRange(range: TextRange): TextRange =
          offsetToRange(range.getStartOffset - shift)
      }
    }

  /**
   * @see [[ScInterpolated.getStringContextExpression]] implementation dependent
   */
  @annotation.tailrec
  private[this] def createOffsetToRangeMap(iterator: Iterator[ScExpression],
                                           length: Int = 1, // "("
                                           result: Map[Int, TextRange] = Map.empty): Map[Int, TextRange] =
    if (iterator.hasNext) {
      val injection = iterator.next()
      createOffsetToRangeMap(
        iterator,
        length + injection.getTextLength + 2, // ", "
        result + (length -> injection.getTextRange)
      )
    } else {
      result
    }
}
