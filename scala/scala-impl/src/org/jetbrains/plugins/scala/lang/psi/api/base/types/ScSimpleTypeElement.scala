package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

trait ScSimpleTypeElement extends ScTypeElement {
  override protected val typeName = "SimpleType"

  def reference: Option[ScStableCodeReference] = findChild[ScStableCodeReference]

  def pathElement: ScPathElement = findChild[ScPathElement].get

  override def singleton: Boolean = getNode.findChildByType(lang.lexer.ScalaTokenTypes.kTYPE) != null

  def annotation: Boolean = ScalaPsiUtil.getContext(this, 2).exists(_.isInstanceOf[ScAnnotationExpr])

  def findConstructorInvocation: Option[ScConstructorInvocation] = {
    val constrInvocationTypeElement = getContext match {
      case typeElement: ScParameterizedTypeElement => typeElement
      case _ => this
    }

    constrInvocationTypeElement.getContext match {
      case constrInvocation: ScConstructorInvocation => Some(constrInvocation)
      case _ => None
    }
  }
}

object ScSimpleTypeElement {

  def unapply(typeElement: ScSimpleTypeElement): Option[ScStableCodeReference] =
    typeElement.reference

  object unwrapped {

    def unapply(typeElement: ScTypeElement): Option[ScStableCodeReference] =
      typeElement match {
        case ScSimpleTypeElement(reference) => Some(reference)
        case ScParameterizedTypeElement(ScSimpleTypeElement(reference), _) => Some(reference)
        case _ => None
      }
  }
}