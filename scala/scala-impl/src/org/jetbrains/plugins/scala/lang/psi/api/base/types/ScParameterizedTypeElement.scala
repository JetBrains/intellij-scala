package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.macroAnnotations.Cached

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
  @Cached(BlockModificationTracker(this), this)
  override final def computeDesugarizedType: Option[ScParameterizedTypeElement] = {
    super.computeDesugarizedType match {
      case Some(typeElement: ScParameterizedTypeElement) => Some(typeElement)
      case _ => None
    }
  }
}