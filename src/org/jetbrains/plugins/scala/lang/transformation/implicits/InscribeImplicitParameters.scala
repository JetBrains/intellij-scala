package org.jetbrains.plugins.scala.lang.transformation
package implicits

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.{&&, BooleanExt, PsiClassExt}
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
  * @author Pavel Fatin
  */
object InscribeImplicitParameters extends AbstractTransformer {
  def transformation = {
    case (_: ScReferenceExpression | _: ScMethodCall | _: ScInfixExpr | _: ScPostfixExpr) &&
      (e @ ImplicitParametersOwner(Seq(ps @ _*))) if ps.exists(isApplicable) =>

      val targets = ps.filter(isApplicable).map(targetFor)

      val result = {
        val enclose = e.isInstanceOf[ScInfixExpr] || e.isInstanceOf[ScPostfixExpr]
        val arguments = targets.map(simpleNameOf).mkString(", ")
        e.replace(enclose.fold(code"($e)($arguments)", code"$e($arguments)"))
      }

      val references = result.getLastChild.asInstanceOf[ScArgumentExprList].exprs

      references.zip(targets).foreach {
        case (reference, target) => bindTo(reference.asInstanceOf[ScReferenceElement], target)
      }
  }

  private def isApplicable(result: ScalaResolveResult): Boolean = result.element match {
    case c: PsiClass if c.qualifiedName.startsWith("scala.reflect.") => false
    case _ => true
  }
}