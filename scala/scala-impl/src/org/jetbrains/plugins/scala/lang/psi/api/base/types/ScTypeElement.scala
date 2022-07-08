package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, ifReadAllowed}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.macroAnnotations.CachedWithRecursionGuard

trait ScTypeElement extends ScalaPsiElement with Typeable {
  protected val typeName: String

  override def toString: String = {
    val text = ifReadAllowed(getText)("")
    s"$typeName: $text"
  }

  override def `type`(): TypeResult = getType

  def singleton: Boolean = false

  @CachedWithRecursionGuard(this, Failure(ScalaBundle.message("recursive.type.of.type.element")),
    BlockModificationTracker(this))
  private[types] def getType: TypeResult = innerType

  def getTypeNoConstructor: TypeResult = getType

  def getNonValueType(withUnnecessaryImplicitsUpdate: Boolean = false): TypeResult = innerType

  protected def innerType: TypeResult

  /** Link from a view or context bound to the type element of the corresponding synthetic parameter. */
  def analog: Option[ScTypeElement] = {
    refreshAnalog()
    _analog
  }

  def analog_=(te: ScTypeElement): Unit = {
    _analog = Some(te)
  }

  /**
   * If the reference is in a type parameter, first compute the effective parameters clauses
   * of the containing method or constructor.
   *
   * As a side-effect, this will register the analogs for each type element in a context or view
   * bound position. See: [[org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.syntheticParamClause]]
   *
   * This in turn is used in the `treeWalkUp` in [[org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceImpl.processQualifier]]
   */
  private def refreshAnalog(): Unit =
    this.parentOfType(classOf[ScTypeParam], strict = false)
      .flatMap(_.parentOfType(classOf[ScMethodLike], strict = false))
      .foreach(_.effectiveParameterClauses)

  @volatile
  private[this] var _analog: Option[ScTypeElement] = None

  def isRepeated: Boolean = {
    val nextNode = Option(getNextSibling).map(_.getNode)

    val isAsterisk = nextNode
      .exists(n => n.getElementType == ScalaTokenTypes.tIDENTIFIER && n.getText == "*")

    val notAnInfixType = (for {
      node <- nextNode
      next <- Option(node.getTreeNext)
    } yield next.getElementType != ScalaTokenTypes.tIDENTIFIER).getOrElse(true)

    isAsterisk && notAnInfixType
  }
}

object ScTypeElement {
  // java compatibility
  def calcType(typeElement: ScTypeElement): ScType = typeElement.calcType
}

trait ScDesugarizableTypeElement extends ScTypeElement {
  def desugarizedText: String

  def computeDesugarizedType: Option[ScTypeElement] = Option(typeElementFromText(desugarizedText))

  def typeElementFromText: String => ScTypeElement = createTypeElementFromText(_, getContext, this)

  override protected def innerType: TypeResult = computeDesugarizedType match {
    case Some(typeElement) => typeElement.getType
    case _ => Failure(ScalaBundle.message("cannot.desugarize.typename", typeName))
  }
}
