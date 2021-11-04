package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScEnd

trait ScMarkerOwner { self: ScalaPsiElement =>
  final def beginMarker: PsiElement = findFirstChildByType(beginMarkerType).get

  final def endMarker: Option[PsiElement] = endMarkerParent.flatMap(_.lastChild).filterByType[ScEnd].map(_.marker)

  protected def beginMarkerType: IElementType

  protected def endMarkerParent: Option[PsiElement] = Some(this)
}

object ScMarkerOwner {
  def unapply(block: ScMarkerOwner): Option[(PsiElement, Option[PsiElement])] = Some((block.beginMarker, block.endMarker))
}