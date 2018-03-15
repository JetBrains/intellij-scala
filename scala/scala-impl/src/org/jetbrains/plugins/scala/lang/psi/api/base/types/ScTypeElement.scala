package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, ifReadAllowed}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithRecursionGuard, ModCount}

/**
* @author Alexander Podkhalyuzin
*/

trait ScTypeElement extends ScalaPsiElement with Typeable with TreeMember[ScTypeElement] {
  protected val typeName: String

  override def isSameTree(p: PsiElement): Boolean = p.isInstanceOf[ScTypeElement]

  override def toString: String = {
    val text = ifReadAllowed(getText)("")
    s"$typeName: $text"
  }

  def `type`(): TypeResult = getType

  @CachedWithRecursionGuard(this, Failure("Recursive type of type element"),
    ModCount.getBlockModificationCount)
  private[types] def getType: TypeResult = innerType

  def getTypeNoConstructor: TypeResult = getType

  def getNonValueType(withUnnecessaryImplicitsUpdate: Boolean = false): TypeResult = innerType

  protected def innerType: TypeResult

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
  private def refreshAnalog(): Unit =
    this.parentOfType(classOf[ScTypeParam], strict = false)
      .flatMap(_.parentOfType(classOf[ScMethodLike], strict = false))
      .foreach(_.effectiveParameterClauses)

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

  override protected def innerType: TypeResult = computeDesugarizedType match {
    case Some(typeElement) => typeElement.getType
    case _ => Failure(s"Cannot desugarize $typeName")
  }
}
