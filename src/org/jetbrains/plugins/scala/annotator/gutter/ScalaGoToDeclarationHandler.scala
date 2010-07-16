package org.jetbrains.plugins.scala
package annotator
package gutter

import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.psi.{PsiElement, PsiReference}
import lang.lexer.ScalaTokenTypes
import lang.psi.api.base.{ScConstructor, ScStableCodeReferenceElement}
import lang.parser.ScalaElementTypes
import lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement}

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.11.2008
 */

class ScalaGoToDeclarationHandler extends GotoDeclarationHandler {
  def getGotoDeclarationTarget(sourceElement: PsiElement): PsiElement = {
    if (sourceElement == null) return null
    if (sourceElement.getLanguage != ScalaFileType.SCALA_LANGUAGE) return null;

    //todo: this keyword navigation
    null
  }
}