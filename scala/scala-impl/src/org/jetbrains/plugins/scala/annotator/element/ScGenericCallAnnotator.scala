package org.jetbrains.plugins.scala.annotator.element

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScGenericCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.DefaultTypeParameterMismatch
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.extensions.ObjectExt

object ScGenericCallAnnotator extends ElementAnnotator[ScGenericCall] {
  override def annotate(genCall: ScGenericCall, typeAware: Boolean)(implicit holder: ScalaAnnotationHolder): Unit = {
    if (typeAware) {
      for {
        ref <- genCall.referencedExpr.asOptionOf[ScReferenceExpression]
        rr  <- ref.bind()
        f = rr.element
        if f.is[ScFunction, PsiMethod, ScSyntheticFunction]
      } {
        rr.problems.foreach {
          case DefaultTypeParameterMismatch(expected, actual) =>
            holder.createErrorAnnotation(
              genCall.typeArgs,
              ScalaBundle.message("type.mismatch.default.args.expected.actual", expected, actual))
          case _ =>
        }
      }
    }
  }
}
