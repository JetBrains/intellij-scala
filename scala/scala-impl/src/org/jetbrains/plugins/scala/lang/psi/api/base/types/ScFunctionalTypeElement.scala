package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.api._


/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScFunctionalTypeElementBase extends ScDesugarizableToParametrizedTypeElement { this: ScFunctionalTypeElement =>
  override protected val typeName = "FunctionalType"

  def paramTypeElement: ScTypeElement = findChild[ScTypeElement].get

  def returnTypeElement: Option[ScTypeElement] = findChildren[ScTypeElement] match {
    case Seq(_) => None
    case many => Some(many(1))
  }

  override def desugarizedText: String = {
    val paramTypes = (paramTypeElement match {
      case tuple: ScTupleTypeElement => tuple.components
      case parenthesised: ScParenthesisedTypeElement => parenthesised.innerElement.toSeq
      case other => Seq(other)
    }).map(_.getParamTypeText) ++
      Seq(returnTypeElement.map(_.getText).getOrElse("Any"))
    s"_root_.scala.Function${paramTypes.length - 1}${paramTypes.mkString("[", ",", "]")}"
  }
}

abstract class ScFunctionalTypeElementCompanion {
  def unapply(e: ScFunctionalTypeElement): Some[(ScTypeElement, Option[ScTypeElement])] =
    Some(e.paramTypeElement, e.returnTypeElement)
}