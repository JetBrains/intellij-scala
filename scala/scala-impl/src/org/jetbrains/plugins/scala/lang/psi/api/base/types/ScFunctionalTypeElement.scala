package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType

trait ScFunctionalTypeElement extends ScDesugarizableToParametrizedTypeElement {
  override protected val typeName = "FunctionalType"

  def paramTypeElement: ScTypeElement = findChild[ScTypeElement].get

  def isContext: Boolean = findFirstChildByType(ScalaTokenType.ImplicitFunctionArrow).nonEmpty

  def returnTypeElement: Option[ScTypeElement] = findChildren[ScTypeElement] match {
    case Seq(_) => None
    case many => Some(many(1))
  }

  override def desugarizedText: String = {
    val typeParams = (paramTypeElement match {
      case tuple: ScTupleTypeElement                 => tuple.components
      case parenthesised: ScParenthesisedTypeElement => parenthesised.innerElement.toSeq
      case other                                     => Seq(other)
    }).map(_.getParamTypeText) :+
      returnTypeElement.map(_.getText).getOrElse("Any")

    val className =
      if (!isContext) "_root_.scala.Function"
      else            "_root_.scala.ContextFunction"

    s"$className${typeParams.length - 1}${typeParams.mkString("[", ",", "]")}"
  }
}

object ScFunctionalTypeElement {
  def unapply(e: ScFunctionalTypeElement): Some[(ScTypeElement, Option[ScTypeElement])] =
    Some(e.paramTypeElement, e.returnTypeElement)
}
