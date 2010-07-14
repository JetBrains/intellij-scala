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
    if (sourceElement.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER) {
      sourceElement.getParent match {
        case parent: ScStableCodeReferenceElement => {
          parent.getParent match {
            case parent: ScSimpleTypeElement => {
              parent.getParent match {
                case constr: ScConstructor => {
                  val res = constr.resolveConstructorMethod
                  if (res.length == 1) return res.apply(0).element
                }
                case p: ScParameterizedTypeElement => {
                  p.getParent match {
                    case constr: ScConstructor => {
                      val res = constr.resolveConstructorMethod
                      if (res.length == 1) return res.apply(0).element
                    }
                    case _ =>
                  }
                }
                case _ =>
              }
            }
            case _ =>
          }
        }
        case _ =>
      }
    }

    //todo: this keyword navigation
    null
  }
}