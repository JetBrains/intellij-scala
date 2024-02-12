package org.jetbrains.plugins.scala.annotator.element

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.annotator.annotationHolder.DelegateAnnotationHolder
import org.jetbrains.plugins.scala.autoImport.quickFix.ImportImplicitConversionFixes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression, ScReferenceExpression}

import scala.annotation.nowarn

/** see also [[ScStringLiteralAnnotator]] */
object ScInterpolatedStringLiteralAnnotator extends ElementAnnotator[ScInterpolatedStringLiteral] {

  override def annotate(literal: ScInterpolatedStringLiteral, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    val partReference = literal.reference
    partReference.bind() match {
      case Some(_) =>
        for {
          (reference, call) <- literal.desugaredExpression

          offsetToRange = createSyntheticToRealRangeMap(literal.getInjections, call.argumentExpressions)
            .withDefaultValue(partReference.getTextRange)
        } annotateDesugared(
          reference,
          call,
          offsetToRange,
          new AnnotationSession(call.getContainingFile): @nowarn("cat=deprecation")
        )
      case _ =>
        holder.createErrorAnnotation(
          partReference.getTextRange,
          ScalaBundle.message("cannot.resolve.in.StringContext", partReference.refName),
          ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
          ImportImplicitConversionFixes(partReference)
        )
    }
  }

  private def annotateDesugared(reference: ScReferenceExpression,
                                call: MethodInvocation,
                                syntheticToReal: Map[TextRange, TextRange],
                                session: AnnotationSession)
                               (implicit holder: ScalaAnnotationHolder): Unit = {
    val delegateHolder = new DelegateAnnotationHolder(session) {

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
