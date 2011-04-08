package org.jetbrains.plugins.scala
package annotator
package gutter

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase
import lang.lexer.ScalaTokenTypes
import lang.psi.api.expr.ScSelfInvocation
import com.intellij.psi.PsiElement
import lang.psi.api.statements.ScFunction
import lang.psi.ScalaPsiUtil
import lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScObject, ScClass}
import lang.psi.api.statements.params.ScParameter


/**
 * User: Alexander Podkhalyuzin
 * Date: 22.11.2008
 */

// TODO we can return multiple targets with IDEA 10.5. Should we return both case class and companion object for A(x)?
class ScalaGoToDeclarationHandler extends GotoDeclarationHandlerBase {
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
                return td
              case _ =>
            }
          }
          ScalaPsiUtil.getCompanionModule(clazz) match {
            case Some(td: ScClass) if td.isCase && td.fakeCompanionModule != None =>
              return td
            case _ =>
          }
          clazz match {
            case o: ScObject if o.objectSyntheticMembers.contains(fun) =>
              return o
            case td: ScTypeDefinition if td.syntheticMembers.contains(fun) =>
              return td
            case _ =>
              return null
          }
        case o: ScObject =>
          ScalaPsiUtil.getCompanionModule(o) match {
            case Some(td: ScClass) if td.isCase && td.fakeCompanionModule != None => return td
            case _ => return null
          }
        case param: ScParameter =>
          return ScalaPsiUtil.parameterForSyntheticParameter(param).orNull
        case _ => return null
      }
    }
    null
  }
}