package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithRecursionGuard, ModCount}

/**
* @author Alexander Podkhalyuzin
*/

trait ScTypeElement extends ScalaPsiElement with Typeable {
  protected val typeName: String

  override def toString: String = {
    val text = ifReadAllowed(getText)("")
    s"$typeName: $text"
  }

  def getType(ctx: TypingContext.type): TypeResult[ScType] = getType

  @CachedWithRecursionGuard(this, Failure("Recursive type of type element", Some(this)),
    ModCount.getBlockModificationCount)
  private[types] def getType: TypeResult[ScType] = innerType

  def getTypeNoConstructor: TypeResult[ScType] = getType

  def getNonValueType(withUnnecessaryImplicitsUpdate: Boolean = false): TypeResult[ScType] = innerType

  protected def innerType: TypeResult[ScType]

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
   * This in turn is used in the `treeWalkUp` in [[org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceElementImpl.processQualifier]]
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

object ScTypeElement {
  // java compatibility
  def calcType(typeElement: ScTypeElement): ScType = typeElement.calcType
}

trait ScDesugarizableTypeElement extends ScTypeElement {
  def desugarizedText: String

  def computeDesugarizedType = Option(typeElementFromText(desugarizedText))

  def typeElementFromText: String => ScTypeElement = createTypeElementFromText(_, getContext, this)

  override protected def innerType: TypeResult[ScType] = computeDesugarizedType match {
    case Some(typeElement) => typeElement.getType
    case _ => Failure(s"Cannot desugarize $typeName", Some(this))
  }
}
