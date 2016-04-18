package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

/** 
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

trait ScParameterizedTypeElement extends ScDesugarizableTypeElement {
  override protected val typeName = "ParametrizedType"

  def typeArgList = findChildByClassScala(classOf[ScTypeArgs])

  def typeElement = findChildByClassScala(classOf[ScTypeElement])

  def findConstructor = getContext match {
    case constructor: ScConstructor => Some(constructor)
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
  @Cached(synchronized = true, ModCount.getBlockModificationCount, this)
  override final def computeDesugarizedType: Option[ScParameterizedTypeElement] = super.computeDesugarizedType match {
    case Some(typeElement: ScParameterizedTypeElement) => Some(typeElement)
    case _ => None
  }
}