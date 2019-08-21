package org.jetbrains.plugins.scala.caches

import java.util.concurrent.atomic.AtomicLong

import com.intellij.openapi.util.{Key, ModificationTracker}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.caches.BlockModificationTracker._
import org.jetbrains.plugins.scala.caches.CachesUtil.scalaTopLevelModTracker
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody

import scala.annotation.tailrec

// TODO Rename to "ExpressionModificationTracker" - it's type annotation, not "block" that makes the difference.
class BlockModificationTracker private (element: PsiElement) extends ModificationTracker {

  private val topLevel = scalaTopLevelModTracker(element.getProject)

  override def getModificationCount: Long = {
    topLevel.getModificationCount + sumOfLocalCountsInContext(element)
  }

  @tailrec
  private def sumOfLocalCountsInContext(element: PsiElement, acc: Long = 0L): Long =
    contextWithStableType(element) match {
      case Some(expr) => sumOfLocalCountsInContext(expr.getContext, acc + LocalCount(expr))
      case None       => acc
    }
}

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

  /**
   * LocalCount is defined for expressions with stable type and incremented on every psi change inside.
   */
  object LocalCount {
    private val key: Key[AtomicLong] = Key.create("local.modification.counter")

    private def getOrCreate(expression: ScExpression): AtomicLong = {
      assert(hasStableType(expression))

      expression.getOrUpdateUserData(key, new AtomicLong(0L))
    }

    def apply(expression: ScExpression): Long = getOrCreate(expression).get

    def increment(expression: ScExpression): Unit = getOrCreate(expression).incrementAndGet()

    def redirect(mirrorElement: ScExpression, originalElement: ScExpression): Unit = {
      val originalCounter = getOrCreate(originalElement)

      assert(hasStableType(mirrorElement))

      mirrorElement.putUserData(key, originalCounter)
    }
  }

  def apply(element: PsiElement): ModificationTracker =
    if (!element.isValid) ModificationTracker.NEVER_CHANGED
    else new BlockModificationTracker(element)

  @tailrec
  def contextWithStableType(element: PsiElement): Option[ScExpression] =
    element match {
      case null | _: ScalaFile => None
      case owner: ScExpression if hasStableType(owner) => Some(owner)
      case owner => contextWithStableType(owner.getContext)
    }

  private def hasUnstableType(expr: ScExpression): Boolean = expr.getContext match {
    case f: ScFunction => f.returnTypeElement.isEmpty && f.hasAssign
    case v: ScValueOrVariable => v.typeElement.isEmpty
    case _: ScTypedExpression |
         _: ScWhile |
         _: ScFinallyBlock |
         _: ScTemplateBody |
         _: ScDo => false
    //expression is not last in a block and not assigned to anything, cannot affect type inference outside
    case _: ScBlock =>
      expr.nextSiblings.forall {
        case _: ScExpression => false
        case _ => true
      }
    case _ => true
  }
}