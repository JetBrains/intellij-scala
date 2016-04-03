package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

/**
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/
trait ScTupleTypeElement extends ScDesugarizableToParametrizedTypeElement {
  override protected val typeName = "TupleType"

  def typeList = findChildByClassScala(classOf[ScTypes])

  def components = typeList.types

  override def desugarizedText = {
    val componentsTexts = components.map(_.getText)
    s"_root_.scala.Tuple${componentsTexts.length}${componentsTexts.mkString("[", ",", "]")}"
  }
}