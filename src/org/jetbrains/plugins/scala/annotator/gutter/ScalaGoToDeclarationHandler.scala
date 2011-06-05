package org.jetbrains.plugins.scala
package annotator
package gutter

import lang.lexer.ScalaTokenTypes
import lang.psi.api.expr.ScSelfInvocation
import com.intellij.psi.PsiElement
import lang.psi.api.statements.ScFunction
import lang.psi.ScalaPsiUtil
import lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScObject, ScClass}
import lang.psi.api.statements.params.ScParameter
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler


/**
 * User: Alexander Podkhalyuzin
 * Date: 22.11.2008
 */

class ScalaGoToDeclarationHandler extends GotoDeclarationHandler {

  def getGotoDeclarationTargets(sourceElement: PsiElement): Array[PsiElement] = {
    if (sourceElement == null) return null
    if (sourceElement.getLanguage != ScalaFileType.SCALA_LANGUAGE) return null;

    if (sourceElement.getNode.getElementType == ScalaTokenTypes.kTHIS) {
      sourceElement.getParent match {
        case self: ScSelfInvocation => {
          self.bind match {
            case Some(elem) => return Array(elem)
            case None => return null
          }
        }
        case _ => return null
      }
    }

    if (sourceElement.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER) {
      val file = sourceElement.getContainingFile
      val ref = file.findReferenceAt(sourceElement.getTextRange.getStartOffset)
      if (ref == null) return null
      val resolve = ref.resolve
      resolve match {
        case fun: ScFunction =>
          val clazz = fun.getContainingClass
          if (fun.name == "copy" && fun.isSyntheticCopy) {
            clazz match {
              case td: ScClass if td.isCase =>
                return Array(td)
              case _ =>
            }
          }
          ScalaPsiUtil.getCompanionModule(clazz) match {
            case Some(td: ScClass) if td.isCase && td.fakeCompanionModule != None =>
              return Array(td)
            case _ =>
          }
          clazz match {
            case o: ScObject if o.objectSyntheticMembers.contains(fun) =>
              return ScalaPsiUtil.getCompanionModule(clazz) match {
                case Some(c: ScClass) => Array(o, c) // Offer navigation to the class and object for apply/unapply.
                case _ => Array(o)
              }
            case td: ScTypeDefinition if td.syntheticMembers.contains(fun) =>
              return Array(td)
            case _ =>
              return null
          }
        case o: ScObject =>
          ScalaPsiUtil.getCompanionModule(o) match {
            case Some(td: ScClass) if td.isCase && td.fakeCompanionModule != None => return Array(td)
            case _ => return null
          }
        case param: ScParameter =>
          return ScalaPsiUtil.parameterForSyntheticParameter(param).map(Array[PsiElement](_)).orNull
        case _ => return null
      }
    }
    null
  }
}