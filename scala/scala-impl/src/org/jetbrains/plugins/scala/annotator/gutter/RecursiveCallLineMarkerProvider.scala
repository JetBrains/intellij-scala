package org.jetbrains.plugins.scala
package annotator
package gutter

import java.{util => ju}

import com.intellij.codeInsight.daemon.{LineMarkerInfo, LineMarkerProvider}
import com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment
import com.intellij.psi.PsiElement
import javax.swing.Icon
import org.jetbrains.plugins.scala.lang.psi.api.statements.{RecursionType, ScFunctionDefinition}

final class RecursiveCallLineMarkerProvider extends LineMarkerProvider {

  import RecursionType._
  import RecursiveCallLineMarkerProvider._
  import icons.Icons._

  override def getLineMarkerInfo(element: PsiElement): LineMarkerInfo[_ <: PsiElement] =
    element.getParent match {
      case function: ScFunctionDefinition if element.getNode.getElementType == lang.lexer.ScalaTokenTypes.tIDENTIFIER =>
        val producer = function.recursionType match {
          case OrdinaryRecursion => createLineMarkerInfo(RECURSION, "method.is.recursive")
          case TailRecursion => createLineMarkerInfo(TAIL_RECURSION, "method.is.tail.recursive")
          case _ => Function.const(null) _
        }
        producer(function.nameId)
      case _ => null
    }

  override def collectSlowLineMarkers(list: ju.List[PsiElement],
                                      collection: ju.Collection[LineMarkerInfo[_ <: PsiElement]]): Unit = {}
}

object RecursiveCallLineMarkerProvider {

  private def createLineMarkerInfo(icon: Icon, key: String) =
    (element: PsiElement) => new LineMarkerInfo(
      element,
      element.getTextRange,
      icon,
      (e: PsiElement) => ScalaBundle.message(key, e.getText),
      null,
      Alignment.LEFT
    )
}
