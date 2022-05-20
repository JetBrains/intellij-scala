package org.jetbrains.plugins.scala
package annotator
package gutter

import com.intellij.codeInsight.daemon.{LineMarkerInfo, LineMarkerProvider}
import com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

import javax.swing.Icon

final class RecursiveCallLineMarkerProvider extends LineMarkerProvider {

  import RecursiveCallLineMarkerProvider._
  import icons.Icons._

  override def getLineMarkerInfo(element: PsiElement): LineMarkerInfo[_ <: PsiElement] = {
    if (!GutterUtil.RecursionOption.isEnabled) {
      return null
    }

    element.getParent match {
      case function: ScFunctionDefinition if element.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER =>
        val references = function.recursiveReferencesGrouped
        if (references.tailRecursionOnly) {
          createLineMarkerInfo(
            TAIL_RECURSION,
            ScalaBundle.message("method.is.tail.recursive", _),
            function.nameId
          )
        }
        else if (!references.noRecursion) {
          createLineMarkerInfo(
            RECURSION,
            ScalaBundle.message("method.is.recursive", _),
            function.nameId
          )
        }
        else null
      case _ => null
    }
  }
}

object RecursiveCallLineMarkerProvider {
  private def createLineMarkerInfo(icon: Icon, psiElemToTooltip: String => String, element: PsiElement): LineMarkerInfo[PsiElement] =
    new LineMarkerInfo(
      element,
      element.getTextRange,
      icon,
      (e: PsiElement) => psiElemToTooltip(e.getText),
      null,
      Alignment.LEFT,
      () => psiElemToTooltip(element.getText)
    )
}
