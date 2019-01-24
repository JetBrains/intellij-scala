package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.{AnnotationHolder, AnnotationSession}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ApplicationAnnotator
import org.jetbrains.plugins.scala.annotator.annotationHolder.DelegateAnnotationHolder
import org.jetbrains.plugins.scala.annotator.usageTracker.UsageTracker.registerUsedImports
import org.jetbrains.plugins.scala.lang.psi.api.Annotatable
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedStringPartReference

import scala.collection.mutable


trait ScInterpolatedStringLiteralAnnotator extends Annotatable { self: ScInterpolatedStringLiteral =>

  abstract override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    if (getFirstChild == null) return

    val ref = findReferenceAt(0) match {
      case r: ScInterpolatedStringPartReference => r
      case _ => return
    }
    val prefix = getFirstChild
    val injections = getInjections

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
      if (injections.length > 0) params.setCharAt(params.length - 1, ')') else params.append(')')

      val (expr, ref, shift) = getStringContextExpression match {
        case Some(mc @ ScMethodCall(invoked: ScReferenceExpression, _)) =>
          val shift = invoked.getTextRange.getEndOffset
          (mc, invoked, shift)
        case _ => return
      }

      val sessionForExpr = new AnnotationSession(expr.getContainingFile)
      def mapPosInExprToElement(range: TextRange) = elementsMap.getOrElse(range.getStartOffset - shift, prefix)
      val fakeAnnotator = new DelegateAnnotationHolder(mapPosInExprToElement(_).getTextRange, holder, sessionForExpr)

      ApplicationAnnotator.annotateReference(ref, fakeAnnotator)
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
