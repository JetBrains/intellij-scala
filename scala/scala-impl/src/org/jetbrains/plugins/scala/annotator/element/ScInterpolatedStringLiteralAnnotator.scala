package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.{AnnotationHolder, AnnotationSession}
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScInterpolated}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedStringPartReference

object ScInterpolatedStringLiteralAnnotator extends ElementAnnotator[ScInterpolatedStringLiteral] {

  override def annotate(literal: ScInterpolatedStringLiteral, typeAware: Boolean)
                       (implicit holder: AnnotationHolder): Unit = literal.reference match {
    case Some(partReference: ScInterpolatedStringPartReference) =>
      partReference match {
        case Resolved(resolveResult) =>
          usageTracker.UsageTracker
            .registerUsedImports(partReference, resolveResult)

          literal.getStringContextExpression match {
            case Some(call@ScMethodCall(reference: ScReferenceExpression, _)) =>
              val offsetToRange = createOffsetToRangeMap(literal.getInjections.iterator)
                .withDefaultValue(partReference.getTextRange)

              annotateDesugared(
                reference,
                offsetToRange,
                new AnnotationSession(call.getContainingFile)
              )
            case _ =>
          }
        case _ =>
          holder.createErrorAnnotation(
            partReference.getTextRange,
            ScalaBundle.message("cannot.resolve.in.StringContext", partReference.refName)
          ).setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
      }
    case _ =>
  }

  private def annotateDesugared(reference: ScReferenceExpression,
                                offsetToRange: Map[Int, TextRange],
                                session: AnnotationSession)
                               (implicit holder: AnnotationHolder): Unit =
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
