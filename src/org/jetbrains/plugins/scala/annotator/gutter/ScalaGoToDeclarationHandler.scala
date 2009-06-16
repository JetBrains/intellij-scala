package org.jetbrains.plugins.scala.annotator.gutter

import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.psi.{PsiElement, PsiReference}
/**
 * User: Alexander Podkhalyuzin
 * Date: 22.11.2008
 */

class ScalaGoToDeclarationHandler extends GotoDeclarationHandler {
  def getGotoDeclarationTarget(sourceElement: PsiElement): PsiElement = {
    val res = new ArrayBuffer[PsiElement]
    val element = sourceElement match {
      case ref: PsiReference => res += ref.resolve; ref.resolve
      case _ => sourceElement
    }

    if (res.length == 0) return null
    else if (res.length == 1) return res(0)
    else null
  }
}