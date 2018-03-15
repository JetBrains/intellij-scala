package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithRecursionGuard, ModCount}

trait ResolvableReferenceExpression

object ResolvableReferenceExpression {

  implicit class Resolver(val reference: ScReferenceExpression) extends AnyVal {
    import reference.projectContext

    @CachedWithRecursionGuard(reference, ScalaResolveResult.EMPTY_ARRAY, ModCount.getBlockModificationCount)
    def multiResolveImpl(incomplete: Boolean): Array[ScalaResolveResult] =
      new ReferenceExpressionResolver().resolve(reference, shapesOnly = false, incomplete)

    @CachedWithRecursionGuard(reference, ScalaResolveResult.EMPTY_ARRAY, ModCount.getBlockModificationCount)
    def shapeResolveImpl: Array[ScalaResolveResult] =
      new ReferenceExpressionResolver().resolve(reference, shapesOnly = true, incomplete = false)
  }

  implicit class Ext(val ref: ScReferenceExpression) extends AnyVal {
    def isAssignmentOperator: Boolean = {
      val context = ref.getContext
      val refName = ref.refName
      (context.isInstanceOf[ScInfixExpr] || context.isInstanceOf[ScMethodCall]) &&
        refName.endsWith("=") &&
        !(refName.startsWith("=") || Seq("!=", "<=", ">=").contains(refName) || refName.exists(_.isLetterOrDigit))
    }

    def isUnaryOperator: Boolean = {
      ref.getContext match {
        case pref: ScPrefixExpr if pref.operation == ref => true
        case _ => false
      }
    }
  }
}