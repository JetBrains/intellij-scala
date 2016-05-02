package org.jetbrains.plugins.scala.lang.transformation
package implicits

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.{&&, BooleanExt, PsiClassExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScMethodCall, ScPostfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
  * @author Pavel Fatin
  */
object InscribeImplicitParameters extends AbstractTransformer {
  def transformation = {
    case (_: ScReferenceExpression | _: ScMethodCall | _: ScInfixExpr | _: ScPostfixExpr) &&
      (e @ ImplicitParametersOwner(Seq(ps @ _*))) if ps.exists(isApplicable) =>

      val arguments = ps.filter(isApplicable)

      val placeholders = arguments.indices.map("arg$" + _).mkString(", ")

      val result = {
        val enclose = e.isInstanceOf[ScInfixExpr] || e.isInstanceOf[ScPostfixExpr]
        e.replace(enclose.fold(code"($e)($placeholders)", code"$e($placeholders)"))
      }

      val references = result.depthFirst.collect {
        case it: ScReferenceExpression if it.text.startsWith("arg$") => it
      }

      arguments.zip(references.toVector).foreach {
        case (argument, reference) => bindTo(reference, targetFor(argument))
      }
  }

  private def isApplicable(result: ScalaResolveResult): Boolean = result.element match {
    case c: PsiClass if c.qualifiedName.startsWith("scala.reflect.") => false
    case _ => true
  }
}