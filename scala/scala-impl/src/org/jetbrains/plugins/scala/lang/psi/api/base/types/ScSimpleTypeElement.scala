package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotationExpr, ScConstructorInvocation, ScPathElement, ScStableCodeReference}

trait ScSimpleTypeElement extends ScTypeElement {
  override protected val typeName = "SimpleType"

  def reference: Option[ScStableCodeReference] = findChild[ScStableCodeReference]

  def pathElement: ScPathElement = findChild[ScPathElement].get

  /**
   *  @return true for `val x: SomIdentifier.type`<br>
   *          false for `val y: String`
   */
  override def isSingleton: Boolean = {
    val typeToken = getNode.findChildByType(ScalaTokenTypes.kTYPE)
    typeToken != null
  }

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