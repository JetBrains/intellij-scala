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

trait ScInfixTypeElement extends ScTypeElement with ScGenericInfixNode[ScTypeElement] {
  def leftTypeElement: ScTypeElement = findChildByClassScala(classOf[ScTypeElement])

  def operation: ScStableCodeReferenceElement = findChildByClassScala(classOf[ScStableCodeReferenceElement])

  def rightTypeElement: Option[ScTypeElement] = findChildrenByClassScala(classOf[ScTypeElement]) match {
    case Array(_, right) => Some(right)
    case _ => None
  }


  override def rightOperand: Option[TreeMember[ScTypeElement]] = rightTypeElement

  override def leftOperand: TreeMember[ScTypeElement] = leftTypeElement
}

object ScInfixTypeElement {
  /** Extracts the left and right type elements of the given infix type. */
  def unapply(arg: ScInfixTypeElement): Option[(ScTypeElement, ScStableCodeReferenceElement, Option[ScTypeElement])]
  = Some((arg.leftTypeElement, arg.operation, arg.rightTypeElement))
}


trait ScReferenceableInfixTypeElement extends ScInfixTypeElement with ScDesugarizableToParametrizedTypeElement {
  override protected val typeName = "InfixType"

  def reference: ScStableCodeReferenceElement = findChildByClassScala(classOf[ScStableCodeReferenceElement])

  override def desugarizedText = s"${reference.getText}[${leftTypeElement.getText}, ${rightTypeElement.map(_.getText).getOrElse("Nothing")}]"
}