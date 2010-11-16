package org.jetbrains.plugins.scala
package annotator
package gutter

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import lang.lexer.ScalaTokenTypes
import lang.psi.api.expr.ScSelfInvocation
import com.intellij.psi.PsiElement
/**
 * User: Alexander Podkhalyuzin
 * Date: 22.11.2008
 */

class ScalaGoToDeclarationHandler extends GotoDeclarationHandler {
  def getGotoDeclarationTarget(sourceElement: PsiElement): PsiElement = {
    if (sourceElement == null) return null
    if (sourceElement.getLanguage != ScalaFileType.SCALA_LANGUAGE) return null;

    if (sourceElement.getNode.getElementType == ScalaTokenTypes.kTHIS) {
      sourceElement.getParent match {
        case self: ScSelfInvocation => {
          self.bind match {
            case Some(elem) => return elem
            case None => return null
          }
        }
        case _ => return null
      }
    }

    null
  }
}