package org.jetbrains.plugins.scala.lang.psi.api.base
package types

import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cached}

trait ScParameterizedTypeElement extends ScDesugarizableTypeElement {
  override protected val typeName = "ParametrizedType"

  def typeArgList: ScTypeArgs = findChild[ScTypeArgs].get

  def typeElement: ScTypeElement = findChild[ScTypeElement].get

  def findConstructorInvocation: Option[ScConstructorInvocation] = getContext match {
    case constrInvocation: ScConstructorInvocation => Some(constrInvocation)
    case _ => None
  }
}

object ScParameterizedTypeElement {
  def unapply(pte: ScParameterizedTypeElement): Option[(ScTypeElement, Seq[ScTypeElement])] = {
    pte match {
      case null => None
      case _ => Some(pte.typeElement, pte.typeArgList.typeArgs)
    }
  }
}

trait ScDesugarizableToParametrizedTypeElement extends ScDesugarizableTypeElement {
  override final def computeDesugarizedType: Option[ScParameterizedTypeElement] = _computeDesugarizedType()

  private val _computeDesugarizedType = cached("computeDesugarizedType", BlockModificationTracker(this), () => {
    super.computeDesugarizedType match {
      case Some(typeElement: ScParameterizedTypeElement) => Some(typeElement)
      case _ => None
    }
  })
}