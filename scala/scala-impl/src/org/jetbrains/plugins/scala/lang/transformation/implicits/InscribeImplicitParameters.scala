package org.jetbrains.plugins.scala.lang.transformation
package implicits

import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions.{&&, BooleanExt, ObjectExt, PsiClassExt}
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
class InscribeImplicitParameters extends AbstractTransformer {
  override protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case (_: ScReferenceExpression | _: ScMethodCall | _: ScInfixExpr | _: ScPostfixExpr) &&
      (e @ ImplicitArgumentsOwner(ps)) if ps.exists(isApplicable) =>

      val targets = ps.filter(isApplicable).map(targetFor)

      val result = {
        val enclose = e.is[ScInfixExpr, ScPostfixExpr]
        val arguments = targets.map(simpleNameOf).mkString(", ")
        e.replace(enclose.fold(code"($e)($arguments)", code"$e($arguments)"))
      }

      val references = result.getLastChild.asInstanceOf[ScArgumentExprList].exprs

      references.zip(targets).foreach {
        case (reference, target) => bindTo(reference.asInstanceOf[ScReference], target)
      }
  }

  private def isApplicable(result: ScalaResolveResult): Boolean = result.element match {
    case c: PsiClass if c.qualifiedName.startsWith("scala.reflect.") => false
    case _ => true
  }
}