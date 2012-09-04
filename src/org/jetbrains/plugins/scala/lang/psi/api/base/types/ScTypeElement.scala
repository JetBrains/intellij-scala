package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.types._
import com.intellij.openapi.progress.ProgressManager
import caches.CachesUtil
import result.{Failure, TypeResult, TypingContext, TypingContextOwner}
import com.intellij.psi.util.{PsiTreeUtil, PsiModificationTracker}
import statements.params.ScTypeParam

/**
* @author Alexander Podkhalyuzin
*/

trait ScTypeElement extends ScalaPsiElement with TypingContextOwner {
  def getType(ctx: TypingContext): TypeResult[ScType] = {
    CachesUtil.getWithRecursionPreventingWithRollback(this, CachesUtil.TYPE_ELEMENT_TYPE_KEY,
      new CachesUtil.MyProvider[ScTypeElement, TypeResult[ScType]](
        this, elem => elem.innerType(ctx)
      )(PsiModificationTracker.MODIFICATION_COUNT), Failure("Recursive type of type element", Some(this)))
  }

  def getNonValueType(ctx: TypingContext): TypeResult[ScType] = innerType(ctx)

  protected def innerType(ctx: TypingContext): TypeResult[ScType]

  def calcType: ScType = getType(TypingContext.empty).getOrAny

  /** Link from a view or context bound to the type element of the corresponding synthetic parameter. */
  def analog: Option[ScTypeElement] = {
    refreshAnalog()
    _analog
  }

  def analog_=(te: ScTypeElement) {
    _analog = Some(te)
  }

  /**
   * If the reference is in a type parameter, first compute the effective parameters clauses
   * of the containing method or constructor.
   *
   * As a side-effect, this will register the analogs for each type element in a context or view
   * bound position. See: [[org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.syntheticParamClause]]
   *
   * This in turn is used in the `treeWalkUp` in [[org.jetbrains.plugins.scala.lang.resolve.ResolvableStableCodeReferenceElement.processQualifier]]
   */
  private def refreshAnalog() {
    ScalaPsiUtil.getParentOfType(this, classOf[ScTypeParam]) match {
      case tp: ScTypeParam =>
        ScalaPsiUtil.getParentOfType(tp, classOf[ScMethodLike]) match {
          case ml: ScMethodLike =>
            ml.effectiveParameterClauses
          case _ =>
        }
      case _ =>
    }
  }

  @volatile
  private[this] var _analog: Option[ScTypeElement] = None
}
