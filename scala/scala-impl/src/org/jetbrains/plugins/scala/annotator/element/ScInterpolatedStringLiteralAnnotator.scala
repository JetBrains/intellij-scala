package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.{AnnotationHolder, AnnotationSession}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.annotationHolder.DelegateAnnotationHolder
import org.jetbrains.plugins.scala.annotator.usageTracker.UsageTracker.registerUsedImports
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedStringPartReference

import scala.collection.mutable

object ScInterpolatedStringLiteralAnnotator extends ElementAnnotator[ScInterpolatedStringLiteral] {

  override def annotate(element: ScInterpolatedStringLiteral, typeAware: Boolean)
                       (implicit holder: AnnotationHolder): Unit = {
    if (element.getFirstChild == null) return

    val ref = element.findReferenceAt(0) match {
      case r: ScInterpolatedStringPartReference => r
      case _ => return
    }
    val prefix = element.getFirstChild
    val injections = element.getInjections

    def annotateBadPrefix(key: String) {
      val annotation = holder.createErrorAnnotation(prefix.getTextRange,
        ScalaBundle.message(key, prefix.getText))
      annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
    }

    def annotateDesugared(): Unit = {
      val elementsMap = mutable.HashMap[Int, PsiElement]()
      val params = new mutable.StringBuilder("(")

      injections.foreach { i =>
        elementsMap += params.length -> i
        params.append(i.getText).append(",")
      }
      if (injections.nonEmpty) params.setCharAt(params.length - 1, ')') else params.append(')')

      val (expr, ref, shift) = element.getStringContextExpression match {
        case Some(mc @ ScMethodCall(invoked: ScReferenceExpression, _)) =>
          val shift = invoked.getTextRange.getEndOffset
          (mc, invoked, shift)
        case _ => return
      }

      val sessionForExpr = new AnnotationSession(expr.getContainingFile)
      def mapPosInExprToElement(range: TextRange) = elementsMap.getOrElse(range.getStartOffset - shift, prefix)

      ScReferenceAnnotator.annotateReference(ref, inDesugaring = true) {
        new DelegateAnnotationHolder(sessionForExpr) {
          override protected def transformRange(range: TextRange): TextRange =
            mapPosInExprToElement(range).getTextRange
        }
      }
    }

    ref.bind() match {
      case Some(srr) =>
        registerUsedImports(ref, srr)
        annotateDesugared()
      case None =>
        annotateBadPrefix("cannot.resolve.in.StringContext")
    }

  }
}
