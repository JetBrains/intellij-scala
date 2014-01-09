package org.jetbrains.plugins.scala
package annotator
package gutter

import lang.lexer.ScalaTokenTypes
import lang.psi.api.statements.ScFunction
import lang.psi.ScalaPsiUtil
import lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScObject, ScClass}
import lang.psi.api.statements.params.ScParameter
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import lang.resolve.ResolvableReferenceElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.actionSystem.DataContext
import lang.psi.api.expr.{ScAssignStmt, ScMethodCall, ScSelfInvocation}
import extensions.toPsiMemberExt
import com.intellij.psi.{PsiFile, PsiMethod, PsiNamedElement, PsiElement}

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.11.2008
 */

class ScalaGoToDeclarationHandler extends GotoDeclarationHandler {

  def getActionText(context: DataContext): String = null

  def getGotoDeclarationTargets(_sourceElement: PsiElement, offset: Int, editor: Editor): Array[PsiElement] = {
    if (_sourceElement == null) return null
    val containingFile: PsiFile = _sourceElement.getContainingFile
    if (containingFile == null) return null
    val sourceElement = containingFile.findElementAt(offset)
    if (sourceElement == null) return null
    if (sourceElement.getLanguage != ScalaFileType.SCALA_LANGUAGE) return null

    if (sourceElement.getNode.getElementType == ScalaTokenTypes.tASSIGN) {
      return sourceElement.getParent match {
        case assign: ScAssignStmt =>
          val elem = assign.assignNavigationElement
          Option(elem).toArray
        case _ => null
      }
    }

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
      val targets = ref match {
        case resRef: ResolvableReferenceElement =>
          resRef.bind() match {
            case Some(x) =>
              /**
               * Extra targets:
               *
               * actualElement              type alias used to access a constructor.
               *                            See also [[org.jetbrains.plugins.scala.findUsages.TypeAliasUsagesSearcher]]
               * innerResolveResult#element apply method
               */
              val all = Seq(x.getActualElement, x.element) ++ x.innerResolveResult.map(_.getElement)
              x.element match {
                case f: ScFunction if f.isSynthetic => Seq(x.getActualElement).flatMap(goToTargets)
                case c: PsiMethod if c.isConstructor =>
                  val clazz = c.containingClass
                  if (clazz == x.getActualElement) Seq(x.element).flatMap(goToTargets)
                  else all.distinct flatMap goToTargets
                case _ =>
                  all.distinct flatMap goToTargets
              }
            case None => return null
          }
        case r =>
          Set(r.resolve()) flatMap goToTargets
      }
      return targets.toArray
    }
    null
  }

  private def goToTargets(element: PsiElement): Seq[PsiElement] = {
    element match {
      case null => Seq.empty
      case fun: ScFunction =>
        val clazz = fun.containingClass
        if (fun.name == "copy" && fun.isSyntheticCopy) {
          clazz match {
            case td: ScClass if td.isCase =>
              return Seq(td)
            case _ =>
          }
        }
        ScalaPsiUtil.getCompanionModule(clazz) match {
          case Some(td: ScClass) if td.isCase && td.fakeCompanionModule != None =>
            return Seq(td)
          case _ =>
        }

        clazz match {
          case o: ScObject if o.objectSyntheticMembers.contains(fun) =>
            ScalaPsiUtil.getCompanionModule(clazz) match {
              case Some(c: ScClass) => Seq(o, c) // Offer navigation to the class and object for apply/unapply.
              case _ => Seq(o)
            }
          case td: ScTypeDefinition if td.syntheticMethodsNoOverride.contains(fun) => Seq(td)
          case _ => fun.getSyntheticNavigationElement match {
            case Some(element) => Seq(element)
            case None => Seq(element)
          }
        }
      case o: ScObject =>
        ScalaPsiUtil.getCompanionModule(o) match {
          case Some(td: ScClass) if td.isCase && td.fakeCompanionModule != None => Seq(td)
          case _ => Seq(element)
        }
      case param: ScParameter =>
        ScalaPsiUtil.parameterForSyntheticParameter(param).map(Seq[PsiElement](_)).getOrElse(Seq[PsiElement](element))
      case _ => Seq(element)
    }
  }
}