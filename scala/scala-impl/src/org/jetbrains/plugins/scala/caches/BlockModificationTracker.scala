package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.util.{Key, ModificationTracker, SimpleModificationTracker}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.caches.CachesUtil.scalaTopLevelModTracker
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody

import scala.annotation.tailrec

object BlockModificationTracker {

  /**
   * Expression have a stable type if changes inside it doesn't affect type inference of anything outside it.
   * Examples are:
   *   - body of a function with explicit return type
   *   - bodies of 'do' or 'while' loops
   *   - expressions in template body (they are effectively unit)
   *   - expressions in block not on the return position
   * */
  def hasStableType(expr: ScExpression): Boolean = !hasUnstableType(expr)

  private val key: Key[ExpressionModificationTracker] = Key.create("local.modification.counter")

  private object ExpressionModificationTracker {
    def apply(expression: ScExpression): ExpressionModificationTracker = {
      assert(hasStableType(expression))

      expression.getOrUpdateUserData(key, new ExpressionModificationTracker(expression))
    }
  }

  /**
   * ExpressionModificationTracker is defined for expressions with stable type.
   * It has a local counter and a reference to the context modification tracker,
   * both are updated on every change inside the expression.
   *
   * This way we have very fast `getModificationCount` and not-so-fast, but much more rare increment.
   */
  private class ExpressionModificationTracker(expression: ScExpression) extends SimpleModificationTracker {

    private var contextTracker: ModificationTracker = BlockModificationTracker(expression.getContext)

    def incrementLocalAndUpdateParent(): Unit = {
      super.incModificationCount()
      contextTracker = BlockModificationTracker(expression.getContext)
    }

    private def localModificationCount: Long = super.getModificationCount

    override def getModificationCount: Long = contextTracker.getModificationCount + localModificationCount
  }

  def incrementLocalCounter(expression: ScExpression): Unit =
    ExpressionModificationTracker(expression).incrementLocalAndUpdateParent()

  def apply(element: PsiElement): ModificationTracker =
    if (!element.isValid)
      ModificationTracker.NEVER_CHANGED
    else
      contextWithStableType(element) match {
        case Some(expr) => ExpressionModificationTracker(expr)
        case None       => scalaTopLevelModTracker(element.getProject)
      }

  def contextWithStableType(element: PsiElement): Option[ScExpression] =
    withStableType(element, _.getContext)

  def parentWithStableType(element: PsiElement): Option[ScExpression] =
    withStableType(element, _.getParent)

  @tailrec
  private def withStableType(element: PsiElement, nextElement: PsiElement => PsiElement): Option[ScExpression] =
    element match {
      case null | _: ScalaFile => None
      case owner: ScExpression if hasStableType(owner) => Some(owner)
      case owner => withStableType(nextElement(owner), nextElement)
    }


  private def hasUnstableType(expr: ScExpression): Boolean = expr.getContext match {
    case f: ScFunction => f.returnTypeElement.isEmpty && f.hasAssign
    case v: ScValueOrVariable => v.typeElement.isEmpty
    case _: ScTypedExpression |
         _: ScThrow |
         _: ScReturn |
         _: ScWhile |
         _: ScFinallyBlock |
         _: ScTemplateBody |
         _: ScDo => false
// TODO enable (SmartIfCondition test needs to be fixed)
//    case `if`: ScIf if `if`.condition.contains(expr) => false
    case guard: ScGuard if guard.expr.contains(expr) => false
    //expression is not last in a block and not assigned to anything, cannot affect type inference outside
    case block: ScBlock => block.resultExpression.contains(expr)
    case _ => true
  }
}