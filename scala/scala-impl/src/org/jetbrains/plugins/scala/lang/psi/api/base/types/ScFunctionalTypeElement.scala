package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScFunctionalTypeElement extends ScDesugarizableToParametrizedTypeElement {
  override protected val typeName = "FunctionalType"

  def paramTypeElement: ScTypeElement = findChildByClassScala(classOf[ScTypeElement])

  def returnTypeElement: Option[ScTypeElement] = findChildrenByClassScala(classOf[ScTypeElement]) match {
    case Array(_) => None
    case many => Some(many(1))
  }

  override def desugarizedText: String = {
    val paramTypes = (paramTypeElement match {
      case tuple: ScTupleTypeElement => tuple.components
      case parenthesised: ScParenthesisedTypeElement if parenthesised.innerElement.isEmpty => Seq.empty
      case other => Seq(other)
    }).map(_.getText) ++
      Seq(returnTypeElement.map(_.getText).getOrElse("Any"))
    s"_root_.scala.Function${paramTypes.length - 1}${paramTypes.mkString("[", ",", "]")}"
  }
}

object ScFunctionalTypeElement {
  def unapply(e: ScFunctionalTypeElement): Some[(ScTypeElement, Option[ScTypeElement])] =
    Some(e.paramTypeElement, e.returnTypeElement)
}
