package org.jetbrains.plugins.scala.worksheet.ammonite

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.implementation.iterator.ParentsIterator
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.worksheet.GotoOriginalHandlerUtil

/**
  * User: Dmitry.Naydanov
  * Date: 03.08.17.
  */
class AmmoniteGotoHandler extends GotoDeclarationHandler {
  override def getGotoDeclarationTargets(sourceElement: PsiElement, offset: Int, editor: Editor): Array[PsiElement] = {
    if (sourceElement == null) return PsiElement.EMPTY_ARRAY
    
    sourceElement.getContainingFile match {
      case ammoniteFile: ScalaFile if AmmoniteUtil.isAmmoniteFile(ammoniteFile) =>
      case _ => return PsiElement.EMPTY_ARRAY 
    }
    
    sourceElement.getParent match {
      case ref: ScReferenceExpression => 
        ref.resolve() match {
          case scalaPsi: ScalaPsiElement if GotoOriginalHandlerUtil.findPsi(scalaPsi.getContainingFile).isDefined => 
            new ParentsIterator(scalaPsi, false).collectFirst {
              case p if GotoOriginalHandlerUtil.findPsi(p).isDefined => GotoOriginalHandlerUtil.findPsi(p).get
            }.toArray
          case _ => PsiElement.EMPTY_ARRAY
        }
      case _ => PsiElement.EMPTY_ARRAY
    }
  }
  
  

  override def getActionText(context: DataContext): String = "GoTo"
}
