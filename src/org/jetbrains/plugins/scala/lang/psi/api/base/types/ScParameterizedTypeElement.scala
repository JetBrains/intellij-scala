package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

trait ScParameterizedTypeElement extends ScTypeElement {

  def typeArgList: ScTypeArgs

  def typeElement: ScTypeElement

  def findConstructor: Option[ScConstructor]

  def desugarizedExistentialType: Option[ScTypeElement]
}

object ScParameterizedTypeElement {
  def unapply(pte: ScParameterizedTypeElement): Option[(ScTypeElement, Seq[ScTypeElement])] = {
    pte match {
      case null => None
      case _ => Some(pte.typeElement, pte.typeArgList.typeArgs)
    }
  }
}