package org.jetbrains.plugins.scala.annotator.gutter

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.psi.PsiElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.11.2008
 */

class ScalaGoToDeclarationHandler extends GotoDeclarationHandler {
  def getGotoDeclarationTarget(sourceElement: PsiElement): PsiElement = {
    return null
  }
}