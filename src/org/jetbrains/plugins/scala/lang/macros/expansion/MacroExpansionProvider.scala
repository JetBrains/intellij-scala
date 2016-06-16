package org.jetbrains.plugins.scala.lang.macros.expansion

import java.awt.event.MouseEvent
import java.util

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.{GutterIconNavigationHandler, LineMarkerInfo, LineMarkerProvider, RelatedItemLineMarkerInfo}
import com.intellij.icons.AllIcons
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.{PsiElement, PsiManager}
import com.intellij.util.Function
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

import scala.collection.JavaConversions._

class MacroExpansionProvider extends LineMarkerProvider {

  def getLineMarkerInfo(element: PsiElement): LineMarkerInfo[_ <: PsiElement] = null

  override def collectSlowLineMarkers(elements: util.List[PsiElement], result: util.Collection[LineMarkerInfo[_ <: PsiElement]]) = {
//    collectReflectExpansions(elements, result)
    collectMetaExpansions(elements, result)
  }

  def collectMetaExpansions(elements: util.List[PsiElement], result: util.Collection[LineMarkerInfo[_ <: PsiElement]]) = {
    elements foreach {
      case o: ScAnnotationsHolder =>
        val metaAnnot = o.annotations.find(_.isMetaAnnotation)
        if (metaAnnot.isDefined) {
          val item = new RelatedItemLineMarkerInfo[PsiElement](o, o.getTextRange,
            AllIcons.General.ExpandAllHover,
            Pass.UPDATE_OVERRIDDEN_MARKERS, new Function[PsiElement, String] {
              override def fun(param: PsiElement): String = "Expand scala.meta macro"
            },
            new GutterIconNavigationHandler[PsiElement] {
              override def navigate(e: MouseEvent, elt: PsiElement): Unit = MacroExpandAction.expandMetaAnnotation(metaAnnot.get)
            }, GutterIconRenderer.Alignment.RIGHT, util.Arrays.asList[GotoRelatedItem]()
          )
          result.add(item)
        }
      case _ =>
    }
  }

  def collectReflectExpansions(elements: util.List[PsiElement], result: util.Collection[LineMarkerInfo[_ <: PsiElement]]): Unit = {
    val expansions = elements.map(p => (p, p.getCopyableUserData(MacroExpandAction.EXPANDED_KEY))).filter(_._2 != null)

    val res = expansions.map { case (current, saved) =>
      new RelatedItemLineMarkerInfo[PsiElement](current, current.getTextRange, Icons.NO_SCALA_SDK, Pass.LINE_MARKERS,
        new Function[PsiElement, String] {
          def fun(param: PsiElement): String = "Undo Macro Expansion"
        },
        new GutterIconNavigationHandler[PsiElement] {
          def navigate(mouseEvent: MouseEvent, elt: PsiElement) = {
            inWriteAction {
              val newPsi = ScalaPsiElementFactory.createBlockExpressionWithoutBracesFromText(saved, PsiManager.getInstance(current.getProject))
              current.replace(newPsi)
              saved
            }
          }
        },
        GutterIconRenderer.Alignment.RIGHT, util.Arrays.asList[GotoRelatedItem]())
    }
    result.addAll(res)
  }
}
