package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.autoImport.quickFix.ImportImplicitConversionFixes
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedExpressionPrefix

/** see also [[ScStringLiteralAnnotator]] */
object ScInterpolatedStringLiteralAnnotator extends ElementAnnotator[ScInterpolatedStringLiteral] {

  override def annotate(literal: ScInterpolatedStringLiteral, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = literal.reference match {
    case Some(partReference: ScInterpolatedExpressionPrefix) =>
      partReference match {
        case Resolved(_) =>

          for {
            (reference, call) <- literal.desugaredExpression

            offsetToRange = createSyntheticToRealRangeMap(literal.getInjections, call.argumentExpressions)
              .withDefaultValue(partReference.getTextRange)
          } annotateDesugared(
            reference,
            call,
            offsetToRange,
            new AnnotationSession(call.getContainingFile)
          )
        case _ =>
          holder.createErrorAnnotation(
            partReference.getTextRange,
            ScalaBundle.message("cannot.resolve.in.StringContext", partReference.refName),
            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
            ImportImplicitConversionFixes(partReference)
          )
      }
    case _ =>
  }

  private def annotateDesugared(reference: ScReferenceExpression,
                                call: MethodInvocation,
                                syntheticToReal: Map[TextRange, TextRange],
                                session: AnnotationSession)
                               (implicit holder: ScalaAnnotationHolder): Unit = {
    val delegateHolder = new annotationHolder.DelegateAnnotationHolder(session) {

      override protected def transformRange(range: TextRange): TextRange =
        syntheticToReal(range)
    }
    ScReferenceAnnotator.annotateReference(reference)(delegateHolder)
    ScMethodInvocationAnnotator.annotateMethodInvocation(call, inDesugaring = true)(delegateHolder)
  }

  private[this] def createSyntheticToRealRangeMap(injections: Seq[ScExpression],
                                                  syntheticArgs: Seq[ScExpression]): Map[TextRange, TextRange] =
    syntheticArgs.map(_.getTextRange).zip(injections.map(_.getTextRange)).toMap
}
