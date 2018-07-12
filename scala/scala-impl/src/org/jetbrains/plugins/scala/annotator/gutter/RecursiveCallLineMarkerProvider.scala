package org.jetbrains.plugins.scala.annotator.gutter

import java.util

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.{DaemonCodeAnalyzerSettings, LineMarkerInfo, LineMarkerProvider}
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.gutter.GutterIcons._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.{RecursionType, ScFunctionDefinition}

class RecursiveCallLineMarkerProvider(
  daemonSettings: DaemonCodeAnalyzerSettings,
  colorsManager:  EditorColorsManager
) extends LineMarkerProvider {
  override def getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo[_ <: PsiElement] = psiElement match {
    case ident if ident.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER =>
      ident.parent.flatMap {
        case method: ScFunctionDefinition => getRecursionMarker(method)
        case _                            => None
      }.orNull
    case _ => null
  }

  private[this] def getRecursionMarker(method: ScFunctionDefinition): Option[LineMarkerInfo[_ <: PsiElement]] =
    method.recursionType match {
      case RecursionType.OrdinaryRecursion =>
        new LineMarkerInfo[PsiElement](
          method.nameId,
          method.nameId.getTextRange,
          RECURSION_ICON,
          Pass.UPDATE_ALL,
          (e: PsiElement) => "Method '%s' is recursive".format(e.getText),
          null,
          GutterIconRenderer.Alignment.LEFT
        ).toOption
      case RecursionType.TailRecursion =>
        new LineMarkerInfo[PsiElement](
          method.nameId,
          method.nameId.getTextRange,
          TAIL_RECURSION_ICON,
          Pass.UPDATE_ALL,
          (e: PsiElement) => "Method '%s' is tail recursive".format(e.getText),
          null,
          GutterIconRenderer.Alignment.LEFT
        ).toOption
      case RecursionType.NoRecursion => None
    }

  override def collectSlowLineMarkers(
    list:       util.List[PsiElement],
    collection: util.Collection[LineMarkerInfo[_ <: PsiElement]]
  ): Unit = ()
}
