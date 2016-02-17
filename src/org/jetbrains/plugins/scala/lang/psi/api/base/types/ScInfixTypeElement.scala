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

trait ScInfixTypeElement extends ScTypeElement {
  def leftTypeElement = findChildByClassScala(classOf[ScTypeElement])

  def rightTypeElement = findChildrenByClassScala(classOf[ScTypeElement]) match {
    case Array(_, right) => Some(right)
    case _ => None
  }
}

trait ScReferenceableInfixTypeElement extends ScInfixTypeElement with ScDesugarizableToParametrizedTypeElement {
  override protected val typeName = "InfixType"

  def reference = findChildByClassScala(classOf[ScStableCodeReferenceElement])

  override def desugarizedText = s"${reference.getText}[${leftTypeElement.getText}, ${rightTypeElement.map(_.getText).getOrElse("Nothing")}]"
}