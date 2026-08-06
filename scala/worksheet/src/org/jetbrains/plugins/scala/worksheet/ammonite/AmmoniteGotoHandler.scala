package org.jetbrains.plugins.scala.worksheet.ammonite

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.implementation.iterator.ParentsIterator
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.worksheet.{GotoOriginalHandlerUtil, WorksheetBundle}

class AmmoniteGotoHandler extends GotoDeclarationHandler {
  override def getGotoDeclarationTargets(sourceElement: PsiElement, offset: Int, editor: Editor): Array[PsiElement] = {
    if (sourceElement == null) return PsiElement.EMPTY_ARRAY
    
    sourceElement.getContainingFile match {
      case ammoniteFile: ScalaFile if AmmoniteUtil.isAmmoniteFile(ammoniteFile) =>
      case _ => return PsiElement.EMPTY_ARRAY 
    }
    
    sourceElement.getParent match {
      case ref: ScReference =>
        ref.resolve() match {
          case scalaPsi: ScalaPsiElement => findPreImage(scalaPsi).toArray
          case _ => PsiElement.EMPTY_ARRAY
        }
      case _ => PsiElement.EMPTY_ARRAY
    }
  }

  override def getActionText(context: DataContext): String = WorksheetBundle.message("ammonite.goto")

  private def findPreImage(scalaPsi: PsiElement): Option[PsiElement] = {
    val originalElement = scalaPsi match {
      case mem: ScMember => mem.syntheticNavigationElement match {
        case null => scalaPsi
        case v => v
      }
      case _ => scalaPsi
    }

    GotoOriginalHandlerUtil.getGoToTarget(originalElement.getContainingFile).flatMap {
      case preImage: PsiFile =>
        Option(preImage.findElementAt(originalElement.getTextRange.getStartOffset - AmmoniteScriptWrappersHolder.getOffsetFix(preImage)))
      case _ => None
    }.map(findNamedParent)
  }

  private def findNamedParent(element: PsiElement) = new ParentsIterator(element, false).find(_.isInstanceOf[PsiNamedElement]).getOrElse(element)
}