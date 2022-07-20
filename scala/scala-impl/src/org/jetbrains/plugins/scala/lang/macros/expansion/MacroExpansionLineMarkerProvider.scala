package org.jetbrains.plugins.scala.lang.macros.expansion

import com.intellij.codeInsight.daemon._
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiElement, PsiElementVisitor, PsiWhiteSpace}
import com.intellij.util.Function
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings.ScalaMetaMode

import java.util
import javax.swing.Icon
import scala.collection.mutable.ArrayBuffer

abstract class MacroExpansionLineMarkerProvider extends RelatedItemLineMarkerProvider {

  protected type Marker = RelatedItemLineMarkerInfo[_]
  protected type Markers = util.Collection[_ >: Marker]

  override def collectNavigationMarkers(element: PsiElement, result: Markers): Unit = {
    if (ScalaProjectSettings.getInstance(element.getProject).getScalaMetaMode == ScalaMetaMode.Disabled)
      return
    if (element.getNode == null || element.getNode.getElementType != ScalaTokenTypes.tIDENTIFIER )
      return

    getExpandMarker(element).foreach(result.add)
  }

  protected def getExpandMarker(element: PsiElement): Option[Marker]

  //noinspection SameParameterValue
  protected def createMarker(
    element: PsiElement, icon: Icon, @Nls caption: String,
    navigationHandler: GutterIconNavigationHandler[PsiElement]
  ): Marker = {
    val startOffset = element.getTextRange.getStartOffset
    val range = new TextRange(startOffset, startOffset)

    val tooltipProvider: Function[PsiElement, String] =
      _ => caption

    new RelatedItemLineMarkerInfo[PsiElement](element, range, icon,
      tooltipProvider,
      navigationHandler,
      GutterIconRenderer.Alignment.RIGHT,
      () => util.Collections.emptyList
    )
  }

  protected def createExpandMarker(elem: PsiElement, navigationHandler: GutterIconNavigationHandler[PsiElement]): Marker = {
    createMarker(elem, AllIcons.Actions.Expandall, ScalaBundle.message("expand.macro"), navigationHandler)
  }

  protected def reformatCode(psi: PsiElement): PsiElement = {
    val res = CodeStyleManager.getInstance(psi.getProject).reformat(psi)
    val tobeDeleted = new ArrayBuffer[PsiElement]
    val v = new PsiElementVisitor {
      override def visitElement(element: PsiElement): Unit = {
        if (element.getNode.getElementType == ScalaTokenTypes.tSEMICOLON) {
          val file = element.getContainingFile
          val nextLeaf = file.findElementAt(element.getTextRange.getEndOffset)
          if (nextLeaf.isInstanceOf[PsiWhiteSpace] && nextLeaf.getText.contains("\n")) {
            tobeDeleted += element
          }
        }
        element.acceptChildren(this)
      }
    }
    v.visitElement(res)
    tobeDeleted.foreach(_.delete())
    res
  }

}
