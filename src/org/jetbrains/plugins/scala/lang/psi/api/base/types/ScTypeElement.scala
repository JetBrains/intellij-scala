package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult, TypingContext, TypingContextOwner}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithRecursionGuard, ModCount}

/**
* @author Alexander Podkhalyuzin
*/

trait ScTypeElement extends ScalaPsiElement with TypingContextOwner {
  protected val typeName: String

  override def toString: String = s"$typeName: $getText"

  @CachedWithRecursionGuard[ScTypeElement](this, Failure("Recursive type of type element", Some(this)),
    ModCount.getBlockModificationCount)
  def getType(ctx: TypingContext): TypeResult[ScType] = innerType(ctx)

  def getTypeNoConstructor(ctx: TypingContext): TypeResult[ScType] = getType(ctx)

  def getNonValueType(ctx: TypingContext, withUnnecessaryImplicitsUpdate: Boolean = false): TypeResult[ScType] = innerType(ctx)

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
